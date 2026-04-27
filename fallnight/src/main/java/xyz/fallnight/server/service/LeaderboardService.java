package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LeaderboardService {
    public enum Type {
        BALANCE,
        EARNINGS,
        BLOCK_BREAKS,
        MINE,
        KILLS,
        KDR
    }

    private final PlayerProfileService profileService;
    private final ConcurrentMap<Type, List<UserProfile>> cache;
    private final int size;

    public LeaderboardService(PlayerProfileService profileService, int size) {
        this.profileService = profileService;
        this.size = Math.max(1, size);
        this.cache = new ConcurrentHashMap<>();
        regenerateAll();
    }

    public void regenerateAll() {
        for (Type type : Type.values()) {
            cache.put(type, compute(type));
        }
    }

    public List<UserProfile> top(Type type) {
        return cache.computeIfAbsent(type, this::compute);
    }

    private List<UserProfile> compute(Type type) {
        Comparator<UserProfile> comparator = switch (type) {
            case BALANCE -> Comparator.comparingDouble(UserProfile::getBalance).reversed();
            case EARNINGS -> Comparator.comparingDouble((UserProfile profile) -> readDouble(profile, "totalEarnedMoney", profile.getBalance())).reversed();
            case BLOCK_BREAKS -> Comparator.comparingLong(UserProfile::getMinedBlocks).reversed();
            case MINE -> Comparator.comparingDouble(this::mineScore).reversed();
            case KILLS -> Comparator.comparingLong(UserProfile::getKills).reversed();
            case KDR -> Comparator.comparingDouble(this::kdr).reversed();
        };
        return profileService.allProfiles().stream().sorted(comparator).limit(size).toList();
    }

    public double value(Type type, UserProfile profile) {
        return switch (type) {
            case BALANCE -> profile.getBalance();
            case EARNINGS -> readDouble(profile, "totalEarnedMoney", profile.getBalance());
            case BLOCK_BREAKS -> profile.getMinedBlocks();
            case MINE -> mineScore(profile);
            case KILLS -> profile.getKills();
            case KDR -> kdr(profile);
        };
    }

    private double mineScore(UserProfile profile) {
        return profile.getMineRank() + (Math.max(1, profile.getPrestige()) * 100d);
    }

    private double kdr(UserProfile profile) {
        long deaths = Math.max(1L, profile.getDeaths());
        return profile.getKills() / (double) deaths;
    }

    private static double readDouble(UserProfile profile, String key, double fallback) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
