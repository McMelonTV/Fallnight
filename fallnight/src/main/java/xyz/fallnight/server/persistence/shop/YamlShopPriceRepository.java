package xyz.fallnight.server.persistence.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minestom.server.item.Material;

public final class YamlShopPriceRepository implements ShopPriceRepository {
    private static final Map<String, Double> DEFAULT_PRICES = Map.ofEntries(
        Map.entry("minecraft:coal", 3.5d),
        Map.entry("minecraft:coal_ore", 20d),
        Map.entry("minecraft:deepslate_coal_ore", 24d),
        Map.entry("minecraft:iron_ingot", 12d),
        Map.entry("minecraft:iron_ore", 28d),
        Map.entry("minecraft:deepslate_iron_ore", 34d),
        Map.entry("minecraft:copper_ingot", 8d),
        Map.entry("minecraft:gold_ingot", 18d),
        Map.entry("minecraft:gold_ore", 38d),
        Map.entry("minecraft:deepslate_gold_ore", 46d),
        Map.entry("minecraft:redstone", 6d),
        Map.entry("minecraft:redstone_ore", 26d),
        Map.entry("minecraft:deepslate_redstone_ore", 32d),
        Map.entry("minecraft:lapis_lazuli", 10d),
        Map.entry("minecraft:lapis_ore", 30d),
        Map.entry("minecraft:deepslate_lapis_ore", 36d),
        Map.entry("minecraft:diamond", 42d),
        Map.entry("minecraft:diamond_ore", 80d),
        Map.entry("minecraft:deepslate_diamond_ore", 95d),
        Map.entry("minecraft:emerald", 55d),
        Map.entry("minecraft:emerald_ore", 100d),
        Map.entry("minecraft:deepslate_emerald_ore", 120d),
        Map.entry("minecraft:quartz", 8d),
        Map.entry("minecraft:nether_quartz_ore", 24d),
        Map.entry("minecraft:ancient_debris", 240d)
    );

    private final Path pricesFile;
    private final ObjectMapper yaml;

    public YamlShopPriceRepository(Path pricesFile) {
        this(pricesFile, JacksonMappers.yamlMapper());
    }

    public YamlShopPriceRepository(Path pricesFile, ObjectMapper yaml) {
        this.pricesFile = pricesFile;
        this.yaml = yaml;
    }

    @Override
    public Map<Material, Double> loadPrices() {
        Path parent = pricesFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ignored) {
                return defaultPrices();
            }
        }

        if (!Files.exists(pricesFile)) {
            return defaultPrices();
        }

        try {
            Object root = yaml.readValue(pricesFile.toFile(), Object.class);
            Map<Material, Double> loaded = new LinkedHashMap<>();
            parseRoot(root, loaded);
            if (loaded.isEmpty()) {
                return defaultPrices();
            }
            return Map.copyOf(loaded);
        } catch (IOException ignored) {
            return defaultPrices();
        }
    }

    private static void parseRoot(Object root, Map<Material, Double> prices) {
        if (root == null) {
            return;
        }

        if (root instanceof Map<?, ?> map) {
            parseMap(map, prices);
            return;
        }

        if (root instanceof Iterable<?> iterable) {
            parseList(iterable, prices);
        }
    }

    private static void parseMap(Map<?, ?> map, Map<Material, Double> prices) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Material material = resolveMaterial(entry.getKey());
            Double amount = toPositiveDouble(entry.getValue());

            if (material != null && amount != null) {
                prices.put(material, amount);
                continue;
            }

            if (!(entry.getValue() instanceof Map<?, ?> nested)) {
                continue;
            }

            if (material == null) {
                material = resolveMaterial(nested.get("material"));
                if (material == null) {
                    material = resolveMaterial(nested.get("block"));
                }
                if (material == null) {
                    material = resolveMaterial(nested.get("id"));
                }
            }

            if (amount == null) {
                amount = toPositiveDouble(nested.get("price"));
            }
            if (amount == null) {
                amount = toPositiveDouble(nested.get("value"));
            }

            if (material != null && amount != null) {
                prices.put(material, amount);
            }
        }
    }

    private static void parseList(Iterable<?> list, Map<Material, Double> prices) {
        for (Object rawEntry : list) {
            if (!(rawEntry instanceof Map<?, ?> entry)) {
                continue;
            }

            Material material = resolveMaterial(entry.get("material"));
            if (material == null) {
                material = resolveMaterial(entry.get("block"));
            }
            if (material == null) {
                material = resolveMaterial(entry.get("id"));
            }
            if (material == null) {
                material = resolveMaterial(entry.get("item"));
            }

            Double amount = toPositiveDouble(entry.get("price"));
            if (amount == null) {
                amount = toPositiveDouble(entry.get("value"));
            }
            if (amount == null) {
                amount = toPositiveDouble(entry.get("cost"));
            }

            if (material != null && amount != null) {
                prices.put(material, amount);
            }
        }
    }

    private static Map<Material, Double> defaultPrices() {
        Map<Material, Double> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : DEFAULT_PRICES.entrySet()) {
            Material material = Material.fromKey(entry.getKey());
            if (material != null) {
                defaults.put(material, entry.getValue());
            }
        }
        return Map.copyOf(defaults);
    }

    private static Material resolveMaterial(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Material.fromKey(normalized);
    }

    private static Double toPositiveDouble(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0d ? value : null;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                double parsed = Double.parseDouble(text.trim());
                return parsed > 0d ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
