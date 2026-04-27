package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class BalanceCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;

    public BalanceCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("balance", permissionService, "bal", "mymoney", "seemoney");
        this.profileService = profileService;

        var playerArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            sendBalance(sender, profile.getUsername(), profile.getBalance(), profile.getPrestigePoints());
        });

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            UserProfile profile = profileService.findByUsername(targetName).orElse(null);
            if (profile == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }

            sendBalance(sender, profile.getUsername(), profile.getBalance(), profile.getPrestigePoints());
        }, playerArgument);
    }

    private void sendBalance(net.minestom.server.command.CommandSender sender, String username, double balance, long prestigePoints) {
        sender.sendMessage(LEGACY.deserialize(
            "§8§l<--§bFN§8--> "
                + "\n§r§7§b " + username + "§7's balance§r"
                + "\n§r §b§l> §r§7Money: §b$" + legacyMoney(balance) + "§r §8(§8$" + xyz.fallnight.server.util.NumberFormatter.shortNumberRounded(balance) + "§r§8)§r"
                + "\n§r §b§l> §r§7Prestige points: §b" + prestigePoints + "§m§oPP"
                + "\n§r§8§l<--++-->⛏"
        ));
    }

    private static String legacyMoney(double balance) {
        if (Math.rint(balance) == balance) {
            return Long.toString((long) balance);
        }
        return Double.toString(balance);
    }

    @Override
    public String permission() {
        return "fallnight.command.balance";
    }

    @Override
    public String summary() {
        return "see your balance";
    }

    @Override
    public String usage() {
        return "/balance [player]";
    }
}
