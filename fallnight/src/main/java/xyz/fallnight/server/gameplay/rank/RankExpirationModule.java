package xyz.fallnight.server.gameplay.rank;

import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.domain.rank.RankInstance;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class RankExpirationModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final RankService rankService;
    private Task task;

    public RankExpirationModule(PlayerProfileService profileService, RankService rankService) {
        this.profileService = profileService;
        this.rankService = rankService;
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(this::expireRanks)
            .repeat(TaskSchedule.seconds(90))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }

    private void expireRanks() {
        Instant now = Instant.now();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            UserProfile profile = profileService.getOrCreate(player);
            List<RankInstance> expired = rankService.removeExpiredRanks(profile, now);
            if (expired.isEmpty()) {
                continue;
            }
            profileService.save(profile);
            for (RankInstance instance : expired) {
                RankDefinition rank = rankService.findById(instance.rankId()).orElse(null);
                String rankName = rank == null ? instance.rankId() : rank.name();
                player.sendMessage(LEGACY.deserialize("§r§a§l> §r§7Your §r§a" + rankName + "§r§7 rank has now expired."));
                player.playSound(Sound.sound(Key.key("minecraft:block.beacon.deactivate"), Sound.Source.MASTER, 1f, 1f));
            }
        }
    }
}
