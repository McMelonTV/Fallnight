package xyz.fallnight.server.domain.shop;

import java.util.Objects;

public record ShopOffer(
    String id,
    String displayName,
    String description,
    String customItemId,
    double dollarPrice,
    long prestigePrice,
    boolean giveItem,
    boolean oneTime,
    String actionKey,
    String actionValue
) {
    public ShopOffer {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase();
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        description = description == null ? "" : description.trim();
        customItemId = customItemId == null ? null : customItemId.trim();
        actionKey = actionKey == null ? null : actionKey.trim().toLowerCase();
        actionValue = actionValue == null ? null : actionValue.trim();
    }
}
