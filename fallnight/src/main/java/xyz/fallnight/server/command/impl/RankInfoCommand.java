package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.service.RankService;
import java.util.StringJoiner;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class RankInfoCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final RankService rankService;

    public RankInfoCommand(PermissionService permissionService, RankService rankService) {
        super("rankinfo", permissionService);
        this.rankService = rankService;

        var rankArg = ArgumentType.Word("rank");
        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> show(sender, context.get(rankArg)), rankArg);
    }

    @Override
    public String permission() {
        return "ranksystem.command.rankinfo";
    }

    @Override
    public String summary() {
        return "display info for a rank";
    }

    @Override
    public String usage() {
        return "/rankinfo <rank>";
    }

    private void show(net.minestom.server.command.CommandSender sender, String rankInput) {
        RankDefinition rank = rankService.findById(rankInput).orElse(null);
        if (rank == null) {
            sender.sendMessage(CommandMessages.error("A rank with the given ID was not found."));
            return;
        }
        sender.sendMessage(LEGACY.deserialize("§r§8<--§r§aFN§r§8-->\n§r§7 Rank §r§a" + rank.name() + "§r§7 info"));
        sender.sendMessage(LEGACY.deserialize(" §r§l§8> §r§7ID: §r§a" + rank.id()));
        sender.sendMessage(LEGACY.deserialize(" §r§l§8> §r§7Name: §r§a" + rank.name()));
        sender.sendMessage(LEGACY.deserialize(" §r§l§8> §r§7Priority: §r§a" + rank.priority()));
        sender.sendMessage(LEGACY.deserialize(" §r§l§8> §r§7Prefix: §r§f" + rank.prefix()));
        sender.sendMessage(LEGACY.deserialize(" §r§l§8> §r§7Inheritance: §r§a" + joinOrNone(rank.inherit()).replace(", ", "§r§8,§r§a ")));
        sender.sendMessage(LEGACY.deserialize("§r§8§l<--++-->⛏"));
    }

    private static String joinOrNone(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        StringJoiner joiner = new StringJoiner(", ");
        values.forEach(joiner::add);
        return joiner.toString();
    }
}
