package xyz.fallnight.worldgen

import xyz.fallnight.worldgen.data.*
import xyz.fallnight.worldgen.reset.ResetPlanner
import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import xyz.fallnight.worldgen.util.ConsoleSummary
import xyz.fallnight.worldgen.world.WorldGenerationService
import xyz.fallnight.worldgen.world.WorldPaths
import java.nio.file.Path

fun main(args: Array<String>) {
    runWorldgen(args)
}

fun runWorldgen(args: Array<String>, workingDir: Path = Path.of("")) {
    val command = args.firstOrNull() ?: "generate"
    require(command == "generate" || command == "generate-runtime") { "Unknown command: $command" }

    val repoRoot = resolveRepoRoot(args, workingDir)
    val spec = BetaBaselineSpec.default().copy(
        root = WorldPaths.forRepoRoot(repoRoot),
    )
    val plan = ResetPlanner.build(spec.root)

    val resetExecuted = command == "generate"
    if (resetExecuted) {
        ResetPlanner.execute(plan)
    }
    WorldGenerationService.generate(spec, resetWorlds = resetExecuted)
    ApplicationConfigWriter.write(spec.root.applicationConfig, spec)
    MineDataWriter.write(spec.root.minesDir, spec.mines)
    MineRankDataWriter.write(spec.root.mineRanksDir, spec.mineRanks)
    RankDataWriter.write(spec.root.ranksFile, spec.ranks)
    TagDataWriter.write(spec.root.tagsFile, spec.tags)
    PriceDataWriter.write(spec.root.pricesFile, spec.prices)
    BroadcastDataWriter.write(spec.root.broadcastFile, spec.broadcasts)
    KothDataWriter.write(spec.root.kothFile, spec.koth)
    PvpZoneDataWriter.write(spec.root.pvpZonesFile, spec.zones)
    ConsoleSummary.print(spec, plan, repoRoot, resetExecuted)
}

internal fun resolveRepoRoot(args: Array<String>, workingDir: Path = Path.of("")): Path {
    return args.getOrNull(1)?.let(Path::of)?.normalize()
        ?: workingDir.resolve(Path.of("..")).normalize()
}
