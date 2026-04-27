package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.WorldAccessService;
import xyz.fallnight.server.domain.holotext.HolotextEntry;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.instance.Instance;

public final class HolotextService {
    private final Path file;
    private final ObjectMapper yaml;
    private final LegacyComponentSerializer serializer;
    private final Map<String, HolotextEntry> entries;
    private final ConcurrentMap<String, Entity> spawned;

    public static HolotextService fromDataRoot(Path dataRoot) {
        return new HolotextService(dataRoot.resolve("holotext.yml"));
    }

    public HolotextService(Path file) {
        this.file = file;
        this.yaml = JacksonMappers.yamlMapper();
        this.serializer = LegacyComponentSerializer.legacySection();
        this.entries = new LinkedHashMap<>();
        this.spawned = new ConcurrentHashMap<>();
    }

    public synchronized void loadAll() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        entries.clear();
        if (!Files.exists(file) || Files.size(file) == 0L) {
            return;
        }
        Object loaded = yaml.readValue(file.toFile(), Object.class);
        loadFromObject(loaded);
    }

    public synchronized void saveAll() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> holotext = new LinkedHashMap<>();
            for (HolotextEntry entry : entries.values()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("world", entry.world());
                data.put("x", entry.x());
                data.put("y", entry.y());
                data.put("z", entry.z());
                data.put("title", entry.title());
                data.put("text", entry.text());
                holotext.put(entry.id(), data);
            }
            root.put("holotext", holotext);
            yaml.writeValue(file.toFile(), root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public synchronized List<HolotextEntry> allEntries() {
        return List.copyOf(entries.values());
    }

    public synchronized void spawnForWorld(Instance instance, String worldName) {
        despawnAll();
        for (HolotextEntry entry : entries.values()) {
            if (!matchesWorld(entry, worldName)) {
                continue;
            }
            Entity entity = createEntity(entry);
            entity.setInstance(instance, new Pos(entry.x(), entry.y(), entry.z())).join();
            spawned.put(entry.id(), entity);
        }
    }

    public synchronized void spawnAll(WorldAccessService worldAccessService) {
        despawnAll();
        for (HolotextEntry entry : entries.values()) {
            if (entry.world() == null || entry.world().isBlank()) {
                continue;
            }
            SpawnService world = worldAccessService.resolve(entry.world()).orElse(null);
            if (world == null) {
                continue;
            }
            Entity entity = createEntity(entry);
            entity.setInstance(world.instance(), new Pos(entry.x(), entry.y(), entry.z())).join();
            spawned.put(entry.id(), entity);
        }
    }

    public synchronized void despawnAll() {
        for (Entity entity : spawned.values()) {
            entity.remove();
        }
        spawned.clear();
    }

    private Entity createEntity(HolotextEntry entry) {
        Entity entity = new Entity(EntityType.ARMOR_STAND);
        entity.setNoGravity(true);
        entity.setInvisible(true);
        entity.setCustomNameVisible(true);
        entity.setCustomName(renderText(entry));
        if (entity.getEntityMeta() instanceof ArmorStandMeta armorStandMeta) {
            armorStandMeta.setMarker(true);
            armorStandMeta.setSmall(true);
            armorStandMeta.setHasNoBasePlate(true);
        }
        return entity;
    }

    private Component renderText(HolotextEntry entry) {
        String title = entry.title() == null ? "" : entry.title().trim();
        String text = entry.text() == null ? "" : entry.text().trim();
        String raw = title.isEmpty() ? text : (text.isEmpty() ? title : title + "\n" + text);
        return serializer.deserialize(raw);
    }

    @SuppressWarnings("unchecked")
    private void loadFromObject(Object loaded) {
        if (loaded instanceof List<?> list) {
            int index = 1;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> data)) {
                    continue;
                }
                HolotextEntry entry = parseEntry(String.valueOf(index++), data);
                if (entry != null) {
                    entries.put(entry.id(), entry);
                }
            }
            return;
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            return;
        }
        Object texts = root.get("holotext");
        if (!(texts instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                continue;
            }
            HolotextEntry parsed = parseEntry(String.valueOf(entry.getKey()), data);
            if (parsed != null) {
                entries.put(parsed.id(), parsed);
            }
        }
    }

    private static HolotextEntry parseEntry(String id, Map<?, ?> data) {
        String world = stringValue(data.get("world"), "spawn");
        if (id == null || id.isBlank() || world.isBlank()) {
            return null;
        }
        HolotextEntry entry = new HolotextEntry();
        entry.setId(id.trim());
        entry.setWorld(world.trim());
        entry.setX(doubleValue(data.get("x"), 0d));
        entry.setY(doubleValue(data.get("y"), 0d));
        entry.setZ(doubleValue(data.get("z"), 0d));
        entry.setTitle(stringValue(data.get("title"), ""));
        entry.setText(stringValue(data.get("text"), ""));
        return entry;
    }

    private static boolean matchesWorld(HolotextEntry entry, String worldName) {
        String entryWorld = entry.world() == null ? "" : entry.world().trim().toLowerCase(Locale.ROOT);
        String activeWorld = worldName == null ? "" : worldName.trim().toLowerCase(Locale.ROOT);
        return entryWorld.equals(activeWorld);
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String text) {
            return text;
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
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
