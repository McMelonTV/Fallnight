package xyz.fallnight.server.domain.shop;

import java.util.List;
import java.util.Objects;

public record ShopCategoryDefinition(String id, String displayName, List<ShopOffer> offers) {
    public ShopCategoryDefinition {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase();
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        offers = List.copyOf(offers);
    }
}
