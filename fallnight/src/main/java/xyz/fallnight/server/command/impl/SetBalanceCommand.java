package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class SetBalanceCommand extends FallnightCommand {
    public static final String PERMISSION = "fallnight.command.setbalance";
    public static final String USAGE = "/setbalance <player> <amount>";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;

    public SetBalanceCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("setbalance", permissionService, "setmoney");
        this.profileService = profileService;

        var playerArgument = ArgumentType.Word("player");
        var amountArgument = ArgumentType.Double("amount");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> applySetBalance(
            sender,
            profileService,
            context.get(playerArgument),
            context.get(amountArgument)
        ), playerArgument, amountArgument);
    }

    static void applySetBalance(CommandSender sender, PlayerProfileService profileService, String targetName, double requestedAmount) {
        UserProfile profile = profileService.findByUsername(targetName).orElse(null);
        if (profile == null) {
            sender.sendMessage(CommandMessages.error("That player was never connected."));
            return;
        }

        double appliedAmount = wholeDollarAmount(requestedAmount);
        profile.setBalance(appliedAmount);
        profileService.save(profile);

        sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Set §b" + profile.getUsername() + "§7's balance to §b$" + (long) appliedAmount + "§7."));

        Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
        if (target != null && target != sender) {
            target.sendMessage(LEGACY.deserialize("§b§l> §r§7Your balance has been changed to §b$" + (long) appliedAmount + "§7."));
        }
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public String summary() {
        return "set someones balance";
    }

    @Override
    public String usage() {
        return USAGE;
    }

    private static double wholeDollarAmount(double requestedAmount) {
        return BigDecimal.valueOf(requestedAmount)
            .setScale(0, RoundingMode.DOWN)
            .doubleValue();
    }
}
