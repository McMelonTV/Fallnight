package xyz.fallnight.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.WrittenBookContent;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LegacyCustomItemService {
    private static final List<String> KNOWN_ENCHANT_IDS = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection", "breach", "channeling",
            "density", "depth_strider", "efficiency", "feather_falling", "fire_aspect", "fire_protection", "flame",
            "fortune", "frost_walker", "impaling", "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea",
            "lunge", "lure", "mending", "multishot", "piercing", "power", "projectile_protection", "protection",
            "punch", "quick_charge", "respiration", "riptide", "sharpness", "silk_touch", "smite", "soul_speed",
            "sweeping_edge", "swift_sneak", "thorns", "unbreaking", "vanishing_curse", "wind_burst"
    );

    private static final Tag<Integer> CUSTOM_ITEM_TAG = Tag.Integer("customitem");
    private static final Tag<String> VARIANT_TAG = Tag.String("variant");
    private static final Tag<Integer> ENCHANT_ID_TAG = Tag.Integer("fnenchantid");
    private static final Tag<Integer> ENCHANT_LEVEL_TAG = Tag.Integer("fnenchantlvl");
    private static final Tag<Integer> CRATE_KEY_TAG = Tag.Integer("cratekey");
    private static final Tag<Integer> MAX_DAMAGE_TAG = Tag.Integer("maxDamage");
    private static final Tag<Integer> FN_DAMAGE_TAG = Tag.Integer("fnDamage");
    private static final Tag<String> DESCRIPTION_TAG = Tag.String("description");
    private static final Tag<String> CUSTOM_ENCHANTS_TAG = Tag.String("customenchants");
    private static final Tag<Integer> QUALITY_TAG = Tag.Integer("quality");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Map<Integer, Definition> byId;
    private final Map<String, Definition> byName;

    public LegacyCustomItemService() {
        Map<Integer, Definition> ids = new LinkedHashMap<>();
        register(ids, new Definition(1, "stardust", Material.GLOWSTONE_DUST));
        register(ids, new Definition(2, "magicdust", Material.BLUE_DYE));
        register(ids, new Definition(3, "obsidianshard", Material.BLACK_DYE));
        register(ids, new Definition(4, "steeldust", Material.SUGAR));
        register(ids, new Definition(5, "testpickaxe", Material.DIAMOND_PICKAXE));
        register(ids, new Definition(6, "basicpickaxe", Material.IRON_PICKAXE));
        register(ids, new Definition(7, "advancedpickaxe", Material.DIAMOND_PICKAXE));
        register(ids, new Definition(8, "enchantmentbook", Material.ENCHANTED_BOOK));
        register(ids, new Definition(9, "basichelmet", Material.IRON_HELMET));
        register(ids, new Definition(10, "basicchestplate", Material.IRON_CHESTPLATE));
        register(ids, new Definition(11, "basicleggings", Material.IRON_LEGGINGS));
        register(ids, new Definition(12, "basicboots", Material.IRON_BOOTS));
        register(ids, new Definition(13, "basicsword", Material.IRON_SWORD));
        register(ids, new Definition(14, "guidebook", Material.WRITTEN_BOOK));
        register(ids, new Definition(15, "advancedsword", Material.DIAMOND_SWORD));
        register(ids, new Definition(16, "advancedboots", Material.DIAMOND_BOOTS));
        register(ids, new Definition(17, "advancedleggings", Material.DIAMOND_LEGGINGS));
        register(ids, new Definition(18, "advancedchestplate", Material.DIAMOND_CHESTPLATE));
        register(ids, new Definition(19, "advancedhelmet", Material.DIAMOND_HELMET));
        register(ids, new Definition(20, "cratekey", Material.TRIPWIRE_HOOK));
        register(ids, new Definition(21, "healapple", Material.APPLE));
        register(ids, new Definition(22, "enchantedhealapple", Material.APPLE));
        register(ids, new Definition(23, "basicaxe", Material.IRON_AXE));
        register(ids, new Definition(24, "advancedaxe", Material.DIAMOND_AXE));
        register(ids, new Definition(25, "basicshovel", Material.IRON_SHOVEL));
        register(ids, new Definition(26, "advancedshovel", Material.DIAMOND_SHOVEL));
        register(ids, new Definition(27, "elitepickaxe", Material.GOLDEN_PICKAXE));
        register(ids, new Definition(28, "kothchestplate", Material.DIAMOND_CHESTPLATE));
        register(ids, new Definition(29, "kothpickaxe", Material.DIAMOND_PICKAXE));
        this.byId = Map.copyOf(ids);
        Map<String, Definition> names = new LinkedHashMap<>();
        for (Definition definition : ids.values()) {
            names.put(normalizeItemName(definition.name()), definition);
        }
        this.byName = Map.copyOf(names);
    }

    private static boolean armorEnchantCompatible(String materialName, String compatibility) {
        if (compatibility == null || compatibility.isBlank()) {
            return false;
        }
        if (compatibility.equals("armor") || compatibility.equals("all")) {
            return true;
        }
        if (materialName.endsWith("helmet")) {
            return compatibility.equals("helmet");
        }
        if (materialName.endsWith("chestplate")) {
            return compatibility.equals("chestplate");
        }
        if (materialName.endsWith("leggings")) {
            return compatibility.equals("leggings");
        }
        if (materialName.endsWith("boots")) {
            return compatibility.equals("boots");
        }
        return false;
    }

    private static String tierDescription(String family, String kind) {
        return switch (family + ":" + kind) {
            case "Basic:Pickaxe" -> "§r§7A basic pickaxe, crafted using steeldust.";
            case "Basic:Sword" -> "§r§7A basic sword, crafted using steeldust.";
            case "Basic:Axe" -> "§r§7A basic axe, crafted using steeldust.";
            case "Basic:Shovel" -> "§r§7A basic shovel, crafted using steeldust.";
            case "Basic:Helmet" -> "§r§7A basic helmet, crafted using steeldust.";
            case "Basic:Chestplate" -> "§r§7A basic chestplate, crafted using steeldust.";
            case "Basic:Leggings" -> "§r§7Basic leggings, crafted using steeldust.";
            case "Basic:Boots" -> "§r§7Basic boots, crafted using steeldust.";
            case "Advanced:Pickaxe" -> "§r§7The advanced pickaxe is faster and more durable than the basic variant.";
            case "Advanced:Axe" -> "§r§7The advanced axe is faster and more durable than the basic variant.";
            case "Advanced:Shovel" -> "§r§7The advanced shovel is faster and more durable than the basic variant.";
            case "Advanced:Sword" -> "§r§7A stronger and more durable sword.";
            case "Advanced:Helmet" -> "§r§7A stronger and more durable helmet.";
            case "Advanced:Chestplate" -> "§r§7A stronger and more durable chestplate.";
            case "Advanced:Leggings" -> "§r§7Stronger and more durable leggings.";
            case "Advanced:Boots" -> "§r§7Stronger and more durable boots.";
            case "Elite:Pickaxe" -> "§r§7This pickaxe mines faster than any other!";
            case "Elite:Chestplate" -> "§r§7An even stronger and more durable chestplate won from KOTH.";
            default -> "";
        };
    }

    private static void register(Map<Integer, Definition> map, Definition definition) {
        map.put(definition.id(), definition);
    }

    private static String resolveEnchantVariant(int variant) {
        List<String> ids = KNOWN_ENCHANT_IDS.stream().sorted().toList();
        if (ids.isEmpty()) {
            return "efficiency";
        }
        int index = Math.floorMod(variant - 1, ids.size());
        return ids.get(index);
    }

    private static String displayEnchantName(String enchantId) {
        String[] parts = enchantId.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static String baseKind(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("pickaxe")) return "Pickaxe";
        if (normalized.endsWith("sword")) return "Sword";
        if (normalized.endsWith("helmet")) return "Helmet";
        if (normalized.endsWith("chestplate")) return "Chestplate";
        if (normalized.endsWith("leggings")) return "Leggings";
        if (normalized.endsWith("boots")) return "Boots";
        if (normalized.endsWith("axe")) return "Axe";
        if (normalized.endsWith("shovel")) return "Shovel";
        return displayName(name);
    }

    private static int maxTier(int id) {
        return switch (id) {
            case 13, 15, 9, 10, 11, 12, 16, 17, 18, 19 -> 5;
            case 23, 24, 25, 26, 27 -> 3;
            default -> 10;
        };
    }

    private static TierSpec tierSpec(int id, int tier) {
        return switch (id) {
            case 6 -> new TierSpec(200 * tier, Math.max(0, tier - 1), "efficiency", "Efficiency");
            case 7 -> new TierSpec(1000 * tier + 0 + (tier == 1 ? 500 : 0), 7 + tier, "efficiency", "Efficiency");
            case 9, 10, 11, 12, 13 ->
                    new TierSpec(200 * tier, Math.max(0, tier - 1), id == 13 ? "sharpness" : "protection", id == 13 ? "Sharpness" : "Protection");
            case 15, 16, 17, 18, 19 ->
                    new TierSpec(1000 * tier + 0 + (tier == 1 ? 500 : 0), 3 + tier, id == 15 ? "sharpness" : "protection", id == 15 ? "Sharpness" : "Protection");
            case 23, 25 ->
                    new TierSpec(tier == 1 ? 400 : tier == 2 ? 1200 : 2000, tier == 1 ? 1 : tier == 2 ? 4 : 8, "efficiency", "Efficiency");
            case 24, 26 ->
                    new TierSpec(tier == 1 ? 1500 : tier == 2 ? 5000 : 10000, tier == 1 ? 8 : tier == 2 ? 12 : 17, "efficiency", "Efficiency");
            case 27 ->
                    new TierSpec(tier == 1 ? 15000 : tier == 2 ? 20000 : 25000, tier == 1 ? 45 : tier == 2 ? 55 : 65, "efficiency", "Efficiency");
            default -> new TierSpec(200 * tier, Math.max(0, tier - 1), "efficiency", "Efficiency");
        };
    }

    private static int applyQuality(int baseMaxDamage, int quality) {
        return baseMaxDamage + (int) (baseMaxDamage * (quality / 100d));
    }

    private static String displayName(String raw) {
        String normalized = raw.replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String normalizeItemName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private static String toRoman(int value) {
        int number = Math.max(1, value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                builder.append(numerals[i]);
                number -= values[i];
            }
        }
        return builder.toString();
    }

    private static CustomData baseCustomData(Definition definition) {
        return CustomData.EMPTY.withTag(CUSTOM_ITEM_TAG, definition.id());
    }

    private static CustomData existingCustomData(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        return data == null ? CustomData.EMPTY : data;
    }

    private static String serializeCustomEnchants(Map<String, Integer> enchants) {
        StringBuilder builder = new StringBuilder();
        enchants.forEach((id, level) -> {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(id).append('=').append(level);
        });
        return builder.toString();
    }

    private static int randomQuality() {
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(101);
        if (roll > 95) {
            return java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? java.util.concurrent.ThreadLocalRandom.current().nextInt(-30, -26)
                    : java.util.concurrent.ThreadLocalRandom.current().nextInt(27, 31);
        }
        if (roll > 80) {
            return java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? java.util.concurrent.ThreadLocalRandom.current().nextInt(-26, -19)
                    : java.util.concurrent.ThreadLocalRandom.current().nextInt(20, 27);
        }
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(-19, 20);
    }

    private static String qualityName(int quality) {
        if (quality <= -27) return "§4Terrible";
        if (quality <= -20) return "§cBad";
        if (quality <= -10) return "§8Mediocre";
        if (quality < 10) return "§7Average";
        if (quality < 20) return "§eSturdy";
        if (quality < 27) return "§aGood";
        return "§bLegendary";
    }

    private static String rarityColor(FallnightCustomEnchantRegistry.Definition definition) {
        if (definition == null) {
            return "§7";
        }
        int weight = Math.max(definition.forgeWeight(), definition.highEndForgeWeight());
        if (weight >= 8) return "§7";
        if (weight == 7) return "§a";
        if (weight >= 5) return "§c";
        if (weight == 4) return "§4";
        if (weight >= 2) return "§5";
        return "§l§6";
    }

    private static String enchantmentBookLore(String enchantId, int level) {
        FallnightCustomEnchantRegistry.Definition definition = FallnightCustomEnchantRegistry.byId(enchantId).orElse(null);
        String compatible = definition == null ? "all" : definition.compatibility();
        String rarityName = definition == null ? "Common" : rarityName(definition);
        String rarityColor = rarityColor(definition);
        int maxLevel = definition == null ? Math.max(1, level) : definition.maxLevel();
        int applyCost = definition == null ? 0 : definition.applyCost(level);
        return "§r§7" + (definition == null ? displayEnchantDescription(enchantId) : definition.description())
                + "\n§r§7Rarity: §r" + rarityColor + rarityName
                + "\n§r§7Max level: §b" + maxLevel
                + "\n§r§7Compatible: §b" + compatible
                + "\n§r§7Apply cost: §b" + applyCost + " XP Levels"
                + "\n §r§8Drag this book onto a compatible\n§r§8item to apply the enchantment.";
    }

    private static List<Component> loreComponents(String... entries) {
        List<Component> lines = new java.util.ArrayList<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            for (String line : entry.split("\\n", -1)) {
                lines.add(LEGACY.deserialize(line));
            }
        }
        return List.copyOf(lines);
    }

    private static List<Component> loreComponents(List<String> entries) {
        return loreComponents(entries.toArray(String[]::new));
    }

    private static String rarityName(FallnightCustomEnchantRegistry.Definition definition) {
        if (definition == null) {
            return "Common";
        }
        int weight = Math.max(definition.forgeWeight(), definition.highEndForgeWeight());
        if (weight >= 8) return "Common";
        if (weight == 7) return "Uncommon";
        if (weight >= 5) return "Rare";
        if (weight == 4) return "Very Rare";
        if (weight >= 2) return "Mythic";
        return "Legendary";
    }

    private static String displayEnchantDescription(String enchantId) {
        return switch (enchantId) {
            case "efficiency" -> "Increases mining speed";
            case "sharpness" -> "Increases melee damage";
            case "protection" -> "Decreases incoming damage";
            case "fire_aspect" -> "Sets enemies on fire";
            case "fortune" -> "Increases block drops";
            case "unbreaking" -> "Reduces durability loss";
            case "silk_touch" -> "Allows fragile blocks to drop themselves";
            case "looting" -> "Increases mob drops";
            case "smite" -> "Deals more damage to undead mobs";
            case "bane_of_arthropods" -> "Deals more damage to arthropods";
            case "power" -> "Increases bow damage";
            case "punch" -> "Increases bow knockback";
            case "flame" -> "Sets arrows on fire";
            case "knockback" -> "Knocks enemies back farther";
            case "thorns" -> "Damages attackers when hit";
            default -> displayEnchantName(enchantId);
        };
    }

    private static String fullEnchantKey(String enchantId) {
        return enchantId.startsWith("minecraft:") ? enchantId : "minecraft:" + enchantId;
    }

    private static boolean oneOf(String value, String... expected) {
        for (String option : expected) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public Optional<ItemStack> createById(int id, int amount, Integer extraData) {
        return Optional.ofNullable(byId.get(id)).map(definition -> create(definition, amount, extraData));
    }

    public Optional<ItemStack> createByName(String name, int amount, Integer extraData) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(normalizeItemName(name))).map(definition -> create(definition, amount, extraData));
    }

    public ItemStack guideBook() {
        return guideBook(InfoPagesService.defaultGuidePage());
    }

    public ItemStack guideBook(List<String> guideLines) {
        return guideBook(byId.get(14), guideLines);
    }

    public Optional<ItemStack> createFromCustomId(String customId) {
        if (customId == null || customId.isBlank()) {
            return Optional.empty();
        }
        String[] parts = customId.split(":");
        try {
            int id = Integer.parseInt(parts[0].trim());
            int amount = 1;
            Integer extra = null;
            if (parts.length == 2) {
                int value = Integer.parseInt(parts[1].trim());
                if (id == 1 || id == 2 || id == 3 || id == 4 || id == 20) {
                    amount = value;
                } else {
                    extra = value;
                }
            } else if (parts.length >= 3) {
                amount = Integer.parseInt(parts[1].trim());
                extra = Integer.parseInt(parts[2].trim());
            }
            return createById(id, amount, extra);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public boolean isEnchantmentBook(ItemStack item) {
        return customItemId(item) == 8;
    }

    public Map<String, Integer> customEnchants(ItemStack item) {
        CustomData data = item == null ? null : item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Map.of();
        }
        String raw = data.getTag(CUSTOM_ENCHANTS_TAG);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0 || separator + 1 >= trimmed.length()) {
                continue;
            }
            try {
                parsed.put(trimmed.substring(0, separator), Integer.parseInt(trimmed.substring(separator + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return Map.copyOf(parsed);
    }

    public ItemStack withCustomEnchant(ItemStack item, String enchantId, int level) {
        if (item == null || item.isAir() || enchantId == null || enchantId.isBlank() || level <= 0) {
            return item;
        }
        Map<String, Integer> next = new LinkedHashMap<>(customEnchants(item));
        next.put(enchantId, level);
        CustomData data = existingCustomData(item).withTag(CUSTOM_ENCHANTS_TAG, serializeCustomEnchants(next));
        return withUpdatedLore(item.with(DataComponents.CUSTOM_DATA, data));
    }

    public ItemStack clearCustomEnchants(ItemStack item) {
        if (item == null || item.isAir()) {
            return item;
        }
        CustomData data = existingCustomData(item).withTag(CUSTOM_ENCHANTS_TAG, "");
        return withUpdatedLore(item.with(DataComponents.CUSTOM_DATA, data));
    }

    public ItemStack removeCustomEnchant(ItemStack item, String enchantId) {
        if (item == null || item.isAir() || enchantId == null || enchantId.isBlank()) {
            return item;
        }
        Map<String, Integer> next = new LinkedHashMap<>(customEnchants(item));
        next.remove(enchantId);
        CustomData data = existingCustomData(item).withTag(CUSTOM_ENCHANTS_TAG, serializeCustomEnchants(next));
        return withUpdatedLore(item.with(DataComponents.CUSTOM_DATA, data));
    }

    public int maxDamage(ItemStack item) {
        CustomData data = item == null ? null : item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        Integer value = data.getTag(MAX_DAMAGE_TAG);
        return value == null ? 0 : Math.max(0, value);
    }

    public int quality(ItemStack item) {
        CustomData data = item == null ? null : item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return -100;
        }
        Integer value = data.getTag(QUALITY_TAG);
        return value == null ? -100 : value;
    }

    public boolean isDurabilityItem(ItemStack item) {
        return switch (customItemId(item)) {
            case 5, 6, 7, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 23, 24, 25, 26, 27, 28, 29 -> maxDamage(item) > 0;
            default -> false;
        };
    }

    public ItemStack rerollQuality(ItemStack item) {
        if (item == null || item.isAir() || !isDurabilityItem(item)) {
            return item;
        }
        int quality = randomQuality();
        int baseMaxDamage = baseMaxDamage(item);
        int adjustedMaxDamage = baseMaxDamage + (int) (baseMaxDamage * (quality / 100d));
        CustomData data = existingCustomData(item)
                .withTag(QUALITY_TAG, quality)
                .withTag(MAX_DAMAGE_TAG, Math.max(1, adjustedMaxDamage));
        int currentDamage = Math.min(currentDamage(item), Math.max(0, adjustedMaxDamage - 1));
        data = data.withTag(FN_DAMAGE_TAG, currentDamage);
        return withUpdatedLore(withNativeDurability(item.with(DataComponents.CUSTOM_DATA, data), adjustedMaxDamage, currentDamage));
    }

    public int currentDamage(ItemStack item) {
        CustomData data = item == null ? null : item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        Integer value = data.getTag(FN_DAMAGE_TAG);
        return value == null ? 0 : Math.max(0, value);
    }

    public ItemStack applyDamage(ItemStack item, int delta) {
        if (!isDurabilityItem(item)) {
            return item;
        }
        int maxDamage = maxDamage(item);
        int nextDamage = Math.max(0, Math.min(maxDamage, currentDamage(item) + delta));
        ItemStack updated = withNativeDurability(
                item.with(DataComponents.CUSTOM_DATA, existingCustomData(item).withTag(FN_DAMAGE_TAG, nextDamage)),
                maxDamage,
                nextDamage
        );
        return withUpdatedLore(updated);
    }

    public boolean isBroken(ItemStack item) {
        int maxDamage = maxDamage(item);
        return isDurabilityItem(item) && currentDamage(item) >= maxDamage;
    }

    public int customItemId(ItemStack item) {
        if (item == null || item.isAir()) {
            return 0;
        }
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        Integer id = data.getTag(CUSTOM_ITEM_TAG);
        return id == null ? 0 : id;
    }

    public int enchantBookVariant(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        Integer variant = data.getTag(ENCHANT_ID_TAG);
        return variant == null ? 0 : variant;
    }

    public int enchantBookLevel(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        Integer level = data.getTag(ENCHANT_LEVEL_TAG);
        return level == null ? 0 : level;
    }

    public ItemStack enchantmentBookByVariantLevel(int variant, int level) {
        return create(byId.get(8), Math.max(1, level), variant);
    }

    public String enchantIdForVariant(int variant) {
        Optional<FallnightCustomEnchantRegistry.Definition> custom = FallnightCustomEnchantRegistry.byLegacyId(variant);
        if (custom.isPresent()) {
            return custom.get().id();
        }
        return resolveEnchantVariant(variant);
    }

    public boolean canApplyStoredEnchant(ItemStack item, String enchantId) {
        if (item == null || item.isAir() || enchantId == null || enchantId.isBlank()) {
            return false;
        }
        String name = item.material().name().toLowerCase(Locale.ROOT);
        if (name.endsWith("sword")) {
            return oneOf(enchantId, "sharpness", "smite", "bane_of_arthropods", "fire_aspect", "knockback", "looting", "sweeping_edge", "unbreaking", "mending")
                    || FallnightCustomEnchantRegistry.byId(enchantId).map(def -> def.compatibility().equals("sword") || def.compatibility().equals("all")).orElse(false);
        }
        if (name.endsWith("pickaxe")) {
            return oneOf(enchantId, "efficiency", "fortune", "silk_touch", "unbreaking", "mending")
                    || FallnightCustomEnchantRegistry.byId(enchantId).map(def -> def.compatibility().equals("pickaxe") || def.compatibility().equals("tools") || def.compatibility().equals("all")).orElse(false);
        }
        if (name.endsWith("axe") || name.endsWith("shovel")) {
            return oneOf(enchantId, "unbreaking", "mending")
                    || FallnightCustomEnchantRegistry.byId(enchantId).map(def -> def.compatibility().equals("tools") || def.compatibility().equals("all")).orElse(false);
        }
        if (name.endsWith("helmet") || name.endsWith("chestplate") || name.endsWith("leggings") || name.endsWith("boots")) {
            return oneOf(enchantId, "protection", "blast_protection", "fire_protection", "projectile_protection", "thorns", "unbreaking", "mending", "feather_falling", "depth_strider", "respiration", "aqua_affinity", "swift_sneak", "soul_speed")
                    || FallnightCustomEnchantRegistry.byId(enchantId).map(def -> armorEnchantCompatible(name, def.compatibility())).orElse(false);
        }
        if (name.endsWith("bow")) {
            return oneOf(enchantId, "power", "punch", "flame", "infinity", "unbreaking", "mending");
        }
        return false;
    }

    private ItemStack create(Definition definition, int amount, Integer extraData) {
        int clampedAmount = Math.max(1, amount);
        return switch (definition.id()) {
            case 8 -> enchantmentBook(definition, extraData == null ? 1 : extraData, clampedAmount);
            case 14 -> guideBook(definition);
            case 20 -> crateKey(definition, extraData == null ? 99 : extraData, clampedAmount);
            case 6, 7, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 23, 24, 25, 26, 27 ->
                    tieredItem(definition, extraData == null ? 1 : extraData, clampedAmount);
            case 21 ->
                    named(definition, clampedAmount, "§r§aHealth Apple", List.of("§r§7Get §a+2 hp §r§7when consumed"), true);
            case 22 ->
                    named(definition, clampedAmount, "§r§aEnchanted Health Apple", List.of("§r§7Get §a+4 hp §r§7when consumed"), true);
            case 28 ->
                    kothItem(definition, clampedAmount, 7000, 9, "protection", "§r§aKOTH Chestplate", "§r§7An even stronger and more durable chestplate won from KOTH.");
            case 29 ->
                    kothItem(definition, clampedAmount, 11000, 18, "efficiency", "§r§aKOTH Pickaxe", "§r§7The KOTH pickaxe is even faster and more durable than an advanced pickaxe.");
            case 1 ->
                    resource(definition, clampedAmount, "§r§eStardust", 200, "§r§7Can be used to forge advanced tools and armor.");
            case 2 ->
                    resource(definition, clampedAmount, "§r§1Magic Dust", 0, "§r§7Can be used to forge enchantments.");
            case 3 ->
                    resource(definition, clampedAmount, "§r§0Obsidian Shard", 400, "§r§7Can be used to more efficiently repair tools and armor.");
            case 4 ->
                    resource(definition, clampedAmount, "§r§fSteeldust", 50, "§r§7Can be used to forge basic tools and armor.");
            case 5 -> withUpdatedLore(withNativeDurability(
                    named(definition, clampedAmount, "§r§4Test Pickaxe", List.of(), true)
                            .with(DataComponents.CUSTOM_DATA, baseCustomData(definition).withTag(MAX_DAMAGE_TAG, 20).withTag(FN_DAMAGE_TAG, 0)),
                    20,
                    0
            ));
            default -> named(definition, clampedAmount, displayName(definition.name()), List.of(), false);
        };
    }

    private ItemStack resource(Definition definition, int amount, String name, int repairValue, String description) {
        CustomData data = baseCustomData(definition)
                .withTag(DESCRIPTION_TAG, description)
                .withTag(MAX_DAMAGE_TAG, repairValue)
                .withTag(FN_DAMAGE_TAG, 0);
        return base(definition, amount).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(description))))
                .with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf("minecraft:unbreaking"), 1))
                .with(DataComponents.CUSTOM_DATA, data);
    }

    private ItemStack tieredItem(Definition definition, int tier, int amount) {
        int normalizedTier = Math.max(1, Math.min(maxTier(definition.id()), tier));
        TierSpec spec = tierSpec(definition.id(), normalizedTier);
        String family = definition.name().startsWith("advanced") ? "Advanced" : definition.name().startsWith("elite") ? "Elite" : "Basic";
        String kind = baseKind(definition.name());
        int quality = randomQuality();
        int maxDamage = applyQuality(spec.maxDamage(), quality);
        CustomData data = baseCustomData(definition)
                .withTag(VARIANT_TAG, Integer.toString(normalizedTier))
                .withTag(MAX_DAMAGE_TAG, maxDamage)
                .withTag(FN_DAMAGE_TAG, 0)
                .withTag(QUALITY_TAG, quality)
                .withTag(DESCRIPTION_TAG, tierDescription(family, kind));
        ItemStack stack = withNativeDurability(base(definition, amount).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§o§7T" + normalizedTier + " §r§" + (family.equals("Advanced") ? "c" : family.equals("Elite") ? "9" : "b") + family + " " + kind)))
                .with(DataComponents.CUSTOM_DATA, data), maxDamage, 0);
        if (spec.enchantLevel() <= 0) {
            return withUpdatedLore(stack);
        }
        return withUpdatedLore(stack.with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf(fullEnchantKey(spec.enchantId())), spec.enchantLevel())));
    }

    private ItemStack kothItem(Definition definition, int amount, int baseMaxDamage, int enchantLevel, String enchantId, String name, String description) {
        int quality = randomQuality();
        int maxDamage = applyQuality(baseMaxDamage, quality);
        CustomData data = baseCustomData(definition)
                .withTag(MAX_DAMAGE_TAG, maxDamage)
                .withTag(FN_DAMAGE_TAG, 0)
                .withTag(QUALITY_TAG, quality)
                .withTag(DESCRIPTION_TAG, description);
        return withUpdatedLore(withNativeDurability(base(definition, amount).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)))
                .with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf(fullEnchantKey(enchantId)), enchantLevel))
                .with(DataComponents.CUSTOM_DATA, data), maxDamage, 0));
    }

    private ItemStack enchantmentBook(Definition definition, int enchantVariant, int level) {
        String enchantId = enchantIdForVariant(enchantVariant);
        int clampedLevel = Math.max(1, level);
        var enchant = FallnightCustomEnchantRegistry.byId(enchantId).orElse(null);
        String display = (enchant == null ? displayEnchantName(enchantId) : enchant.displayName())
                + (clampedLevel > 1 ? " " + toRoman(clampedLevel) : "");
        CustomData data = baseCustomData(definition)
                .withTag(VARIANT_TAG, enchantVariant + ":" + clampedLevel)
                .withTag(ENCHANT_ID_TAG, enchantVariant)
                .withTag(ENCHANT_LEVEL_TAG, clampedLevel);
        ItemStack stack = base(definition, 1).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r" + rarityColor(enchant) + display + " §r§fBook"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(loreComponents(enchantmentBookLore(enchantId, clampedLevel))))
                .with(DataComponents.CUSTOM_DATA, data);
        if (!FallnightCustomEnchantRegistry.isCustomEnchantId(enchantId)) {
            stack = stack.with(DataComponents.STORED_ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf(fullEnchantKey(enchantId)), clampedLevel));
        }
        return stack;
    }

    private ItemStack crateKey(Definition definition, int crateId, int amount) {
        String crateName = switch (crateId) {
            case 10 -> "Iron";
            case 20 -> "Gold";
            case 30 -> "Diamond";
            case 40 -> "Emerald";
            case 50 -> "Netherrite";
            case 220 -> "Rare";
            case 230 -> "Legendary";
            case 120 -> "KOTH";
            default -> "Vote";
        };
        CustomData data = baseCustomData(definition)
                .withTag(VARIANT_TAG, Integer.toString(crateId))
                .withTag(CRATE_KEY_TAG, crateId);
        return base(definition, amount).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§b§l" + crateName + "§r§7 key"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(loreComponents(
                "§r§7Go to §b/crates§r§7 to open the crate.",
                "§r§7Tap the crate to open it.",
                "§r§7Sneak + tap the crate to view possible rewards."
        )))
                .with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf("minecraft:unbreaking"), 1))
                .with(DataComponents.CUSTOM_DATA, data);
    }

    private ItemStack guideBook(Definition definition) {
        return guideBook(definition, InfoPagesService.defaultGuidePage());
    }

    private ItemStack guideBook(Definition definition, List<String> guideLines) {
        List<Component> pages = new BookMenuService().pages(guideLines);
        return base(definition, 1).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§bFallnight Guide Book"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(loreComponents("§r§7A guide to help new players\n§r§7to get familiar with the server.")))
                .with(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                        "Fallnight Guide Book",
                        "§bFallnight§r",
                        pages
                ))
                .with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf("minecraft:unbreaking"), 1))
                .with(DataComponents.CUSTOM_DATA, baseCustomData(definition));
    }

    private ItemStack named(Definition definition, int amount, String name, List<String> lore, boolean glow) {
        ItemStack stack = base(definition, amount).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)));
        if (!lore.isEmpty()) {
            stack = stack.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(loreComponents(lore)));
        }
        if (glow) {
            stack = stack.with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(RegistryKey.unsafeOf("minecraft:unbreaking"), 1));
        }
        return stack.with(DataComponents.CUSTOM_DATA, baseCustomData(definition));
    }

    private ItemStack base(Definition definition, int amount) {
        return ItemStack.of(definition.baseMaterial(), Math.max(1, amount));
    }

    private ItemStack withNativeDurability(ItemStack item, int maxDamage, int currentDamage) {
        if (item == null || item.isAir() || maxDamage <= 0 || !isDurabilityItem(item)) {
            return item;
        }
        int clampedCurrentDamage = Math.max(0, Math.min(maxDamage, currentDamage));
        return item.with(DataComponents.MAX_DAMAGE, maxDamage)
                .with(DataComponents.DAMAGE, clampedCurrentDamage);
    }

    private ItemStack withUpdatedLore(ItemStack item) {
        CustomData data = existingCustomData(item);
        List<Component> lines = new java.util.ArrayList<>();
        String signed = data.getTag(Tag.String("signed"));
        if (signed != null && !signed.isBlank()) {
            lines.add(LEGACY.deserialize("§r§7Signed by: §b" + signed));
        }
        String description = data.getTag(DESCRIPTION_TAG);
        if (description != null && !description.isBlank()) {
            lines.addAll(loreComponents(description));
        }
        int quality = quality(item);
        if (quality != -100) {
            lines.add(LEGACY.deserialize("§r§7Quality: " + qualityName(quality) + "§r§8 (" + (quality >= 0 ? "+" : "") + quality + "§8%)"));
        }
        int maxDamage = maxDamage(item);
        if (isDurabilityItem(item) && maxDamage > 0) {
            lines.add(LEGACY.deserialize("§r§7Durability: §b" + Math.max(0, maxDamage - currentDamage(item)) + "§8/§b" + maxDamage));
        }
        Map<String, Integer> enchants = customEnchants(item);
        if (!enchants.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            enchants.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String display = FallnightCustomEnchantRegistry.byId(entry.getKey()).map(FallnightCustomEnchantRegistry.Definition::displayName).orElse(displayEnchantName(entry.getKey()));
                String color = FallnightCustomEnchantRegistry.byId(entry.getKey()).map(LegacyCustomItemService::rarityColor).orElse("§7");
                lines.add(LEGACY.deserialize("§r" + color + display + (entry.getValue() > 1 ? " " + toRoman(entry.getValue()) : "")));
            });
        }
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lines));
    }

    private int baseMaxDamage(ItemStack item) {
        int custom = maxDamage(item);
        int quality = quality(item);
        if (custom <= 0) {
            return 0;
        }
        if (quality == -100) {
            return custom;
        }
        double divisor = 1D + (quality / 100d);
        if (divisor <= 0D) {
            return custom;
        }
        return Math.max(1, (int) Math.round(custom / divisor));
    }

    private record TierSpec(int maxDamage, int enchantLevel, String enchantId, String enchantLabel) {
    }

    private record Definition(int id, String name, Material baseMaterial) {
        private Definition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(baseMaterial, "baseMaterial");
        }
    }
}
