package xyz.fallnight.worldgen.spec

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int,
)

data class BlockBox(
    val min: BlockPos,
    val max: BlockPos,
)

data class KothLandmarks(
    val east: BlockPos,
    val west: BlockPos,
) {
    fun asList(): List<BlockPos> = listOf(east, west)
}

data class SpawnLayout(
    val worldName: String,
    val hubSpawn: BlockPos,
    val minePortal: BlockPos,
    val pvpPortal: BlockPos,
    val kothPad: BlockPos,
    val mineLadderOrigin: BlockPos,
) {
    companion object {
        fun default(): SpawnLayout = SpawnLayout(
            worldName = "spawn-world",
            hubSpawn = BlockPos(0, 51, 0),
            minePortal = BlockPos(12, 51, 0),
            pvpPortal = BlockPos(-12, 51, 0),
            kothPad = BlockPos(0, 52, 12),
            mineLadderOrigin = BlockPos(224, 64, 200),
        )
    }
}

data class PvpMineLayout(
    val worldName: String,
    val arrivalPoint: BlockPos,
    val mineAAnchor: BlockPos,
    val arenaCenter: BlockPos,
    val kothLandmarks: KothLandmarks,
    val combatZone: BlockBox,
) {
    companion object {
        fun default(): PvpMineLayout = PvpMineLayout(
            worldName = "pvpmine",
            arrivalPoint = BlockPos(0, 72, 0),
            mineAAnchor = BlockPos(48, 72, 0),
            arenaCenter = BlockPos(0, 70, 48),
            kothLandmarks = KothLandmarks(
                east = BlockPos(32, 74, 32),
                west = BlockPos(-32, 74, -32),
            ),
            combatZone = BlockBox(
                min = BlockPos(-64, 0, -64),
                max = BlockPos(64, 255, 64),
            ),
        )
    }
}
