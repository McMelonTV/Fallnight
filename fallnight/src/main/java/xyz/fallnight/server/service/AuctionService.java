package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.auction.AuctionClaim;
import xyz.fallnight.server.domain.auction.AuctionItem;
import xyz.fallnight.server.domain.auction.AuctionListing;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.auction.AuctionRepository;
import net.minestom.server.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AuctionService {
    private static final long DEFAULT_DURATION_SECONDS = 86_400L;

    private final AuctionRepository repository;
    private final PlayerProfileService profileService;
    private final ConcurrentMap<String, AuctionListing> activeListings;
    private final ConcurrentMap<String, List<AuctionClaim>> claimsByOwner;
    private long nextId;

    public AuctionService(AuctionRepository repository, PlayerProfileService profileService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.activeListings = new ConcurrentHashMap<>();
        this.claimsByOwner = new ConcurrentHashMap<>();
        this.nextId = 1L;
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized void loadAll() throws IOException {
        AuctionRepository.AuctionState state = repository.load();
        activeListings.clear();
        for (AuctionListing listing : state.listings()) {
            activeListings.put(listing.getId(), listing);
        }

        claimsByOwner.clear();
        for (Map.Entry<String, List<AuctionClaim>> entry : state.claimsByOwner().entrySet()) {
            claimsByOwner.put(normalize(entry.getKey()), new ArrayList<>(entry.getValue()));
        }

        nextId = Math.max(1L, state.nextId());
    }

    public synchronized List<AuctionListing> listActive() {
        return activeListings.values().stream()
                .sorted(Comparator.comparing(AuctionListing::getCreatedAt).thenComparing(AuctionListing::getId))
                .toList();
    }

    public synchronized List<AuctionListing> listOwned(String username) {
        String normalized = normalize(username);
        return activeListings.values().stream()
                .filter(listing -> normalize(listing.getSeller()).equals(normalized))
                .sorted(Comparator.comparing(AuctionListing::getCreatedAt).reversed())
                .toList();
    }

    public synchronized int activeListingCount(String username) {
        return listOwned(username).size();
    }

    public synchronized List<AuctionClaim> listClaims(String username) {
        List<AuctionClaim> claims = claimsByOwner.get(normalize(username));
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        return List.copyOf(claims);
    }

    public synchronized List<AuctionClaim> reclaimClaims(String username) {
        List<AuctionClaim> claims = claimsByOwner.remove(normalize(username));
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        persist();
        return List.copyOf(claims);
    }

    public synchronized Optional<AuctionClaim> reclaimClaim(String username, String listingId) {
        List<AuctionClaim> claims = claimsByOwner.get(normalize(username));
        if (claims == null || claims.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < claims.size(); i++) {
            AuctionClaim claim = claims.get(i);
            if (Objects.equals(claim.getListingId(), listingId)) {
                claims.remove(i);
                if (claims.isEmpty()) {
                    claimsByOwner.remove(normalize(username));
                }
                persist();
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public synchronized void restoreClaim(String username, AuctionClaim claim) {
        if (claim == null) {
            return;
        }
        claimsByOwner.computeIfAbsent(normalize(username), ignored -> new ArrayList<>()).add(0, claim);
        persist();
    }

    public synchronized Optional<AuctionListing> findById(String listingId) {
        if (listingId == null || listingId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeListings.get(listingId.trim()));
    }

    public synchronized AuctionListing createListing(String seller, ItemStack itemStack, int price) {
        return createListing(seller, itemStack, price, DEFAULT_DURATION_SECONDS);
    }

    public synchronized AuctionListing createListing(String seller, ItemStack itemStack, int price, long durationSeconds) {
        if (seller == null || seller.isBlank()) {
            throw new IllegalArgumentException("seller is required");
        }
        Objects.requireNonNull(itemStack, "itemStack");

        Instant now = Instant.now();
        long duration = Math.max(1L, durationSeconds);
        String id = Long.toString(nextId++);
        AuctionListing listing = new AuctionListing(
                id,
                seller,
                AuctionItem.from(itemStack),
                Math.max(0, price),
                now,
                duration,
                now.plusSeconds(duration)
        );
        activeListings.put(listing.getId(), listing);
        persist();
        return listing;
    }

    public synchronized PurchaseResult buyListing(String listingId, String buyerUsername) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return PurchaseResult.notFound();
        }

        if (buyerUsername == null || buyerUsername.isBlank()) {
            return PurchaseResult.invalidBuyer();
        }
        if (normalize(listing.getSeller()).equals(normalize(buyerUsername))) {
            return PurchaseResult.cannotBuyOwn();
        }

        Instant now = Instant.now();
        if (listing.isExpired(now)) {
            expireListing(listing, now);
            persist();
            return PurchaseResult.expired();
        }

        UserProfile buyer = profileService.getOrCreateByUsername(buyerUsername);
        if (!buyer.withdraw(listing.getPrice())) {
            return PurchaseResult.insufficientBalance();
        }

        UserProfile seller = profileService.getOrCreateByUsername(listing.getSeller());
        seller.deposit(listing.getPrice());
        profileService.save(buyer);
        profileService.save(seller);

        listing.setBuyer(buyerUsername);
        activeListings.remove(listing.getId());
        persist();
        return PurchaseResult.success(listing);
    }

    public synchronized Optional<AuctionListing> cancelListing(String sellerUsername, String listingId) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return Optional.empty();
        }
        if (!normalize(listing.getSeller()).equals(normalize(sellerUsername))) {
            return Optional.empty();
        }
        activeListings.remove(listing.getId());
        persist();
        return Optional.of(listing);
    }

    public synchronized void restoreListing(AuctionListing listing) {
        if (listing == null) {
            return;
        }
        listing.setBuyer(null);
        activeListings.putIfAbsent(listing.getId(), listing);
        persist();
    }

    public synchronized void rollbackPurchase(AuctionListing listing, String buyerUsername) {
        if (listing == null) {
            return;
        }
        listing.setBuyer(null);
        activeListings.putIfAbsent(listing.getId(), listing);

        UserProfile buyer = profileService.getOrCreateByUsername(buyerUsername);
        UserProfile seller = profileService.getOrCreateByUsername(listing.getSeller());
        buyer.deposit(listing.getPrice());
        if (!seller.withdraw(listing.getPrice())) {
            seller.setBalance(seller.getBalance() - listing.getPrice());
        }
        profileService.save(buyer);
        profileService.save(seller);
        persist();
    }

    public synchronized int expireListings() {
        return expireListings(Instant.now());
    }

    public synchronized int expireListings(Instant now) {
        int expired = 0;
        List<AuctionListing> listings = new ArrayList<>(activeListings.values());
        for (AuctionListing listing : listings) {
            if (listing.isSold()) {
                continue;
            }
            if (!listing.isExpired(now)) {
                continue;
            }
            expireListing(listing, now);
            expired++;
        }

        if (expired > 0) {
            persist();
        }
        return expired;
    }

    public synchronized void saveAll() {
        persist();
    }

    public synchronized void resetAll() {
        activeListings.clear();
        claimsByOwner.clear();
        nextId = 1L;
        persist();
    }

    private void expireListing(AuctionListing listing, Instant now) {
        activeListings.remove(listing.getId());
        AuctionClaim claim = new AuctionClaim(
                listing.getId(),
                listing.getSeller(),
                listing.getItem(),
                "EXPIRED",
                now
        );
        claimsByOwner.computeIfAbsent(normalize(listing.getSeller()), ignored -> new ArrayList<>()).add(claim);
    }

    private void persist() {
        Map<String, List<AuctionClaim>> claims = new LinkedHashMap<>();
        for (Map.Entry<String, List<AuctionClaim>> entry : claimsByOwner.entrySet()) {
            claims.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        AuctionRepository.AuctionState state = new AuctionRepository.AuctionState(
                List.copyOf(activeListings.values()),
                Map.copyOf(claims),
                Math.max(1L, nextId)
        );

        try {
            repository.save(state);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public enum PurchaseStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_BUYER,
        CANNOT_BUY_OWN,
        INSUFFICIENT_BALANCE,
        EXPIRED
    }

    public record PurchaseResult(PurchaseStatus status, AuctionListing listing) {
        public static PurchaseResult success(AuctionListing listing) {
            return new PurchaseResult(PurchaseStatus.SUCCESS, listing);
        }

        public static PurchaseResult notFound() {
            return new PurchaseResult(PurchaseStatus.NOT_FOUND, null);
        }

        public static PurchaseResult invalidBuyer() {
            return new PurchaseResult(PurchaseStatus.INVALID_BUYER, null);
        }

        public static PurchaseResult cannotBuyOwn() {
            return new PurchaseResult(PurchaseStatus.CANNOT_BUY_OWN, null);
        }

        public static PurchaseResult insufficientBalance() {
            return new PurchaseResult(PurchaseStatus.INSUFFICIENT_BALANCE, null);
        }

        public static PurchaseResult expired() {
            return new PurchaseResult(PurchaseStatus.EXPIRED, null);
        }
    }
}
