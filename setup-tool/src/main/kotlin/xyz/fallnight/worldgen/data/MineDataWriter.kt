package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.MineSpec
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object MineDataWriter {
    fun write(minesDir: Path, mines: List<MineSpec>) {
        resetDirectory(minesDir)
        mines.forEach { mine ->
            RuntimeYamlWriter.write(
                minesDir.resolve("${safeFileName(mine.id)}.yml"),
                buildList {
                    add("---")
                    add("name: ${mine.id}")
                    add("id: ${mine.ordinal}")
                    add("world: ${mine.worldName}")
                    add("x1: ${mine.region.min.x}")
                    add("y1: ${mine.region.max.y}")
                    add("z1: ${mine.region.max.z}")
                    add("x2: ${mine.region.max.x}")
                    add("y2: ${mine.region.min.y}")
                    add("z2: ${mine.region.min.z}")
                    add("blocks:")
                    mine.blocks.forEach { block ->
                        add("  - ${RuntimeYamlWriter.quoted(RuntimeYamlWriter.minecraftMaterial(block))}")
                    }
                    add("prices:")
                    mine.prices.forEach { price ->
                        add("  ${RuntimeYamlWriter.quoted(RuntimeYamlWriter.minecraftMaterial(price.material))}: ${price.value}")
                    }
                    add("spawnX: ${mine.spawn.x}")
                    add("spawnY: ${mine.spawn.y}")
                    add("spawnZ: ${mine.spawn.z}")
                },
            )
        }
    }

    private fun resetDirectory(path: Path) {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .use { paths -> paths.forEach(Files::deleteIfExists) }
        }
        Files.createDirectories(path)
    }

    private fun safeFileName(input: String): String {
        return input.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "-")
    }
}
