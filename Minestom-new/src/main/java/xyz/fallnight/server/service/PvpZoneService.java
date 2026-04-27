package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.pvp.PvpZone;
import xyz.fallnight.server.domain.pvp.PvpZoneRegion;
import xyz.fallnight.server.persistence.pvp.PvpZoneRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PvpZoneService {
    private final PvpZoneRepository repository;
    private final ConcurrentMap<String, PvpZone> zones;

    public PvpZoneService(PvpZoneRepository repository) {
        this.repository = repository;
        this.zones = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Map<String, PvpZone> loaded = repository.loadAll();
        zones.clear();
        zones.putAll(loaded);
    }

    public List<PvpZone> allZones() {
        return zones.values().stream()
            .sorted(Comparator.comparing(PvpZone::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Optional<PvpZone> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(zones.get(normalize(name)));
    }

    public CreateResult create(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (name == null || name.isBlank()) {
            return CreateResult.INVALID_NAME;
        }

        String normalized = normalize(name);
        if (zones.containsKey(normalized)) {
            return CreateResult.ALREADY_EXISTS;
        }

        String resolvedWorld = worldName == null || worldName.isBlank() ? "spawn" : worldName;
        PvpZone zone = new PvpZone(name.trim(), resolvedWorld.trim(), new PvpZoneRegion(x1, y1, z1, x2, y2, z2));
        try {
            repository.save(zone);
            zones.put(normalized, zone);
            return CreateResult.SUCCESS;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public boolean remove(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        PvpZone removed = zones.remove(normalize(name));
        if (removed == null) {
            return false;
        }

        try {
            repository.remove(name);
        } catch (IOException exception) {
            zones.put(normalize(removed.getName()), removed);
            throw new UncheckedIOException(exception);
        }
        return true;
    }

    public boolean isInPvpZone(int x, int y, int z, String worldName) {
        for (PvpZone zone : zones.values()) {
            if (zone.contains(x, y, z, worldName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSafe(int x, int y, int z, String worldName) {
        for (PvpZone zone : zones.values()) {
            if (zone.contains(x, y, z, worldName) && zone.isSafe()) {
                return true;
            }
        }
        return false;
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }

    public void renameWorldLabel(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank() || from.equalsIgnoreCase(to)) {
            return;
        }
        boolean changed = false;
        for (PvpZone zone : zones.values()) {
            if (zone.getWorld() != null && zone.getWorld().equalsIgnoreCase(from)) {
                zone.setWorld(to);
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

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    public enum CreateResult {
        SUCCESS,
        INVALID_NAME,
        ALREADY_EXISTS
    }
}
