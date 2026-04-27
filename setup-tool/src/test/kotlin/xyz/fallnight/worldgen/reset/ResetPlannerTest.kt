package xyz.fallnight.worldgen.reset

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetPlannerTest {
    @Test
    fun `reset plan removes closed beta live state and recreates generated baseline directories`() {
        val plan = ResetPlanner.build(BetaBaselineSpec.default().root)

        val pathsToDelete = plan.pathsToDelete.map { it.toString() }.toSet()
        assertEquals(11, pathsToDelete.size)
        assertEquals(true, "spawn-world" in pathsToDelete)
        assertEquals(true, "fallnight/data/PvPMine" in pathsToDelete)
        assertEquals(true, "fallnight/data/mineranks" in pathsToDelete)
        assertEquals(false, "fallnight/data/plots" in pathsToDelete)
        assertEquals(true, "fallnight/data/users" in pathsToDelete)
        assertEquals(true, "fallnight/data/vaults" in pathsToDelete)
        assertEquals(true, "fallnight/data/gangs" in pathsToDelete)
        assertEquals(true, "fallnight/data/auction.json" in pathsToDelete)
        assertEquals(true, "fallnight/data/bans.json" in pathsToDelete)
        assertEquals(true, "fallnight/data/warnings.json" in pathsToDelete)
        assertEquals(true, "fallnight/data/lottery.json" in pathsToDelete)
        assertEquals(true, "fallnight/data/vote_party.json" in pathsToDelete)

        val pathsToCreate = plan.pathsToCreate.map { it.toString() }.toSet()
        assertEquals(3, pathsToCreate.size)
        assertEquals(true, "spawn-world" in pathsToCreate)
        assertEquals(true, "fallnight/data/PvPMine" in pathsToCreate)
        assertEquals(true, "fallnight/data/plots" in pathsToCreate)
    }
}
