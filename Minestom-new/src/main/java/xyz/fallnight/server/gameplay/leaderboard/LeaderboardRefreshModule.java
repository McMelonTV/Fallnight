package xyz.fallnight.server.gameplay.leaderboard;

import xyz.fallnight.server.service.LeaderboardService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class LeaderboardRefreshModule {
    private final LeaderboardService leaderboardService;
    private Task task;

    public LeaderboardRefreshModule(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public void register() {
        leaderboardService.regenerateAll();
        task = MinecraftServer.getSchedulerManager()
            .buildTask(leaderboardService::regenerateAll)
            .repeat(TaskSchedule.seconds(300))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }
}
