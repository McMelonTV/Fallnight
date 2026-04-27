package xyz.fallnight.server.gameplay.lottery;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.service.LotteryService;
import xyz.fallnight.server.util.LegacyTextFormatter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class LotteryDrawModule {
    private final LotteryService lotteryService;
    private Task task;

    public LotteryDrawModule(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(this::runDraw)
            .repeat(TaskSchedule.seconds(1))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runDraw() {
        LotteryService.DrawResult result = lotteryService.tickSecond();
        if (result.status() != LotteryService.DrawStatus.SUCCESS) {
            return;
        }

        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(LegacyTextFormatter.deserialize(
                "§r§8[§bFN§8] §r§b" + result.winnerUsername() + " §r§7won the jackpot! They received §b$"
                    + (long) result.payout() + "§r§7."
            ));
        }
    }
}
