package xyz.fallnight.server.persistence.gang;

import xyz.fallnight.server.domain.gang.Gang;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface GangRepository {
    Map<String, Gang> loadAll() throws IOException;

    Optional<Gang> findById(String id);

    Optional<Gang> findByName(String name);

    void save(Gang gang) throws IOException;

    void delete(String gangId) throws IOException;

    void saveAll() throws IOException;
}
