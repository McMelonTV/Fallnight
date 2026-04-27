package xyz.fallnight.server.domain.crate;

import java.util.Objects;

public record WeightedCrateReward(CrateReward reward, int weight) {
    public WeightedCrateReward {
        reward = Objects.requireNonNull(reward, "reward");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
    }
}
