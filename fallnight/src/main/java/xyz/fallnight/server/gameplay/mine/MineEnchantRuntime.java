package xyz.fallnight.server.gameplay.mine;

import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import java.util.Map;
import java.util.Random;
import net.minestom.server.item.ItemStack;

final class MineEnchantRuntime {
    private static final Random RANDOM = new Random();

    static MiningModifiers evaluate(ItemStack tool, LegacyCustomItemService itemService) {
        Map<String, Integer> enchants = itemService.customEnchants(tool);
        double priceModifier = 1D;
        double xpBoost = 0D;
        double resourceBoost = 1D;
        int fusionLevel = 0;
        boolean autoRepair = false;
        boolean mineStardust = false;
        int drillerLevel = 0;
        int unbreakingLevel = 0;
        int obsidianBreakerLevel = 0;

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String id = entry.getKey();
            int level = Math.max(1, entry.getValue());
            switch (id) {
                case FallnightCustomEnchantRegistry.PROFIT -> priceModifier += 0.2D * level;
                case FallnightCustomEnchantRegistry.XP_EXTRACTION -> xpBoost += level;
                case FallnightCustomEnchantRegistry.EXTRACTION -> resourceBoost += 10D * level;
                case FallnightCustomEnchantRegistry.STARDUST_EXTRACTION -> mineStardust = true;
                case FallnightCustomEnchantRegistry.AUTOREPAIR -> autoRepair = true;
                case FallnightCustomEnchantRegistry.FUSION -> fusionLevel = Math.max(fusionLevel, level);
                case FallnightCustomEnchantRegistry.DRILLER -> drillerLevel = Math.max(drillerLevel, level);
                case FallnightCustomEnchantRegistry.UNBREAKING_CUSTOM -> unbreakingLevel = Math.max(unbreakingLevel, level);
                case FallnightCustomEnchantRegistry.OBSIDIAN_BREAKER -> obsidianBreakerLevel = Math.max(obsidianBreakerLevel, level);
                default -> {
                }
            }
        }

        return new MiningModifiers(priceModifier, xpBoost, resourceBoost, fusionLevel, autoRepair, mineStardust, drillerLevel, unbreakingLevel, obsidianBreakerLevel);
    }

    static boolean shouldDrill(int level) {
        return shouldDrill(level, RANDOM);
    }

    static boolean shouldFuse(int level) {
        return shouldFuse(level, RANDOM);
    }

    static boolean shouldBreakObsidian(int level) {
        return shouldBreakObsidian(level, RANDOM);
    }

    static boolean shouldDrill(int level, Random random) {
        return level > 0 && random.nextInt(101) <= Math.min(100, 10 * level);
    }

    static boolean shouldFuse(int level, Random random) {
        return level > 0 && random.nextInt(101) <= Math.min(100, 20 * level);
    }

    static boolean shouldBreakObsidian(int level, Random random) {
        return level > 0 && random.nextInt(101) < Math.min(100, 5 * level);
    }

    record MiningModifiers(
        double priceModifier,
        double xpBoost,
        double resourceBoost,
        int fusionLevel,
        boolean autoRepair,
        boolean mineStardust,
        int drillerLevel,
        int unbreakingLevel,
        int obsidianBreakerLevel
    ) {
    }
}
