package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.KothSpec
import java.nio.file.Path

object KothDataWriter {
    fun write(path: Path, hills: List<KothSpec>) {
        val nextHillId = hills.firstOrNull()?.id
        RuntimeYamlWriter.write(
            path,
            buildList {
                add("---")
                add("hills:")
                hills.forEach { hill ->
                    add("- id: ${RuntimeYamlWriter.quoted(hill.id)}")
                    add("  displayName: ${RuntimeYamlWriter.quoted(displayName(hill.id))}")
                    add("  world: ${RuntimeYamlWriter.quoted(hill.worldName)}")
                    add("  x: ${hill.center.x}")
                    add("  y: ${hill.center.y}")
                    add("  z: ${hill.center.z}")
                    add("  radius: ${hill.radius}")
                    add("  captureSeconds: 120")
                    add("  rewardMoney: 2500.0")
                    add("  rewardPrestige: 10")
                }
                add("state:")
                add("  nextHillId: ${nextHillId?.let(RuntimeYamlWriter::quoted) ?: "null"}")
                add("  nextStartEpochSecond: 0")
                add("  spawnNext: 0")
                add("  active:")
                add("    hillId: null")
                add("    capturer: null")
                add("    progressSeconds: 0")
                add("next-koth: 0")
                add("spawn-next: 0")
            },
        )
    }

    private fun displayName(id: String): String = id.split('-', '_').joinToString(" ") { part ->
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
