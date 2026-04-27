package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.util.ModerationTimeFormatter;
import java.time.Duration;
import java.time.Instant;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class AddRankCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final RankService rankService;

    public AddRankCommand(PermissionService permissionService, PlayerProfileService profileService, RankService rankService) {
        super("addrank", permissionService, "giverank");
        this.profileService = profileService;
        this.rankService = rankService;

        var playerArg = ArgumentType.Word("player");
        var rankArg = ArgumentType.Word("rank");
        var expireArg = ArgumentType.Long("expireSeconds");
        var persistArg = ArgumentType.Boolean("persist");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> assign(sender, context.get(playerArg), context.get(rankArg), -1L, true), playerArg, rankArg);
        addSyntax((sender, context) -> assign(sender, context.get(playerArg), context.get(rankArg), context.get(expireArg), true), playerArg, rankArg, expireArg);
        addSyntax((sender, context) -> assign(sender, context.get(playerArg), context.get(rankArg), context.get(expireArg), context.get(persistArg)), playerArg, rankArg, expireArg, persistArg);
    }

    @Override
    public String permission() {
        return "ranksystem.command.addrank";
    }

    @Override
    public String summary() {
        return "Grant a rank to a player with optional expiry.";
    }

    @Override
    public String usage() {
        return "/addrank <player> <rank> [expireSeconds] [persist]";
    }

    private void assign(net.minestom.server.command.CommandSender sender, String playerName, String rankInput, long expireSeconds, boolean persist) {
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
        if (expireSeconds < -1L) {
            sender.sendMessage(CommandMessages.error("Expiration must be -1 or a positive number of seconds."));
            return;
        }
        long expire = expireSeconds < 0L ? -1L : Instant.now().getEpochSecond() + expireSeconds;
        rankService.assignRank(profile, rank.id(), expire, persist);
        profileService.save(profile);
        String duration = expire < 0L ? "never" : ModerationTimeFormatter.remaining(Duration.ofSeconds(expireSeconds));
        sender.sendMessage(CommandMessages.success(
            profile.getUsername() + " has been given the " + rank.name() + " rank that expires in " + duration + " and with persist=" + persist + "."
        ));
    }
}
