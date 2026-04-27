package xyz.fallnight.worldgen.world

import xyz.fallnight.worldgen.spec.BlockPos
import xyz.fallnight.worldgen.spec.PvpMineLayout
import java.nio.file.Files
import java.nio.file.Path

object PvpMineWorldGenerator {
    fun generate(worldDir: Path, layout: PvpMineLayout) {
        Files.createDirectories(worldDir)
        WorldWriter(worldDir).use { writer ->
            val arrivalFloorY = layout.arrivalPoint.y - 1
            writer.fill(BlockPos(-6, arrivalFloorY - 1, -6), BlockPos(6, arrivalFloorY, 6), "minecraft:deepslate_tiles")
            writer.fill(BlockPos(0, arrivalFloorY, -2), BlockPos(layout.mineAAnchor.x, arrivalFloorY, 2), "minecraft:stone_bricks")
            writer.fill(layout.mineAAnchor.offset(-4, -1, -4), layout.mineAAnchor.offset(4, 2, 4), "minecraft:polished_andesite")
            writer.fill(layout.arenaCenter.offset(-10, -2, -10), layout.arenaCenter.offset(10, -1, 10), "minecraft:packed_mud")
            writer.fill(layout.arenaCenter.offset(-6, 0, -6), layout.arenaCenter.offset(6, 0, 6), "minecraft:coarse_dirt")
            layout.kothLandmarks.asList().forEach { landmark ->
                writer.fill(landmark.offset(-2, -1, -2), landmark.offset(2, 0, 2), "minecraft:chiseled_stone_bricks")
                writer.fill(landmark.offset(0, 1, 0), landmark.offset(0, 3, 0), "minecraft:polished_blackstone_wall")
                writer.set(landmark.offset(0, 4, 0), "minecraft:beacon")
            }
            writer.set(layout.arrivalPoint.offset(0, -1, 0), "minecraft:sea_lantern")
            writer.set(layout.mineAAnchor.offset(0, 3, 0), "minecraft:bell")
        }
    }
}
