package xyz.fallnight.worldgen.spec

data class MinePrice(
    val material: String,
    val value: Int,
)

data class MineSpec(
    val id: String,
    val ordinal: Int,
    val worldName: String,
    val region: BlockBox,
    val spawn: BlockPos,
    val blocks: List<String>,
    val prices: List<MinePrice>,
    val schematicName: String? = null,
    val schematicOffset: BlockPos? = null,
    val clearRegion: BlockBox? = null,
    val sourceFillBlocks: Set<String> = emptySet(),
)

private data class PremiereMineTemplate(
    val name: String,
    val schematicName: String,
    val schematicOffset: BlockPos,
    val localMineRegion: BlockBox,
    val sourceFillBlocks: Set<String>,
)

internal fun buildDefaultMines(layout: PvpMineLayout): List<MineSpec> = buildList {
    val palette = listOf(
        listOf("STONE"),
        listOf("STONE", "COBBLESTONE"),
        listOf("COAL_ORE", "STONE"),
        listOf("IRON_ORE", "STONE"),
        listOf("GOLD_ORE", "STONE"),
        listOf("REDSTONE_ORE", "STONE"),
        listOf("LAPIS_ORE", "STONE"),
        listOf("DIAMOND_ORE", "STONE"),
        listOf("EMERALD_ORE", "STONE"),
        listOf("OBSIDIAN", "STONE"),
        listOf("NETHERRACK", "STONE"),
        listOf("BASALT", "STONE"),
        listOf("BLACKSTONE", "STONE"),
        listOf("ANCIENT_DEBRIS", "BLACKSTONE"),
        listOf("PRISMARINE", "STONE"),
        listOf("SEA_LANTERN", "PRISMARINE"),
        listOf("MUD", "STONE"),
        listOf("CLAY", "STONE"),
        listOf("SAND", "STONE"),
        listOf("RED_SAND", "STONE"),
        listOf("ICE", "STONE"),
        listOf("PACKED_ICE", "STONE"),
        listOf("BLUE_ICE", "STONE"),
        listOf("END_STONE", "STONE"),
        listOf("PURPUR_BLOCK", "END_STONE"),
        listOf("DEEPSLATE", "STONE"),
    )

    repeat(26) { index ->
        val id = ('A'.code + index).toChar().toString()
        val x = layout.mineAAnchor.x + (index * 6)
        val min = BlockPos(x, layout.mineAAnchor.y, layout.mineAAnchor.z)
        val max = BlockPos(x + 4, layout.mineAAnchor.y + 24, layout.mineAAnchor.z + 4)
        add(
            MineSpec(
                id = id,
                ordinal = index,
                worldName = layout.worldName,
                region = BlockBox(min = min, max = max),
                spawn = BlockPos(x + 2, layout.mineAAnchor.y + 1, layout.mineAAnchor.z + 2),
                blocks = palette[index],
                prices = listOf(MinePrice(material = palette[index].first(), value = (index + 1) * 10)),
            ),
        )
    }
}

