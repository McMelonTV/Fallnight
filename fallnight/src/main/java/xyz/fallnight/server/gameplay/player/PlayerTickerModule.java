package xyz.fallnight.server.gameplay.player;

import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class PlayerTickerModule {
    private static final int AUTO_SAVE_INTERVAL_SECONDS = 1200;

    private final Runnable autoSaveAction;
    private final EventNode<Event> eventNode;
    private Task autoSaveTask;

    public PlayerTickerModule(PlayerProfileService profileService, Runnable autoSaveAction) {
        this.autoSaveAction = autoSaveAction == null ? profileService::saveAllOnline : autoSaveAction;
        this.eventNode = EventNode.all("player-ticker");
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);

        autoSaveTask = MinecraftServer.getSchedulerManager()
            .buildTask(this::autoSave)
            .repeat(TaskSchedule.seconds(AUTO_SAVE_INTERVAL_SECONDS))
            .schedule();
    }

    public void unregister() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    private void autoSave() {
        autoSaveAction.run();
    }
}
