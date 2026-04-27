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
        Files.createDirectories(root.resolve("fallnight/data/users"))
        Files.createDirectories(root.resolve("fallnight/data/mines"))
        Files.createDirectories(root.resolve("fallnight/data/mineranks"))
        Files.createDirectories(root.resolve("fallnight/data/plots"))
        Files.writeString(root.resolve("fallnight/data/users/stale-user.yml"), "stale")
        Files.writeString(root.resolve("fallnight/data/mines/A.yml"), "stale")
        Files.writeString(root.resolve("fallnight/data/mineranks/old.yml"), "stale")
        Files.writeString(root.resolve("fallnight/data/plots/keep-me.txt"), "keep")

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out
        try {
            System.setOut(PrintStream(stdout, true))
            runWorldgen(arrayOf("generate", root.toString()))
        } finally {
            System.setOut(originalOut)
        }

        assertTrue(Files.exists(root.resolve("fallnight/application.yml")))
        val applicationConfig = Files.readString(root.resolve("fallnight/application.yml"))
        assertTrue(!applicationConfig.contains("autoImportLegacy"))
        assertTrue(!applicationConfig.contains("legacyDataPath"))
        assertTrue(Files.exists(root.resolve("fallnight/data/mines/a.yml")))
        assertTrue(Files.readString(root.resolve("fallnight/data/mines/a.yml")).contains("world: spawn-world"))
        assertTrue(Files.notExists(root.resolve("fallnight/data/mines/A.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/mineranks/a.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/mineranks/z.yml")))
        assertTrue(Files.notExists(root.resolve("fallnight/data/mineranks/old.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/ranks.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/tags.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/prices.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/broadcast.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/koth.yml")))
        assertTrue(Files.exists(root.resolve("fallnight/data/pvpzones.yml")))
        assertTrue(Files.exists(root.resolve("spawn-world/region")))
        assertTrue(Files.exists(root.resolve("fallnight/data/PvPMine/region")))
        assertTrue(Files.isDirectory(root.resolve("fallnight/data/plots")))
        assertTrue(Files.exists(root.resolve("fallnight/data/plots/keep-me.txt")))
        assertTrue(Files.notExists(root.resolve("fallnight/data/users/stale-user.yml")))
        assertTrue(stdout.toString().contains("Worldgen complete"))
    }

    @Test
    fun `generate command defaults repo root from working directory parent`() {
        val repoRoot = Files.createTempDirectory("worldgen-default-root-test")
        val workingDir = repoRoot.resolve("setup-tool")
        Files.createDirectories(workingDir)

        runWorldgen(emptyArray(), workingDir)

        assertTrue(Files.exists(repoRoot.resolve("fallnight/application.yml")))
        assertTrue(Files.exists(repoRoot.resolve("fallnight/data/mines/a.yml")))
        assertTrue(Files.exists(repoRoot.resolve("fallnight/data/mineranks/a.yml")))
        assertTrue(Files.isDirectory(repoRoot.resolve("fallnight/data/plots")))
        assertEquals(
            repoRoot.normalize().toAbsolutePath(),
            resolveRepoRoot(emptyArray(), workingDir).normalize().toAbsolutePath(),
        )
    }
}
