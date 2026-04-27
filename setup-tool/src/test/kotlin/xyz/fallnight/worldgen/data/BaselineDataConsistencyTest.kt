package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaselineDataConsistencyTest {
    @Test
    fun `baseline data writers use generated world names consistently`() {
        val temp = Files.createTempDirectory("worldgen-data-consistency")
        val spec = BetaBaselineSpec.default()
        val dataDir = temp.resolve("data")

        ApplicationConfigWriter.write(temp.resolve("application.yml"), spec)
        MineDataWriter.write(dataDir.resolve("mines"), spec.mines)
        RankDataWriter.write(dataDir.resolve("ranks.yml"), spec.ranks)
        TagDataWriter.write(dataDir.resolve("tags.yml"), spec.tags)
        PriceDataWriter.write(dataDir.resolve("prices.yml"), spec.prices)
        BroadcastDataWriter.write(dataDir.resolve("broadcast.yml"), spec.broadcasts)
        KothDataWriter.write(dataDir.resolve("koth.yml"), spec.koth)
        PvpZoneDataWriter.write(dataDir.resolve("pvpzones.yml"), spec.zones)

        val application = Files.readString(temp.resolve("application.yml"))
        val mine = Files.readString(dataDir.resolve("mines/a.yml"))
        val ranks = Files.readString(dataDir.resolve("ranks.yml"))
        val prices = Files.readString(dataDir.resolve("prices.yml"))
        val koth = Files.readString(dataDir.resolve("koth.yml"))
        val zones = Files.readString(dataDir.resolve("pvpzones.yml"))

        assertTrue(application.contains("world: \"${spec.spawn.worldName}\""))
        assertTrue(mine.contains("world: ${spec.spawn.worldName}"))
        assertTrue(ranks.contains("ranks:"))
        assertTrue(ranks.contains("  ${spec.ranks.first().id}:"))
        assertTrue(prices.contains("\"minecraft:stone\": 2"))
        assertTrue(!prices.contains("prices:"))
        assertTrue(koth.contains("world: \"${spec.spawn.worldName}\""))
        assertTrue(koth.contains("world: \"${spec.pvpMine.worldName}\""))
        assertTrue(zones.contains("world: ${spec.pvpMine.worldName}"))
    }

    @Test
    fun `koth writer handles empty hills without crashing and writes null next hill`() {
        val temp = Files.createTempDirectory("worldgen-empty-koth")
        val kothFile = temp.resolve("koth.yml")

        KothDataWriter.write(kothFile, emptyList())

        val written = Files.readString(kothFile)
        assertTrue(written.contains("hills:"))
        assertTrue(written.contains("state:"))
        assertTrue(written.contains("nextHillId: null"))
        assertEquals(1, "nextHillId: null".toRegex().findAll(written).count())
    }
}
