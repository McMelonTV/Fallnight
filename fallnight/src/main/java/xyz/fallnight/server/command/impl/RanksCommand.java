package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.NumberFormatter;
import java.util.Optional;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class RanksCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final MineRankService mineRankService;

    public RanksCommand(PermissionService permissionService, PlayerProfileService profileService, MineRankService mineRankService) {
        super("ranks", permissionService);
        this.profileService = profileService;
        this.mineRankService = mineRankService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            UserProfile profile = profileService.getOrCreate((Player) sender);
            StringBuilder builder = new StringBuilder("§8§l<--§bFN§8--> ")
                .append("\n§r§7§7 Available mines");
            for (MineRank mineRank : mineRankService.allRanks()) {
                long price = Math.round(mineRank.priceForPrestige(profile.getPrestige()));
                if (price <= 0L) {
                    builder.append("\n§r §a§l> §r")
                        .append(mineRank.getTag())
                        .append("§r§7 mine");
                } else if (mineRank.getId() > profile.getMineRank()) {
                    builder.append("\n§r §c§l> §r")
                        .append(mineRank.getTag())
                        .append("§r§7 mine §8[§b$")
                        .append(NumberFormatter.shortNumber(price))
                        .append("§r§8]");
                } else {
                    builder.append("\n§r §a§l> §r")
                        .append(mineRank.getTag())
                        .append("§r§7 mine §8[§b$")
                        .append(NumberFormatter.shortNumber(price))
                        .append("§r§8]");
                }
            }
            int nextPrestige = profile.getPrestige() + 1;
            builder.append("\n§r §c§l> §r§7§lPrestige ")
                .append(nextPrestige)
                .append(" §r§8[§b$")
                .append(NumberFormatter.shortNumber(xyz.fallnight.server.util.ProgressionMath.prestigePrice(nextPrestige)))
                .append("§r§l§8]")
                .append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LEGACY.deserialize(builder.toString()));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.ranks";
    }

    @Override
    public String summary() {
        return "see the mines";
    }

    @Override
    public String usage() {
        return "/ranks";
    }
}
