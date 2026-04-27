package xyz.fallnight.server.persistence.mine;

import xyz.fallnight.server.domain.mine.MineRank;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface MineRankRepository {
    Map<Integer, MineRank> loadAll() throws IOException;

    Optional<MineRank> findById(int id);

    Optional<MineRank> findByName(String name);

    void save(MineRank mineRank) throws IOException;

    void saveAll() throws IOException;
}
