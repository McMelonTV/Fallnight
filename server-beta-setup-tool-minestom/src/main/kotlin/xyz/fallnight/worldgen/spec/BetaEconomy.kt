package xyz.fallnight.worldgen.spec

data class RankSpec(
    val id: String,
    val name: String,
    val prefix: String,
    val permissions: List<String> = emptyList(),
    val plots: Int? = null,
    val vaults: Int? = null,
    val inherit: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val isDonator: Boolean = false,
    val isStaff: Boolean = false,
    val priority: Int,
)

data class MineRankSpec(
    val id: Int,
    val name: String,
    val tag: String,
    val price: Int,
)

data class TagSpec(
    val id: String,
    val displayName: String,
)

data class PriceSpec(
    val itemPrices: Map<String, Int>,
)

data class KothSpec(
    val id: String,
    val worldName: String,
    val center: BlockPos,
    val radius: Int,
)

data class PvpZoneSpec(
    val id: String,
    val worldName: String,
    val bounds: BlockBox,
)

internal fun buildDefaultRanks(): List<RankSpec> = listOf(
    RankSpec(
        id = "member",
        name = "Member",
        prefix = "§fMember",
        permissions = listOf("fallnight.kit.starter"),
        isDefault = true,
        priority = 0,
    ),
    RankSpec(
        id = "mercenary",
        name = "Mercenary",
        prefix = "§eMercenary",
        permissions = listOf("fallnight.kit.mercenary", "fallnight.command.nick"),
        plots = 1,
        vaults = 1,
        inherit = listOf("member"),
        isDonator = true,
        priority = 10,
    ),
    RankSpec(
        id = "warrior",
        name = "Warrior",
        prefix = "§4Warrior",
        permissions = listOf("fallnight.kit.warrior", "fallnight.tag.colored", "fallnight.chat.colored"),
        plots = 1,
        vaults = 2,
        inherit = listOf("mercenary"),
        isDonator = true,
        priority = 20,
    ),
    RankSpec(
        id = "knight",
        name = "Knight",
        prefix = "§2Knight",
        permissions = listOf("fallnight.kit.knight", "fallnight.command.fly"),
        plots = 2,
        vaults = 3,
        inherit = listOf("warrior"),
        isDonator = true,
        priority = 30,
    ),
    RankSpec(
        id = "lord",
        name = "Lord",
        prefix = "§cLord",
        permissions = listOf("fallnight.kit.lord", "fallnight.command.size"),
        plots = 2,
        vaults = 4,
        inherit = listOf("knight"),
        isDonator = true,
        priority = 40,
    ),
    RankSpec(
        id = "titan",
        name = "Titan",
        prefix = "§9Titan",
        permissions = listOf("fallnight.kit.titan"),
        plots = 3,
        vaults = 5,
        inherit = listOf("lord"),
        isDonator = true,
        priority = 50,
    ),
    RankSpec(
        id = "admin",
        name = "Admin",
        prefix = "§4Admin",
        isStaff = true,
        priority = 90,
    ),
    RankSpec(
        id = "owner",
        name = "Owner",
        prefix = "§4Owner",
        isStaff = true,
        inherit = listOf("admin"),
        priority = 100,
    ),
)

private val defaultMineRankPrices = listOf(
    0,
    10_000,
    25_000,
    60_000,
    125_000,
    250_000,
    500_000,
    1_000_000,
    2_000_000,
    4_000_000,
    7_000_000,
    12_000_000,
    20_000_000,
    35_000_000,
    60_000_000,
    100_000_000,
    160_000_000,
    250_000_000,
    375_000_000,
    550_000_000,
    750_000_000,
    1_000_000_000,
    1_300_000_000,
    1_600_000_000,
    1_900_000_000,
    2_100_000_000,
)

internal fun buildDefaultMineRanks(): List<MineRankSpec> = List(defaultMineRankPrices.size) { index ->
    val name = ('A'.code + index).toChar().toString()
    MineRankSpec(
        id = index,
        name = name,
        tag = name,
        price = defaultMineRankPrices[index],
    )
}

internal fun buildDefaultTags(): List<TagSpec> = listOf(
    TagSpec(id = "beta", displayName = "Beta"),
    TagSpec(id = "miner", displayName = "Miner"),
    TagSpec(id = "founder", displayName = "Founder"),
)

internal fun buildDefaultBroadcasts(): List<String> = listOf(
    "Welcome to Fallnight Beta.",
    "Use /mine to jump into the progression ladder.",
    "PvP and mining are tuned for quick beta testing.",
)

internal fun buildDefaultPrices(): PriceSpec = PriceSpec(
    itemPrices = mapOf(
        "STONE" to 2,
        "COBBLESTONE" to 2,
        "COAL_ORE" to 6,
        "COPPER_ORE" to 8,
        "IRON_ORE" to 12,
        "GOLD_ORE" to 18,
        "REDSTONE_ORE" to 24,
        "LAPIS_ORE" to 32,
        "DIAMOND_ORE" to 55,
        "EMERALD_ORE" to 80,
        "OBSIDIAN" to 100,
        "NETHERRACK" to 15,
        "NETHER_GOLD_ORE" to 120,
        "NETHER_QUARTZ_ORE" to 135,
        "BASALT" to 18,
        "BLACKSTONE" to 22,
        "DEEPSLATE" to 10,
        "DEEPSLATE_DIAMOND_ORE" to 180,
        "DEEPSLATE_EMERALD_ORE" to 220,
        "ANCIENT_DEBRIS" to 250,
        "RAW_GOLD_BLOCK" to 320,
        "RAW_IRON_BLOCK" to 420,
        "RAW_COPPER_BLOCK" to 520,
        "CALCITE" to 24,
        "AMETHYST_BLOCK" to 650,
        "SMOOTH_BASALT" to 28,
        "PRISMARINE" to 800,
        "SEA_LANTERN" to 950,
        "END_STONE" to 1_100,
        "PURPUR_BLOCK" to 1_250,
        "GILDED_BLACKSTONE" to 1_800,
        "NETHERITE_BLOCK" to 8_000,
        "EMERALD_BLOCK" to 5_200,
        "DIAMOND_BLOCK" to 4_800,
    ),
)

internal fun buildDefaultKoth(layout: PvpMineLayout): List<KothSpec> = listOf(
    KothSpec(id = "spawn-koth", worldName = SpawnLayout.default().worldName, center = SpawnLayout.default().kothPad, radius = 8),
    KothSpec(id = "pvpmine-east", worldName = layout.worldName, center = layout.kothLandmarks.east, radius = 10),
    KothSpec(id = "pvpmine-west", worldName = layout.worldName, center = layout.kothLandmarks.west, radius = 10),
)

internal fun buildDefaultZones(layout: PvpMineLayout): List<PvpZoneSpec> = listOf(
    PvpZoneSpec(
        id = "pvpmine-core",
        worldName = layout.worldName,
        bounds = layout.combatZone,
    ),
)
