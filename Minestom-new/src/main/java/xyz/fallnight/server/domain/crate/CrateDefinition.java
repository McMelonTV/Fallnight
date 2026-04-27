package xyz.fallnight.server.domain.crate;

import java.util.List;
import java.util.Objects;

public record CrateDefinition(String id, String displayName, List<WeightedCrateReward> rewards) {
    public CrateDefinition {
        id = normalizeId(id);
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (rewards.isEmpty()) {
            throw new IllegalArgumentException("rewards cannot be empty");
        }
    }

    private static String normalizeId(String value) {
        return Objects.requireNonNull(value, "id").trim().toLowerCase();
    }
}
