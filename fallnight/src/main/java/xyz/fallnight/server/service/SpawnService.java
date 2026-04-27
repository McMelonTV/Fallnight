package xyz.fallnight.server.service;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public final class SpawnService {
    private final Instance instance;
    private volatile Pos spawn;
    private volatile String worldName;

    public SpawnService(Instance instance, Pos spawn) {
        this(instance, spawn, "world");
    }

    public SpawnService(Instance instance, Pos spawn, String worldName) {
        this.instance = instance;
        this.spawn = spawn;
        this.worldName = worldName == null || worldName.isBlank() ? "world" : worldName.trim();
        setWorldName(worldName);
    }

    public void teleportToSpawn(Player player) {
        Pos target = normalizedSpawn(spawn);
        loadTargetChunk(target);
        if (player.getInstance() == instance) {
            player.teleport(target);
            return;
        }
        player.setInstance(instance, target);
    }

    public void teleportWithinSpawnInstance(Player player, Pos position) {
        Pos target = normalizedSpawn(position);
        loadTargetChunk(target);
        if (player.getInstance() == instance) {
            player.teleport(target);
            return;
        }
        player.setInstance(instance, target);
    }

    public Pos spawn() {
        return spawn;
    }

    public void setSpawn(Pos spawn) {
        if (spawn == null) {
            return;
        }
        this.spawn = spawn;
    }

    public Instance instance() {
        return instance;
    }

    public String worldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        this.worldName = worldName.trim();
    }

    public static Pos normalizedSpawn(Pos position) {
        if (position == null) {
            return null;
        }
        return new Pos(position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    private void loadTargetChunk(Pos target) {
        if (target == null) {
            return;
        }
        instance.loadChunk(target.chunkX(), target.chunkZ()).join();
    }
}
