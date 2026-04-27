package xyz.fallnight.server.persistence.tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.tag.TagDefinition;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class YamlTagDefinitionRepository implements TagDefinitionRepository {
    private static final List<String> TAG_LIST_KEYS = List.of("tags", "tagList");

    private final Path file;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, TagDefinition> byId;

    public YamlTagDefinitionRepository(Path file) {
        this(file, JacksonMappers.yamlMapper());
    }

    public YamlTagDefinitionRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        this.byId = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, TagDefinition> loadAll() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            byId.clear();
            return Map.of();
        }

        Object root = mapper.readValue(file.toFile(), Object.class);
        List<TagDefinition> parsed = parseRoot(root);

        byId.clear();
        for (TagDefinition tagDefinition : parsed) {
            byId.put(normalize(tagDefinition.id()), tagDefinition);
        }
        return Map.copyOf(byId);
    }

    @Override
    public Optional<TagDefinition> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(normalize(id)));
    }

    private List<TagDefinition> parseRoot(Object root) {
        List<TagDefinition> tags = new ArrayList<>();
        if (root instanceof List<?> list) {
            for (Object entry : list) {
                parseTag(entry, null).ifPresent(tags::add);
            }
            return tags;
        }

        if (!(root instanceof Map<?, ?> map)) {
            return tags;
        }

        if (looksLikeTag(map)) {
            parseTag(map, null).ifPresent(tags::add);
            return tags;
        }

        Object listCandidate = extractListCandidate(map);
        if (listCandidate instanceof List<?> list) {
            for (Object entry : list) {
                parseTag(entry, null).ifPresent(tags::add);
            }
            return tags;
        }
        if (listCandidate instanceof Map<?, ?> tagMap) {
            for (Map.Entry<?, ?> entry : tagMap.entrySet()) {
                String fallbackId = stringValue(entry.getKey());
                parseTag(entry.getValue(), fallbackId).ifPresent(tags::add);
            }
            return tags;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String fallbackId = stringValue(entry.getKey());
            parseTag(entry.getValue(), fallbackId).ifPresent(tags::add);
        }
        return tags;
    }

    private Optional<TagDefinition> parseTag(Object raw, String fallbackId) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        String id = stringValue(firstNonNull(map, "id", "name", "key"));
        if (id == null || id.isBlank()) {
            id = fallbackId;
        }
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String tagText = stringValue(firstNonNull(map, "tag", "display", "text", "prefix"));
        if (tagText == null || tagText.isBlank()) {
            tagText = id;
        }

        int rarity = intValue(firstNonNull(map, "rarity"), 0);
        boolean crateDrop = booleanValue(firstNonNull(map, "isCrateDrop", "crateDrop", "crate"), true);
        boolean publicTag = booleanValue(firstNonNull(map, "isPublic", "public"), false);
        boolean receiveOnJoin = booleanValue(firstNonNull(map, "receiveOnJoin", "recieveOnJoin", "join"), false);
        return Optional.of(new TagDefinition(id.trim(), tagText, Math.max(0, rarity), crateDrop, publicTag, receiveOnJoin));
    }

    private Object extractListCandidate(Map<?, ?> map) {
        for (String key : TAG_LIST_KEYS) {
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

    private static String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("1")) {
                return true;
            }
            if (normalized.equals("false") || normalized.equals("no") || normalized.equals("0")) {
                return false;
            }
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        return fallback;
    }

    private static boolean looksLikeTag(Map<?, ?> map) {
        return getByKeyIgnoreCase(map, "id") != null || getByKeyIgnoreCase(map, "tag") != null;
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
