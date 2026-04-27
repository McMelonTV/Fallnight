package xyz.fallnight.worldgen

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MainSmokeTest {
    @Test
    fun `main rejects unknown command`() {
        assertFailsWith<IllegalArgumentException> {
            runWorldgen(arrayOf("unknown"))
        }
    }

    @Test
    fun `generate command performs reset world build and data writes`() {
        val root = Files.createTempDirectory("worldgen-cli-test")
        Files.createDirectories(root.resolve("Minestom-new/data/users"))
        Files.createDirectories(root.resolve("Minestom-new/data/mines"))
        Files.createDirectories(root.resolve("Minestom-new/data/mineranks"))
        Files.createDirectories(root.resolve("Minestom-new/data/plots"))
        Files.writeString(root.resolve("Minestom-new/data/users/stale-user.yml"), "stale")
        Files.writeString(root.resolve("Minestom-new/data/mines/A.yml"), "stale")
        Files.writeString(root.resolve("Minestom-new/data/mineranks/old.yml"), "stale")
        Files.writeString(root.resolve("Minestom-new/data/plots/keep-me.txt"), "keep")

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out
        try {
            System.setOut(PrintStream(stdout, true))
            runWorldgen(arrayOf("generate", root.toString()))
        } finally {
            System.setOut(originalOut)
        }

        assertTrue(Files.exists(root.resolve("Minestom-new/application.yml")))
        val applicationConfig = Files.readString(root.resolve("Minestom-new/application.yml"))
        assertTrue(!applicationConfig.contains("autoImportLegacy"))
        assertTrue(!applicationConfig.contains("legacyDataPath"))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/mines/a.yml")))
        assertTrue(Files.readString(root.resolve("Minestom-new/data/mines/a.yml")).contains("world: spawn-world"))
        assertTrue(Files.notExists(root.resolve("Minestom-new/data/mines/A.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/mineranks/a.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/mineranks/z.yml")))
        assertTrue(Files.notExists(root.resolve("Minestom-new/data/mineranks/old.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/ranks.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/tags.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/prices.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/broadcast.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/koth.yml")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/pvpzones.yml")))
        assertTrue(Files.exists(root.resolve("spawn-world/region")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/PvPMine/region")))
        assertTrue(Files.isDirectory(root.resolve("Minestom-new/data/plots")))
        assertTrue(Files.exists(root.resolve("Minestom-new/data/plots/keep-me.txt")))
        assertTrue(Files.notExists(root.resolve("Minestom-new/data/users/stale-user.yml")))
        assertTrue(stdout.toString().contains("Worldgen complete"))
    }

    @Test
    fun `generate command defaults repo root from working directory parent`() {
        val repoRoot = Files.createTempDirectory("worldgen-default-root-test")
        val workingDir = repoRoot.resolve("server-beta-setup-tool-minestom")
        Files.createDirectories(workingDir)

        runWorldgen(emptyArray(), workingDir)

        assertTrue(Files.exists(repoRoot.resolve("Minestom-new/application.yml")))
        assertTrue(Files.exists(repoRoot.resolve("Minestom-new/data/mines/a.yml")))
        assertTrue(Files.exists(repoRoot.resolve("Minestom-new/data/mineranks/a.yml")))
        assertTrue(Files.isDirectory(repoRoot.resolve("Minestom-new/data/plots")))
        assertEquals(
            repoRoot.normalize().toAbsolutePath(),
            resolveRepoRoot(emptyArray(), workingDir).normalize().toAbsolutePath(),
        )
    }
}
