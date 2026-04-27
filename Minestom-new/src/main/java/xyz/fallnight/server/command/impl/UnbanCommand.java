package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class UnbanCommand extends FallnightCommand {
    private final ModerationSanctionsService sanctionsService;

    public UnbanCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService) {
        super("unban", permissionService, "pardon");
        this.sanctionsService = sanctionsService;

        var playerArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            if (!sanctionsService.unban(targetName)) {
                sender.sendMessage(CommandMessages.error("No active ban found for " + targetName + "."));
                return;
            }
            sender.sendMessage(CommandMessages.success("Unbanned " + targetName + "."));
        }, playerArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.unban";
    }

    @Override
    public String summary() {
        return "unban a banned player";
    }

    @Override
    public String usage() {
        return "/unban <player>";
    }
}
