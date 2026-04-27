package xyz.fallnight.server.domain.achievement;

public record AchievementStatus(
    AchievementDefinition definition,
    long progress,
    boolean unlocked,
    boolean claimed
) {
}
