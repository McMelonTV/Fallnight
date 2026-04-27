package xyz.fallnight.worldgen.util

import xyz.fallnight.worldgen.reset.ResetPlan
import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import java.nio.file.Path

object ConsoleSummary {
    fun print(spec: BetaBaselineSpec, plan: ResetPlan, repoRoot: Path, resetExecuted: Boolean = true) {
        println(
            buildString {
                appendLine("Worldgen complete")
                appendLine("- repoRoot: ${repoRoot.normalize()}")
                appendLine("- deleted targets: ${if (resetExecuted) plan.pathsToDelete.size else 0}")
                appendLine("- created targets: ${plan.pathsToCreate.size}")
                appendLine("- mines written: ${spec.mines.size}")
                appendLine("- mine ranks written: ${spec.mineRanks.size}")
                appendLine("- worlds: ${spec.root.spawnWorld.fileName}, ${spec.root.pvpMineWorld.fileName}, ${spec.root.plotsWorld.fileName}")
            }.trimEnd(),
        )
    }
}
