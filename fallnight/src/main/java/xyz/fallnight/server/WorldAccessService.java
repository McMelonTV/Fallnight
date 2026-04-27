package xyz.fallnight.server;

import xyz.fallnight.server.service.SpawnService;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldAccessService {
    private static final Pos FALLBACK_SPAWN = new Pos(0.5, 64.0, 0.5);

    private final Path dataRoot;
    private final String mainWorldDirectory;
    private final SpawnService spawnService;
    private final SpawnService plotWorldService;
    private final SpawnService pvpMineWorldService;
    private final Map<String, DynamicWorld> dynamicWorlds = new ConcurrentHashMap<>();

    public WorldAccessService(Path dataRoot, String mainWorldDirectory, SpawnService spawnService, SpawnService plotWorldService, SpawnService pvpMineWorldService) {
        this.dataRoot = dataRoot;
        this.mainWorldDirectory = mainWorldDirectory;
        this.spawnService = spawnService;
        this.plotWorldService = plotWorldService;
        this.pvpMineWorldService = pvpMineWorldService;
    }

    private static boolean matches(String normalized, SpawnService service, String... aliases) {
        if (normalized.equals(service.worldName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String alias : aliases) {
            if (normalized.equals(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedPathName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            Path fileName = Path.of(value.trim()).normalize().getFileName();
            return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
        } catch (InvalidPathException exception) {
            return "";
        }
    }

    private boolean matchesMainWorldReference(String normalized) {
        String configured = mainWorldDirectory == null ? "" : mainWorldDirectory.trim().toLowerCase(Locale.ROOT);
        return !configured.isBlank() && (normalized.equals(configured) || normalized.equals(normalizedPathName(mainWorldDirectory)));
    }

    public static Pos readSpawn(Path directory, Pos fallback) {
        Path levelDat = directory.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return fallback;
        }
        try {
            CompoundBinaryTag root = BinaryTagIO.reader().read(levelDat, BinaryTagIO.Compression.GZIP);
            CompoundBinaryTag data = root.getCompound("Data");
            if (data == null || data.isEmpty()) {
                return fallback;
            }
            return new Pos(
                    data.getInt("SpawnX", fallback.blockX()) + 0.5,
                    data.getInt("SpawnY", (int) fallback.y()),
                    data.getInt("SpawnZ", fallback.blockZ()) + 0.5
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Pos readSpawn(Path directory) {
        return readSpawn(directory, FALLBACK_SPAWN);
    }

    public static String readLevelName(Path directory, String fallback) {
        Path levelDat = directory.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return fallback;
        }
        try {
            CompoundBinaryTag root = BinaryTagIO.reader().read(levelDat, BinaryTagIO.Compression.GZIP);
            CompoundBinaryTag data = root.getCompound("Data");
            if (data == null || data.isEmpty()) {
                return fallback;
            }
            String levelName = data.getString("LevelName", fallback);
            return levelName == null || levelName.isBlank() ? fallback : levelName;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Optional<SpawnService> resolve(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        String normalized = worldName.trim().toLowerCase(Locale.ROOT);
        if (matches(normalized, spawnService, "spawn", "spawn-world", "world") || matchesMainWorldReference(normalized)) {
            return Optional.of(spawnService);
        }
        if (matches(normalized, plotWorldService, "plotworld", "plots")) {
            return Optional.of(plotWorldService);
        }
        if (matches(normalized, pvpMineWorldService, "pvpmine", "minepvp")) {
            return Optional.of(pvpMineWorldService);
        }
        DynamicWorld cached = dynamicWorlds.get(normalized);
        if (cached != null) {
            return Optional.of(cached.service());
        }
        for (DynamicWorld dynamic : dynamicWorlds.values()) {
            if (dynamic.service().worldName().equalsIgnoreCase(worldName)) {
                return Optional.of(dynamic.service());
            }
        }
        Path directory = resolveDirectory(worldName.trim());
        if (directory == null) {
            return Optional.empty();
        }
        DynamicWorld loaded = loadDynamicWorld(directory);
        dynamicWorlds.put(directory.getFileName().toString().toLowerCase(Locale.ROOT), loaded);
        return Optional.of(loaded.service());
    }

    public SpawnService resolveCurrent(Player player) {
        if (player == null || player.getInstance() == null) {
            return spawnService;
        }
        if (player.getInstance() == spawnService.instance()) {
            return spawnService;
        }
        if (player.getInstance() == plotWorldService.instance()) {
            return plotWorldService;
        }
        if (player.getInstance() == pvpMineWorldService.instance()) {
            return pvpMineWorldService;
        }
        for (DynamicWorld dynamic : dynamicWorlds.values()) {
            if (player.getInstance() == dynamic.service().instance()) {
                return dynamic.service();
            }
        }
        return spawnService;
    }

    public void persistSpawn(SpawnService service, Pos spawn) {
        if (isReadOnlyMainWorld(service)) {
            return;
        }
        Path directory = directoryFor(service);
        if (directory == null || spawn == null) {
            return;
        }
        updateLevelData(directory, data -> data
                .putInt("SpawnX", spawn.blockX())
                .putInt("SpawnY", spawn.blockY())
                .putInt("SpawnZ", spawn.blockZ())
        );
    }

    public void persistLevelName(SpawnService service, String worldName) {
        if (isReadOnlyMainWorld(service)) {
            return;
        }
        Path directory = directoryFor(service);
        if (directory == null || worldName == null || worldName.isBlank()) {
            return;
        }
        updateLevelData(directory, data -> data.putString("LevelName", worldName));
    }

    public List<String> availableWorldNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(spawnService.worldName());
        names.add(plotWorldService.worldName());
        names.add(pvpMineWorldService.worldName());
        dynamicWorlds.values().forEach(dynamic -> names.add(dynamic.service().worldName()));
        try {
            if (Files.isDirectory(dataRoot)) {
                try (var entries = Files.list(dataRoot)) {
                    entries.filter(this::isWorldDirectory)
                            .filter(path -> dynamicWorlds.values().stream().noneMatch(dynamic -> dynamic.directory().equals(path)))
                            .forEach(path -> names.add(readLevelName(path, path.getFileName().toString())));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new ArrayList<>(names);
    }

    public void saveAll() {
        for (DynamicWorld dynamic : dynamicWorlds.values()) {
            if (dynamic.service().instance() instanceof InstanceContainer container) {
                container.saveChunksToStorage().join();
            }
        }
    }

    private DynamicWorld loadDynamicWorld(Path directory) {
        Pos fallback = spawnService.spawn() == null ? FALLBACK_SPAWN : spawnService.spawn();
        Pos spawn = readSpawn(directory, fallback);
        var instance = Main.createMainInstance(directory, spawn);
        instance.setTime(6000);
        instance.setTimeRate(0);
        return new DynamicWorld(directory, new SpawnService(instance, spawn, readLevelName(directory, directory.getFileName().toString())));
    }

    private Path resolveDirectory(String input) {
        Path direct = dataRoot.resolve(input);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        try {
            if (!Files.isDirectory(dataRoot)) {
                return null;
            }
            try (var entries = Files.list(dataRoot)) {
                return entries
                        .filter(this::isWorldDirectory)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase(input)
                                || readLevelName(path, path.getFileName().toString()).equalsIgnoreCase(input))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private boolean isWorldDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        return Files.isDirectory(path.resolve("region")) || Files.exists(path.resolve("level.dat"));
    }

    private boolean isReadOnlyMainWorld(SpawnService service) {
        return service == spawnService;
    }

    private Path directoryFor(SpawnService service) {
        if (service == spawnService) {
            return dataRoot.resolve(mainWorldDirectory);
        }
        if (service == plotWorldService) {
            return dataRoot.resolve("plots");
        }
        if (service == pvpMineWorldService) {
            return dataRoot.resolve("PvPMine");
        }
        for (DynamicWorld dynamic : dynamicWorlds.values()) {
            if (dynamic.service() == service) {
                return dynamic.directory();
            }
        }
        return null;
    }

    private void updateLevelData(Path directory, java.util.function.UnaryOperator<CompoundBinaryTag> updater) {
        Path levelDat = directory.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return;
        }
        try {
            CompoundBinaryTag root = BinaryTagIO.reader().read(levelDat, BinaryTagIO.Compression.GZIP);
            CompoundBinaryTag data = root.getCompound("Data");
            if (data == null || data.isEmpty()) {
                return;
            }
            CompoundBinaryTag updated = root.put("Data", updater.apply(data));
            BinaryTagIO.writer().write(updated, levelDat, BinaryTagIO.Compression.GZIP);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record DynamicWorld(Path directory, SpawnService service) {
    }
}
