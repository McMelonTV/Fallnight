package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.persistence.mine.MineDefinitionRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MineService {
    private final MineDefinitionRepository repository;
    private final ConcurrentMap<Integer, MineDefinition> mines;

    public MineService(MineDefinitionRepository repository) {
        this.repository = repository;
        this.mines = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Map<Integer, MineDefinition> loaded = repository.loadAll();
        mines.clear();
        mines.putAll(loaded);
    }

    public Optional<MineDefinition> find(int id) {
        return Optional.ofNullable(mines.get(id));
    }

    public MineDefinition findByName(String name) {
        if (name == null) {
            return null;
        }
        return mines.values().stream()
            .filter(mine -> mine.getName() != null && mine.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public List<MineDefinition> allMines() {
        return mines.values().stream()
            .sorted(Comparator.comparingInt(MineDefinition::getId))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Optional<MineDefinition> findByCoordinates(int x, int y, int z, String worldName) {
        return mines.values().stream()
            .filter(mine -> mine.contains(x, y, z, worldName))
            .findFirst();
    }

    public void save(MineDefinition mineDefinition) {
        try {
            repository.save(mineDefinition);
            mines.put(mineDefinition.getId(), mineDefinition);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }

    public void renameWorldLabel(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank() || from.equalsIgnoreCase(to)) {
            return;
        }
        boolean changed = false;
        for (MineDefinition mine : mines.values()) {
            if (mine.getWorld() != null && mine.getWorld().equalsIgnoreCase(from)) {
                mine.setWorld(to);
                changed = true;
            }
        }
        if (changed) {
            try {
                repository.saveAll();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
