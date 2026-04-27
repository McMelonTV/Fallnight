package xyz.fallnight.server.persistence.pvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.pvp.PvpZone;
import xyz.fallnight.server.domain.pvp.PvpZoneRegion;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class YamlPvpZoneRepository implements PvpZoneRepository {
    private static final List<String> ZONE_LIST_KEYS = List.of("zones", "pvpzones", "zoneList");
    private static final List<String> POS1_KEYS = List.of("pos1", "loc1", "point1", "location1");
    private static final List<String> POS2_KEYS = List.of("pos2", "loc2", "point2", "location2");

    private final Path file;
    private final String defaultWorldName;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, PvpZone> byName;

    public YamlPvpZoneRepository(Path file, String defaultWorldName) {
        this(file, defaultWorldName, JacksonMappers.yamlMapper());
    }

    public YamlPvpZoneRepository(Path file, String defaultWorldName, ObjectMapper mapper) {
        this.file = file;
        this.defaultWorldName = defaultWorldName == null || defaultWorldName.isBlank() ? "spawn" : defaultWorldName;
        this.mapper = mapper;
        this.byName = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, PvpZone> loadAll() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            byName.clear();
            return Map.of();
        }
        if (Files.size(file) == 0L) {
            byName.clear();
            return Map.of();
        }

        Object root = mapper.readValue(file.toFile(), Object.class);
        List<PvpZone> parsed = parseRoot(root);
        byName.clear();
        for (PvpZone zone : parsed) {
            byName.put(normalize(zone.getName()), zone);
        }
        return Map.copyOf(byName);
    }

    @Override
    public Optional<PvpZone> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    @Override
    public void save(PvpZone zone) throws IOException {
        if (zone == null || zone.getName() == null || zone.getName().isBlank()) {
            return;
        }
        byName.put(normalize(zone.getName()), zone);
        writeAll();
    }

    @Override
    public void remove(String name) throws IOException {
        if (name == null || name.isBlank()) {
            return;
        }
        byName.remove(normalize(name));
        writeAll();
    }

    @Override
    public void saveAll() throws IOException {
        writeAll();
    }

    private List<PvpZone> parseRoot(Object root) {
        List<PvpZone> zones = new ArrayList<>();
        if (root instanceof List<?> list) {
            for (Object entry : list) {
                parseZone(entry, null).ifPresent(zones::add);
            }
            return zones;
        }

        if (!(root instanceof Map<?, ?> map)) {
            return zones;
        }

        if (looksLikeZone(map)) {
            parseZone(map, null).ifPresent(zones::add);
            return zones;
        }

        Object listCandidate = extractListCandidate(map);
        if (listCandidate != null) {
            if (listCandidate instanceof List<?> list) {
                for (Object entry : list) {
                    parseZone(entry, null).ifPresent(zones::add);
                }
            } else if (listCandidate instanceof Map<?, ?> listedMap) {
                for (Map.Entry<?, ?> entry : listedMap.entrySet()) {
                    String name = stringValue(entry.getKey());
                    parseZone(entry.getValue(), name).ifPresent(zones::add);
                }
            }
            return zones;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = stringValue(entry.getKey());
            parseZone(entry.getValue(), name).ifPresent(zones::add);
        }
        return zones;
    }

    private Optional<PvpZone> parseZone(Object value, String fallbackName) {
        if (!(value instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        PvpZone zone = mapper.convertValue(map, PvpZone.class);
        if (zone.getName() == null || zone.getName().isBlank()) {
            zone.setName(fallbackName);
        }
        if (zone.getRegion() == null) {
            zone.setRegion(extractRegion(map));
        }

        if (zone.getName() == null || zone.getName().isBlank() || zone.getRegion() == null) {
            return Optional.empty();
        }
        if (zone.getWorld() == null || zone.getWorld().isBlank()) {
            zone.setWorld(defaultWorldName);
        }
        return Optional.of(zone);
    }

    private PvpZoneRegion extractRegion(Map<?, ?> map) {
        Integer x1 = intValue(firstNonNull(map, "x1", "minX"));
        Integer y1 = intValue(firstNonNull(map, "y1", "minY"));
        Integer z1 = intValue(firstNonNull(map, "z1", "minZ"));
        Integer x2 = intValue(firstNonNull(map, "x2", "maxX"));
        Integer y2 = intValue(firstNonNull(map, "y2", "maxY"));
        Integer z2 = intValue(firstNonNull(map, "z2", "maxZ"));
        if (x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
            return new PvpZoneRegion(x1, y1, z1, x2, y2, z2);
        }

        ParsedPos pos1 = readPosition(map, POS1_KEYS);
        ParsedPos pos2 = readPosition(map, POS2_KEYS);
        if (pos1 == null || pos2 == null) {
            return null;
        }
        return new PvpZoneRegion(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z);
    }

    private ParsedPos readPosition(Map<?, ?> map, List<String> keys) {
        for (String key : keys) {
            Object raw = getByKeyIgnoreCase(map, key);
            ParsedPos parsed = ParsedPos.from(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Object extractListCandidate(Map<?, ?> map) {
        for (String key : ZONE_LIST_KEYS) {
            Object value = getByKeyIgnoreCase(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNull(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = getByKeyIgnoreCase(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object getByKeyIgnoreCase(Map<?, ?> map, String wanted) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key != null && key.equalsIgnoreCase(wanted)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Integer intValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeZone(Map<?, ?> map) {
        return getByKeyIgnoreCase(map, "name") != null
            || getByKeyIgnoreCase(map, "x1") != null
            || getByKeyIgnoreCase(map, "minX") != null
            || getByKeyIgnoreCase(map, "pos1") != null
            || getByKeyIgnoreCase(map, "loc1") != null;
    }

    private void writeAll() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        Map<String, Object> serialized = new java.util.LinkedHashMap<>();
        byName.values().stream()
            .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
            .forEach(zone -> serialized.put(zone.getName(), toLegacyNode(zone)));
        root.put("pvpzones", serialized);
        mapper.writeValue(tempFile.toFile(), root);
        if (Files.size(tempFile) == 0L) {
            Files.writeString(tempFile, "pvpzones: {}\n", StandardCharsets.UTF_8);
        }
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Map<String, Object> toLegacyNode(PvpZone zone) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", zone.getName());
        data.put("world", zone.getWorld());
        data.put("safe", zone.isSafe());
        data.put("x1", zone.getRegion().x1());
        data.put("y1", zone.getRegion().y1());
        data.put("z1", zone.getRegion().z1());
        data.put("x2", zone.getRegion().x2());
        data.put("y2", zone.getRegion().y2());
        data.put("z2", zone.getRegion().z2());
        return data;
    }

    private record ParsedPos(int x, int y, int z) {
        private static ParsedPos from(Object raw) {
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            Integer x = intValue(firstNonNull(map, "x", "blockX"));
            Integer y = intValue(firstNonNull(map, "y", "blockY"));
            Integer z = intValue(firstNonNull(map, "z", "blockZ"));
            if (x == null || y == null || z == null) {
                return null;
            }
            return new ParsedPos(x, y, z);
        }
    }
}
