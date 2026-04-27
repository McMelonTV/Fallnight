package xyz.fallnight.server.domain.auction;

import java.time.Instant;
import java.util.Objects;

public final class AuctionListing {
    private String id;
    private String seller;
    private AuctionItem item;
    private int price;
    private Instant createdAt;
    private long durationSeconds;
    private Instant expireAt;
    private String buyer;

    public AuctionListing() {
        this.item = new AuctionItem();
        this.createdAt = Instant.now();
        this.durationSeconds = 86_400L;
        this.expireAt = createdAt.plusSeconds(durationSeconds);
    }

    public AuctionListing(String id, String seller, AuctionItem item, int price, Instant createdAt, long durationSeconds, Instant expireAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.seller = normalizeName(seller);
        this.item = Objects.requireNonNull(item, "item");
        this.price = Math.max(0, price);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.durationSeconds = Math.max(1L, durationSeconds);
        this.expireAt = Objects.requireNonNull(expireAt, "expireAt");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSeller() {
        return seller;
    }

    public void setSeller(String seller) {
        this.seller = normalizeName(seller);
    }

    public AuctionItem getItem() {
        return item;
    }

    public void setItem(AuctionItem item) {
        this.item = item == null ? new AuctionItem() : item;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = Math.max(0, price);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = Math.max(1L, durationSeconds);
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt == null ? createdAt.plusSeconds(durationSeconds) : expireAt;
    }

    public String getBuyer() {
        return buyer;
    }

    public void setBuyer(String buyer) {
        this.buyer = normalizeNameOrNull(buyer);
    }

    public boolean isExpired(Instant now) {
        return expireAt != null && !expireAt.isAfter(now);
    }

    public boolean isSold() {
        return buyer != null && !buyer.isBlank();
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private static String normalizeNameOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
