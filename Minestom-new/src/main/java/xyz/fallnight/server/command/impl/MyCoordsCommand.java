package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.minestom.server.entity.Player;

public final class MyCoordsCommand extends FallnightCommand {
    public MyCoordsCommand(PermissionService permissionService) {
        super("mycoords", permissionService);

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            sender.sendMessage(player.getPosition().toString());
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.mycoords";
    }

    @Override
    public String summary() {
        return "a debug command";
    }

    @Override
    public String usage() {
        return "/mycoords";
    }
}