internal fun buildDefaultMines(layout: SpawnLayout): List<MineSpec> = buildList {
    val palettes = listOf(
        listOf(MinePrice("STONE", 2)),
        listOf(MinePrice("COAL_ORE", 6), MinePrice("STONE", 2)),
        listOf(MinePrice("COPPER_ORE", 8), MinePrice("COAL_ORE", 6)),
        listOf(MinePrice("IRON_ORE", 12), MinePrice("STONE", 3)),
        listOf(MinePrice("GOLD_ORE", 18), MinePrice("SANDSTONE", 4)),
        listOf(MinePrice("REDSTONE_ORE", 24), MinePrice("DEEPSLATE", 5)),
        listOf(MinePrice("LAPIS_ORE", 32), MinePrice("STONE", 4)),
        listOf(MinePrice("DIAMOND_ORE", 55), MinePrice("DEEPSLATE", 6)),
        listOf(MinePrice("EMERALD_ORE", 80), MinePrice("DEEPSLATE", 8)),
        listOf(MinePrice("OBSIDIAN", 100), MinePrice("BLACKSTONE", 12)),
        listOf(MinePrice("NETHER_GOLD_ORE", 120), MinePrice("NETHERRACK", 15)),
        listOf(MinePrice("NETHER_QUARTZ_ORE", 135), MinePrice("BASALT", 18)),
        listOf(MinePrice("DEEPSLATE_DIAMOND_ORE", 180), MinePrice("DEEPSLATE", 10)),
        listOf(MinePrice("ANCIENT_DEBRIS", 250), MinePrice("BLACKSTONE", 22)),
        listOf(MinePrice("RAW_GOLD_BLOCK", 320), MinePrice("DEEPSLATE_EMERALD_ORE", 220)),
        listOf(MinePrice("RAW_IRON_BLOCK", 420), MinePrice("DEEPSLATE", 20)),
        listOf(MinePrice("RAW_COPPER_BLOCK", 520), MinePrice("CALCITE", 24)),
        listOf(MinePrice("AMETHYST_BLOCK", 650), MinePrice("SMOOTH_BASALT", 28)),
        listOf(MinePrice("PRISMARINE", 800), MinePrice("SEA_LANTERN", 950)),
        listOf(MinePrice("END_STONE", 1_100), MinePrice("PURPUR_BLOCK", 1_250)),
        listOf(MinePrice("BLACKSTONE", 1_400), MinePrice("GILDED_BLACKSTONE", 1_800)),
        listOf(MinePrice("DEEPSLATE_EMERALD_ORE", 2_200), MinePrice("DEEPSLATE_DIAMOND_ORE", 1_900)),
        listOf(MinePrice("ANCIENT_DEBRIS", 2_800), MinePrice("NETHERITE_BLOCK", 4_500)),
        listOf(MinePrice("RAW_GOLD_BLOCK", 3_200), MinePrice("RAW_IRON_BLOCK", 2_600)),
        listOf(MinePrice("EMERALD_BLOCK", 5_200), MinePrice("DIAMOND_BLOCK", 4_800)),
        listOf(MinePrice("NETHERITE_BLOCK", 8_000), MinePrice("ANCIENT_DEBRIS", 3_500)),
    )

    premiereMineTemplatesForAlphabet.forEachIndexed { index, placement ->
        val id = ('A'.code + index).toChar().toString()
        val prices = palettes[index]
        val region = placement.template.localMineRegion.offset(placement.offset)
        add(
            MineSpec(
                id = id,
                ordinal = index,
                worldName = layout.worldName,
                region = region,
                spawn = BlockPos(
                    x = (region.min.x + region.max.x) / 2,
                    y = region.max.y + 1,
                    z = (region.min.z + region.max.z) / 2,
                ),
                blocks = prices.map(MinePrice::material),
                prices = prices,
                schematicName = placement.template.schematicName,
                schematicOffset = placement.offset,
                sourceFillBlocks = placement.template.sourceFillBlocks,
            ),
        )
    }
}

private data class PremiereMinePlacement(
    val template: PremiereMineTemplate,
    val offset: BlockPos,
)

