package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.warning.WarningEntry;
import xyz.fallnight.server.service.WarningService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class WarnCommand extends FallnightCommand {
    private final WarningService warningService;

    public WarnCommand(PermissionService permissionService, WarningService warningService) {
        super("warn", permissionService);
        this.warningService = warningService;

        var playerArgument = ArgumentType.Word("player");
        var reasonArgument = ArgumentType.StringArray("reason");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String targetUsername = context.get(playerArgument);
            String reason = String.join(" ", context.get(reasonArgument)).trim();
            if (reason.isBlank()) {
                sender.sendMessage(CommandMessages.error("Reason cannot be empty."));
                return;
            }

            String actor = sender instanceof Player player ? player.getUsername() : "console";
            WarningEntry warning = warningService.addWarning(targetUsername, actor, reason);
            sender.sendMessage(CommandMessages.success(
                "Issued warning #" + warning.getId() + " to " + warning.getTargetUsername() + "."
            ));

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetUsername);
            if (target != null) {
                target.sendMessage(CommandMessages.error(
                    "You were warned by " + warning.getActor() + ": " + warning.getReason()
                ));
            }
        }, playerArgument, reasonArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.warn";
    }

    @Override
    public String summary() {
        return "warn a player";
    }

    @Override
    public String usage() {
        return "/warn <player> <reason>";
    }
}
