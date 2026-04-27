package xyz.fallnight.worldgen.world

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import java.nio.file.Files
import kotlin.test.*

class WorldGenerationServiceTest {
    @BeforeTest
    fun initServer() {
        WorldgenMinestom.bootstrap()
    }

    @Test
    fun `world generation creates spawn and pvp world directories with region output`() {
        val root = Files.createTempDirectory("worldgen-world-test")
        val spec = BetaBaselineSpec.default().copy(root = WorldPaths.forRoot(root.resolve("fallnight")))

        WorldGenerationService.generate(spec)

        assertTrue(Files.exists(spec.root.spawnWorld.resolve("region")))
        assertTrue(
            Files.list(spec.root.spawnWorld.resolve("region"))
                .use { it.anyMatch { path -> path.fileName.toString().endsWith(".mca") } })
        assertTrue(Files.exists(spec.root.pvpMineWorld.resolve("region")))
        assertTrue(
            Files.list(spec.root.pvpMineWorld.resolve("region"))
                .use { it.anyMatch { path -> path.fileName.toString().endsWith(".mca") } })
        assertTrue(Files.isDirectory(spec.root.plotsWorld))
    }

    @Test
    fun `generated spawn world loads a non-air floor through Minestom anvil loader`() {
        val root = Files.createTempDirectory("worldgen-load-test")
        val spec = BetaBaselineSpec.default().copy(root = WorldPaths.forRoot(root.resolve("fallnight")))

        WorldGenerationService.generate(spec)

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.setChunkLoader(AnvilLoader(spec.root.spawnWorld))
        instance.enableAutoChunkLoad(true)
        instance.loadChunk(0, 0).join()

        val floor = instance.getBlock(0, spec.spawn.hubSpawn.y - 1, 0)
        assertIs<LightingChunk>(instance.getChunk(0, 0))
        assertNotEquals(Block.AIR, floor)
    }

    @Test
    fun `world generation clears stale world output before rerun`() {
        val root = Files.createTempDirectory("worldgen-world-rerun-test")
        val spec = BetaBaselineSpec.default().copy(root = WorldPaths.forRoot(root.resolve("fallnight")))

        WorldGenerationService.generate(spec)

        val staleSpawnFile = spec.root.spawnWorld.resolve("stale.txt")
        val stalePvpFile = spec.root.pvpMineWorld.resolve("region/stale.mca")
        Files.writeString(staleSpawnFile, "stale")
        Files.writeString(stalePvpFile, "stale")

        WorldGenerationService.generate(spec)

        assertFalse(Files.exists(staleSpawnFile))
        assertFalse(Files.exists(stalePvpFile))
        assertTrue(
            Files.list(spec.root.spawnWorld.resolve("region"))
                .use { it.anyMatch { path -> path.fileName.toString().endsWith(".mca") } })
        assertTrue(
            Files.list(spec.root.pvpMineWorld.resolve("region"))
                .use { it.anyMatch { path -> path.fileName.toString().endsWith(".mca") } })
    }
}
