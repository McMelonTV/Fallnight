package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class PluginsCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public PluginsCommand(PermissionService permissionService) {
        super("plugins", permissionService, "pl");

        setDefaultExecutor((sender, context) -> {
            StringBuilder builder = new StringBuilder("§8§l<--§bFN§8--> ")
                .append("\n§r§7 Fallnight plugin list §r§8(")
                .append(1)
                .append(")§r");
            builder.append("\n§r §8§l> §r§bFallnight §7vdev§7 by §bFallnight");
            builder.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LEGACY.deserialize(builder.toString()));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.plugins";
    }

    @Override
    public String summary() {
        return "view a list of the plugins in the server";
    }

    @Override
    public String usage() {
        return "/plugins";
    }
}
