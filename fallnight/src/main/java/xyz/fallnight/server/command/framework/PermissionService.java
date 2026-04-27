package xyz.fallnight.server.command.framework;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

public final class PermissionService {
    private final PlayerProfileService profileService;
    private final RankService rankService;

    public PermissionService(PlayerProfileService profileService, RankService rankService) {
        this.profileService = profileService;
        this.rankService = rankService;
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }

        if (!(sender instanceof Player player)) {
            return true;
        }

        UserProfile profile = profileService.getOrCreate(player);
        if (rankService.hasPermission(profile, permission)) {
            return true;
        }

        if ("fallnight.command.eval".equalsIgnoreCase(permission)) {
            return false;
        }

        if (permission.endsWith(".balance")
                || permission.endsWith(".kit")
                || permission.endsWith(".rankup")
                || permission.endsWith(".prestige")
                || permission.endsWith(".mine")
                || permission.endsWith(".mines")
                || permission.endsWith(".gang")
                || permission.endsWith(".vault")
                || permission.endsWith(".shop")
                || permission.endsWith(".auction")
                || permission.endsWith(".baltop")
                || permission.endsWith(".leaderboard")
                || permission.endsWith(".vote")
                || permission.endsWith(".lottery")
                || permission.endsWith(".koth")
                || permission.endsWith(".tags")
                || permission.endsWith(".kit")
                || permission.endsWith(".plot")
                || permission.endsWith(".plots")
                || permission.endsWith(".nicklist")
                || permission.endsWith(".afk")
                || permission.endsWith(".block")
                || permission.endsWith(".unblock")
                || permission.endsWith(".blocklist")
                || permission.endsWith(".ranks")
                || permission.endsWith(".stats")
                || permission.endsWith(".crates")
                || permission.endsWith(".crateitems")
                || permission.endsWith(".forge")
                || permission.endsWith(".reforge")
                || permission.endsWith(".enchantmentforge")
                || permission.endsWith(".enchantmentlist")
                || permission.endsWith(".disenchant")
                || permission.endsWith(".tell")
                || permission.endsWith(".reply")
                || permission.endsWith(".mywarns")
                || permission.endsWith(".trash")
                || permission.endsWith(".rules")
                || permission.endsWith(".news")
                || permission.endsWith(".guide")
                || permission.endsWith(".help")
                || permission.endsWith(".plugins")
                || permission.endsWith(".achievements")
                || permission.endsWith(".spawn")
                || permission.endsWith(".ping")
                || permission.endsWith(".list")) {
            return true;
        }

        if (permission.startsWith("fallnight.command.plot.")) {
            return true;
        }

        if (permission.startsWith("fallnight.command.gang.")) {
            return !permission.endsWith(".forcekick");
        }

        if ("fallnight.command.auction.sell".equalsIgnoreCase(permission)) {
            return true;
        }

        if ("fallnight.kit.starter".equalsIgnoreCase(permission)) {
            return true;
        }

        return player.getPermissionLevel() >= 2;
    }
}
