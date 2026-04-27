package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.achievement.AchievementCategory;
import xyz.fallnight.server.domain.achievement.AchievementDefinition;
import xyz.fallnight.server.domain.achievement.AchievementStatus;
import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class AchievementService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    public static final String CLAIMED_ACHIEVEMENTS_KEY = "claimedAchievements";
    public static final String ACHIEVEMENTS_KEY = "achievements";

    public static final String TIME_TO_MINE = "time_to_mine";
    public static final String GETTING_AN_UPGRADE = "getting_an_upgrade";
    public static final String BIG_BUCKS_1 = "big_bucks_1";
    public static final String BIG_BUCKS_2 = "big_bucks_2";
    public static final String BIG_BUCKS_3 = "big_bucks_3";
    public static final String BIG_BUCKS_4 = "big_bucks_4";
    public static final String BIG_BUCKS_5 = "big_bucks_5";
    public static final String GRINDER_1 = "grinder_1";
    public static final String GRINDER_2 = "grinder_2";
    public static final String GRINDER_3 = "grinder_3";
    public static final String GRINDER_4 = "grinder_4";
    public static final String GRINDER_5 = "grinder_5";
    public static final String QUALITY_MARKSMANSHIP = "quality_marksmanship";
    public static final String DAREDEVIL = "daredevil";
    public static final String PRESTIGIOUS = "prestigious";
    public static final String WHAT_DID_IT_COST = "what_did_it_cost";
    public static final String TEAM_UP = "team_up";
    public static final String KOTH = "koth";

    private final PlayerProfileService profileService;
    private final List<AchievementDefinition> definitions;
    private final Map<String, AchievementDefinition> definitionsById;

    public AchievementService(PlayerProfileService profileService) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.definitions = buildDefinitions();
        this.definitionsById = new LinkedHashMap<>();
        for (AchievementDefinition definition : definitions) {
            definitionsById.put(normalizeId(definition.id()), definition);
        }
    }

    public List<AchievementDefinition> definitions() {
        return definitions;
    }

    public List<AchievementStatus> statuses(UserProfile profile) {
        Set<String> achieved = achievedIds(profile);
        List<AchievementStatus> statuses = new ArrayList<>(definitions.size());
        for (AchievementDefinition definition : definitions) {
            long progress = definition.progress(profile);
            boolean unlocked = achieved.contains(normalizeId(definition.id())) || progress >= definition.requiredProgress();
            statuses.add(new AchievementStatus(definition, progress, unlocked, unlocked));
        }
        return List.copyOf(statuses);
    }

    public ClaimResult claim(UserProfile profile, String achievementId) {
        AchievementDefinition definition = definitionsById.get(normalizeId(achievementId));
        if (definition == null) {
            return ClaimResult.notFound(null);
        }
        if (achievedIds(profile).contains(normalizeId(definition.id()))) {
            return ClaimResult.alreadyClaimed(definition);
        }
        return ClaimResult.locked(definition, definition.progress(profile));
    }

    public ClaimAllResult claimAll(UserProfile profile) {
        return new ClaimAllResult(0, 0D, 0L, List.of());
    }

    public boolean onBlockBreak(Player player, UserProfile profile, String blockKey) {
        boolean changed = false;
        changed |= threshold(player, profile, TIME_TO_MINE, profile.getMinedBlocks() >= 1);
        changed |= threshold(player, profile, GRINDER_1, profile.getMinedBlocks() >= 10_000);
        changed |= threshold(player, profile, GRINDER_2, profile.getMinedBlocks() >= 50_000);
        changed |= threshold(player, profile, GRINDER_3, profile.getMinedBlocks() >= 100_000);
        changed |= threshold(player, profile, GRINDER_4, profile.getMinedBlocks() >= 250_000);
        changed |= threshold(player, profile, GRINDER_5, profile.getMinedBlocks() >= 1_000_000);
        double totalEarned = totalEarnedMoney(profile);
        changed |= threshold(player, profile, BIG_BUCKS_1, totalEarned >= 10_000D);
        changed |= threshold(player, profile, BIG_BUCKS_2, totalEarned >= 100_000D);
        changed |= threshold(player, profile, BIG_BUCKS_3, totalEarned >= 1_000_000D);
        changed |= threshold(player, profile, BIG_BUCKS_4, totalEarned >= 10_000_000D);
        changed |= threshold(player, profile, BIG_BUCKS_5, totalEarned >= 100_000_000D);
        return changed;
    }

    public boolean onForge(Player player, UserProfile profile, String customId) {
        boolean changed = false;
        if (customId != null && (customId.startsWith("6:") || customId.startsWith("7:") || customId.equals("27"))) {
            changed |= threshold(player, profile, GETTING_AN_UPGRADE, true);
        }
        if (customId != null && (customId.equals("27") || customId.startsWith("7:8") || customId.startsWith("7:9") || customId.startsWith("7:10"))) {
            changed |= threshold(player, profile, QUALITY_MARKSMANSHIP, true);
        }
        return changed;
    }

    public boolean onFallDamage(Player player, UserProfile profile, float amount) {
        return threshold(player, profile, DAREDEVIL, amount >= 22f);
    }

    public boolean onGangJoined(Player player, UserProfile profile) {
        return threshold(player, profile, TEAM_UP, true);
    }

    public boolean onKothWin(Player player, UserProfile profile) {
        return threshold(player, profile, KOTH, true);
    }

    public boolean onPrestige(Player player, UserProfile profile) {
        return threshold(player, profile, PRESTIGIOUS, profile.getPrestige() >= 2);
    }

    public boolean onMineRank(Player player, UserProfile profile) {
        return threshold(player, profile, WHAT_DID_IT_COST, profile.getMineRank() >= 25);
    }

    public void registerEarnedMoney(UserProfile profile, double delta) {
        if (delta <= 0D) {
            return;
        }
        profile.getExtraData().put("totalEarnedMoney", totalEarnedMoney(profile) + delta);
    }

    private boolean threshold(Player player, UserProfile profile, String id, boolean condition) {
        if (!condition || profile == null) {
            return false;
        }
        String normalized = normalizeId(id);
        synchronized (profile) {
            Set<String> achieved = achievedIds(profile);
            if (achieved.contains(normalized)) {
                return false;
            }
            AchievementDefinition definition = definitionsById.get(normalized);
            if (definition == null) {
                return false;
            }
            achieved.add(normalized);
            storeAchieved(profile, achieved);
            applyRewards(profile, definition.moneyReward(), definition.prestigePointsReward());
            profileService.save(profile);
            if (player != null) {
                notifyUnlocked(player, definition);
            }
            return true;
        }
    }

    private static void notifyUnlocked(Player player, AchievementDefinition definition) {
        StringBuilder message = new StringBuilder("§8§l<--§bFN§8-->\n §r§7You have achieved: §b")
            .append(definition.title())
            .append("\n §r§b§l>§r§7 Description: §b")
            .append(definition.description());
        if (definition.moneyReward() > 0D && definition.prestigePointsReward() > 0L) {
            message.append("\n §r§b§l> §r§7You have been rewarded §b$")
                .append((long) definition.moneyReward())
                .append("§r§7 and §b")
                .append(definition.prestigePointsReward())
                .append("§opp§r§7 for achieving this.");
        } else if (definition.moneyReward() > 0D) {
            message.append("\n §r§b§l> §r§7You have been rewarded §b$")
                .append((long) definition.moneyReward())
                .append("§r§7 for achieving this.");
        } else if (definition.prestigePointsReward() > 0L) {
            message.append("\n §r§b§l> §r§7You have been rewarded §b")
                .append(definition.prestigePointsReward())
                .append("§opp§r§7 for achieving this.");
        }
        message.append("\n§r§8§l<--++-->⛏");
        player.sendMessage(LEGACY.deserialize(message.toString()));
    }

    private static void applyRewards(UserProfile profile, double money, long prestigePoints) {
        if (money > 0D) {
            profile.deposit(money);
        }
        if (prestigePoints > 0L) {
            profile.addPrestigePoints(prestigePoints);
        }
    }

    private static void storeAchieved(UserProfile profile, Set<String> achieved) {
        List<String> ids = List.copyOf(achieved);
        profile.getExtraData().put(CLAIMED_ACHIEVEMENTS_KEY, ids);
        profile.getExtraData().put(ACHIEVEMENTS_KEY, ids);
    }

    private static Set<String> achievedIds(UserProfile profile) {
        LinkedHashSet<String> claimed = new LinkedHashSet<>();
        if (profile == null) {
            return claimed;
        }
        for (String key : new String[] {ACHIEVEMENTS_KEY, CLAIMED_ACHIEVEMENTS_KEY}) {
            Object raw = profile.getExtraData().get(key);
            if (raw instanceof Iterable<?> list) {
                for (Object value : list) {
                    String id = normalizeId(value == null ? null : String.valueOf(value));
                    if (!id.isEmpty()) {
                        claimed.add(id);
                    }
                }
            }
        }
        return claimed;
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static List<AchievementDefinition> buildDefinitions() {
        return List.of(
            def(TIME_TO_MINE, "Time to mine", "Mine your first block", AchievementCategory.MINING, 1, UserProfile::getMinedBlocks, 1000D, 100L),
            def(GETTING_AN_UPGRADE, "Getting an upgrade", "Forge a better pickaxe in the forge", AchievementCategory.PROGRESSION, 1, profile -> achievedFlag(profile, GETTING_AN_UPGRADE), 0D, 100L),
            def(BIG_BUCKS_1, "Big Bucks I", "Earn a total of $10000", AchievementCategory.ECONOMY, 10_000, AchievementService::totalEarnedMoneyLong, 0D, 100L),
            def(BIG_BUCKS_2, "Big Bucks II", "Earn a total of $100000", AchievementCategory.ECONOMY, 100_000, AchievementService::totalEarnedMoneyLong, 0D, 150L),
            def(BIG_BUCKS_3, "Big Bucks III", "Earn a total of $1000000", AchievementCategory.ECONOMY, 1_000_000, AchievementService::totalEarnedMoneyLong, 0D, 200L),
            def(BIG_BUCKS_4, "Big Bucks IV", "Earn a total of $10000000", AchievementCategory.ECONOMY, 10_000_000, AchievementService::totalEarnedMoneyLong, 0D, 250L),
            def(BIG_BUCKS_5, "Big Bucks V", "Earn a total of $100000000", AchievementCategory.ECONOMY, 100_000_000, AchievementService::totalEarnedMoneyLong, 0D, 250L),
            def(GRINDER_1, "Grinder I", "Mine a total of 10000 blocks", AchievementCategory.MINING, 10_000, UserProfile::getMinedBlocks, 0D, 100L),
            def(GRINDER_2, "Grinder II", "Mine a total of 50000 blocks", AchievementCategory.MINING, 50_000, UserProfile::getMinedBlocks, 0D, 150L),
            def(GRINDER_3, "Grinder III", "Mine a total of 100000 blocks", AchievementCategory.MINING, 100_000, UserProfile::getMinedBlocks, 0D, 200L),
            def(GRINDER_4, "Grinder IV", "Mine a total of 250000 blocks", AchievementCategory.MINING, 250_000, UserProfile::getMinedBlocks, 0D, 250L),
            def(GRINDER_5, "Grinder V", "Mine a total of 1000000 blocks", AchievementCategory.MINING, 1_000_000, UserProfile::getMinedBlocks, 0D, 250L),
            def(QUALITY_MARKSMANSHIP, "Quality Marksmanship", "Forge a legendary item", AchievementCategory.PROGRESSION, 1, profile -> achievedFlag(profile, QUALITY_MARKSMANSHIP), 0D, 300L),
            def(DAREDEVIL, "Daredevil", "Find out fall damage is disabled", AchievementCategory.COMBAT, 1, profile -> achievedFlag(profile, DAREDEVIL), 0D, 250L),
            def(PRESTIGIOUS, "Prestigious", "Reach prestige 2", AchievementCategory.PROGRESSION, 2, profile -> profile.getPrestige(), 0D, 100L),
            def(WHAT_DID_IT_COST, "What did it cost?", "Everything.", AchievementCategory.PROGRESSION, 25, profile -> profile.getMineRank(), 0D, 200L),
            def(TEAM_UP, "Team Up", "Join or create a gang", AchievementCategory.SOCIAL, 1, profile -> achievedFlag(profile, TEAM_UP), 1000D, 150L),
            def(KOTH, "King of the Hill", "Become King of the Hill", AchievementCategory.COMBAT, 1, profile -> achievedFlag(profile, KOTH), 1000D, 150L)
        );
    }

    private static AchievementDefinition def(String id, String title, String description, AchievementCategory category, long required, java.util.function.ToLongFunction<UserProfile> progress, double money, long pp) {
        return new AchievementDefinition(id, title, description, category, required, progress, money, pp);
    }

    private static long achievedFlag(UserProfile profile, String id) {
        return achievedIds(profile).contains(normalizeId(id)) ? 1L : 0L;
    }

    private static long totalEarnedMoneyLong(UserProfile profile) {
        return (long) Math.floor(totalEarnedMoney(profile));
    }

    private static double totalEarnedMoney(UserProfile profile) {
        Object value = profile.getExtraData().get("totalEarnedMoney");
        if (value instanceof Number number) {
            return Math.max(0D, number.doubleValue());
        }
        if (value instanceof String text) {
            try {
                return Math.max(0D, Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    public enum ClaimStatus {
        CLAIMED,
        NOT_FOUND,
        LOCKED,
        ALREADY_CLAIMED
    }

    public record ClaimResult(ClaimStatus status, AchievementDefinition definition, double moneyReward, long prestigePointsReward, long currentProgress) {
        public static ClaimResult claimed(AchievementDefinition definition, double moneyReward, long prestigePointsReward) {
            return new ClaimResult(ClaimStatus.CLAIMED, definition, moneyReward, prestigePointsReward, definition.requiredProgress());
        }
        public static ClaimResult notFound(AchievementDefinition definition) {
            return new ClaimResult(ClaimStatus.NOT_FOUND, definition, 0D, 0L, 0L);
        }
        public static ClaimResult locked(AchievementDefinition definition, long currentProgress) {
            return new ClaimResult(ClaimStatus.LOCKED, definition, 0D, 0L, currentProgress);
        }
        public static ClaimResult alreadyClaimed(AchievementDefinition definition) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, definition, 0D, 0L, definition == null ? 0L : definition.requiredProgress());
        }
    }

    public record ClaimAllResult(int claimedCount, double moneyReward, long prestigePointsReward, List<String> claimedIds) {
    }
}
