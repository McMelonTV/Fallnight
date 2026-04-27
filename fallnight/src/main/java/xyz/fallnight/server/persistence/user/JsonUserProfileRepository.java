package xyz.fallnight.server.persistence.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JsonUserProfileRepository implements UserProfileRepository {
    private final Path usersDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, UserProfile> cache;

    public JsonUserProfileRepository(Path usersDirectory) {
        this(usersDirectory, JacksonMappers.jsonMapper());
    }

    public JsonUserProfileRepository(Path usersDirectory, ObjectMapper mapper) {
        this.usersDirectory = usersDirectory;
        this.mapper = mapper;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, UserProfile> loadAll() throws IOException {
        Files.createDirectories(usersDirectory);
        Map<String, UserProfile> loaded = new LinkedHashMap<>();
        try (var files = Files.list(usersDirectory)) {
            files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        UserProfile profile = mapper.readValue(path.toFile(), UserProfile.class);
                        String key = normalizeUsername(profile.getUsername() == null ? stripExtension(path) : profile.getUsername());
                        if (profile.getUsername() == null || profile.getUsername().isBlank()) {
                            profile.setUsername(stripExtension(path));
                        }
                        loaded.put(key, profile);
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
    public Optional<UserProfile> findByUsername(String username) {
        return Optional.ofNullable(cache.get(normalizeUsername(username)));
    }

    @Override
    public void save(UserProfile profile) throws IOException {
        Files.createDirectories(usersDirectory);
        String key = normalizeUsername(profile.getUsername());
        Path file = usersDirectory.resolve(key + ".json");
        mapper.writeValue(file.toFile(), profile);
        cache.put(key, profile);
    }

    @Override
    public void saveAll() throws IOException {
        for (UserProfile profile : cache.values()) {
            save(profile);
        }
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static final class RepositoryReadException extends RuntimeException {
        private final IOException cause;

        private RepositoryReadException(Path path, IOException cause) {
            super("Failed reading user file: " + path, cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
