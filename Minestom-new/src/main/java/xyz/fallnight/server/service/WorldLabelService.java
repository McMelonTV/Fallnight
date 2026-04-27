package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.persistence.JacksonMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldLabelService {
    private final Path file;
    private final ObjectMapper yaml;

    public WorldLabelService(Path file) {
        this.file = file;
        this.yaml = JacksonMappers.yamlMapper();
    }

    public static WorldLabelService fromDataRoot(Path dataRoot) {
        return new WorldLabelService(dataRoot.resolve("worlds.yml"));
    }

    private static String readLabel(Object raw, String fallback) {
        if (raw instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
    }

    public WorldLabels loadOrDefaults(String main, String plots, String pvpMine) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0L) {
            WorldLabels labels = new WorldLabels(main, plots, pvpMine);
            save(labels);
            return labels;
        }
        Object loaded = yaml.readValue(file.toFile(), Object.class);
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("worlds") instanceof Map<?, ?> worlds)) {
            WorldLabels labels = new WorldLabels(main, plots, pvpMine);
            save(labels);
            return labels;
        }
        return new WorldLabels(
                readLabel(worlds.get("main"), main),
                readLabel(worlds.get("plots"), plots),
                readLabel(worlds.get("pvpMine"), pvpMine)
        );
    }

    public void save(WorldLabels labels) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> worlds = new LinkedHashMap<>();
            worlds.put("main", labels.main());
            worlds.put("plots", labels.plots());
            worlds.put("pvpMine", labels.pvpMine());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("worlds", worlds);
            yaml.writeValue(file.toFile(), root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public record WorldLabels(String main, String plots, String pvpMine) {
    }
}
