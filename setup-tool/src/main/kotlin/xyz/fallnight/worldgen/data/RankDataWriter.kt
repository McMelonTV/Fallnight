package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.RankSpec
import java.nio.file.Files
import java.nio.file.Path

object RankDataWriter {
    fun write(path: Path, fallbackRanks: List<RankSpec>) {
        Files.createDirectories(path.parent)

        RuntimeYamlWriter.write(
            path,
            buildList {
                add("ranks:")
                fallbackRanks.forEach { rank ->
                    val id = rank.id
                    add("  $id:")
                    add("    id: ${RuntimeYamlWriter.quoted(id)}")
                    add("    name: ${RuntimeYamlWriter.quoted(rank.name)}")
                    if (rank.permissions.isNotEmpty()) {
                        add("    permissions:")
                        rank.permissions.forEach { permission ->
                            add("      - ${RuntimeYamlWriter.quoted(permission)}")
                        }
                    }
                    add("    prefix: ${RuntimeYamlWriter.quoted(rank.prefix)}")
                    if (rank.plots != null) {
                        add("    plots: ${rank.plots}")
                    }
                    if (rank.vaults != null) {
                        add("    vaults: ${rank.vaults}")
                    }
                    if (rank.isStaff) {
                        add("    isStaff: true")
                    }
                    if (rank.inherit.isNotEmpty()) {
                        add("    inherit:")
                        rank.inherit.forEach { inherited ->
                            add("      - ${inherited}")
                        }
                    }
                    if (rank.isDonator) {
                        add("    isDonator: true")
                    }
                    if (rank.isDefault) {
                        add("    isDefault: true")
                    }
                    add("    priority: ${rank.priority}")
                }
            },
        )
    }
}
