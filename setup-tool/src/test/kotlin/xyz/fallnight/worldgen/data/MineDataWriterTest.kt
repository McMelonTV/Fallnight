package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MineDataWriterTest {
    @Test
    fun `mine writer emits ordered yaml files using beta mine schema`() {
        val temp = Files.createTempDirectory("worldgen-mine-test")
        val spec = BetaBaselineSpec.default()

        MineDataWriter.write(temp, spec.mines)

        val writtenFiles = Files.list(temp).use { paths -> paths.map { it.fileName.name }.sorted().toList() }
        assertEquals(26, writtenFiles.size)
        assertEquals("a.yml", writtenFiles.first())
        assertEquals("z.yml", writtenFiles.last())

        val firstMine = Files.readString(temp.resolve("a.yml"))
        assertTrue(firstMine.contains("name: A"))
        assertTrue(firstMine.contains("id: 0"))
        assertTrue(firstMine.contains("world: spawn-world"))
        assertTrue(firstMine.contains("blocks:"))
        assertTrue(firstMine.contains("prices:"))
        assertTrue(firstMine.contains("spawnX:"))
    }

    @Test
    fun `mine writer removes stale mine files before regeneration`() {
        val temp = Files.createTempDirectory("worldgen-mine-reset-test")
        Files.writeString(temp.resolve("A.yml"), "stale")
        Files.writeString(temp.resolve("old.yml"), "stale")

        MineDataWriter.write(temp, BetaBaselineSpec.default().mines)

        assertFalse(Files.exists(temp.resolve("A.yml")))
        assertFalse(Files.exists(temp.resolve("old.yml")))
        assertTrue(Files.exists(temp.resolve("a.yml")))
        val writtenFiles = Files.list(temp).use { paths -> paths.map { it.fileName.name }.sorted().toList() }
        assertEquals(26, writtenFiles.size)
    }
}
