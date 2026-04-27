package xyz.fallnight.worldgen.world

import java.nio.file.Path
import java.nio.file.Paths

data class WorldPaths(
    val root: Path,
    val dataDir: Path,
    val applicationConfig: Path,
    val mineRanksDir: Path,
    val minesDir: Path,
    val ranksFile: Path,
    val tagsFile: Path,
    val pricesFile: Path,
    val broadcastFile: Path,
    val kothFile: Path,
    val pvpZonesFile: Path,
    val spawnWorld: Path,
    val pvpMineWorld: Path,
    val plotsWorld: Path,
    val usersDir: Path,
    val vaultsDir: Path,
    val gangsDir: Path,
    val auctionFile: Path,
    val bansFile: Path,
    val warningsFile: Path,
    val lotteryFile: Path,
    val votePartyFile: Path,
) {
    companion object {
        const val SPAWN_WORLD_NAME = "spawn-world"
        const val PVPMINE_WORLD_NAME = "pvpmine"
        const val PVPMINE_WORLD_DIR_NAME = "PvPMine"
        const val PLOTS_WORLD_DIR_NAME = "plots"

        fun default(): WorldPaths {
            return forRoot(Paths.get("fallnight"))
        }

        fun forRepoRoot(repoRoot: Path): WorldPaths {
            return forRoot(repoRoot.resolve("fallnight"))
        }

        fun forRoot(root: Path): WorldPaths {
            val data = root.resolve("data")
            return WorldPaths(
                root = root,
                dataDir = data,
                applicationConfig = root.resolve("application.yml"),
                mineRanksDir = data.resolve("mineranks"),
                minesDir = data.resolve("mines"),
                ranksFile = data.resolve("ranks.yml"),
                tagsFile = data.resolve("tags.yml"),
                pricesFile = data.resolve("prices.yml"),
                broadcastFile = data.resolve("broadcast.yml"),
                kothFile = data.resolve("koth.yml"),
                pvpZonesFile = data.resolve("pvpzones.yml"),
                spawnWorld = root.resolve("..").resolve(SPAWN_WORLD_NAME).normalize(),
                pvpMineWorld = data.resolve(PVPMINE_WORLD_DIR_NAME),
                plotsWorld = data.resolve(PLOTS_WORLD_DIR_NAME),
                usersDir = data.resolve("users"),
                vaultsDir = data.resolve("vaults"),
                gangsDir = data.resolve("gangs"),
                auctionFile = data.resolve("auction.json"),
                bansFile = data.resolve("bans.json"),
                warningsFile = data.resolve("warnings.json"),
                lotteryFile = data.resolve("lottery.json"),
                votePartyFile = data.resolve("vote_party.json"),
            )
        }
    }
}
