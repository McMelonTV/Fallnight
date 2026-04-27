package xyz.fallnight.server.persistence.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.auction.AuctionClaim;
import xyz.fallnight.server.domain.auction.AuctionItem;
import xyz.fallnight.server.domain.auction.AuctionListing;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JsonAuctionRepository implements AuctionRepository {
    private static final String[] MATERIAL_KEYS = {"material", "type", "id", "minecraftId", "name"};
    private static final String[] AMOUNT_KEYS = {"amount", "count", "qty"};

    private final Path auctionFile;
    private final ObjectMapper mapper;

    public JsonAuctionRepository(Path auctionFile) {
        this(auctionFile, JacksonMappers.jsonMapper());
    }

    public JsonAuctionRepository(Path auctionFile, ObjectMapper mapper) {
        this.auctionFile = auctionFile;
        this.mapper = mapper;
    }

    @Override
    public AuctionState load() throws IOException {
        Path parent = auctionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(auctionFile)) {
            return new AuctionState(List.of(), Map.of(), 1L);
        }

        JsonNode root = mapper.readTree(auctionFile.toFile());
        ArrayNode listingsNode = listingsNode(root);
        List<AuctionListing> listings = new ArrayList<>();
        long maxNumericId = 0L;
        for (JsonNode listingNode : listingsNode) {
            AuctionListing listing = readListing(listingNode);
            listings.add(listing);
            long numericId = parseLong(listing.getId(), 0L);
            maxNumericId = Math.max(maxNumericId, numericId);
        }

        Map<String, List<AuctionClaim>> claimsByOwner = readClaims(root.get("claims"));
        long nextId = longValue(root, "nextId", maxNumericId + 1L);
        if (nextId <= 0L) {
            nextId = maxNumericId + 1L;
        }
        if (nextId <= 0L) {
            nextId = 1L;
        }
        return new AuctionState(List.copyOf(listings), Map.copyOf(claimsByOwner), nextId);
    }

    @Override
    public void save(AuctionState state) throws IOException {
        Path parent = auctionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectNode root = mapper.createObjectNode();
        ArrayNode listings = root.putArray("auction");
        for (AuctionListing listing : state.listings()) {
            listings.add(writeListing(listing));
        }

        ObjectNode claims = root.putObject("claims");
        for (Map.Entry<String, List<AuctionClaim>> entry : state.claimsByOwner().entrySet()) {
            ArrayNode ownerClaims = claims.putArray(entry.getKey());
            for (AuctionClaim claim : entry.getValue()) {
                ownerClaims.add(writeClaim(claim));
            }
        }
        root.put("nextId", Math.max(1L, state.nextId()));
        mapper.writeValue(auctionFile.toFile(), root);
    }

    private AuctionListing readListing(JsonNode node) {
        String id = text(node, "id");
        if (id == null || id.isBlank()) {
            id = text(node, "auctionId");
        }
        if (id == null || id.isBlank()) {
            id = Long.toString(Math.max(1L, Instant.now().toEpochMilli()));
        }

        String seller = text(node, "seller");
        if (seller == null || seller.isBlank()) {
            seller = text(node, "owner");
        }
        if (seller == null || seller.isBlank()) {
            seller = text(node, "player");
        }

        AuctionItem item = readItem(node.get("item"));
        if (item == null) {
            item = readItem(node.get("i"));
        }
        if (item == null) {
            item = new AuctionItem();
        }

        int price = (int) doubleValue(node, "price", 0d);
        if (price <= 0) {
            price = (int) doubleValue(node, "cost", 0d);
        }

        Instant createdAt = instantValue(node, "createdAt");
        if (createdAt == null) {
            createdAt = instantValue(node, "sellTime");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        long duration = longValue(node, "duration", 0L);
        Instant expireAt = instantValue(node, "expireAt");
        if (expireAt == null) {
            if (duration > 0L) {
                expireAt = createdAt.plusSeconds(duration);
            } else {
                expireAt = createdAt.plusSeconds(86_400L);
            }
        }
        if (duration <= 0L) {
            duration = Math.max(1L, expireAt.getEpochSecond() - createdAt.getEpochSecond());
        }

        AuctionListing listing = new AuctionListing(id, seller, item, price, createdAt, duration, expireAt);
        String buyer = text(node, "buyer");
        if (buyer == null || buyer.isBlank()) {
            buyer = text(node, "boughtBy");
        }
        listing.setBuyer(buyer);
        return listing;
    }

    private JsonNode writeListing(AuctionListing listing) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", listing.getId());
        node.put("seller", listing.getSeller());
        node.set("item", writeItem(listing.getItem()));
        node.put("price", listing.getPrice());
        node.put("sellTime", listing.getCreatedAt().toEpochMilli());
        node.put("createdAt", listing.getCreatedAt().toString());
        node.put("duration", listing.getDurationSeconds());
        node.put("expireAt", listing.getExpireAt().toString());
        if (listing.getBuyer() != null && !listing.getBuyer().isBlank()) {
            node.put("buyer", listing.getBuyer());
        }
        return node;
    }

    private Map<String, List<AuctionClaim>> readClaims(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }

        Map<String, List<AuctionClaim>> claimsByOwner = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            List<AuctionClaim> claims = new ArrayList<>();
            JsonNode claimArray = entry.getValue();
            if (claimArray != null && claimArray.isArray()) {
                for (JsonNode claimNode : claimArray) {
                    claims.add(readClaim(claimNode, entry.getKey()));
                }
            }
            claimsByOwner.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(claims));
        });
        return claimsByOwner;
    }

    private AuctionClaim readClaim(JsonNode node, String ownerKey) {
        AuctionClaim claim = new AuctionClaim();
        claim.setListingId(text(node, "listingId"));

        String owner = text(node, "owner");
        if (owner == null || owner.isBlank()) {
            owner = ownerKey;
        }
        claim.setOwner(owner);

        AuctionItem item = readItem(node.get("item"));
        if (item != null) {
            claim.setItem(item);
        }

        claim.setReason(defaultText(node, "reason", "EXPIRED"));
        Instant createdAt = instantValue(node, "createdAt");
        if (createdAt != null) {
            claim.setCreatedAt(createdAt);
        }
        return claim;
    }

    private JsonNode writeClaim(AuctionClaim claim) {
        ObjectNode node = mapper.createObjectNode();
        node.put("listingId", claim.getListingId());
        node.put("owner", claim.getOwner());
        node.set("item", writeItem(claim.getItem()));
        node.put("reason", claim.getReason());
        node.put("createdAt", claim.getCreatedAt().toString());
        return node;
    }

    private AuctionItem readItem(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return new AuctionItem(node.asText(), 1);
        }
        if (!node.isObject()) {
            return null;
        }

        String material = null;
        for (String key : MATERIAL_KEYS) {
            material = text(node, key);
            if (material != null && !material.isBlank()) {
                break;
            }
        }

        int amount = 1;
        for (String key : AMOUNT_KEYS) {
            int candidate = intValue(node, key, amount);
            if (candidate > 0) {
                amount = candidate;
                break;
            }
        }

        if (material == null || material.isBlank()) {
            return null;
        }
        return new AuctionItem(material, amount, text(node, "nbt"));
    }

    private JsonNode writeItem(AuctionItem item) {
        ObjectNode node = mapper.createObjectNode();
        node.put("material", item.getMaterial());
        node.put("amount", item.getAmount());
        if (item.getNbt() != null && !item.getNbt().isBlank()) {
            node.put("nbt", item.getNbt());
        }
        return node;
    }

    private static ArrayNode listingsNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return JacksonMappers.jsonMapper().createArrayNode();
        }
        if (root.isArray()) {
            return (ArrayNode) root;
        }
        JsonNode auctionNode = root.get("auction");
        if (auctionNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return JacksonMappers.jsonMapper().createArrayNode();
    }

    private static String text(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String defaultText(JsonNode node, String key, String fallback) {
        String value = text(node, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int intValue(JsonNode node, String key, int fallback) {
        return (int) longValue(node, key, fallback);
    }

    private static long longValue(JsonNode node, String key, long fallback) {
        if (node == null || key == null) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            return parseLong(value.asText(), fallback);
        }
        return fallback;
    }

    private static double doubleValue(JsonNode node, String key, double fallback) {
        if (node == null || key == null) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Instant instantValue(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long raw = value.longValue();
            if (raw > 10_000_000_000L) {
                return Instant.ofEpochMilli(raw);
            }
            return Instant.ofEpochSecond(raw);
        }
        if (!value.isTextual()) {
            return null;
        }
        String raw = value.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        long maybeEpoch = parseLong(raw, Long.MIN_VALUE);
        if (maybeEpoch != Long.MIN_VALUE) {
            if (maybeEpoch > 10_000_000_000L) {
                return Instant.ofEpochMilli(maybeEpoch);
            }
            return Instant.ofEpochSecond(maybeEpoch);
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
