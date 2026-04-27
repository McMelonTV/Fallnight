package xyz.fallnight.server.gameplay.player;

import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.SpawnService;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class WorldRuleModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final DefaultWorldService defaultWorldService;
    private final List<SpawnService> worldSpawns;
    private final EventNode<Event> eventNode;
    private Task hungerTask;

    public WorldRuleModule(DefaultWorldService defaultWorldService, SpawnService... worldSpawns) {
        this.defaultWorldService = defaultWorldService;
        this.worldSpawns = List.of(worldSpawns);
        this.eventNode = EventNode.all("world-rules");
    }

    public void register() {
        eventNode.addListener(PlayerMoveEvent.class, event -> {
            Pos to = event.getNewPosition();
            double x = Math.abs(to.x());
            double z = Math.abs(to.z());
            if (x <= 55_000 && z <= 55_000) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendActionBar(LEGACY.deserialize("§r§8[§bFN§8]\n§r§7You have reached the world border.\n§r§7Please don't continue further."));
            if (x > 55_005 || z > 55_005) {
                teleportToCurrentWorldSpawn(event.getPlayer());
            }
        });
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        hungerTask = MinecraftServer.getSchedulerManager()
            .buildTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(this::applyHungerRule))
            .repeat(TaskSchedule.seconds(2))
            .schedule();
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        if (hungerTask != null) {
            hungerTask.cancel();
        }
    }

    public void applyHungerRule(net.minestom.server.entity.Player player) {
        player.setFood(20);
        player.setFoodSaturation(20f);
    }

    private void teleportToCurrentWorldSpawn(net.minestom.server.entity.Player player) {
        for (SpawnService spawnService : worldSpawns) {
            if (spawnService.instance() == player.getInstance()) {
                spawnService.teleportToSpawn(player);
                return;
            }
        }
        defaultWorldService.teleportToSpawn(player);
    }
}
