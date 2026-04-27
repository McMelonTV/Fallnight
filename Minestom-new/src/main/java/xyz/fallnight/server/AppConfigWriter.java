package xyz.fallnight.server;

import net.minestom.server.coordinate.Pos;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppConfigWriter {
    private AppConfigWriter() {
    }

    public static void save(ServerConfig config, Path path) throws IOException {
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        Map<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("world", config.spawnWorld());
        spawn.put("x", config.spawnX());
        spawn.put("y", config.spawnY());
        spawn.put("z", config.spawnZ());
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("host", config.host());
        server.put("port", config.port());
        server.put("maxPlayers", config.maxPlayers());
        server.put("devServer", config.devServer());
        server.put("maintenanceMode", config.maintenanceMode());
        server.put("maintenanceWhitelist", config.maintenanceWhitelist());
        server.put("motd", config.motd());
        server.put("dataPath", config.dataPath());
        server.put("mainWorldDirectory", config.mainWorldDirectory());
        server.put("spawn", spawn);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("server", server);
        Dump dump = new Dump(DumpSettings.builder().build());
        Files.writeString(path, dump.dumpToString(root));
    }

    public static ServerConfig withSpawn(ServerConfig config, String worldName, Pos spawn) {
        return new ServerConfig(
                config.host(),
                config.port(),
                config.maxPlayers(),
                config.devServer(),
                config.maintenanceMode(),
                config.maintenanceWhitelist(),
                config.motd(),
                config.dataPath(),
                config.mainWorldDirectory(),
                worldName,
                spawn.x(),
                spawn.y(),
                spawn.z()
        );
    }
}
