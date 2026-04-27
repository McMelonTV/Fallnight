package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class HelpCommand extends FallnightCommand {
    private static final int PAGE_SIZE = 10;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PermissionService permissionService;
    private final CommandManager commandManager;

    public HelpCommand(PermissionService permissionService, CommandManager commandManager) {
        super("help", permissionService, "help", "h", "?", "commands");
        this.permissionService = permissionService;
        this.commandManager = commandManager;

        var argsArgument = ArgumentType.StringArray("args");

        setDefaultExecutor((sender, context) -> sendCommandList(sender, 1));
        addSyntax((sender, context) -> handleArgs(sender, context.get(argsArgument)), argsArgument);
    }

    private void handleArgs(CommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            sendCommandList(sender, 1);
            return;
        }

        List<String> values = new ArrayList<>(Arrays.asList(args));
        int page = 1;
        String last = values.get(values.size() - 1);
        if (last.chars().allMatch(Character::isDigit)) {
            try {
                page = Math.max(1, Integer.parseInt(last));
                values.remove(values.size() - 1);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        if (values.isEmpty()) {
            sendCommandList(sender, page);
            return;
        }

        sendSingleCommandHelp(sender, String.join(" ", values).trim().toLowerCase(Locale.ROOT));
    }

    private void sendCommandList(CommandSender sender, int page) {
        List<Command> visibleCommands = commandManager.getCommands().stream()
            .filter(command -> canUse(sender, command))
            .sorted(Comparator.comparing(Command::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        int totalPages = Math.max(1, (visibleCommands.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(visibleCommands.size(), from + PAGE_SIZE);

        sender.sendMessage(LEGACY.deserialize("§8§l<--§bFN§8--> "));
        sender.sendMessage(LEGACY.deserialize("§r§7§7 Fallnight help page §r§8(" + currentPage + " out of " + totalPages + ")§r"));
        for (Command command : visibleCommands.subList(from, to)) {
            sender.sendMessage(LEGACY.deserialize(" §r§8§l>§r§b /" + command.getName() + "§r§8 - §r§7" + commandSummary(command)));
        }
        sender.sendMessage(LEGACY.deserialize("\n§r§8§l<--++-->⛏"));
    }

    private void sendSingleCommandHelp(CommandSender sender, String name) {
        Command command = commandManager.getCommand(name);
        if (command == null || !canUse(sender, command)) {
            sender.sendMessage(LEGACY.deserialize("§cNo help for " + name));
            return;
        }

        sender.sendMessage(LEGACY.deserialize("§e--------- §f Help: /" + command.getName() + "§e ---------"));
        sender.sendMessage(LEGACY.deserialize("§6Description: §f" + commandSummary(command)));
        String usage = commandUsage(command).replace("\n", "\n§f");
        sender.sendMessage(LEGACY.deserialize("§6Usage: §f" + usage));
    }

    private boolean canUse(CommandSender sender, Command command) {
        if (command instanceof FallnightCommand fallnightCommand) {
            return permissionService.hasPermission(sender, fallnightCommand.permission());
        }
        return command.getCondition() == null || command.getCondition().canUse(sender, command.getName());
    }

    private static String commandSummary(Command command) {
        if (command instanceof FallnightCommand fallnightCommand) {
            return fallnightCommand.summary();
        }
        return switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "nick" -> "change your nickname";
            case "nicklist" -> "nicklist";
            case "fly" -> "toggle flight mode";
            case "baltop", "topmoney" -> "see the leaderboard for money";
            case "leaderboard", "top" -> "see the leaderboards";
            case "size" -> "change your size";
            case "feed" -> "feed someone";
            case "feedall" -> "feed someone else";
            case "gangdescription" -> "set description";
            default -> "Available command.";
        };
    }

    private static String commandUsage(Command command) {
        if (command instanceof FallnightCommand fallnightCommand) {
            return fallnightCommand.usage();
        }
        return switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "nick" -> "/nick [nick|clear]";
            case "nicklist" -> "/nicklist";
            case "fly" -> "/fly [player]";
            case "baltop", "topmoney" -> "/baltop";
            case "leaderboard", "top" -> "/leaderboard";
            case "size" -> "/size [size]";
            case "feed" -> "/feed [player]";
            case "feedall" -> "/feedall";
            case "gangdescription" -> "/gangdescription <description>";
            default -> "/" + command.getName();
        };
    }

    @Override
    public String permission() {
        return "fallnight.command.help";
    }

    @Override
    public String summary() {
        return "Provides a list of commands";
    }

    @Override
    public String usage() {
        return "/help [page]";
    }
}
