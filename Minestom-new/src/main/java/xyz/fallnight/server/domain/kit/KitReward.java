package xyz.fallnight.server.domain.kit;

import java.util.Locale;
import java.util.Objects;

public record KitReward(String customItemId, String slot) {
    public KitReward {
        customItemId = Objects.requireNonNull(customItemId, "customItemId").trim();
        slot = slot == null ? "inventory" : slot.trim().toLowerCase(Locale.ROOT);
        if (customItemId.isEmpty()) {
            throw new IllegalArgumentException("customItemId cannot be blank");
        }
    }

    public boolean armorSlot() {
        return switch (slot) {
            case "helmet", "chestplate", "leggings", "boots" -> true;
            default -> false;
        };
    }
}
