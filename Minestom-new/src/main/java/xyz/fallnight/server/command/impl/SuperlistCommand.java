package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

public final class SuperlistCommand extends FallnightCommand {
    private final PlayerProfileService profileService;

    public SuperlistCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("superlist", permissionService);
        this.profileService = profileService;

        setDefaultExecutor((sender, context) -> {
            var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
            if (players.isEmpty()) {
                sender.sendMessage(CommandMessages.info("Superlist: no players online."));
                return;
            }
            List<String> normal = new ArrayList<>();
            List<String> vanished = new ArrayList<>();
            for (Player player : players) {
                if (readBoolean(profileService.getOrCreate(player), "vanish")) {
                    vanished.add(player.getUsername());
                } else {
                    normal.add(player.getUsername());
                }
            }
            sender.sendMessage(CommandMessages.info("Players: " + String.join(", ", normal)));
            if (!vanished.isEmpty()) {
                sender.sendMessage(CommandMessages.info("Vanished players: " + String.join(", ", vanished)));
            }
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.superlist";
    }

    @Override
    public String summary() {
        return "list ALL the online users";
    }

    @Override
    public String usage() {
        return "/superlist";
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
}
