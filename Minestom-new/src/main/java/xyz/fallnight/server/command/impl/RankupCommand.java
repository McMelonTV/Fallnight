package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.text.DecimalFormat;
import java.util.Optional;
import net.minestom.server.entity.Player;

public final class RankupCommand extends FallnightCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private final PlayerProfileService profileService;
    private final MineRankService mineRankService;
    private final AchievementService achievementService;

    public RankupCommand(PermissionService permissionService, PlayerProfileService profileService, MineRankService mineRankService) {
        super("rankup", permissionService, "ru", "upgrade");
        this.profileService = profileService;
        this.mineRankService = mineRankService;
        this.achievementService = new AchievementService(profileService);

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            UserProfile profile = profileService.getOrCreate((net.minestom.server.entity.Player) sender);
            int currentRank = profile.getMineRank();
            Optional<MineRank> nextRank = mineRankService.nextRank(currentRank);
            if (nextRank.isEmpty()) {
                sender.sendMessage(CommandMessages.info("You already reached the max rank. Use /prestige."));
                return;
            }

            long cost = mineRankService.rankUpPrice(currentRank, profile.getPrestige());
            if (!profile.withdraw(cost)) {
                sender.sendMessage(CommandMessages.error("You need $" + MONEY_FORMAT.format(cost) + " to rank up."));
                return;
            }

            MineRank targetRank = nextRank.get();
            profile.setMineRank(targetRank.getId());
            achievementService.onMineRank((Player) sender, profile);
            profileService.save(profile);
            sender.sendMessage(CommandMessages.success("Ranked up to " + targetRank.getName() + " for $" + MONEY_FORMAT.format(cost) + "."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.rankup";
    }

    @Override
    public String summary() {
        return "rank up to another mine";
    }

    @Override
    public String usage() {
        return "/rankup";
    }
}
