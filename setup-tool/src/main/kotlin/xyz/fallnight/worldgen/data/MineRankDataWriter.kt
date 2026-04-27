package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.MineRankSpec
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object MineRankDataWriter {
    fun write(path: Path, mineRanks: List<MineRankSpec>) {
        resetDirectory(path)
        mineRanks.forEach { mineRank ->
            RuntimeYamlWriter.write(
                path.resolve("${safeFileName(mineRank.name)}.yml"),
                listOf(
                    "---",
                    "name: ${mineRank.name}",
                    "id: ${mineRank.id}",
                    "tag: ${mineRank.tag}",
                    "price: ${mineRank.price}",
                ),
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
