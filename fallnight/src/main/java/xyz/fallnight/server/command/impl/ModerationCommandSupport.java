package xyz.fallnight.server.command.impl;

import java.time.Duration;
import java.util.Arrays;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

final class ModerationCommandSupport {
    private ModerationCommandSupport() {
    }

    static String actorName(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUsername();
        }
        return "Console";
    }

    static String reasonOrDefault(String[] parts) {
        if (parts == null || parts.length == 0) {
            return "No reason provided.";
        }
        String reason = String.join(" ", parts).trim();
        if (reason.isEmpty()) {
            return "No reason provided.";
        }
        return reason;
    }

    static String reasonOrDefault(String[] parts, int offset) {
        if (parts == null || offset >= parts.length) {
            return "No reason provided.";
        }
        String reason = String.join(" ", Arrays.copyOfRange(parts, offset, parts.length)).trim();
        if (reason.isEmpty()) {
            return "No reason provided.";
        }
        return reason;
    }

    static Player findOnlinePlayerIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        Player exact = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username);
        if (exact != null) {
            return exact;
        }

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }

    static Player findOnlinePlayerByPrefixIgnoreCase(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        Player exact = findOnlinePlayerIgnoreCase(username);
        if (exact != null) {
            return exact;
        }

        String normalized = username.trim().toLowerCase();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().toLowerCase().startsWith(normalized)) {
                return player;
            }
        }
        return null;
    }

    static String renderDuration(Duration duration) {
        return xyz.fallnight.server.util.ModerationTimeFormatter.remaining(duration);
    }
}
