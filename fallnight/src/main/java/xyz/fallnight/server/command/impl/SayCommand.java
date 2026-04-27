package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class SayCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public SayCommand(PermissionService permissionService) {
        super("say", permissionService);

        var messageArgument = ArgumentType.StringArray("message");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String[] messageParts = context.get(messageArgument);
            String message = String.join(" ", messageParts).trim();
            if (message.isBlank()) {
                sender.sendMessage(CommandMessages.error("Message cannot be empty."));
                return;
            }

            String actor = ModerationCommandSupport.actorName(sender);
            var line = LEGACY.deserialize("§8[§b" + actor + "§8] §7" + message);
            for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.sendMessage(line);
            }
        }, messageArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.say";
    }

    @Override
    public String summary() {
        return "say something";
    }

    @Override
    public String usage() {
        return "/say <message>";
    }
}
