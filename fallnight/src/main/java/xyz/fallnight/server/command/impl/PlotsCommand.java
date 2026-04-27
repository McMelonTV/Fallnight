package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.SpawnService;
import net.minestom.server.entity.Player;

public final class PlotsCommand extends FallnightCommand {
    private final PlotService plotService;
    private final SpawnService plotWorldService;

    public PlotsCommand(PermissionService permissionService, PlotService plotService, SpawnService plotWorldService) {
        super("plots", permissionService, "plotworld");
        this.plotService = plotService;
        this.plotWorldService = plotWorldService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            var targetInstance = plotWorldService.instance();
            var targetSpawn = plotWorldService.spawn();
            if (player.getInstance() == targetInstance) {
                player.teleport(targetSpawn);
            } else {
                player.setInstance(targetInstance, targetSpawn).join();
            }
            player.sendMessage(CommandMessages.info("You have been teleported to the plots."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.plots";
    }

    @Override
    public String summary() {
        return "teleport to the plotworld";
    }

    @Override
    public String usage() {
        return "/plots";
    }
}
