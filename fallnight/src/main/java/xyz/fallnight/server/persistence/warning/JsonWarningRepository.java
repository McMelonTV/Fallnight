package xyz.fallnight.server.persistence.warning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.warning.WarningEntry;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JsonWarningRepository implements WarningRepository {
    private final Path warningsFile;
    private final ObjectMapper mapper;

    public JsonWarningRepository(Path warningsFile) {
        this(warningsFile, JacksonMappers.jsonMapper());
    }

    public JsonWarningRepository(Path warningsFile, ObjectMapper mapper) {
        this.warningsFile = warningsFile;
        this.mapper = mapper;
    }

    @Override
    public WarningState load() throws IOException {
        Path parent = warningsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(warningsFile)) {
            return new WarningState(Map.of(), 1L);
        }

        JsonNode root = mapper.readTree(warningsFile.toFile());
        JsonNode warningsNode = root == null ? null : root.get("warnings");
        Map<String, List<WarningEntry>> warningsByTarget = readWarnings(warningsNode);

        long maxId = maxId(warningsByTarget);
        long nextId = longValue(root, "nextId", maxId + 1L);
        if (nextId <= 0L) {
            nextId = maxId + 1L;
        }
        if (nextId <= 0L) {
            nextId = 1L;
        }

        return new WarningState(Map.copyOf(warningsByTarget), nextId);
    }

    @Override
    public void save(WarningState state) throws IOException {
        Path parent = warningsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("nextId", Math.max(1L, state.nextId()));

        ObjectNode warningsNode = root.putObject("warnings");
        for (Map.Entry<String, List<WarningEntry>> entry : state.warningsByTarget().entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            ArrayNode targetWarnings = warningsNode.putArray(key);
            for (WarningEntry warning : entry.getValue()) {
                targetWarnings.add(writeWarning(warning));
            }
        }

        mapper.writeValue(warningsFile.toFile(), root);
    }

    private Map<String, List<WarningEntry>> readWarnings(JsonNode warningsNode) {
        if (warningsNode == null || !warningsNode.isObject()) {
            return Map.of();
        }

        Map<String, List<WarningEntry>> warningsByTarget = new LinkedHashMap<>();
        warningsNode.properties().forEach(entry -> {
            String key = normalize(entry.getKey());
            if (key.isEmpty()) {
                return;
            }

            List<WarningEntry> warnings = new ArrayList<>();
            JsonNode value = entry.getValue();
            if (value != null && value.isArray()) {
                for (JsonNode warningNode : value) {
                    warnings.add(readWarning(warningNode, entry.getKey()));
                }
            }

            warningsByTarget.put(key, List.copyOf(warnings));
        });
        return warningsByTarget;
    }

    private WarningEntry readWarning(JsonNode warningNode, String ownerKey) {
        WarningEntry warning = new WarningEntry();
        warning.setId(longValue(warningNode, "id", 1L));

        String targetUsername = text(warningNode, "targetUsername");
        if (targetUsername == null || targetUsername.isBlank()) {
            targetUsername = ownerKey;
        }
        warning.setTargetUsername(targetUsername);

        String actor = text(warningNode, "actor");
        warning.setActor(actor == null ? "unknown" : actor);

        String reason = text(warningNode, "reason");
        warning.setReason(reason == null ? "No reason provided." : reason);

        Instant createdAt = instantValue(warningNode, "createdAt");
        warning.setCreatedAt(createdAt == null ? Instant.now() : createdAt);
        return warning;
    }

    private JsonNode writeWarning(WarningEntry warning) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", Math.max(1L, warning.getId()));
        node.put("targetUsername", warning.getTargetUsername());
        node.put("actor", warning.getActor());
        node.put("reason", warning.getReason());
        node.put("createdAt", warning.getCreatedAt().toString());
        return node;
    }

    private static long maxId(Map<String, List<WarningEntry>> warningsByTarget) {
        long maxId = 0L;
        for (List<WarningEntry> warnings : warningsByTarget.values()) {
            for (WarningEntry warning : warnings) {
                maxId = Math.max(maxId, warning.getId());
            }
        }
        return maxId;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String text(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static long longValue(JsonNode node, String key, long fallback) {
        if (node == null || key == null) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Instant instantValue(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }

        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isNumber()) {
            return Instant.ofEpochMilli(value.longValue());
        }

        if (value.isTextual()) {
            String raw = value.asText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(raw.trim());
            } catch (DateTimeParseException ignored) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(raw.trim()));
                } catch (NumberFormatException ignored2) {
                    return null;
                }
            }
        }

        return null;
    }
}
