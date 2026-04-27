package xyz.fallnight.server.util;

public final class ProgressionMath {
    private static final double PRESTIGE_MULTIPLIER = 0.6d;
    private static final long BASE_PRESTIGE_PRICE = 2_500_000L;
    private static final long BASE_PRESTIGE_REWARD = 10_000L;

    private ProgressionMath() {
    }

    public static long rankUpPrice(double nextRankBasePrice, int prestige) {
        double safeBase = Math.max(0d, nextRankBasePrice);
        int safePrestige = Math.max(1, prestige);
        double value = safeBase + (safeBase * PRESTIGE_MULTIPLIER * (safePrestige - 1));
        return (long) value;
    }

    public static long prestigePrice(int targetPrestige) {
        int safeTarget = Math.max(2, targetPrestige);
        double value = BASE_PRESTIGE_PRICE + ((safeTarget - 2) * BASE_PRESTIGE_PRICE * PRESTIGE_MULTIPLIER);
        return (long) value;
    }

    public static long prestigeReward(int targetPrestige) {
        return BASE_PRESTIGE_REWARD;
    }
}
