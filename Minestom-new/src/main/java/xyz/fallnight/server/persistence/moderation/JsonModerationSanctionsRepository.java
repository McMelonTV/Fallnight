package xyz.fallnight.server.persistence.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.domain.moderation.PlayerMute;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class JsonModerationSanctionsRepository implements ModerationSanctionsRepository {
    private final Path sanctionsFile;
    private final ObjectMapper mapper;

    public JsonModerationSanctionsRepository(Path sanctionsFile) {
        this(sanctionsFile, JacksonMappers.jsonMapper());
    }

    public JsonModerationSanctionsRepository(Path sanctionsFile, ObjectMapper mapper) {
        this.sanctionsFile = sanctionsFile;
        this.mapper = mapper;
    }

    @Override
    public ModerationSanctionsState load() throws IOException {
        Path parent = sanctionsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(sanctionsFile)) {
            return new ModerationSanctionsState(List.of(), List.of(), false);
        }

        JsonNode root = mapper.readTree(sanctionsFile.toFile());
        List<PlayerBan> bans = readBans(root.get("bans"));
        List<PlayerMute> mutes = readMutes(root.get("mutes"));
        boolean globalMute = boolValue(root, "globalMute", boolValue(root, "global_mute", false));
        return new ModerationSanctionsState(bans, mutes, globalMute);
    }

    @Override
    public void save(ModerationSanctionsState state) throws IOException {
        Path parent = sanctionsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("globalMute", state.globalMute());

        ObjectNode bansNode = root.putObject("bans");
        for (PlayerBan ban : state.bans()) {
            bansNode.set(ban.username(), writeBan(ban));
        }

        ObjectNode mutesNode = root.putObject("mutes");
        for (PlayerMute mute : state.mutes()) {
            mutesNode.set(mute.username(), writeMute(mute));
        }

        mapper.writeValue(sanctionsFile.toFile(), root);
    }

    private List<PlayerBan> readBans(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<PlayerBan> bans = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode entry : node) {
                PlayerBan ban = readBan(entry, null);
                if (ban != null) {
                    bans.add(ban);
                }
            }
            return bans;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                PlayerBan ban = readBan(entry.getValue(), entry.getKey());
                if (ban != null) {
                    bans.add(ban);
                }
            });
            return bans;
        }
        return List.of();
    }

    private List<PlayerMute> readMutes(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<PlayerMute> mutes = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode entry : node) {
                PlayerMute mute = readMute(entry);
                if (mute != null) {
                    mutes.add(mute);
                }
            }
            return mutes;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                PlayerMute mute = readMute(entry.getValue());
                if (mute != null) {
                    mutes.add(mute);
                }
            });
        }
        return mutes;
    }

    private PlayerBan readBan(JsonNode node, String fallbackName) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            String username = node.asText("").trim();
            if (username.isEmpty()) {
                return null;
            }
            Instant now = Instant.now();
            return new PlayerBan(username, "Banned", "Legacy", now, null);
        }

        if (!node.isObject()) {
            return null;
        }

        String username = firstText(node, "username", "player", "name");
        if ((username == null || username.isBlank()) && fallbackName != null) {
            username = fallbackName;
        }
        if (username == null || username.isBlank()) {
            return null;
        }

        String reason = firstText(node, "reason", "message");
        String actor = firstText(node, "actor", "staff", "by", "source", "banner");
        Instant createdAt = firstInstant(node, "createdAt", "created_at", "issuedAt", "date", "banDate");
        Instant expiresAt = firstInstant(node, "expiresAt", "expires_at", "expiry", "until", "expire");
        boolean superBan = boolValue(node, "superban", boolValue(node, "superBan", false));
        String expireText = firstText(node, "expire");
        if (expireText != null && expireText.trim().equals("-1")) {
            expiresAt = null;
        }
        return new PlayerBan(username, reason, actor, createdAt, expiresAt, superBan);
    }

    private PlayerMute readMute(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }

        String username = firstText(node, "username", "player", "name");
        if (username == null || username.isBlank()) {
            return null;
        }

        String reason = firstText(node, "reason", "message");
        String actor = firstText(node, "actor", "staff", "by", "source");
        Instant createdAt = firstInstant(node, "createdAt", "created_at", "issuedAt", "date");
        Instant expiresAt = firstInstant(node, "expiresAt", "expires_at", "expiry", "until");
        return new PlayerMute(username, reason, actor, createdAt, expiresAt);
    }

    private ObjectNode writeBan(PlayerBan ban) {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", ban.username());
        node.put("reason", ban.reason());
        node.put("actor", ban.actor());
        node.put("createdAt", ban.createdAt().toString());
        if (ban.expiresAt() != null) {
            node.put("expiresAt", ban.expiresAt().toString());
        } else {
            node.putNull("expiresAt");
        }
        node.put("superban", ban.superBan());
        return node;
    }

    private ObjectNode writeMute(PlayerMute mute) {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", mute.username());
        node.put("reason", mute.reason());
        node.put("actor", mute.actor());
        node.put("createdAt", mute.createdAt().toString());
        if (mute.expiresAt() != null) {
            node.put("expiresAt", mute.expiresAt().toString());
        } else {
            node.putNull("expiresAt");
        }
        return node;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Instant firstInstant(JsonNode node, String... keys) {
        for (String key : keys) {
            Instant instant = instantValue(node.get(key));
            if (instant != null) {
                return instant;
            }
        }
        return null;
    }

    private static Instant instantValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isNumber()) {
            long raw = node.asLong();
            if (Math.abs(raw) >= 1_000_000_000_000L) {
                return Instant.ofEpochMilli(raw);
            }
            return Instant.ofEpochSecond(raw);
        }

        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            long raw = Long.parseLong(text);
            if (Math.abs(raw) >= 1_000_000_000_000L) {
                return Instant.ofEpochMilli(raw);
            }
            return Instant.ofEpochSecond(raw);
        } catch (NumberFormatException ignored) {
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean boolValue(JsonNode node, String key, boolean fallback) {
        if (node == null) {
            return fallback;
        }

        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isTextual()) {
            String text = value.asText("").trim();
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("off")) {
                return false;
            }
        }

        if (value.isNumber()) {
            return value.asInt(0) != 0;
        }

        return fallback;
    }
}
