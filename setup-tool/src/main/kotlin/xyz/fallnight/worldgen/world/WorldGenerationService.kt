package xyz.fallnight.worldgen.world

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import xyz.fallnight.worldgen.spec.BlockPos
import java.nio.file.Files
import java.nio.file.Path
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block

object WorldGenerationService {
    fun generate(spec: BetaBaselineSpec, resetWorlds: Boolean = true) {
        WorldgenMinestom.bootstrap()
        if (resetWorlds) {
            resetWorldDirectory(spec.root.spawnWorld)
            resetWorldDirectory(spec.root.pvpMineWorld)
        }
        SpawnWorldGenerator.generate(spec.root.spawnWorld, spec.spawn, spec.mines)
        PvpMineWorldGenerator.generate(spec.root.pvpMineWorld, spec.pvpMine)
        Files.createDirectories(spec.root.plotsWorld)
    }

    private fun resetWorldDirectory(worldDir: Path) {
        if (Files.notExists(worldDir)) return
        Files.walk(worldDir)
            .sorted(Comparator.reverseOrder())
            .use { paths -> paths.forEach(Files::deleteIfExists) }
    }
}

internal object WorldgenMinestom {
    private var initialized = false

    fun bootstrap() {
        if (!initialized) {
            MinecraftServer.init()
            initialized = true
        }
    }
}

internal class WorldWriter(worldDir: Path) : AutoCloseable {
    private val instance: InstanceContainer

    init {
        Files.createDirectories(worldDir)
        instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier(::LightingChunk)
        instance.setChunkLoader(AnvilLoader(worldDir))
        instance.enableAutoChunkLoad(true)
    }

    fun fill(from: BlockPos, to: BlockPos, blockName: String) {
        val minX = minOf(from.x, to.x)
        val maxX = maxOf(from.x, to.x)
        val minY = minOf(from.y, to.y)
        val maxY = maxOf(from.y, to.y)
        val minZ = minOf(from.z, to.z)
        val maxZ = maxOf(from.z, to.z)
        preloadChunks(minX, minZ, maxX, maxZ)
        val block = net.minestom.server.instance.block.Block.fromKey(blockName)
            ?: error("Unknown block: $blockName")
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    instance.setBlock(x, y, z, block)
                }
            }
        }
    }

    fun set(pos: BlockPos, blockName: String) {
        preloadChunks(pos.x, pos.z, pos.x, pos.z)
        setLoaded(pos, blockName)
    }

    fun preload(from: BlockPos, to: BlockPos) {
        preloadChunks(
            minOf(from.x, to.x),
            minOf(from.z, to.z),
            maxOf(from.x, to.x),
            maxOf(from.z, to.z),
        )
    }

    fun setLoaded(pos: BlockPos, blockName: String): Boolean {
        val block = net.minestom.server.instance.block.Block.fromKey(blockName)
            ?: return false
        instance.setBlock(pos.x, pos.y, pos.z, block)
        return true
    }

    fun setLoaded(pos: BlockPos, block: Block) {
        instance.setBlock(pos.x, pos.y, pos.z, block)
    }

    override fun close() {
        instance.saveChunksToStorage().join()
    }

    private fun preloadChunks(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        val minChunkX = Math.floorDiv(minX, 16)
        val maxChunkX = Math.floorDiv(maxX, 16)
        val minChunkZ = Math.floorDiv(minZ, 16)
        val maxChunkZ = Math.floorDiv(maxZ, 16)
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                instance.loadChunk(chunkX, chunkZ).join()
            }
        }
    }
}

internal fun BlockPos.offset(dx: Int, dy: Int, dz: Int): BlockPos = BlockPos(x + dx, y + dy, z + dz)
