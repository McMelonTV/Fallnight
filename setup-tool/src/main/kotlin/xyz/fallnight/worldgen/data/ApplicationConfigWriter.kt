package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import java.nio.file.Path

object ApplicationConfigWriter {
    fun write(path: Path, spec: BetaBaselineSpec) {
        val spawn = spec.spawn.hubSpawn
        RuntimeYamlWriter.write(
            path,
            listOf(
                "server:",
                "  host: ${RuntimeYamlWriter.quoted("0.0.0.0")}",
                "  port: 25565",
                "  maxPlayers: 420",
                "  devServer: false",
                "  maintenanceMode: false",
                "  maintenanceWhitelist: []",
                "  motd: ${RuntimeYamlWriter.quoted("Fallnight")}",
                "  dataPath: ${RuntimeYamlWriter.quoted("data")}",
                "  mainWorldDirectory: ${RuntimeYamlWriter.quoted("../../spawn-world")}",
                "  spawn:",
                "    world: ${RuntimeYamlWriter.quoted(spec.spawn.worldName)}",
                "    x: ${spawn.x}.5",
                "    y: ${spawn.y}.0",
                "    z: ${spawn.z}.5",
            ),
        )
    }
}
