package xyz.fallnight.server.command.framework;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class LegacyMultiCommandHelp {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int PAGE_HEIGHT = 6;

    private LegacyMultiCommandHelp() {
    }

    public static void sendHelp(CommandSender sender, String commandName, List<HelpEntry> entries, Predicate<String> permissionCheck, List<String> args) {
        int pageNumber = resolvePageNumber(sender, args);
        if (pageNumber < 0) {
            return;
        }

        List<HelpEntry> visibleEntries = new ArrayList<>();
        visibleEntries.add(new HelpEntry("help", "get help for a command", ""));
        for (HelpEntry entry : entries) {
            if (entry.permission().isBlank() || permissionCheck.test(entry.permission())) {
                visibleEntries.add(entry);
            }
        }
        visibleEntries.sort(Comparator.comparing(HelpEntry::name, String.CASE_INSENSITIVE_ORDER));

        int totalPages = Math.max(1, (visibleEntries.size() + PAGE_HEIGHT - 1) / PAGE_HEIGHT);
        int boundedPage = Math.max(1, Math.min(totalPages, pageNumber));
        int startIndex = (boundedPage - 1) * PAGE_HEIGHT;
        int endIndex = Math.min(visibleEntries.size(), startIndex + PAGE_HEIGHT);

        StringBuilder message = new StringBuilder("§8§l<--§bFN§8--> \n§r§7§7 /")
                .append(commandName)
                .append("§r§7 help page §r§8(")
                .append(boundedPage)
                .append(" out of ")
                .append(totalPages)
                .append(")§r");
        for (int index = startIndex; index < endIndex; index++) {
            HelpEntry entry = visibleEntries.get(index);
            message.append("\n §r§8§l>§r§b /")
                    .append(commandName)
                    .append(' ')
                    .append(entry.name())
                    .append("§r§8 - §r§7")
                    .append(entry.description());
        }
        message.append("\n§r§8§l<--++-->⛏");
        sender.sendMessage(LEGACY.deserialize(message.toString()));
    }

    public static void sendUnknownSubcommand(CommandSender sender) {
        sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7That subcommand was not found."));
    }

    public static boolean isHelpToken(String token) {
        return token != null && (token.equalsIgnoreCase("help") || token.equals("?"));
    }

    private static int resolvePageNumber(CommandSender sender, List<String> args) {
        if (args == null || args.isEmpty()) {
            return 1;
        }
        String lastArgument = args.get(args.size() - 1);
        if (!lastArgument.matches("-?\\d+")) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a valid page number."));
            return -1;
        }
        int pageNumber = Integer.parseInt(lastArgument);
        return Math.max(1, pageNumber);
    }

    public record HelpEntry(String name, String description, String permission) {
    }
}
