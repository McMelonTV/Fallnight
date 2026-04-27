package xyz.fallnight.server.persistence.auction;

import xyz.fallnight.server.domain.auction.AuctionClaim;
import xyz.fallnight.server.domain.auction.AuctionListing;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface AuctionRepository {
    AuctionState load() throws IOException;

    void save(AuctionState state) throws IOException;

    record AuctionState(List<AuctionListing> listings, Map<String, List<AuctionClaim>> claimsByOwner, long nextId) {
    }
}
