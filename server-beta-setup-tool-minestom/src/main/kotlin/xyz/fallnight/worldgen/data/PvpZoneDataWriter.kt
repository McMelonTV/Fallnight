package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.PvpZoneSpec
import java.nio.file.Path

object PvpZoneDataWriter {
    fun write(path: Path, zones: List<PvpZoneSpec>) {
        RuntimeYamlWriter.write(
            path,
            buildList {
                add("---")
                zones.forEach { zone ->
                    add("- id: ${zone.id}")
                    add("  world: ${zone.worldName}")
                    add("  x1: ${zone.bounds.min.x}")
                    add("  y1: ${zone.bounds.min.y}")
                    add("  z1: ${zone.bounds.min.z}")
                    add("  x2: ${zone.bounds.max.x}")
                    add("  y2: ${zone.bounds.max.y}")
                    add("  z2: ${zone.bounds.max.z}")
                }
            },
        )
    }
}
