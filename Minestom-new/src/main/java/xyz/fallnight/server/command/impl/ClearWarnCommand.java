package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.WarningService;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class ClearWarnCommand extends FallnightCommand {
    private final WarningService warningService;

    public ClearWarnCommand(PermissionService permissionService, WarningService warningService) {
        super("clearwarn", permissionService);
        this.warningService = warningService;

        var playerArgument = ArgumentType.Word("player");
        var idArgument = ArgumentType.Word("idOrAll");

        setDefaultExecutor((sender, context) -> sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 Usage: §r§b/warn <player> <warning>")));
        addSyntax((sender, context) -> {
            String target = context.get(playerArgument);
            String idOrAll = context.get(idArgument);
            if ("all".equalsIgnoreCase(idOrAll)) {
                int removedCount = warningService.clearAllForTarget(target);
                if (removedCount <= 0) {
                    sender.sendMessage(CommandMessages.info("No warnings found for " + target + "."));
                    return;
                }

                sender.sendMessage(CommandMessages.success(
                    "Cleared " + removedCount + " warning(s) for " + target + "."
                ));
                return;
            }

            long id;
            try {
                id = Long.parseLong(idOrAll);
            } catch (NumberFormatException exception) {
                sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 Usage: §r§b/warn <player> <warning>"));
                return;
            }

            var warning = warningService.listWarnings(target).stream().filter(entry -> entry.getId() == id).findFirst().orElse(null);
            if (!warningService.clearWarning(target, id)) {
                sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 The provided player does not have a warning with this ID."));
                return;
            }

            String reason = warning == null ? "unknown" : warning.getReason();
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§b§l> §r§7You cleared warning §r§b#" + id + " §r§7with reason §r§b" + reason + " §r§7from §r§b" + target + "§r§7."));
        }, playerArgument, idArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.clearwarn";
    }

    @Override
    public String summary() {
        return "clear a warning from a player";
    }

    @Override
    public String usage() {
        return "/clearwarn <player> <warning>";
    }
}
