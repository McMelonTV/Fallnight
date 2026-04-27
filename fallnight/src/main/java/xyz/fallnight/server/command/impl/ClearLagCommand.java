package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.gameplay.maintenance.MaintenanceModule;

public final class ClearLagCommand extends FallnightCommand {
    public ClearLagCommand(PermissionService permissionService) {
        super("clearlag", permissionService);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(CommandMessages.success("Cleared §b" + MaintenanceModule.clearItemEntities() + " §r§7items."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.clearlag";
    }

    @Override
    public String summary() {
        return "clear the fucking lag ffs";
    }

    @Override
    public String usage() {
        return "/clearlag";
    }
}