private val premiereMineTemplates = listOf(
    PremiereMineTemplate(
        "Christmas",
        "Mine1-Christmas.schem",
        BlockPos(-46, 15, -80),
        BlockBox(BlockPos(61, 40, 53), BlockPos(88, 47, 87)),
        setOf("minecraft:snow_block"),
    ),
    PremiereMineTemplate(
        "Aquatic",
        "Mine2-Aquatic.schem",
        BlockPos(119, 33, -69),
        BlockBox(BlockPos(58, 14, 47), BlockPos(85, 28, 81)),
        setOf("minecraft:sandstone", "minecraft:prismarine", "minecraft:end_stone"),
    ),
    PremiereMineTemplate(
        "MushroomForest",
        "Mine3-MushroomForest.schem",
        BlockPos(266, 33, -69),
        BlockBox(BlockPos(62, 24, 51), BlockPos(89, 38, 85)),
        setOf("minecraft:dirt", "minecraft:orange_wool", "minecraft:red_wool"),
    ),
    PremiereMineTemplate(
        "WaspNest",
        "Mine4-WaspNest.schem",
        BlockPos(422, 58, -60),
        BlockBox(BlockPos(65, 6, 47), BlockPos(92, 20, 81)),
        setOf("minecraft:dirt", "minecraft:green_terracotta"),
    ),
    PremiereMineTemplate(
        "Egypt",
        "Mine5-Egypt.schem",
        BlockPos(621, 58, -70),
        BlockBox(BlockPos(36, 2, 48), BlockPos(73, 12, 80)),
        setOf("minecraft:sandstone"),
    ),
    PremiereMineTemplate(
        "Space",
        "Mine6-Space.schem",
        BlockPos(773, 29, -104),
        BlockBox(BlockPos(61, 28, 95), BlockPos(93, 43, 135)),
        setOf("minecraft:end_stone"),
    ),
    PremiereMineTemplate(
        "Space2",
        "Mine7Space.schem",
        BlockPos(1102, 48, -54),
        BlockBox(BlockPos(57, 14, 54), BlockPos(84, 14, 83)),
        setOf("minecraft:end_stone"),
    ),
    PremiereMineTemplate(
        "OakForest",
        "Mine8-OakForest.schem",
        BlockPos(1386, 63, -24),
        BlockBox(BlockPos(26, 0, 34), BlockPos(51, 0, 58)),
        setOf("minecraft:green_wool", "minecraft:green_terracotta"),
    ),
    PremiereMineTemplate(
        "AbandonedTown",
        "Mine9-AbandonedTown.schem",
        BlockPos(1546, 36, -45),
        BlockBox(BlockPos(10, 13, 28), BlockPos(83, 31, 130)),
        setOf("minecraft:andesite", "minecraft:stone_bricks"),
    ),
    PremiereMineTemplate(
        "DesertTrain",
        "Mine10-DesertTrain.schem",
        BlockPos(1661, 31, -28),
        BlockBox(BlockPos(47, 14, 26), BlockPos(102, 31, 69)),
        setOf("minecraft:red_sandstone", "minecraft:red_sand"),
    ),
    PremiereMineTemplate(
        "City",
        "Mine11-City.schem",
        BlockPos(1844, 31, -28),
        BlockBox(BlockPos(47, 14, 26), BlockPos(102, 31, 69)),
        setOf("minecraft:red_sandstone", "minecraft:red_sand"),
    ),
    PremiereMineTemplate(
        "FlowerForest",
        "Mine12-FlowerForest.schem",
        BlockPos(2027, 26, -63),
        BlockBox(BlockPos(33, 13, 108), BlockPos(58, 23, 129)),
        setOf("minecraft:dirt", "minecraft:green_terracotta"),
    ),
    PremiereMineTemplate(
        "CalicoDesert",
        "Mine13-CalicoDesert.schem",
        BlockPos(1276, 67, -34),
        BlockBox(BlockPos(33, 0, 42), BlockPos(52, 0, 62)),
        setOf("minecraft:yellow_wool", "minecraft:yellow_terracotta"),
    ),
    PremiereMineTemplate(
        "SpruceForest",
        "Mine14-SpruceForest.schem",
        BlockPos(943, 44, -49),
        BlockBox(BlockPos(67, 8, 35), BlockPos(89, 23, 75)),
        setOf("minecraft:dirt", "minecraft:green_terracotta"),
    ),
    PremiereMineTemplate(
        "Tower",
        "Mine15-Tower.schem",
        BlockPos(2184, 22, -40),
        BlockBox(BlockPos(61, 15, 51), BlockPos(98, 26, 104)),
        setOf("minecraft:dirt", "minecraft:green_terracotta"),
    ),
)

private val repeatTemplateIndexes = listOf(5, 12, 3, 14, 1, 8, 0, 10, 4, 13, 6)

private val premiereMineTemplatesForAlphabet = buildList {
    addAll(premiereMineTemplates.map { PremiereMinePlacement(it, it.schematicOffset.shiftedForSpawnWorld(0)) })
    repeatTemplateIndexes.forEach { templateIndex ->
        val template = premiereMineTemplates[templateIndex]
        add(PremiereMinePlacement(template, template.schematicOffset.shiftedForSpawnWorld(1)))
    }
}

private fun BlockBox.offset(offset: BlockPos): BlockBox = BlockBox(
    min = BlockPos(min.x + offset.x, min.y + offset.y, min.z + offset.z),
    max = BlockPos(max.x + offset.x, max.y + offset.y, max.z + offset.z),
)

private fun BlockPos.shiftedForSpawnWorld(row: Int): BlockPos = BlockPos(
    x = x + 4_000 + (row * 2_700),
    y = y,
    z = z + (row * 260),
)

internal fun validateMineLayout(layout: PvpMineLayout) {
    require(layout.worldName == "pvpmine") { "PvPMine world must use lowercase logical world name" }
    require(layout.combatZone.min.x <= layout.combatZone.max.x && layout.combatZone.min.z <= layout.combatZone.max.z) {
        "PvPMine combat zone must have ordered bounds"
    }
    require(layout.kothLandmarks.east != layout.kothLandmarks.west) {
        "PvPMine KOTH landmarks must be distinct"
    }
}
