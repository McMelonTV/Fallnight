package xyz.fallnight.server.persistence.pvp;

import xyz.fallnight.server.domain.pvp.PvpZone;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface PvpZoneRepository {
    Map<String, PvpZone> loadAll() throws IOException;

    Optional<PvpZone> findByName(String name);

    void save(PvpZone zone) throws IOException;

    void remove(String name) throws IOException;

    void saveAll() throws IOException;
}
