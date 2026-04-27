package xyz.fallnight.worldgen.spec

import xyz.fallnight.worldgen.world.WorldPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BetaMinesTest {
    @Test
    fun `beta baseline defines premiere mines A through Z in order`() {
        val ids = BetaBaselineSpec.default().mines.map { it.id }
        assertEquals(26, ids.size)
        assertEquals("A", ids.first())
        assertEquals("Z", ids.last())
        assertTrue(ids.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun `beta baseline keeps mine ladder on spawn while retaining lowercase logical world names`() {
        val spec = BetaBaselineSpec.default()

        assertEquals("spawn-world", spec.spawn.worldName)
        assertEquals("pvpmine", spec.pvpMine.worldName)
        assertEquals(WorldPaths.PVPMINE_WORLD_DIR_NAME, spec.root.pvpMineWorld.fileName.toString())
        assertTrue(spec.mines.all { it.worldName == spec.spawn.worldName })
        assertTrue(spec.koth.all { it.worldName in setOf(spec.spawn.worldName, spec.pvpMine.worldName) })
        assertTrue(spec.zones.all { it.worldName == spec.pvpMine.worldName })
        assertEquals(2, spec.pvpMine.kothLandmarks.asList().size)
        assertEquals(spec.pvpMine.kothLandmarks.east, spec.koth.single { it.id == "pvpmine-east" }.center)
        assertEquals(spec.pvpMine.kothLandmarks.west, spec.koth.single { it.id == "pvpmine-west" }.center)
        assertEquals(spec.pvpMine.combatZone, spec.zones.single().bounds)
    }

    @Test
    fun `premiere schematic reuse stays spread across alphabet mines`() {
        val schematicNames = BetaBaselineSpec.default().mines.mapNotNull { it.schematicName }

        assertEquals(26, schematicNames.size)
        assertTrue(schematicNames.zipWithNext().none { (left, right) -> left == right })
        assertTrue(schematicNames.groupingBy { it }.eachCount().values.all { count -> count <= 2 })
    }

    @Test
    fun `premiere mines define source fill blocks for schematic-safe replacement`() {
        val schematicMines = BetaBaselineSpec.default().mines.filter { it.schematicName != null }

        assertEquals(26, schematicMines.size)
        assertTrue(schematicMines.all { it.sourceFillBlocks.isNotEmpty() })
        assertTrue(schematicMines.all { mine -> mine.sourceFillBlocks.all { it.startsWith("minecraft:") } })
    }
}
