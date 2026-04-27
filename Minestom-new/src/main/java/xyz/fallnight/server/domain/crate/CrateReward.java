package xyz.fallnight.server.domain.crate;

import java.util.List;
import java.util.Objects;

public record CrateReward(
    String description,
    double money,
    long prestigePoints,
    String customItemId,
    int forgedBookCount,
    boolean highEndForge,
    int randomTagCount,
    String grantedTag
) {
    public CrateReward {
        description = Objects.requireNonNull(description, "description").trim();
        money = Math.max(0D, money);
        prestigePoints = Math.max(0L, prestigePoints);
        customItemId = customItemId == null ? null : customItemId.trim();
        forgedBookCount = Math.max(0, forgedBookCount);
        randomTagCount = Math.max(0, randomTagCount);
        grantedTag = grantedTag == null ? null : grantedTag.trim();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
        if (money <= 0D && prestigePoints <= 0L && (customItemId == null || customItemId.isEmpty())
            && forgedBookCount <= 0 && randomTagCount <= 0 && (grantedTag == null || grantedTag.isEmpty())) {
            throw new IllegalArgumentException("reward must include at least one effect");
        }
    }

    public static CrateReward money(String description, double money) {
        return new CrateReward(description, money, 0L, null, 0, false, 0, null);
    }

    public static CrateReward moneyPrestige(String description, double money, long prestigePoints) {
        return new CrateReward(description, money, prestigePoints, null, 0, false, 0, null);
    }

    public static CrateReward item(String description, String customItemId) {
        return new CrateReward(description, 0D, 0L, customItemId, 0, false, 0, null);
    }

    public static CrateReward tag(String description, int randomTagCount, String grantedTag) {
        return new CrateReward(description, 0D, 0L, null, 0, false, randomTagCount, grantedTag);
    }

    public static CrateReward forgedBooks(String description, int count, boolean highEnd) {
        return new CrateReward(description, 0D, 0L, null, count, highEnd, 0, null);
    }
}
