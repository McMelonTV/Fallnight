package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerChatEvent;

public final class SudoCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    public SudoCommand(PermissionService permissionService) {
        super("sudo", permissionService);

        var playerArg = ArgumentType.Word("player");
        var inputArg = ArgumentType.StringArray("input");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Please enter a target.")));

        addSyntax((sender, context) -> sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Please enter a command or message to send.")), playerArg);

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArg);
            String input = String.join(" ", context.get(inputArg)).trim();
            Player target = findOnlinePlayerIgnoreCase(targetName);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("§b§l> §r§7That player was not found."));
                return;
            }

            if (input.isBlank()) {
                sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Please enter a command or message to send."));
                return;
            }

            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Executing command..."));
            if (input.startsWith("/")) {
                MinecraftServer.getCommandManager().execute(target, input.substring(1));
            } else {
                PlayerChatEvent chatEvent = new PlayerChatEvent(target, MinecraftServer.getConnectionManager().getOnlinePlayers(), input);
                EventDispatcher.call(chatEvent);
                if (!chatEvent.isCancelled()) {
                    Component chatLine = chatEvent.getFormattedMessage() == null
                            ? Component.text("<" + target.getUsername() + "> " + input)
                            : chatEvent.getFormattedMessage();
                    chatEvent.getRecipients().forEach(viewer -> viewer.sendMessage(chatLine));
                }
            }
        }, playerArg, inputArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.sudo";
    }

    @Override
    public String summary() {
        return "execute something as someone else";
    }

    @Override
    public String usage() {
        return "/sudo <player> <command|message>";
    }

    private static Player findOnlinePlayerIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Player exact = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username);
        if (exact != null) {
            return exact;
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }
}
