package xyz.fallnight.server.gameplay.mine;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class MineLocalPriceRepository {
    private MineLocalPriceRepository() {
    }

    static Map<String, Double> loadDefaults(Path dataRoot) {
        ObjectMapper mapper = JacksonMappers.yamlMapper();
        Map<String, Double> prices = new LinkedHashMap<>();
        loadIfPresent(mapper, dataRoot.resolve("prices.yml"), prices);
        loadIfPresent(mapper, dataRoot.resolve("legacy").resolve("prices.yml"), prices);
        return Map.copyOf(prices);
    }

    private static void loadIfPresent(ObjectMapper mapper, Path path, Map<String, Double> prices) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Object loaded = mapper.readValue(path.toFile(), Object.class);
            if (loaded instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> {
                    String id = normalizeNamespace(key);
                    Double amount = toDouble(value);
                    if (id != null && amount != null) {
                        prices.put(id, amount);
                    }
                });
                return;
            }

            if (loaded instanceof Iterable<?> iterable) {
                for (Object entry : iterable) {
                    if (!(entry instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String block = normalizeNamespace(map.get("block"));
                    if (block == null) {
                        block = normalizeNamespace(map.get("id"));
                    }
                    Double amount = toDouble(map.get("price"));
                    if (amount == null) {
                        amount = toDouble(map.get("value"));
                    }
                    if (block != null && amount != null) {
                        prices.put(block, amount);
                    }
                }
            }
        } catch (IOException ignored) {
            // Local prices are optional at runtime.
        }
    }

    private static String normalizeNamespace(Object value) {
        if (!(value instanceof String raw) || raw.isBlank()) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
