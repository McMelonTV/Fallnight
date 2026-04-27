package xyz.fallnight.worldgen.world

import xyz.fallnight.worldgen.data.RuntimeYamlWriter
import xyz.fallnight.worldgen.spec.BlockPos
import xyz.fallnight.worldgen.spec.MineSpec
import xyz.fallnight.worldgen.spec.SpawnLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

object SpawnWorldGenerator {
    fun generate(worldDir: Path, layout: SpawnLayout, mines: List<MineSpec>) {
        Files.createDirectories(worldDir)
        WorldWriter(worldDir).use { writer ->
            val importedMineIds = mutableSetOf<String>()
            val imported = PremiereSchematicImporter.openNear(worldDir)?.use { importer ->
                mines.count { mine ->
                    importer.pasteMine(writer, mine).also { importedMine ->
                        if (importedMine) importedMineIds += mine.id
                    }
                }
            } ?: 0
            val hubFloorY = layout.hubSpawn.y - 1
            if (imported == 0) {
                writer.fill(BlockPos(-128, hubFloorY - 3, -128), BlockPos(layout.mineLadderOrigin.x + 420, hubFloorY - 1, 320), "minecraft:dirt")
                writer.fill(BlockPos(-128, hubFloorY, -128), BlockPos(layout.mineLadderOrigin.x + 420, hubFloorY, 320), "minecraft:grass_block")
                writer.fill(BlockPos(-12, hubFloorY - 2, -12), BlockPos(12, hubFloorY, 12), "minecraft:stone_bricks")
                writer.fill(BlockPos(-8, hubFloorY, -1), BlockPos(8, hubFloorY, 1), "minecraft:smooth_stone")
                writer.fill(BlockPos(-1, hubFloorY, -8), BlockPos(1, hubFloorY, 8), "minecraft:smooth_stone")
                writer.fill(BlockPos(layout.minePortal.x, hubFloorY, -1), BlockPos(layout.mineLadderOrigin.x + 320, hubFloorY, 1), "minecraft:stone_bricks")
                writer.fill(layout.minePortal.offset(-2, -1, -2), layout.minePortal.offset(2, -1, 2), "minecraft:gold_block")
                writer.fill(layout.pvpPortal.offset(-2, -1, -2), layout.pvpPortal.offset(2, -1, 2), "minecraft:iron_block")
                writer.fill(BlockPos(-2, hubFloorY, -12), BlockPos(2, hubFloorY, -8), "minecraft:oak_planks")
                writer.fill(layout.kothPad.offset(-2, -1, -2), layout.kothPad.offset(2, -1, 2), "minecraft:emerald_block")
                writer.set(layout.hubSpawn.offset(0, -1, 0), "minecraft:glowstone")
                writer.set(layout.minePortal.offset(0, 0, 0), "minecraft:campfire")
                writer.set(layout.pvpPortal.offset(0, 0, 0), "minecraft:lantern")
            }
            mines.forEach { mine ->
                if (mine.id !in importedMineIds) {
                    fillMine(writer, mine)
                    writer.fill(
                        BlockPos(mine.region.min.x - 1, mine.region.min.y - 1, mine.region.min.z - 1),
                        BlockPos(mine.region.max.x + 1, mine.region.min.y - 1, mine.region.max.z + 1),
                        "minecraft:polished_andesite",
                    )
                }
            }
        }
    }

    private fun fillMine(writer: WorldWriter, mine: MineSpec) {
        val random = Random(mine.ordinal.toLong())
        val palette = mine.blocks.map(RuntimeYamlWriter::minecraftMaterial)
        for (x in mine.region.min.x..mine.region.max.x) {
            for (y in mine.region.min.y..mine.region.max.y) {
                for (z in mine.region.min.z..mine.region.max.z) {
                    writer.set(BlockPos(x, y, z), palette[random.nextInt(palette.size)])
                }
            }
        }
    }
}
