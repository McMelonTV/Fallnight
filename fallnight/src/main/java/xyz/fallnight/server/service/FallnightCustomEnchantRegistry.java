package xyz.fallnight.server.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FallnightCustomEnchantRegistry {
    public static final String DRILLER = "driller";
    public static final String PROFIT = "profit";
    public static final String FUSION = "fusion";
    public static final String FREEZE = "freeze";
    public static final String ICE_THORNS = "ice_thorns";
    public static final String EXTRACTION = "extraction";
    public static final String STARDUST_EXTRACTION = "stardust_extraction";
    public static final String AUTOREPAIR = "autorepair";
    public static final String XP_EXTRACTION = "xp_extraction";
    public static final String FIRE_ASPECT = "fire_aspect_custom";
    public static final String POISON = "poison";
    public static final String CRITICAL = "critical";
    public static final String DEATHBRINGER = "deathbringer";
    public static final String DAMAGE = "damage";
    public static final String LIFESTEAL = "lifesteal";
    public static final String EXPLOSIVE = "explosive";
    public static final String HEALTH = "health";
    public static final String LEAPER = "leaper";
    public static final String RUNNER = "runner";
    public static final String NIGHT_VISION = "night_vision_custom";
    public static final String DEFLECT = "deflect";
    public static final String TANK = "tank";
    public static final String TOUGHNESS = "toughness";
    public static final String BLESSING = "blessing";
    public static final String FIRE_THORNS = "fire_thorns";
    public static final String LIGHTNING = "lightning";
    public static final String INSULATOR = "insulator";
    public static final String AERIAL = "aerial";
    public static final String UNBREAKING_CUSTOM = "unbreaking_custom";
    public static final String HEAT_SHIELD = "heatshield";
    public static final String OBSIDIAN_BREAKER = "obsidian_breaker";

    private static final Map<Integer, Definition> BY_LEGACY_ID;
    private static final Map<String, Definition> BY_ID;

    static {
        Map<Integer, Definition> legacy = new LinkedHashMap<>();
        register(legacy, new Definition(101, DRILLER, "Driller", 10, "tools", "Mine in a 3x3 area.", 1, 1));
        register(legacy, new Definition(102, PROFIT, "Profit", 10, "pickaxe", "Sell your mined resources at a higher price.", 4, 4));
        register(legacy, new Definition(104, FUSION, "Fusion", 5, "pickaxe", "Gives the tool a chance to transform a resource into a higher value resource.", 7, 6));
        register(legacy, new Definition(105, FREEZE, "Freeze", 5, "sword", "Has a chance to slow a player down on hit for 3 seconds.", 7, 6, false));
        register(legacy, new Definition(106, ICE_THORNS, "Ice Thorns", 5, "chestplate", "Has a chance to slow a player down for 3 seconds when they hit you.", 5, 5, false));
        register(legacy, new Definition(109, EXTRACTION, "Extraction", 8, "pickaxe", "Get more resources while mining.", 2, 3));
        register(legacy, new Definition(110, STARDUST_EXTRACTION, "Stardust Extraction", 1, "pickaxe", "Get stardust while mining. You will not get steeldust anymore.", 2, 3));
        register(legacy, new Definition(113, UNBREAKING_CUSTOM, "Unbreaking", 6, "all", "Has a chance to not take away durability on item use.", 4, 4));
        register(legacy, new Definition(114, AUTOREPAIR, "Autorepair", 1, "all", "Repair the item with 1 obsidian shard whenever it's durability passes below 50%.", 4, 4));
        register(legacy, new Definition(119, FIRE_ASPECT, "Fire Aspect", 5, "sword", "Has a chance to light a player on fire.", 7, 6));
        register(legacy, new Definition(124, POISON, "Poison", 5, "sword", "Has a chance to give poison for 5 seconds to someone when attacking", 7, 6));
        register(legacy, new Definition(127, DAMAGE, "Damage", 10, "sword", "Deals more damage when attacking", 4, 4));
        register(legacy, new Definition(128, LIFESTEAL, "Lifesteal", 5, "sword", "Receive health when killing someone", 2, 3));
        register(legacy, new Definition(122, DEATHBRINGER, "Deathbringer", 5, "sword", "Has a chance to deal more damage.", 4, 4));
        register(legacy, new Definition(135, CRITICAL, "Critical", 3, "sword", "Has a chance to critical your opponent.", 2, 3));
        register(legacy, new Definition(133, XP_EXTRACTION, "XP Extraction", 5, "pickaxe", "Get more XP while mining.", 5, 5));
        register(legacy, new Definition(121, HEAT_SHIELD, "Heatshield", 4, "chestplate", "Reduces fire damage.", 4, 4));
        register(legacy, new Definition(129, OBSIDIAN_BREAKER, "Obsidian Breaker", 5, "pickaxe", "Chance to instantly break obsidian", 1, 1));
        register(legacy, new Definition(115, HEALTH, "Health", 1, "armor", "Get an extra heart of HP.", 1, 1));
        register(legacy, new Definition(116, LEAPER, "Leaper", 2, "boots", "Get jump boost", 8, 0));
        register(legacy, new Definition(117, RUNNER, "Runner", 2, "boots", "Get a speed boost", 7, 6));
        register(legacy, new Definition(118, NIGHT_VISION, "Night Vision", 1, "helmet", "See in the dark", 8, 0));
        register(legacy, new Definition(126, EXPLOSIVE, "Explosive", 1, "chestplate", "Explode when killed", 1, 1, false));
        register(legacy, new Definition(130, DEFLECT, "Deflect", 5, "sword", "Has a chance to deflect someones hit on themself", 7, 6, false));
        register(legacy, new Definition(131, TANK, "Tank", 5, "chestplate", "Reduce damage received", 2, 3));
        register(legacy, new Definition(132, TOUGHNESS, "Toughness", 10, "chestplate", "Reduce damage received from swords", 4, 4));
        register(legacy, new Definition(123, BLESSING, "Blessing", 6, "helmet", "Has a chance to remove all negative effects when hit", 5, 5));
        register(legacy, new Definition(120, FIRE_THORNS, "Fire Thorns", 5, "chestplate", "Has a chance to light an enemy on fire when they hit you.", 7, 6));
        register(legacy, new Definition(125, LIGHTNING, "Lightning", 3, "sword", "Has a chance to strike someone with lightning, dealing 3 hearts of damage + fire damage", 1, 1));
        register(legacy, new Definition(136, INSULATOR, "Insulator", 3, "helmet", "Insulates lightning.", 5, 5));
        register(legacy, new Definition(134, AERIAL, "Aerial", 6, "sword", "Deal more damage to opponents in air.", 4, 4));
        BY_LEGACY_ID = Map.copyOf(legacy);
        Map<String, Definition> byId = new LinkedHashMap<>();
        for (Definition definition : legacy.values()) {
            byId.put(definition.id(), definition);
        }
        BY_ID = Map.copyOf(byId);
    }

    private FallnightCustomEnchantRegistry() {
    }

    public static Optional<Definition> byLegacyId(int legacyId) {
        return Optional.ofNullable(BY_LEGACY_ID.get(legacyId));
    }

    public static Optional<Definition> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public static boolean isCustomEnchantId(String id) {
        return byId(id).isPresent();
    }

    public static List<Definition> all() {
        return BY_ID.values().stream().sorted(Comparator.comparingInt(Definition::legacyId)).toList();
    }

    public static List<Definition> registeredDefaults() {
        return all().stream().filter(Definition::registeredByDefault).toList();
    }

    private static void register(Map<Integer, Definition> target, Definition definition) {
        target.put(definition.legacyId(), definition);
    }

    public record Definition(int legacyId, String id, String displayName, int maxLevel, String compatibility, String description, int forgeWeight, int highEndForgeWeight, boolean registeredByDefault) {
        public Definition(int legacyId, String id, String displayName, int maxLevel, String compatibility, String description, int forgeWeight, int highEndForgeWeight) {
            this(legacyId, id, displayName, maxLevel, compatibility, description, forgeWeight, highEndForgeWeight, true);
        }

        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(compatibility, "compatibility");
            Objects.requireNonNull(description, "description");
        }

        public int applyCost(int level) {
            int clamped = Math.max(1, level);
            int multiplier = switch (rarityBucket()) {
                case 0 -> 3;
                case 1 -> 6;
                case 2 -> 8;
                case 3 -> 12;
                case 4 -> 15;
                default -> 20;
            };
            return multiplier * clamped;
        }

        public int rarityBucket() {
            int weight = Math.max(forgeWeight, highEndForgeWeight);
            if (weight >= 8) {
                return 0;
            }
            if (weight == 7) {
                return 1;
            }
            if (weight >= 5) {
                return 2;
            }
            if (weight == 4) {
                return 3;
            }
            if (weight >= 2) {
                return 4;
            }
            return 5;
        }

        public int disenchantMagicDustCost() {
            return switch (rarityBucket()) {
                case 0 -> 5;
                case 1 -> 8;
                case 2 -> 14;
                case 3 -> 17;
                case 4 -> 22;
                default -> 25;
            };
        }

        public int disenchantXpCost() {
            return switch (rarityBucket()) {
                case 0 -> 1;
                case 1 -> 3;
                case 2 -> 7;
                case 3 -> 10;
                case 4 -> 15;
                default -> 18;
            };
        }
    }
}
