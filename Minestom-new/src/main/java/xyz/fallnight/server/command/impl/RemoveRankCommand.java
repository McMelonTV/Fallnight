package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class RemoveRankCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final RankService rankService;

    public RemoveRankCommand(PermissionService permissionService, PlayerProfileService profileService, RankService rankService) {
        super("removerank", permissionService, "takerank");
        this.profileService = profileService;
        this.rankService = rankService;

        var playerArg = ArgumentType.Word("player");
        var rankArg = ArgumentType.Word("rank");
        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> remove(sender, context.get(playerArg), context.get(rankArg)), playerArg, rankArg);
    }

    @Override
    public String permission() {
        return "ranksystem.command.removerank";
    }

    @Override
    public String summary() {
        return "Remove a rank from a player.";
    }

    @Override
    public String usage() {
        return "/removerank <player> <rank>";
    }

    private void remove(net.minestom.server.command.CommandSender sender, String playerName, String rankInput) {
        UserProfile profile = profileService.findByUsername(playerName).orElse(null);
        if (profile == null) {
            sender.sendMessage(CommandMessages.error("That player was never connected."));
            return;
        }
        RankDefinition rank = rankService.findById(rankInput).orElse(null);
        if (rank == null) {
            sender.sendMessage(CommandMessages.error("That rank could not be found."));
            return;
        }
        if (!rankService.removeRank(profile, rank.id())) {
            sender.sendMessage(CommandMessages.error(profile.getUsername() + " does not currently have that rank assigned."));
            return;
        }
        profileService.save(profile);
        sender.sendMessage(CommandMessages.success("The " + rank.name() + " rank has been removed from " + profile.getUsername() + "."));
    }
}
