package xyz.fallnight.server.persistence.mine;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class YamlMineRankRepository implements MineRankRepository {
    private final Path ranksDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<Integer, MineRank> byId;
    private final ConcurrentMap<String, MineRank> byName;

    public YamlMineRankRepository(Path ranksDirectory) {
        this(ranksDirectory, JacksonMappers.yamlMapper());
    }

    public YamlMineRankRepository(Path ranksDirectory, ObjectMapper mapper) {
        this.ranksDirectory = ranksDirectory;
        this.mapper = mapper;
        this.byId = new ConcurrentHashMap<>();
        this.byName = new ConcurrentHashMap<>();
    }

    @Override
    public Map<Integer, MineRank> loadAll() throws IOException {
        Files.createDirectories(ranksDirectory);
        Map<Integer, MineRank> loaded = new LinkedHashMap<>();
        try (var files = Files.list(ranksDirectory)) {
            files
                .filter(path -> {
                    String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return file.endsWith(".yml") || file.endsWith(".yaml");
                })
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    try {
                        MineRank rank = mapper.readValue(path.toFile(), MineRank.class);
                        loaded.put(rank.getId(), rank);
                    } catch (IOException exception) {
                        throw new RepositoryReadException(path, exception);
                    }
                });
        } catch (RepositoryReadException wrapped) {
            throw wrapped.getCause();
        }

        byId.clear();
        byName.clear();
        loaded.forEach((id, rank) -> {
            byId.put(id, rank);
            if (rank.getName() != null) {
                byName.put(rank.getName().toLowerCase(Locale.ROOT), rank);
            }
        });
        return Map.copyOf(loaded);
    }

    @Override
    public Optional<MineRank> findById(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<MineRank> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void save(MineRank mineRank) throws IOException {
        Files.createDirectories(ranksDirectory);
        String fileName = toSafeFileName(mineRank.getName() == null ? Integer.toString(mineRank.getId()) : mineRank.getName());
        Path file = ranksDirectory.resolve(fileName + ".yml");
        mapper.writeValue(file.toFile(), mineRank);
        byId.put(mineRank.getId(), mineRank);
        if (mineRank.getName() != null) {
            byName.put(mineRank.getName().toLowerCase(Locale.ROOT), mineRank);
        }
    }

    @Override
    public void saveAll() throws IOException {
        for (MineRank rank : byId.values()) {
            save(rank);
        }
    }

    private static String toSafeFileName(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static final class RepositoryReadException extends RuntimeException {
        private final IOException cause;

        private RepositoryReadException(Path path, IOException cause) {
            super("Failed reading mine rank file: " + path, cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
