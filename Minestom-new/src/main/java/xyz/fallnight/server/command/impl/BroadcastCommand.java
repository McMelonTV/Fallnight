package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.BroadcastService;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class BroadcastCommand extends FallnightCommand {
    private final BroadcastService broadcastService;

    public BroadcastCommand(PermissionService permissionService, BroadcastService broadcastService) {
        super("broadcast", permissionService);
        this.broadcastService = broadcastService;

        var messageArgument = ArgumentType.StringArray("message");

        setDefaultExecutor((sender, context) -> broadcastService.broadcastImmediate(""));
        addSyntax((sender, context) -> {
            String[] messageParts = context.get(messageArgument);
            String message = String.join(" ", messageParts);
            broadcastService.broadcastImmediate(message);
        }, messageArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.broadcast";
    }

    @Override
    public String summary() {
        return "broadcast a message";
    }

    @Override
    public String usage() {
        return "/broadcast";
    }
}
