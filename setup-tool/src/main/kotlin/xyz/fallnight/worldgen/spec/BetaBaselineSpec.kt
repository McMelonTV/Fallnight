package xyz.fallnight.worldgen.spec

import xyz.fallnight.worldgen.world.WorldPaths

data class BetaBaselineSpec(
    val root: WorldPaths,
    val spawn: SpawnLayout,
    val pvpMine: PvpMineLayout,
    val mines: List<MineSpec>,
    val mineRanks: List<MineRankSpec>,
    val ranks: List<RankSpec>,
    val tags: List<TagSpec>,
    val broadcasts: List<String>,
    val prices: PriceSpec,
    val koth: List<KothSpec>,
    val zones: List<PvpZoneSpec>,
) {
    companion object {
        fun default(): BetaBaselineSpec {
            val spawn = SpawnLayout.default()
            val pvpMine = PvpMineLayout.default()
            validateMineLayout(pvpMine)
            return BetaBaselineSpec(
                root = WorldPaths.default(),
                spawn = spawn,
                pvpMine = pvpMine,
                mines = buildDefaultMines(spawn),
                mineRanks = buildDefaultMineRanks(),
                ranks = buildDefaultRanks(),
                tags = buildDefaultTags(),
                broadcasts = buildDefaultBroadcasts(),
                prices = buildDefaultPrices(),
                koth = buildDefaultKoth(pvpMine),
                zones = buildDefaultZones(pvpMine),
            )
        }
    }
}
