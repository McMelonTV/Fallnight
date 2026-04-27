package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.WarningService;
import net.minestom.server.entity.Player;

public final class MyWarnsCommand extends FallnightCommand {
    private final WarningService warningService;

    public MyWarnsCommand(PermissionService permissionService, WarningService warningService) {
        super("mywarns", permissionService);
        this.warningService = warningService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            WarningsCommand.sendWarnings(sender, player.getUsername(), warningService.listWarnings(player.getUsername()));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.mywarns";
    }

    @Override
    public String summary() {
        return "show your warnings";
    }

    @Override
    public String usage() {
        return "/mywarns";
    }
}
