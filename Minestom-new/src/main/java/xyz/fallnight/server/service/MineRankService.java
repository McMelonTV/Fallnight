package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.persistence.mine.MineRankRepository;
import xyz.fallnight.server.util.ProgressionMath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MineRankService {
    private final MineRankRepository repository;
    private final ConcurrentMap<Integer, MineRank> ranks;

    public MineRankService(MineRankRepository repository) {
        this.repository = repository;
        this.ranks = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Map<Integer, MineRank> loaded = repository.loadAll();
        ranks.clear();
        ranks.putAll(loaded);
    }

    public Optional<MineRank> find(int id) {
        return Optional.ofNullable(ranks.get(id));
    }

    public Optional<MineRank> nextRank(int currentRankId) {
        return find(currentRankId + 1);
    }

    public List<MineRank> allRanks() {
        return ranks.values().stream()
            .sorted(Comparator.comparingInt(MineRank::getId))
            .toList();
    }

    public long rankUpPrice(int currentRankId, int prestige) {
        MineRank target = nextRank(currentRankId)
            .orElseThrow(() -> new IllegalArgumentException("No rank after id " + currentRankId));
        return ProgressionMath.rankUpPrice(target.getPrice(), prestige);
    }

    public void save(MineRank rank) {
        try {
            repository.save(rank);
            ranks.put(rank.getId(), rank);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }
}
