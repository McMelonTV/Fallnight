package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

public final class ListCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final RankService rankService;

    public ListCommand(PermissionService permissionService, PlayerProfileService profileService, RankService rankService) {
        super("list", permissionService, "players");
        this.profileService = profileService;
        this.rankService = rankService;

        setDefaultExecutor((sender, context) -> {
            var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
            List<String> normalPlayers = new ArrayList<>();
            List<String> staffPlayers = new ArrayList<>();
            for (Player target : players) {
                if (!canSee(sender, target)) {
                    continue;
                }
                if (this.rankService.isStaff(profileService.getOrCreate(target))) {
                    staffPlayers.add(target.getUsername());
                } else {
                    normalPlayers.add(target.getUsername());
                }
            }
            normalPlayers.sort(String::compareToIgnoreCase);
            staffPlayers.sort(String::compareToIgnoreCase);

            StringBuilder builder = new StringBuilder("§8§l<--§bFN§8--> ")
                .append("\n§r§7 Fallnight online player list §r§8(")
                .append(normalPlayers.size() + staffPlayers.size())
                .append("/")
                .append(MinecraftServer.getConnectionManager().getOnlinePlayerCount())
                .append(" online)§r");
            if (!normalPlayers.isEmpty()) {
                builder.append("\n §r§8§l> §r§7§b")
                    .append(normalPlayers.size())
                    .append(" §7players: §b")
                    .append(String.join("§r§7, §r§b", normalPlayers));
            }
            if (!staffPlayers.isEmpty()) {
                builder.append("\n §r§8§l> §r§7§b")
                    .append(staffPlayers.size())
                    .append(" §7staff members: §b")
                    .append(String.join("§r§7, §r§b", staffPlayers));
            }
            builder.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LEGACY.deserialize(builder.toString()));
        });
    }

    private boolean canSee(CommandSender sender, Player target) {
        if (!(sender instanceof Player viewer)) {
            return true;
        }
        if (viewer.getUuid().equals(target.getUuid())) {
            return true;
        }
        return !readBoolean(profileService.getOrCreate(target), "vanish");
    }

    private static boolean readBoolean(xyz.fallnight.server.domain.user.UserProfile profile, String key) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return false;
    }

    @Override
    public String permission() {
        return "fallnight.command.list";
    }

    @Override
    public String summary() {
        return "list the online users";
    }

    @Override
    public String usage() {
        return "/list";
    }
}
