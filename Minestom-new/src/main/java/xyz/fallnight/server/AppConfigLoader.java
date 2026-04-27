package xyz.fallnight.server;

import org.slf4j.Logger;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AppConfigLoader {
    private static final String COFNIG_PATH = "application.yml";
    private static final List<Path> EXTERNAL_COFNIG_PATHS = List.of(
            Path.of("application.yml"),
            Path.of("Minestom-new", "application.yml")
    );

    private AppConfigLoader() {
    }

    public static ServerConfig load(Logger logger) {
        try {
            for (Path externalConfigPath : EXTERNAL_COFNIG_PATHS) {
                if (!Files.exists(externalConfigPath)) {
                    continue;
                }
                String yaml = Files.readString(externalConfigPath);
                ServerConfig config = parse(yaml, logger);
                if (config != null) {
                    return config;
                }
            }
        } catch (Exception exception) {
            logger.error("Failed to load external config. Falling back to bundled config.", exception);
        }

        try (InputStream inputStream = AppConfigLoader.class.getClassLoader().getResourceAsStream(COFNIG_PATH)) {
            if (inputStream == null) {
                logger.warn("Config file '{}' not found on classpath. Falling back to defaults.", COFNIG_PATH);
                return ServerConfig.defaults();
            }
            return parse(new String(inputStream.readAllBytes()), logger);
        } catch (Exception exception) {
            logger.error("Failed to load '{}'. Falling back to defaults.", COFNIG_PATH, exception);
            return ServerConfig.defaults();
        }
    }

    public static Path writableConfigPath() {
        for (Path path : EXTERNAL_COFNIG_PATHS) {
            if (Files.exists(path)) {
                return path;
            }
        }
        return EXTERNAL_COFNIG_PATHS.get(0);
    }

    private static ServerConfig parse(String yaml, Logger logger) {
        LoadSettings settings = LoadSettings.builder().build();
        Load loader = new Load(settings);
        Object loaded = loader.loadFromString(yaml);

        if (!(loaded instanceof Map<?, ?> root)) {
            logger.warn("Config is not a YAML object. Falling back to defaults.");
            return ServerConfig.defaults();
        }

        Object serverObject = root.get("server");
        if (!(serverObject instanceof Map<?, ?> serverMap)) {
            logger.warn("Missing 'server' section in config. Falling back to defaults.");
            return ServerConfig.defaults();
        }

        String host = asString(serverMap.get("host"), ServerConfig.defaults().host());
        int port = asInt(serverMap.get("port"), ServerConfig.defaults().port());
        int maxPlayers = asInt(serverMap.get("maxPlayers"), ServerConfig.defaults().maxPlayers());
        boolean devServer = asBoolean(serverMap.containsKey("devServer") ? serverMap.get("devServer") : serverMap.get("devserver"), ServerConfig.defaults().devServer());
        boolean maintenanceMode = asBoolean(serverMap.get("maintenanceMode"), ServerConfig.defaults().maintenanceMode());
        List<String> maintenanceWhitelist = asStringList(serverMap.get("maintenanceWhitelist"), ServerConfig.defaults().maintenanceWhitelist());
        String motd = asString(serverMap.get("motd"), ServerConfig.defaults().motd());
        String dataPath = asString(serverMap.get("dataPath"), ServerConfig.defaults().dataPath());
        String mainWorldDirectory = asString(serverMap.get("mainWorldDirectory"), ServerConfig.defaults().mainWorldDirectory());

        Object spawnObject = serverMap.get("spawn");
        String spawnWorld = ServerConfig.defaults().spawnWorld();
        double spawnX = ServerConfig.defaults().spawnX();
        double spawnY = ServerConfig.defaults().spawnY();
        double spawnZ = ServerConfig.defaults().spawnZ();
        if (spawnObject instanceof Map<?, ?> spawnMap) {
            spawnWorld = asString(spawnMap.get("world"), spawnWorld);
            spawnX = asDouble(spawnMap.get("x"), spawnX);
            spawnY = asDouble(spawnMap.get("y"), spawnY);
            spawnZ = asDouble(spawnMap.get("z"), spawnZ);
        }

        return new ServerConfig(host, port, maxPlayers, devServer, maintenanceMode, maintenanceWhitelist, motd, dataPath, mainWorldDirectory, spawnWorld, spawnX, spawnY, spawnZ);
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            int candidate = number.intValue();
            return candidate > 0 && candidate <= 65535 ? candidate : fallback;
        }
        return fallback;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }

    private static List<String> asStringList(Object value, List<String> fallback) {
        if (!(value instanceof Iterable<?> iterable)) {
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object entry : iterable) {
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result.isEmpty() ? fallback : List.copyOf(result);
    }
}
