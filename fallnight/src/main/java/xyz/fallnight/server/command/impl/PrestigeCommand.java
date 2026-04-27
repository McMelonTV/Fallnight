package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.ProgressionMath;
import java.text.DecimalFormat;
import net.minestom.server.entity.Player;

public final class PrestigeCommand extends FallnightCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private final PlayerProfileService profileService;
    private final MineRankService mineRankService;

    public PrestigeCommand(PermissionService permissionService, PlayerProfileService profileService, MineRankService mineRankService) {
        super("prestige", permissionService);
        this.profileService = profileService;
        this.mineRankService = mineRankService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            UserProfile profile = profileService.getOrCreate((Player) sender);
            if (mineRankService.nextRank(profile.getMineRank()).isPresent()) {
                sender.sendMessage(CommandMessages.error("You need to be in the last mine to be able to prestige!"));
                return;
            }

            int targetPrestige = profile.getPrestige() + 1;
            long cost = ProgressionMath.prestigePrice(targetPrestige);
            if (!profile.withdraw(cost)) {
                sender.sendMessage(CommandMessages.error("You don't have enough money to rank up to prestige §c" + targetPrestige + "§r§7. You require §c$" + MONEY_FORMAT.format(cost) + "§r§7."));
                return;
            }

            profile.setPrestige(targetPrestige);
            profile.setMineRank(0);
            profile.addPrestigePoints(ProgressionMath.prestigeReward(targetPrestige));
            profileService.save(profile);
            sender.sendMessage(CommandMessages.success(
                "You have been ranked up to prestige§b " + targetPrestige + "§r§7 for §b$" + MONEY_FORMAT.format(cost) + "§r§7 and have received §b" + ProgressionMath.prestigeReward(targetPrestige) + "§opp§r§7!"
            ));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.prestige";
    }

    @Override
    public String summary() {
        return "rank up to another prestige";
    }

    @Override
    public String usage() {
        return "/prestige";
    }
}
