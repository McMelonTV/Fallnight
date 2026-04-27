package xyz.fallnight.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.bounty.BountyEntry;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BountyService {
    private final Path file;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, BountyEntry> bounties;

    public static BountyService fromDataRoot(Path dataRoot) {
        return new BountyService(dataRoot.resolve("bounties.json"));
    }

    public BountyService(Path file) {
        this.file = file;
        this.mapper = JacksonMappers.jsonMapper();
        this.bounties = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file) || Files.size(file) == 0L) {
            bounties.clear();
            return;
        }
        List<BountyEntry> loaded = mapper.readValue(file.toFile(), new TypeReference<List<BountyEntry>>() {});
        bounties.clear();
        if (loaded == null) {
            return;
        }
        for (BountyEntry entry : loaded) {
            if (entry == null || entry.player() == null || entry.player().isBlank()) {
                continue;
            }
            entry.setPlayer(entry.player().trim());
            entry.setBounty(entry.bounty());
            bounties.put(normalize(entry.player()), entry);
        }
    }

    public List<BountyEntry> getBounties() {
        return bounties.values().stream()
            .sorted((a, b) -> Long.compare(b.bounty(), a.bounty()))
            .toList();
    }

    public Optional<BountyEntry> find(String player) {
        if (player == null || player.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bounties.get(normalize(player)));
    }

    public void setBounty(BountyEntry bounty) {
        if (bounty == null || bounty.player() == null || bounty.player().isBlank()) {
            return;
        }
        bounty.setPlayer(bounty.player().trim());
        bounty.setBounty(bounty.bounty());
        bounties.put(normalize(bounty.player()), bounty);
    }

    public void saveAll() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), getBounties());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String normalize(String player) {
        return player.toLowerCase(Locale.ROOT);
    }
}
