package xyz.fallnight.server.persistence.mine;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.mine.MineDefinition;
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

public final class YamlMineDefinitionRepository implements MineDefinitionRepository {
    private final Path minesDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<Integer, MineDefinition> byId;
    private final ConcurrentMap<String, MineDefinition> byName;

    public YamlMineDefinitionRepository(Path minesDirectory) {
        this(minesDirectory, JacksonMappers.yamlMapper());
    }

    public YamlMineDefinitionRepository(Path minesDirectory, ObjectMapper mapper) {
        this.minesDirectory = minesDirectory;
        this.mapper = mapper;
        this.byId = new ConcurrentHashMap<>();
        this.byName = new ConcurrentHashMap<>();
    }

    @Override
    public Map<Integer, MineDefinition> loadAll() throws IOException {
        Files.createDirectories(minesDirectory);
        Map<Integer, MineDefinition> loaded = new LinkedHashMap<>();
        try (var files = Files.list(minesDirectory)) {
            files
                .filter(path -> {
                    String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return file.endsWith(".yml") || file.endsWith(".yaml");
                })
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    try {
                        if (Files.size(path) == 0L) {
                            return;
                        }
                        String yaml = Files.readString(path);
                        if (yaml.isBlank()) {
                            return;
                        }
                        MineDefinition mine = mapper.readValue(yaml, MineDefinition.class);
                        loaded.put(mine.getId(), mine);
                    } catch (IOException exception) {
                        throw new RepositoryReadException(path, exception);
                    }
                });
        } catch (RepositoryReadException wrapped) {
            throw wrapped.getCause();
        }

        byId.clear();
        byName.clear();
        loaded.forEach((id, mine) -> {
            byId.put(id, mine);
            if (mine.getName() != null) {
                byName.put(mine.getName().toLowerCase(Locale.ROOT), mine);
            }
        });
        return Map.copyOf(loaded);
    }

    @Override
    public Optional<MineDefinition> findById(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<MineDefinition> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void save(MineDefinition mineDefinition) throws IOException {
        Files.createDirectories(minesDirectory);
        String fileName = toSafeFileName(mineDefinition.getName() == null ? Integer.toString(mineDefinition.getId()) : mineDefinition.getName());
        Path file = minesDirectory.resolve(fileName + ".yml");
        mapper.writeValue(file.toFile(), mineDefinition);
        byId.put(mineDefinition.getId(), mineDefinition);
        if (mineDefinition.getName() != null) {
            byName.put(mineDefinition.getName().toLowerCase(Locale.ROOT), mineDefinition);
        }
    }

    @Override
    public void saveAll() throws IOException {
        for (MineDefinition mineDefinition : byId.values()) {
            save(mineDefinition);
        }
    }

    private static String toSafeFileName(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static final class RepositoryReadException extends RuntimeException {
        private final IOException cause;

        private RepositoryReadException(Path path, IOException cause) {
            super("Failed reading mine file: " + path, cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
