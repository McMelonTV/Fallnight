package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.domain.rank.RankInstance;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class PlayerRanksCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final RankService rankService;

    public PlayerRanksCommand(PermissionService permissionService, PlayerProfileService profileService, RankService rankService) {
        super("playerranks", permissionService, "pranks");
        this.profileService = profileService;
        this.rankService = rankService;

        var playerArg = ArgumentType.Word("player");
        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> show(sender, context.get(playerArg)), playerArg);
    }

    @Override
    public String permission() {
        return "ranksystem.command.playerranks";
    }

    @Override
    public String summary() {
        return "Display a player's assigned and effective ranks.";
    }

    @Override
    public String usage() {
        return "/playerranks <player>";
    }

    private void show(net.minestom.server.command.CommandSender sender, String playerName) {
        UserProfile profile = profileService.findByUsername(playerName).orElse(null);
        if (profile == null) {
            sender.sendMessage(CommandMessages.error("That player was never connected."));
            return;
        }

        List<RankInstance> assigned = rankService.assignedRanks(profile);
        String effective = rankService.effectiveRankDefinitions(profile).stream()
            .map(RankDefinition::id)
            .collect(java.util.stream.Collectors.joining(", "));
        sender.sendMessage(LEGACY.deserialize("§r§8<--§r§aFN§r§8-->\n§r§7 §r§a" + profile.getUsername() + "§r§7's ranks:§r"));
        if (assigned.isEmpty()) {
            sender.sendMessage(LEGACY.deserialize("§r§8 - §r§7(none)"));
        } else {
            long now = Instant.now().getEpochSecond();
            for (RankInstance instance : assigned) {
                RankDefinition rank = rankService.findById(instance.rankId()).orElse(null);
                String rankName = rank == null ? instance.rankId() : rank.id();
                String expire = instance.permanent() ? "never" : Long.toString(Math.max(now, instance.expire()));
                sender.sendMessage(LEGACY.deserialize(
                    "§r§8 - §r§7Id: §r§a" + instance.rankId()
                        + " §r§8| §r§7Expire: §r§a" + expire
                        + " §r§8| §r§7Persistent: §r§a" + (instance.persistent() ? "yes" : "no")
                        + " §r§8| §r§7Rank: §r§a" + rankName
                ));
            }
        }
        sender.sendMessage(LEGACY.deserialize("§r§8 - §r§7Effective: §r§a" + (effective.isBlank() ? "none" : effective)));
        sender.sendMessage(LEGACY.deserialize("§r§8§l<--++-->⛏"));
    }
}
