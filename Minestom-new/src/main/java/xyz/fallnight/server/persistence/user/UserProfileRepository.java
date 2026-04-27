package xyz.fallnight.server.persistence.user;

import xyz.fallnight.server.domain.user.UserProfile;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface UserProfileRepository {
    Map<String, UserProfile> loadAll() throws IOException;

    Optional<UserProfile> findByUsername(String username);

    void save(UserProfile profile) throws IOException;

    void saveAll() throws IOException;
}
