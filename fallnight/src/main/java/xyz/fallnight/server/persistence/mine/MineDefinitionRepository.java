package xyz.fallnight.server.persistence.mine;

import xyz.fallnight.server.domain.mine.MineDefinition;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface MineDefinitionRepository {
    Map<Integer, MineDefinition> loadAll() throws IOException;

    Optional<MineDefinition> findById(int id);

    Optional<MineDefinition> findByName(String name);

    void save(MineDefinition mineDefinition) throws IOException;

    void saveAll() throws IOException;
}
