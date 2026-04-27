package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.minestom.server.MinecraftServer;
import xyz.fallnight.server.persistence.JacksonMappers;
import xyz.fallnight.server.util.LegacyTextFormatter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

public final class BroadcastService {
    private static final long DEFAULT_INTERVAL_SECONDS = 300L;

    private final Path broadcastFile;
    private final ObjectMapper yaml;
    private final IntUnaryOperator randomIndexPicker;
    private volatile List<String> messages;
    private volatile long intervalSeconds;

    public BroadcastService(Path broadcastFile) {
        this(broadcastFile, size -> ThreadLocalRandom.current().nextInt(size));
    }

    public BroadcastService(Path broadcastFile, IntUnaryOperator randomIndexPicker) {
        this.broadcastFile = broadcastFile;
        this.yaml = JacksonMappers.yamlMapper();
        this.randomIndexPicker = randomIndexPicker;
        this.messages = List.of();
        this.intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    }

    public static BroadcastService fromDataRoot(Path dataRoot) {
        return new BroadcastService(dataRoot.resolve("broadcast.yml"));
    }

    private static List<String> readMessages(Map<?, ?> root) {
        Object messagesNode = root.get("messages");
        if (!(messagesNode instanceof Iterable<?>)) {
            messagesNode = root.get("broadcasts");
        }

        if (!(messagesNode instanceof Iterable<?> iterable)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Object value : iterable) {
            if (!(value instanceof String text)) {
                continue;
            }

            String normalized = LegacyTextFormatter.normalize(text).trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static long readInterval(Map<?, ?> root) {
        Object intervalNode = root.get("intervalSeconds");
        if (!(intervalNode instanceof Number number)) {
            intervalNode = root.get("interval");
        }

        if (intervalNode instanceof Number number) {
            long candidate = number.longValue();
            return candidate > 0L ? candidate : DEFAULT_INTERVAL_SECONDS;
        }
        return DEFAULT_INTERVAL_SECONDS;
    }

    public void load() throws IOException {
        if (!Files.exists(broadcastFile)) {
            saveDefaults();
        }

        Object data = yaml.readValue(broadcastFile.toFile(), Object.class);
        if (!(data instanceof Map<?, ?> root)) {
            messages = List.of();
            intervalSeconds = DEFAULT_INTERVAL_SECONDS;
            return;
        }

        messages = List.copyOf(readMessages(root));
        intervalSeconds = readInterval(root);
    }

    public List<String> messages() {
        return messages;
    }

    public long intervalSeconds() {
        return intervalSeconds;
    }

    public int broadcastNext() {
        String nextMessage = nextMessage();
        if (nextMessage == null) {
            return 0;
        }
        return broadcastImmediate(nextMessage);
    }

    public int broadcastImmediate(String message) {
        String normalized = LegacyTextFormatter.normalize(message);
        if (normalized.isBlank()) {
            return 0;
        }

        var formatted = LegacyTextFormatter.deserialize(normalized);
        int count = 0;
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(formatted);
            count++;
        }
        return count;
    }

    public Path dataRoot() {
        Path parent = broadcastFile.getParent();
        return parent != null ? parent : Path.of(".");
    }

    public void saveDefaults() {
        try {
            Path parent = broadcastFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("intervalSeconds", DEFAULT_INTERVAL_SECONDS);
            data.put("broadcasts", List.of(
//                "§8[§6FN§8] §r§7You can vote at §6vote.fallnight.xyz§r§7 to win awesome prizes!",
//                "§8[§6FN§8] §r§7bc1",
//                "§8[§6FN§8] §r§7bc2",
//                "§8[§6FN§8] §r§7bc3",
//                "§8[§6FN§8] §r§7bc4",
//                "§8[§6FN§8] §r§7bc5"
                    "§8[§6FN§8] §r§7welcome to your favorite legally distinct minecraft prison server"
            ));
            yaml.writeValue(broadcastFile.toFile(), data);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String nextMessage() {
        List<String> current = messages;
        if (current.isEmpty()) {
            return null;
        }

        int currentIndex = Math.floorMod(randomIndexPicker.applyAsInt(current.size()), current.size());
        return current.get(currentIndex);
    }
}
