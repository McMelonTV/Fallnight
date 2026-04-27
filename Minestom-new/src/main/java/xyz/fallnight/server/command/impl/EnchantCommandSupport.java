package xyz.fallnight.server.command.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

final class EnchantCommandSupport {
    private static final Set<String> KNOWN_ENCHANTMENTS = Set.of(
        "aqua_affinity",
        "bane_of_arthropods",
        "binding_curse",
        "blast_protection",
        "breach",
        "channeling",
        "density",
        "depth_strider",
        "efficiency",
        "feather_falling",
        "fire_aspect",
        "fire_protection",
        "flame",
        "fortune",
        "frost_walker",
        "impaling",
        "infinity",
        "knockback",
        "looting",
        "loyalty",
        "luck_of_the_sea",
        "lunge",
        "lure",
        "mending",
        "multishot",
        "piercing",
        "power",
        "projectile_protection",
        "protection",
        "punch",
        "quick_charge",
        "respiration",
        "riptide",
        "sharpness",
        "silk_touch",
        "smite",
        "soul_speed",
        "sweeping_edge",
        "swift_sneak",
        "thorns",
        "unbreaking",
        "vanishing_curse",
        "wind_burst"
    );

    private EnchantCommandSupport() {
    }

    static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized;
    }

    static boolean isKnown(String enchantId) {
        return KNOWN_ENCHANTMENTS.contains(enchantId);
    }

    static Set<String> knownIds() {
        return KNOWN_ENCHANTMENTS;
    }

    static RegistryKey<Enchantment> key(String enchantId) {
        return RegistryKey.unsafeOf(enchantId);
    }

    static EnchantmentList currentList(ItemStack stack) {
        EnchantmentList list = stack.get(DataComponents.ENCHANTMENTS);
        return list == null ? EnchantmentList.EMPTY : list;
    }

    static ItemStack withEnchant(ItemStack stack, String enchantId, int level) {
        EnchantmentList list = currentList(stack);
        EnchantmentList next = list.with(key(enchantId), Math.max(1, level));
        return stack.with(DataComponents.ENCHANTMENTS, next);
    }

    static ItemStack removeEnchant(ItemStack stack, String enchantId) {
        EnchantmentList list = currentList(stack);
        EnchantmentList next = list.remove(key(enchantId));
        return stack.with(DataComponents.ENCHANTMENTS, next);
    }

    static ItemStack removeAllEnchants(ItemStack stack) {
        return stack.with(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY);
    }

    static Map<String, Integer> toDisplayMap(ItemStack stack) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<RegistryKey<Enchantment>, Integer> entry : currentList(stack).enchantments().entrySet()) {
            String id = entry.getKey().name().toLowerCase(Locale.ROOT);
            if (id.startsWith("minecraft:")) {
                id = id.substring("minecraft:".length());
            }
            map.put(id, Math.max(1, entry.getValue()));
        }
        return map;
    }
}
