package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.DefaultWorldService;
import net.minestom.server.entity.Player;

public final class SpawnCommand extends FallnightCommand {
    private final DefaultWorldService defaultWorldService;

    public SpawnCommand(PermissionService permissionService, DefaultWorldService defaultWorldService) {
        super("spawn", permissionService, "hub");
        this.defaultWorldService = defaultWorldService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            defaultWorldService.teleportToSpawn(player);
            sender.sendMessage(CommandMessages.info("Teleported to spawn."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.spawn";
    }

    @Override
    public String summary() {
        return "teleport to spawn";
    }

    @Override
    public String usage() {
        return "/spawn";
    }
}
