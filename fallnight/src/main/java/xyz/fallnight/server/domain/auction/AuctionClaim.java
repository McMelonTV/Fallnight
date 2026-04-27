package xyz.fallnight.server.domain.auction;

import java.time.Instant;

public final class AuctionClaim {
    private String listingId;
    private String owner;
    private AuctionItem item;
    private String reason;
    private Instant createdAt;

    public AuctionClaim() {
        this.item = new AuctionItem();
        this.reason = "EXPIRED";
        this.createdAt = Instant.now();
    }

    public AuctionClaim(String listingId, String owner, AuctionItem item, String reason, Instant createdAt) {
        this.listingId = listingId;
        this.owner = owner;
        this.item = item == null ? new AuctionItem() : item;
        this.reason = reason == null || reason.isBlank() ? "EXPIRED" : reason;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public AuctionItem getItem() {
        return item;
    }

    public void setItem(AuctionItem item) {
        this.item = item == null ? new AuctionItem() : item;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null || reason.isBlank() ? "EXPIRED" : reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
