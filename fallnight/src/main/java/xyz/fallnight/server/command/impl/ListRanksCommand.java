package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.service.RankService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ListRanksCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final RankService rankService;

    public ListRanksCommand(PermissionService permissionService, RankService rankService) {
        super("listranks", permissionService);
        this.rankService = rankService;
        setDefaultExecutor((sender, context) -> {
            if (rankService.allRanks().isEmpty()) {
                sender.sendMessage(CommandMessages.info("No ranks are defined."));
                return;
            }
            sender.sendMessage(LEGACY.deserialize("§r§8<--§r§aFN§r§8-->\n§r§7 List of available ranks§r"));
            for (RankDefinition rank : rankService.allRanks()) {
                sender.sendMessage(LEGACY.deserialize(
                    "§r§8 - §r§7Id: §r§a" + rank.id()
                        + "§r§8 | §r§7Name: §r§a" + rank.name()
                        + "§r§8 | §r§7Priority: §r§a" + rank.priority()
                ));
            }
            sender.sendMessage(LEGACY.deserialize("§r§8§l<--++-->⛏"));
        });
    }

    @Override
    public String permission() {
        return "ranksystem.command.listranks";
    }

    @Override
    public String summary() {
        return "List all defined ranks.";
    }

    @Override
    public String usage() {
        return "/listranks";
    }
}
