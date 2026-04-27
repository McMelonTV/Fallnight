package xyz.fallnight.worldgen.reset

import xyz.fallnight.worldgen.world.WorldPaths
import java.nio.file.Files
import java.nio.file.Path

data class ResetPlan(
    val pathsToDelete: List<Path>,
    val pathsToCreate: List<Path>,
)

object ResetPlanner {
    fun build(paths: WorldPaths): ResetPlan {
        return ResetPlan(
            pathsToDelete = listOf(
                paths.spawnWorld,
                paths.pvpMineWorld,
                paths.mineRanksDir,
                paths.usersDir,
                paths.vaultsDir,
                paths.gangsDir,
                paths.auctionFile,
                paths.bansFile,
                paths.warningsFile,
                paths.lotteryFile,
                paths.votePartyFile,
            ),
            pathsToCreate = listOf(
                paths.spawnWorld,
                paths.pvpMineWorld,
                paths.plotsWorld,
            ),
        )
    }

    fun execute(plan: ResetPlan) {
        plan.pathsToDelete.forEach(::deleteIfExists)
        plan.pathsToCreate.forEach(Files::createDirectories)
    }

    private fun deleteIfExists(path: Path) {
        if (Files.notExists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .use { paths -> paths.forEach(Files::deleteIfExists) }
    }
}
