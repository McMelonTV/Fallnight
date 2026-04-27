package xyz.fallnight.server.domain.achievement;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.Objects;
import java.util.function.ToLongFunction;

public record AchievementDefinition(
    String id,
    String title,
    String description,
    AchievementCategory category,
    long requiredProgress,
    ToLongFunction<UserProfile> progressFunction,
    double moneyReward,
    long prestigePointsReward
) {
    public AchievementDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(progressFunction, "progressFunction");
        requiredProgress = Math.max(1L, requiredProgress);
        moneyReward = Math.max(0D, moneyReward);
        prestigePointsReward = Math.max(0L, prestigePointsReward);
    }

    public long progress(UserProfile profile) {
        if (profile == null) {
            return 0L;
        }
        return Math.max(0L, progressFunction.applyAsLong(profile));
    }

    public boolean unlocked(UserProfile profile) {
        return progress(profile) >= requiredProgress;
    }
}
