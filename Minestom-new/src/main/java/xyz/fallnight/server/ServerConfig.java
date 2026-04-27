package xyz.fallnight.server;

import java.util.List;

public record ServerConfig(
        String host,
        int port,
        int maxPlayers,
        boolean devServer,
        boolean maintenanceMode,
        List<String> maintenanceWhitelist,
        String motd,
        String dataPath,
        String mainWorldDirectory,
        String spawnWorld,
        double spawnX,
        double spawnY,
        double spawnZ
) {
    public static ServerConfig defaults() {
        return new ServerConfig(
                "0.0.0.0",
                25565,
                420,
                false,
                false,
                List.of(),
                "Fallnight",
                "data",
                "spawn",
                "spawn",
                0.5,
                67.0,
                0.5
        );
    }
}
