package xyz.fallnight.server.gameplay.broadcast;

import xyz.fallnight.server.service.BroadcastService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class BroadcastRotationModule {
    private final BroadcastService broadcastService;
    private Task task;
    private long seconds;
    private int messages;

    public BroadcastRotationModule(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(this::tick)
            .repeat(TaskSchedule.seconds(1))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }

    public void addMessage() {
        messages++;
    }

    private void tick() {
        seconds++;
        if (seconds >= 60 * 6L
            || (seconds >= 60 * 5L && messages >= 10)
            || (seconds >= 60 * 4L && messages >= 40)
            || (seconds >= 60 * 3L && messages >= 75)
            || (seconds >= 60 && messages >= 150)) {
            seconds = 0L;
            messages = 0;
            broadcastService.broadcastNext();
        }
    }
}
