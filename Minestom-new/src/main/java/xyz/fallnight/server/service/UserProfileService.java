package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.user.UserProfileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class UserProfileService {
    private final UserProfileRepository repository;
    private final ConcurrentMap<String, UserProfile> users;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
        this.users = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Map<String, UserProfile> loaded = repository.loadAll();
        users.clear();
        users.putAll(loaded);
    }

    public Optional<UserProfile> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(username.toLowerCase()));
    }

    public UserProfile getOrCreate(String username) {
        return users.computeIfAbsent(username.toLowerCase(), ignored -> new UserProfile(username));
    }

    public void save(UserProfile profile) {
        try {
            repository.save(profile);
            users.put(profile.getUsername().toLowerCase(), profile);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }

    public List<UserProfile> allProfiles() {
        return List.copyOf(users.values());
    }
}
