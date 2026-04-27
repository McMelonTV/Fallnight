package xyz.fallnight.server.bootstrap;

import xyz.fallnight.server.ServerConfig;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.mine.MineRegion;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.MineService;
import java.util.List;
import java.util.Map;

public final class DefaultContentSeeder {
    private DefaultContentSeeder() {
    }

    public static void seedIfNeeded(ServerConfig config, MineRankService mineRankService, MineService mineService) {
        if (mineRankService.find(0).isEmpty()) {
            MineRank rankA = new MineRank(0, "A", "A", 0);
            mineRankService.save(rankA);
        }

        if (mineService.find(0).isEmpty()) {
            int spawnX = (int) Math.floor(config.spawnX());
            int spawnY = (int) Math.floor(config.spawnY());
            int spawnZ = (int) Math.floor(config.spawnZ());
            MineRegion region = new MineRegion(spawnX - 5, spawnY - 4, spawnZ - 5, spawnX + 5, spawnY, spawnZ + 5);
            MineDefinition mineA = new MineDefinition(0, "A", config.spawnWorld(), region);
            mineA.setBlocks(List.of("minecraft:stone"));
            mineA.setPrices(Map.of("minecraft:stone", 1.0));
            mineA.setSpawnX(spawnX);
            mineA.setSpawnY(spawnY + 1);
            mineA.setSpawnZ(spawnZ);
            mineService.save(mineA);
        }
    }
}
