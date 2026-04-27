package xyz.fallnight.server.service;

import net.minestom.server.entity.Player;

public final class DefaultWorldService {
    private volatile SpawnService currentWorld;

    public DefaultWorldService(SpawnService currentWorld) {
        this.currentWorld = currentWorld;
    }

    public SpawnService currentWorld() {
        return currentWorld;
    }

    public void setCurrentWorld(SpawnService currentWorld) {
        if (currentWorld != null) {
            this.currentWorld = currentWorld;
        }
    }

    public String worldName() {
        return currentWorld.worldName();
    }

    public void teleportToSpawn(Player player) {
        currentWorld.teleportToSpawn(player);
    }
}
