package xyz.fallnight.server.domain.achievement;

import java.util.Locale;
import java.util.Optional;

public enum AchievementCategory {
    MINING("mining"),
    ECONOMY("economy"),
    COMBAT("combat"),
    PROGRESSION("progression"),
    SOCIAL("social");

    private final String id;

    AchievementCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<AchievementCategory> fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AchievementCategory category : values()) {
            if (category.id.equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
