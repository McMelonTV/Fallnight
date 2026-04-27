package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.BookMenuService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class StatsCommand extends FallnightCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final RankService rankService;
    private final BookMenuService bookMenuService = new BookMenuService();

    public StatsCommand(PermissionService permissionService, PlayerProfileService profileService, MineService mineService, RankService rankService) {
        super("stats", permissionService, "profile");
        this.profileService = profileService;
        this.mineService = mineService;
        this.rankService = rankService;

        var playerArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            sendStats(sender, profileService.getOrCreate(player));
        });

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            UserProfile profile = profileService.findByUsername(targetName).orElse(null);
            if (profile == null) {
                sender.sendMessage(CommandMessages.error("Player '" + targetName + "' was not found."));
                return;
            }

            sendStats(sender, profile);
        }, playerArgument);
    }

    private void sendStats(net.minestom.server.command.CommandSender sender, UserProfile profile) {
        MineDefinition mine = mineService.find(profile.getMineRank()).orElse(null);
        String mineName = mine == null ? "unassigned" : mine.getName();
        if (sender instanceof Player player) {
            bookMenuService.open(player, "Stats", List.of(
                "§8§l<--§bFN§8-->§r",
                "§b " + profile.getUsername() + "§r§7's stats§r",
                "§b > §r§7Rank: §b" + rankService.displayPrefix(profile) + "§r",
                "§b > §r§7Mine: §b" + mineName + "§r",
                "§b > §r§7Gang: §b" + (profile.getGangId() == null || profile.getGangId().isBlank() ? "None" : profile.getGangId()) + "§r",
                "§b > §r§7Prestige: §b" + toRoman(profile.getPrestige()) + "§r",
                "§b > §r§7Money: §b$" + legacyMoney(profile.getBalance()) + "§r",
                "§b > §r§7Prestige points: §b" + profile.getPrestigePoints() + "§opp§r",
                "§b > §r§7Kills: §b" + profile.getKills() + "§r",
                "§b > §r§7Deaths: §b" + profile.getDeaths() + "§r",
                "§b > §r§7K/D Ratio: §b" + String.format(java.util.Locale.ROOT, "%.2f", kdr(profile)) + "§r",
                "§b > §r§7Votes: §b" + readLong(profile, "votes") + "§r",
                "§b > §r§7Total money earned: §b" + readDouble(profile, "totalEarnedMoney", profile.getBalance()) + "§r",
                "§b > §r§7Total blocks mined: §b" + profile.getMinedBlocks() + "§r",
                "§b > §r§7Join date: §b" + formatDate(readLong(profile, "joinDate")) + "§r",
                "§b > §r§7Last seen: §b" + formatLastSeen(profile) + "§r",
                "§b > §r§7Total online time: §b" + formatDuration(readLong(profile, "totalOnlineTime")) + "§r",
                "§r§8§l<--++-->⛏"
            ));
            return;
        }
        sender.sendMessage(CommandMessages.info("Stats for " + profile.getUsername() + ":"));
        sender.sendMessage(CommandMessages.info("Rank: " + profile.getMineRank() + " | Mine: " + mineName));
        sender.sendMessage(CommandMessages.info("Gang: " + (profile.getGangId() == null || profile.getGangId().isBlank() ? "None" : profile.getGangId())));
        sender.sendMessage(CommandMessages.info("Prestige: " + profile.getPrestige() + " | Prestige points: " + profile.getPrestigePoints()));
        sender.sendMessage(CommandMessages.info("Money: $" + MONEY_FORMAT.format(profile.getBalance())));
        sender.sendMessage(CommandMessages.info("Kills: " + profile.getKills() + " | Deaths: " + profile.getDeaths() + " | K/D: " + String.format(java.util.Locale.ROOT, "%.2f", kdr(profile))));
        sender.sendMessage(CommandMessages.info("Votes: " + readLong(profile, "votes") + " | Total money earned: $" + MONEY_FORMAT.format(readDouble(profile, "totalEarnedMoney", profile.getBalance()))));
        sender.sendMessage(CommandMessages.info("Total blocks mined: " + profile.getMinedBlocks()));
        sender.sendMessage(CommandMessages.info("Join date: " + formatDate(readLong(profile, "joinDate"))));
        sender.sendMessage(CommandMessages.info("Last seen: " + formatLastSeen(profile)));
        sender.sendMessage(CommandMessages.info("Total online time: " + formatDuration(readLong(profile, "totalOnlineTime"))));
    }

    private static double kdr(UserProfile profile) {
        return profile.getKills() / (double) Math.max(1L, profile.getDeaths());
    }

    private static String formatDate(long epochSeconds) {
        return epochSeconds <= 0L ? "unknown" : DATE_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private static String formatLastSeen(UserProfile profile) {
        return readBoolean(profile, "online", false) ? "now" : formatDate(readLong(profile, "lastSeen"));
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours + "h " + minutes + "m " + secs + "s";
    }

    private static String legacyMoney(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String toRoman(int value) {
        int number = Math.max(1, value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                builder.append(numerals[i]);
                number -= values[i];
            }
        }
        return builder.toString();
    }

    private static long readLong(UserProfile profile, String key) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static double readDouble(UserProfile profile, String key, double fallback) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean readBoolean(UserProfile profile, String key, boolean fallback) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return fallback;
    }

    @Override
    public String permission() {
        return "fallnight.command.stats";
    }

    @Override
    public String summary() {
        return "see someones stats";
    }

    @Override
    public String usage() {
        return "/stats [player]";
    }
}
