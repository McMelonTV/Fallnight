package xyz.fallnight.worldgen.world

import xyz.fallnight.worldgen.data.RuntimeYamlWriter
import xyz.fallnight.worldgen.spec.BlockBox
import xyz.fallnight.worldgen.spec.BlockPos
import xyz.fallnight.worldgen.spec.MineSpec
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.io.path.name
import net.minestom.server.instance.block.Block

internal class PremiereSchematicImporter private constructor(
    private val archive: Path,
) : AutoCloseable {
    private val zip = ZipFile(archive.toFile())

    fun pasteMine(writer: WorldWriter, mine: MineSpec): Boolean {
        val schematicName = mine.schematicName ?: return false
        val offset = mine.schematicOffset ?: return false
        val entry = zip.getEntry("Schematics-1.0/$schematicName")
            ?: error("Missing schematic $schematicName in ${archive.name}")
        val schematic = SpongeSchematic.read(zip.getInputStream(entry).readBytes())
        writer.preload(offset, offset.offset(schematic.width - 1, schematic.height - 1, schematic.length - 1))
        schematic.forEachBlock { local, blockKey ->
            val block = resolveBlock(blockKey) ?: return@forEachBlock
            writer.setLoaded(local.offset(offset.x, offset.y, offset.z), block)
        }
        fillMine(writer, mine, schematic, offset)
        return true
    }

    private fun fillMine(writer: WorldWriter, mine: MineSpec, schematic: SpongeSchematic, offset: BlockPos) {
        if (mine.sourceFillBlocks.isEmpty()) return
        val localRegion = mine.region.offset(-offset.x, -offset.y, -offset.z)
        val random = Random(mine.ordinal.toLong())
        val palette = mine.blocks.map(RuntimeYamlWriter::minecraftMaterial)
        for (x in localRegion.min.x..localRegion.max.x) {
            for (y in localRegion.min.y..localRegion.max.y) {
                for (z in localRegion.min.z..localRegion.max.z) {
                    writer.setLoaded(BlockPos(x + offset.x, y + offset.y, z + offset.z), palette[random.nextInt(palette.size)])
                }
            }
        }
    }

    override fun close() {
        zip.close()
    }

    companion object {
        fun openNear(path: Path): PremiereSchematicImporter? {
            var current: Path? = path.toAbsolutePath().normalize()
            while (current != null) {
                val candidate = current.resolve("PremiereSetups-15FreeMines.zip")
                if (Files.exists(candidate)) {
                    return PremiereSchematicImporter(candidate)
                }
                current = current.parent
            }
            return null
        }

        private fun resolveBlock(blockKey: String): Block? {
            runCatching { Block.fromKey(blockKey) }.getOrNull()?.let { return it }
            val baseKey = blockKey.substringBefore('[')
            runCatching { Block.fromKey(baseKey) }.getOrNull()?.let { return it }
            return when (baseKey) {
                "minecraft:grass" -> Block.fromKey("minecraft:short_grass")
                else -> null
            }
        }
    }
}

private operator fun BlockBox.contains(pos: BlockPos): Boolean =
    pos.x in min.x..max.x && pos.y in min.y..max.y && pos.z in min.z..max.z

private fun BlockBox.offset(dx: Int, dy: Int, dz: Int): BlockBox = BlockBox(
    min = min.offset(dx, dy, dz),
    max = max.offset(dx, dy, dz),
)

private data class SpongeSchematic(
    val width: Int,
    val height: Int,
    val length: Int,
    private val palette: Map<Int, String>,
    private val blockData: ByteArray,
) {
    fun forEachBlock(includeAir: Boolean = false, consumer: (BlockPos, String) -> Unit) {
        var index = 0
        var cursor = 0
        while (cursor < blockData.size) {
            var value = 0
            var shift = 0
            while (true) {
                val next = blockData[cursor++].toInt() and 0xFF
                value = value or ((next and 0x7F) shl shift)
                if (next and 0x80 == 0) {
                    break
                }
                shift += 7
            }
            val blockKey = palette[value]
            if (blockKey != null && (includeAir || !blockKey.startsWith("minecraft:air"))) {
                val x = index % width
                val z = (index / width) % length
                val y = index / (width * length)
                consumer(BlockPos(x, y, z), blockKey)
            }
            index++
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun read(raw: ByteArray): SpongeSchematic {
            val decompressed = GZIPInputStream(ByteArrayInputStream(raw)).readBytes()
            val root = NbtReader(decompressed).readRoot() as Map<String, Any>
            val rawPalette = root["Palette"] as Map<String, Any>
            return SpongeSchematic(
                width = (root["Width"] as Number).toInt(),
                height = (root["Height"] as Number).toInt(),
                length = (root["Length"] as Number).toInt(),
                palette = rawPalette.entries.associate { (key, value) -> (value as Number).toInt() to key },
                blockData = root["BlockData"] as ByteArray,
            )
        }
    }
}

private class NbtReader(bytes: ByteArray) {
    private val input = DataInputStream(ByteArrayInputStream(bytes))

    fun readRoot(): Any {
        val type = input.readUnsignedByte()
        readString()
        return readPayload(type)
    }

    private fun readPayload(type: Int): Any {
        return when (type) {
            1 -> input.readByte()
            2 -> input.readShort()
            3 -> input.readInt()
            4 -> input.readLong()
            5 -> input.readFloat()
            6 -> input.readDouble()
            7 -> ByteArray(input.readInt()).also(input::readFully)
            8 -> readString()
            9 -> readList()
            10 -> readCompound()
            11 -> IntArray(input.readInt()) { input.readInt() }.toList()
            12 -> LongArray(input.readInt()) { input.readLong() }.toList()
            else -> error("Unsupported NBT tag: $type")
        }
    }

    private fun readList(): List<Any> {
        val childType = input.readUnsignedByte()
        val size = input.readInt()
        return List(size) { readPayload(childType) }
    }

    private fun readCompound(): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        while (true) {
            val type = input.readUnsignedByte()
            if (type == 0) {
                return result
            }
            result[readString()] = readPayload(type)
        }
    }

    private fun readString(): String {
        val size = input.readUnsignedShort()
        val data = ByteArray(size)
        input.readFully(data)
        return data.toString(Charsets.UTF_8)
    }
}
