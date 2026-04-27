package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class PingCommand extends FallnightCommand {
    public PingCommand(PermissionService permissionService) {
        super("ping", permissionService);
        var playerArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }

            sender.sendMessage(CommandMessages.info("Your ping: §b" + player.getLatency() + "§r§7."));
        });

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }
            sender.sendMessage(CommandMessages.info("§b" + target.getUsername() + "§7's ping: §b" + target.getLatency() + "§r§7."));
        }, playerArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.ping";
    }

    @Override
    public String summary() {
        return "check your ping";
    }

    @Override
    public String usage() {
        return "/ping";
    }
}
