package xyz.fallnight.server.domain.kit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record KitDefinition(String id, String permission, long cooldownSeconds, List<KitReward> rewards) {
    public KitDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(permission, "permission");
        String normalizedId = id.trim().toLowerCase(Locale.ROOT);
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
        String normalizedPermission = permission.trim().toLowerCase(Locale.ROOT);
        if (normalizedPermission.isEmpty()) {
            throw new IllegalArgumentException("permission is required");
        }
        if (cooldownSeconds < 0L) {
            throw new IllegalArgumentException("cooldownSeconds cannot be negative");
        }
        Objects.requireNonNull(rewards, "rewards");
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("rewards cannot be empty");
        }

        id = normalizedId;
        permission = normalizedPermission;
        rewards = List.copyOf(rewards);
    }

    public String displayName() {
        if (id.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
