package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class ForgeService {
    private final LegacyCustomItemService customItemService;
    private final AchievementService achievementService;
    private final ItemDeliveryService itemDeliveryService;
    private final Map<String, ForgeCategory> categories;
    private final Map<String, ForgeRecipe> recipes;
    private final Random random;

    public ForgeService(LegacyCustomItemService customItemService, PlayerProfileService profileService) {
        this(customItemService, profileService, null);
    }

    public ForgeService(LegacyCustomItemService customItemService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        this.customItemService = customItemService;
        this.achievementService = new AchievementService(profileService);
        this.itemDeliveryService = itemDeliveryService;
        this.categories = new LinkedHashMap<>();
        this.recipes = new LinkedHashMap<>();
        this.random = new Random();
        registerDefaults();
    }

    public List<ForgeCategory> categories() {
        return List.copyOf(categories.values());
    }

    public Optional<ForgeCategory> category(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categories.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<ForgeRecipe> recipe(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipes.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public CraftResult craft(Player player, UserProfile profile, String recipeName) {
        ForgeRecipe recipe = recipe(recipeName).orElse(null);
        if (recipe == null) {
            return CraftResult.missing();
        }
        if (!canAfford(player, profile, recipe.price())) {
            return CraftResult.cannotAfford(recipe);
        }
        ItemStack item = customItemService.createFromCustomId(recipe.customId()).orElse(null);
        if (item == null) {
            return CraftResult.missing();
        }
        pay(player, profile, recipe.price());
        deliverItem(player, profile, item);
        if (achievementService != null) {
            achievementService.onForge(player, profile, recipe.customId());
        }
        return CraftResult.success(recipe, item);
    }

    public RepairResult repair(Player player) {
        ItemStack held = player.getItemInMainHand();
        RepairResult quote = previewRepair(player);
        if (!quote.success()) {
            return quote;
        }

        removeItem(player, 3, quote.obsidianUsed());
        removeItem(player, 1, quote.stardustUsed());
        removeItem(player, 4, quote.steeldustUsed());
        int currentDamage = customItemService.currentDamage(held);
        player.setItemInMainHand(customItemService.applyDamage(held, -currentDamage));
        return quote;
    }

    public RepairResult previewRepair(Player player) {
        ItemStack held = player.getItemInMainHand();
        if (!customItemService.isDurabilityItem(held)) {
            return RepairResult.invalid();
        }
        int maxDamage = customItemService.maxDamage(held);
        int currentDamage = customItemService.currentDamage(held);
        if (held == null || held.isAir() || maxDamage <= 0 || currentDamage <= 0) {
            return RepairResult.invalid();
        }

        int remaining = currentDamage;
        int usedSteel = 0;
        int usedStardust = 0;
        int usedObsidian = 0;

        long availableObsidian = countItem(player, 3);
        long availableStardust = countItem(player, 1);
        long availableSteeldust = countItem(player, 4);
        while (remaining > 0 && availableObsidian > 0) {
            remaining -= 400;
            availableObsidian--;
            usedObsidian++;
        }
        while (remaining > 0 && availableStardust > 0) {
            remaining -= 200;
            availableStardust--;
            usedStardust++;
        }
        while (remaining > 0 && availableSteeldust > 0) {
            remaining -= 50;
            availableSteeldust--;
            usedSteel++;
        }

        if (usedSteel == 0 && usedStardust == 0 && usedObsidian == 0) {
            return RepairResult.cannotAfford();
        }
        return RepairResult.success(usedSteel, usedStardust, usedObsidian);
    }

    public EnchantForgeResult forgeEnchantment(Player player, UserProfile profile, boolean highEnd) {
        ForgePrice price = highEnd
            ? new ForgePrice(0, 0, 40, 0, 0, 0, 0)
            : new ForgePrice(0, 0, 25, 0, 0, 0, 0);
        if (!canAfford(player, profile, price)) {
            return EnchantForgeResult.cannotAfford(price);
        }
        FallnightCustomEnchantRegistry.Definition forged = chooseRandomEnchant(highEnd);
        if (forged == null) {
            return EnchantForgeResult.missing(price);
        }
        int level = 1;
        if (highEnd) {
            FallnightCustomEnchantRegistry.Definition second = chooseRandomEnchant(true);
            if (second != null) {
                if (second.forgeWeight() < forged.forgeWeight()) {
                    forged = second;
                } else if (second.forgeWeight() == forged.forgeWeight()) {
                    level = Math.min(2, forged.maxLevel());
                }
            }
        }
        ItemStack book = customItemService.enchantmentBookByVariantLevel(forged.legacyId(), level);
        pay(player, profile, price);
        deliverItem(player, profile, book);
        return EnchantForgeResult.success(price, forged, level, book);
    }

    private void deliverItem(Player player, UserProfile profile, ItemStack item) {
        if (itemDeliveryService != null) {
            itemDeliveryService.deliver(player, profile, item);
            return;
        }
        player.getInventory().addItemStack(item);
    }

    private boolean canAfford(Player player, UserProfile profile, ForgePrice price) {
        return countItem(player, 4) >= price.steeldust()
            && countItem(player, 3) >= price.obsidianShard()
            && countItem(player, 2) >= price.magicdust()
            && countItem(player, 1) >= price.stardust()
            && profile.getBalance() >= price.money()
            && profile.getPrestigePoints() >= price.prestigePoints()
            && player.getLevel() >= price.xpLevels();
    }

    private void pay(Player player, UserProfile profile, ForgePrice price) {
        removeItem(player, 4, price.steeldust());
        removeItem(player, 3, price.obsidianShard());
        removeItem(player, 2, price.magicdust());
        removeItem(player, 1, price.stardust());
        if (price.money() > 0) {
            profile.withdraw(price.money());
        }
        if (price.prestigePoints() > 0) {
            profile.setPrestigePoints(profile.getPrestigePoints() - price.prestigePoints());
        }
        if (price.xpLevels() > 0) {
            player.setLevel(player.getLevel() - price.xpLevels());
        }
    }

    private long countItem(Player player, int customItemId) {
        long count = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (customItemService.customItemId(stack) == customItemId) {
                count += stack.amount();
            }
        }
        return count;
    }

    private void removeItem(Player player, int customItemId, long amount) {
        long remaining = amount;
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (customItemService.customItemId(stack) != customItemId) {
                continue;
            }
            int take = (int) Math.min(remaining, stack.amount());
            int next = stack.amount() - take;
            player.getInventory().setItemStack(slot, next <= 0 ? ItemStack.AIR : stack.withAmount(next));
            remaining -= take;
        }
    }

    private void registerDefaults() {
        addCategory("weapons", "Weapons", List.of(
            recipe("basicsword1", "13:1", 10, 0, 0, 0),
            recipe("basicsword2", "13:2", 25, 0, 0, 0),
            recipe("basicsword3", "13:3", 50, 0, 0, 0),
            recipe("basicsword4", "13:4", 100, 0, 0, 0),
            recipe("basicsword5", "13:5", 160, 0, 0, 0),
            recipe("advancedsword1", "15:1", 0, 0, 0, 10),
            recipe("advancedsword2", "15:2", 0, 0, 0, 25),
            recipe("advancedsword3", "15:3", 0, 0, 0, 50),
            recipe("advancedsword4", "15:4", 0, 0, 0, 100),
            recipe("advancedsword5", "15:5", 0, 0, 0, 160)
        ));
        addGeneratedCategory("pickaxes", "Pickaxes", "basicpickaxe", "6", 1, 10, new int[] {5,15,30,50,70,100,130,160,200,250}, "advancedpickaxe", "7", new int[] {25,50,75,100,125,150,175,200,225,250});
        addGeneratedCategory("helmets", "Helmets", "basichelmet", "9", 1, 5, new int[] {10,25,50,100,160}, "advancedhelmet", "19", new int[] {10,25,50,100,160});
        addGeneratedCategory("chestplates", "Chestplates", "basicchestplate", "10", 1, 5, new int[] {10,25,50,100,160}, "advancedchestplate", "18", new int[] {10,25,50,100,160});
        addGeneratedCategory("leggings", "Leggings", "basicleggings", "11", 1, 5, new int[] {10,25,50,100,160}, "advancedleggings", "17", new int[] {10,25,50,100,160});
        addGeneratedCategory("boots", "Boots", "basicboots", "12", 1, 5, new int[] {10,25,50,100,160}, "advancedboots", "16", new int[] {10,25,50,100,160});
        addCategory("tools", "Tools", List.of(
            recipe("basicaxe1", "23:1", 10, 0, 0, 0), recipe("basicaxe2", "23:2", 50, 0, 0, 0), recipe("basicaxe3", "23:3", 160, 0, 0, 0),
            recipe("advancedaxe1", "24:1", 0, 0, 0, 10), recipe("advancedaxe2", "24:2", 0, 0, 0, 50), recipe("advancedaxe3", "24:3", 0, 0, 0, 160),
            recipe("basicshovel1", "25:1", 10, 0, 0, 0), recipe("basicshovel2", "25:2", 50, 0, 0, 0), recipe("basicshovel3", "25:3", 160, 0, 0, 0),
            recipe("advancedshovel1", "26:1", 0, 0, 0, 10), recipe("advancedshovel2", "26:2", 0, 0, 0, 50), recipe("advancedshovel3", "26:3", 0, 0, 0, 160)
        ));
    }

    private void addGeneratedCategory(String key, String displayName, String basicNamePrefix, String basicId, int startTier, int endTier, int[] basicSteel, String advancedNamePrefix, String advancedId, int[] advancedStardust) {
        List<ForgeRecipe> list = new ArrayList<>();
        for (int i = startTier; i <= endTier; i++) {
            list.add(recipe(basicNamePrefix + i, basicId + ":" + i, basicSteel[i - startTier], 0, 0, 0));
            list.add(recipe(advancedNamePrefix + i, advancedId + ":" + i, 0, 0, 0, advancedStardust[i - startTier]));
        }
        addCategory(key, displayName, list);
    }

    private ForgeRecipe recipe(String name, String customId, int steeldust, int obsidian, int magicdust, int stardust) {
        return new ForgeRecipe(name, customId, new ForgePrice(steeldust, obsidian, magicdust, stardust, 0, 0, 0));
    }

    private void addCategory(String key, String displayName, List<ForgeRecipe> recipes) {
        ForgeCategory category = new ForgeCategory(key, displayName, recipes);
        categories.put(key, category);
        for (ForgeRecipe recipe : recipes) {
            this.recipes.put(recipe.name().toLowerCase(Locale.ROOT), recipe);
        }
    }

    private FallnightCustomEnchantRegistry.Definition chooseRandomEnchant(boolean highEnd) {
        List<FallnightCustomEnchantRegistry.Definition> weighted = new ArrayList<>();
        for (FallnightCustomEnchantRegistry.Definition definition : FallnightCustomEnchantRegistry.registeredDefaults()) {
            int weight = highEnd ? definition.highEndForgeWeight() : definition.forgeWeight();
            for (int i = 0; i < weight; i++) {
                weighted.add(definition);
            }
        }
        if (weighted.isEmpty()) {
            return null;
        }
        return weighted.get(random.nextInt(weighted.size()));
    }

    public record ForgeCategory(String key, String displayName, List<ForgeRecipe> recipes) {
    }

    public record ForgeRecipe(String name, String customId, ForgePrice price) {
    }

    public record ForgePrice(int steeldust, int obsidianShard, int magicdust, int stardust, double money, long prestigePoints, int xpLevels) {
        public String display() {
            List<String> parts = new ArrayList<>();
            if (steeldust > 0) parts.add("§r§b" + steeldust + " steeldust");
            if (obsidianShard > 0) parts.add("§r§b" + obsidianShard + " obsidian shards");
            if (magicdust > 0) parts.add("§r§b" + magicdust + " magicdust");
            if (stardust > 0) parts.add("§r§b" + stardust + " stardust");
            if (money > 0) parts.add("§r§b$" + legacyMoney(money));
            if (prestigePoints > 0) parts.add("§r§b" + prestigePoints + "§opp");
            if (xpLevels > 0) parts.add("§r§b" + xpLevels + " levels");
            return parts.isEmpty() ? "§r§7Free" : String.join(" ", parts);
        }

        private static String legacyMoney(double money) {
            if (Math.rint(money) == money) {
                return Long.toString((long) money);
            }
            return Double.toString(money);
        }
    }

    public record CraftResult(boolean success, boolean found, boolean affordable, ForgeRecipe recipe, ItemStack item) {
        static CraftResult missing() {
            return new CraftResult(false, false, false, null, null);
        }

        static CraftResult cannotAfford(ForgeRecipe recipe) {
            return new CraftResult(false, true, false, recipe, null);
        }

        static CraftResult success(ForgeRecipe recipe, ItemStack item) {
            return new CraftResult(true, true, true, recipe, item);
        }
    }

    public record RepairResult(boolean success, boolean validItem, boolean affordable, int steeldustUsed, int stardustUsed, int obsidianUsed) {
        static RepairResult invalid() {
            return new RepairResult(false, false, false, 0, 0, 0);
        }

        static RepairResult cannotAfford() {
            return new RepairResult(false, true, false, 0, 0, 0);
        }

        static RepairResult success(int steeldustUsed, int stardustUsed, int obsidianUsed) {
            return new RepairResult(true, true, true, steeldustUsed, stardustUsed, obsidianUsed);
        }

        public String display() {
            List<String> parts = new ArrayList<>();
            if (steeldustUsed > 0) parts.add(steeldustUsed + " steeldust");
            if (stardustUsed > 0) parts.add(stardustUsed + " stardust");
            if (obsidianUsed > 0) parts.add(obsidianUsed + " obsidian shard(s)");
            return String.join(", ", parts);
        }
    }

    public record EnchantForgeResult(boolean success, boolean affordable, ForgePrice price, FallnightCustomEnchantRegistry.Definition enchant, int level, ItemStack book) {
        static EnchantForgeResult missing(ForgePrice price) {
            return new EnchantForgeResult(false, false, price, null, 0, null);
        }

        static EnchantForgeResult cannotAfford(ForgePrice price) {
            return new EnchantForgeResult(false, false, price, null, 0, null);
        }

        static EnchantForgeResult success(ForgePrice price, FallnightCustomEnchantRegistry.Definition enchant, int level, ItemStack book) {
            return new EnchantForgeResult(true, true, price, enchant, level, book);
        }
    }
}
