package xyz.fallnight.server.gameplay.auction;

import xyz.fallnight.server.service.AuctionService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class AuctionExpirationModule {
    private final AuctionService auctionService;
    private Task task;

    public AuctionExpirationModule(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(auctionService::expireListings)
            .repeat(TaskSchedule.seconds(30))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }
}
