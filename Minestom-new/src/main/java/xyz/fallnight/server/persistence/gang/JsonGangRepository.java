package xyz.fallnight.server.persistence.gang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.domain.gang.GangMemberRole;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JsonGangRepository implements GangRepository {
    private final Path gangsDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, Gang> cache;

    public JsonGangRepository(Path gangsDirectory) {
        this(gangsDirectory, JacksonMappers.jsonMapper());
    }

    public JsonGangRepository(Path gangsDirectory, ObjectMapper mapper) {
        this.gangsDirectory = gangsDirectory;
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, Gang> loadAll() throws IOException {
        Files.createDirectories(gangsDirectory);
        Map<String, Gang> loaded = new LinkedHashMap<>();
        try (var files = Files.list(gangsDirectory)) {
            files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        Gang gang = readGang(path);
                        if (gang == null || gang.getId() == null || gang.getId().isBlank()) {
                            return;
                        }
                        loaded.put(gang.getId(), gang);
                    } catch (IOException exception) {
                        throw new RepositoryReadException(path, exception);
                    }
                });
        } catch (RepositoryReadException wrapped) {
            throw wrapped.getCause();
        }
        cache.clear();
        cache.putAll(loaded);
        return Map.copyOf(loaded);
    }

    @Override
    public Optional<Gang> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(id.trim()));
    }

    @Override
    public Optional<Gang> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String expected = name.trim();
        return cache.values().stream()
            .filter(gang -> gang.getName() != null && gang.getName().equalsIgnoreCase(expected))
            .findFirst();
    }

    @Override
    public void save(Gang gang) throws IOException {
        Files.createDirectories(gangsDirectory);
        String id = gang.getId();
        if (id == null || id.isBlank()) {
            throw new IOException("Cannot save gang without id.");
        }
        Path file = gangsDirectory.resolve(id + ".json");
        mapper.writeValue(file.toFile(), toLegacyNode(gang));
        cache.put(id, gang);
    }

    @Override
    public void delete(String gangId) throws IOException {
        if (gangId == null || gangId.isBlank()) {
            return;
        }
        cache.remove(gangId);
        Files.deleteIfExists(gangsDirectory.resolve(gangId + ".json"));
    }

    @Override
    public void saveAll() throws IOException {
        for (Gang gang : cache.values()) {
            save(gang);
        }
    }

    private static String stripExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private Gang readGang(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        if (root == null || !root.isObject()) {
            return null;
        }

        String id = text(root, "id");
        if (id == null || id.isBlank()) {
            return null;
        }

        Gang gang = new Gang();
        gang.setId(id);
        gang.setName(defaultText(root, "name", stripExtension(path)));
        gang.setDescription(defaultText(root, "description", "A new gang!"));
        gang.setCreationDate(readCreationDate(root.get("creationDate")));
        gang.setMembers(readStringSet(root.get("members")));
        gang.setMemberRoles(readStringMap(root.get("memberRoles")));
        gang.setAllies(readStringSet(root.get("allies")));
        if (gang.getMembers().isEmpty()) {
            String owner = text(root, "owner");
            if (owner != null && !owner.isBlank()) {
                gang.addMember(owner, GangMemberRole.LEADER);
            }
        }
        if (!gang.getMembers().isEmpty() && gang.leader() == null) {
            String firstMember = gang.getMembers().iterator().next();
            gang.setRole(firstMember, GangMemberRole.LEADER);
        }
        return gang;
    }

    private ObjectNode toLegacyNode(Gang gang) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", gang.getName());
        node.put("id", gang.getId());
        node.put("creationDate", gang.getCreationDate().getEpochSecond());
        ArrayNode members = node.putArray("members");
        for (String member : gang.getMembers()) {
            members.add(member);
        }
        ObjectNode memberRoles = node.putObject("memberRoles");
        for (Map.Entry<String, String> entry : gang.getMemberRoles().entrySet()) {
            memberRoles.put(entry.getKey(), entry.getValue());
        }
        ArrayNode allies = node.putArray("allies");
        for (String ally : gang.getAllies()) {
            allies.add(ally);
        }
        node.put("description", gang.getDescription());
        return node;
    }

    private static LinkedHashMap<String, String> readStringMap(JsonNode node) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String text = value.asText("").trim();
            if (!key.isEmpty() && !text.isEmpty()) {
                values.put(key, text);
            }
        });
        return values;
    }

    private static LinkedHashSet<String> readStringSet(JsonNode node) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode child : node) {
            if (child == null || child.isNull()) {
                continue;
            }
            String text = child.asText("").trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private static Instant readCreationDate(JsonNode node) {
        if (node == null || node.isNull()) {
            return Instant.now();
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.longValue());
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(raw);
            } catch (Exception ignoredAgain) {
                return Instant.now();
            }
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String defaultText(JsonNode node, String key, String fallback) {
        String value = text(node, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class RepositoryReadException extends RuntimeException {
        private final IOException cause;

        private RepositoryReadException(Path path, IOException cause) {
            super("Failed reading gang file: " + path, cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
