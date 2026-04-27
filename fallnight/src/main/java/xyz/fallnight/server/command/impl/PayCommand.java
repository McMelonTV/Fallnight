package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PayMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class PayCommand extends FallnightCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private final PlayerProfileService profileService;
    private final PayMenuService payMenuService;

    public PayCommand(PermissionService permissionService, PlayerProfileService profileService, PayMenuService payMenuService) {
        super("pay", permissionService);
        this.profileService = profileService;
        this.payMenuService = payMenuService;

        var playerArgument = ArgumentType.Word("player");
        var amountArgument = ArgumentType.Double("amount").min(0D);

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                sendUsage(sender);
                return;
            }
            payMenuService.open((Player) sender);
        });
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            String targetName = context.get(playerArgument);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            UserProfile targetProfile = target != null
                ? profileService.getOrCreate(target)
                : profileService.findOfflineByUsername(targetName).orElse(null);
            if (targetProfile == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }

            Player source = (Player) sender;
            double amount = wholeDollarAmount(context.get(amountArgument));
            if (amount < 0D) {
                sender.sendMessage(CommandMessages.error("Please enter a valid prestige amount of money."));
                return;
            }
            UserProfile sourceProfile = profileService.getOrCreate(source);
            if (amount > 0D && !sourceProfile.withdraw(amount)) {
                sender.sendMessage(CommandMessages.error("You don't have enough money for this transaction."));
                return;
            }

            if (amount > 0D) {
                targetProfile.deposit(amount);
            }
            profileService.save(sourceProfile);
            if (targetProfile != sourceProfile) {
                profileService.save(targetProfile);
            }

            sender.sendMessage(CommandMessages.info("You paid §e$" + MONEY_FORMAT.format(amount) + "§r§7 to §e" + targetProfile.getUsername() + "§r§7."));
            if (target != null && target != source) {
                target.sendMessage(CommandMessages.info("§e" + source.getUsername() + " §r§7paid you §e$" + MONEY_FORMAT.format(amount) + "§r§7."));
            }
        }, playerArgument, amountArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.pay";
    }

    @Override
    public String summary() {
        return "pay someone";
    }

    @Override
    public String usage() {
        return "/pay [player] [amount]";
    }

    private static double wholeDollarAmount(double requestedAmount) {
        return BigDecimal.valueOf(requestedAmount)
            .setScale(0, RoundingMode.DOWN)
            .doubleValue();
    }
}
