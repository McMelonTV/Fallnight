package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.warning.WarningEntry;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.service.WarningService;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class WarningsCommand extends FallnightCommand {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC);

    private final WarningService warningService;

    public WarningsCommand(PermissionService permissionService, WarningService warningService) {
        super("warnings", permissionService);
        this.warningService = warningService;

        var playerArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String target = context.get(playerArgument);
            sendWarnings(sender, target, warningService.listWarnings(target));
        }, playerArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.warnings";
    }

    @Override
    public String summary() {
        return "get the warnings for a player";
    }

    @Override
    public String usage() {
        return "/warnings <player>";
    }

    static void sendWarnings(CommandSender sender, String target, List<WarningEntry> warnings) {
        if (warnings.isEmpty()) {
            sender.sendMessage(CommandMessages.info("No warnings found for " + target + "."));
            return;
        }

        if (sender instanceof net.minestom.server.entity.Player player) {
            List<String> lines = new ArrayList<>();
            for (WarningEntry warning : warnings) {
                lines.add("§b#" + warning.getId() + " §7by §f" + warning.getActor());
                lines.add("§7At: §f" + TIME_FORMATTER.format(warning.getCreatedAt()));
                lines.add("§7Reason: §f" + warning.getReason());
            }
            new PagedTextMenuService().open(player, "Warnings: " + target, lines);
            return;
        }

        sender.sendMessage(CommandMessages.info("Warnings for " + target + " (" + warnings.size() + ")"));
        for (WarningEntry warning : warnings) {
            sender.sendMessage(CommandMessages.info(
                "#" + warning.getId()
                    + " by " + warning.getActor()
                    + " at " + TIME_FORMATTER.format(warning.getCreatedAt())
                    + " - " + warning.getReason()
            ));
        }
    }
}
