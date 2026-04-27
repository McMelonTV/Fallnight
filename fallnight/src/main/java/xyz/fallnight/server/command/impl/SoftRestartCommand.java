package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.gameplay.maintenance.MaintenanceModule;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class SoftRestartCommand extends FallnightCommand {
    public SoftRestartCommand(PermissionService permissionService) {
        super("softrestart", permissionService);

        var secondsArg = ArgumentType.Integer("seconds").min(1);

        setDefaultExecutor((sender, context) -> {
            if (!MaintenanceModule.scheduleRestart(10)) {
                sender.sendMessage(CommandMessages.error("Restart scheduler is unavailable."));
                return;
            }
            sender.sendMessage(CommandMessages.info("Enabled softrestart."));
        });
        addSyntax((sender, context) -> {
            int seconds = context.get(secondsArg);
            if (!MaintenanceModule.scheduleRestart(seconds)) {
                sender.sendMessage(CommandMessages.error("Restart scheduler is unavailable."));
                return;
            }
            sender.sendMessage(CommandMessages.info("Enabled softrestart."));
        }, secondsArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.softrestart";
    }

    @Override
    public String summary() {
        return "softrestart";
    }

    @Override
    public String usage() {
        return "/softrestart";
    }
}
