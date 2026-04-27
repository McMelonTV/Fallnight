package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.LotteryMenuService;
import xyz.fallnight.server.service.LotteryService;
import xyz.fallnight.server.util.NumberFormatter;
import java.util.Comparator;
import java.util.Map;
import net.minestom.server.entity.Player;

public final class LotteryCommand extends FallnightCommand {
    private final LotteryService lotteryService;
    private final LotteryMenuService lotteryMenuService;

    public LotteryCommand(PermissionService permissionService, LotteryService lotteryService, LotteryMenuService lotteryMenuService) {
        super("lottery", permissionService);
        this.lotteryService = lotteryService;
        this.lotteryMenuService = lotteryMenuService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            lotteryMenuService.open((Player) sender);
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.lottery";
    }

    @Override
    public String summary() {
        return "open the lottery";
    }

    @Override
    public String usage() {
        return "/lottery";
    }

    private void sendStatus(net.minestom.server.command.CommandSender sender) {
        LotteryService.LotteryStatus status = lotteryService.status();
        sender.sendMessage(CommandMessages.info(
            "Lottery jackpot: " + NumberFormatter.currency(status.jackpotPool())
                + " | tickets: " + status.totalTickets()
                + " | entrants: " + status.uniqueEntrants()
        ));
        sender.sendMessage(CommandMessages.info(
            "Next draw in: " + formatDuration(status.remainingDrawSeconds())
        ));
        sender.sendMessage(CommandMessages.info(
            "Ticket price: " + NumberFormatter.currency(status.ticketPrice())
                + " | jackpot contribution: " + NumberFormatter.currency(status.jackpotContribution())
                + " per ticket"
        ));

        if (status.ticketsByUsername().isEmpty()) {
            sender.sendMessage(CommandMessages.info("No tickets sold in this round. Use /lottery buy <amount>."));
            return;
        }

        sender.sendMessage(CommandMessages.info("Top entrants:"));
        status.ticketsByUsername().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .forEach(entry -> sender.sendMessage(CommandMessages.info("- " + entry.getKey() + ": " + entry.getValue() + " ticket(s)")));
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + remSeconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + remSeconds + "s";
        }
        return remSeconds + "s";
    }
}
