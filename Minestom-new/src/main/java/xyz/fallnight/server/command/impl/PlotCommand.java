package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.LegacyMultiCommandHelp;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.AnvilInputService;
import xyz.fallnight.server.service.BookMenuService;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.PlotTeleportMenuService;
import xyz.fallnight.server.service.SpawnService;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PlotCommand extends FallnightCommand {
    private static final Map<String, String> SUBCOMMAND_PERMISSIONS = Map.ofEntries(
            Map.entry("claim", "fallnight.command.plot.claim"),
            Map.entry("c", "fallnight.command.plot.claim"),
            Map.entry("autoclaim", "fallnight.command.plot.autoclaim"),
            Map.entry("auto", "fallnight.command.plot.autoclaim"),
            Map.entry("a", "fallnight.command.plot.autoclaim"),
            Map.entry("teleport", "fallnight.command.plot.teleport"),
            Map.entry("tp", "fallnight.command.plot.teleport"),
            Map.entry("info", "fallnight.command.plot.info"),
            Map.entry("about", "fallnight.command.plot.info"),
            Map.entry("list", "fallnight.command.plot.list"),
            Map.entry("rename", "fallnight.command.plot.rename"),
            Map.entry("setname", "fallnight.command.plot.rename"),
            Map.entry("addmember", "fallnight.command.plot.addmember"),
            Map.entry("trust", "fallnight.command.plot.addmember"),
            Map.entry("add", "fallnight.command.plot.addmember"),
            Map.entry("removemember", "fallnight.command.plot.removemember"),
            Map.entry("untrust", "fallnight.command.plot.removemember"),
            Map.entry("remove", "fallnight.command.plot.removemember"),
            Map.entry("blockplayer", "fallnight.command.plot.blockplayer"),
            Map.entry("block", "fallnight.command.plot.blockplayer"),
            Map.entry("unblockplayer", "fallnight.command.plot.unblockplayer"),
            Map.entry("unblock", "fallnight.command.plot.unblockplayer"),
            Map.entry("unclaim", "fallnight.command.plot.unclaim"),
            Map.entry("clear", "fallnight.command.plot.clear"),
            Map.entry("transferownership", "fallnight.command.plot.transferownership"),
            Map.entry("transfer", "fallnight.command.plot.transferownership"),
            Map.entry("setowner", "fallnight.command.plot.transferownership")
    );
    private static final List<LegacyMultiCommandHelp.HelpEntry> HELP_ENTRIES = List.of(
            new LegacyMultiCommandHelp.HelpEntry("addmember", "add a member to a plot", "fallnight.command.plot.addmember"),
            new LegacyMultiCommandHelp.HelpEntry("autoclaim", "auto claim a plot", "fallnight.command.plot.autoclaim"),
            new LegacyMultiCommandHelp.HelpEntry("blockplayer", "block a player", "fallnight.command.plot.blockplayer"),
            new LegacyMultiCommandHelp.HelpEntry("claim", "claim an unoccupied plot as your own", "fallnight.command.plot.claim"),
            new LegacyMultiCommandHelp.HelpEntry("clear", "clear a plot", "fallnight.command.plot.clear"),
            new LegacyMultiCommandHelp.HelpEntry("info", "get info about a plot", "fallnight.command.plot.info"),
            new LegacyMultiCommandHelp.HelpEntry("list", "list someones plots", "fallnight.command.plot.list"),
            new LegacyMultiCommandHelp.HelpEntry("removemember", "remove a member from a plot", "fallnight.command.plot.removemember"),
            new LegacyMultiCommandHelp.HelpEntry("rename", "rename a plot", "fallnight.command.plot.rename"),
            new LegacyMultiCommandHelp.HelpEntry("teleport", "teleport to a plot", "fallnight.command.plot.teleport"),
            new LegacyMultiCommandHelp.HelpEntry("transferownership", "transfer ownership", "fallnight.command.plot.transferownership"),
            new LegacyMultiCommandHelp.HelpEntry("unblockplayer", "unblock a blocked player", "fallnight.command.plot.unblockplayer"),
            new LegacyMultiCommandHelp.HelpEntry("unclaim", "unclaim a plot", "fallnight.command.plot.unclaim")
    );

    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final PlotService plotService;
    private final SpawnService spawnService;
    private final PlotRuntimeModule plotRuntimeModule;
    private final BookMenuService bookMenuService = new BookMenuService();
    private final PagedTextMenuService pagedTextMenuService = new PagedTextMenuService();
    private final PlotTeleportMenuService plotTeleportMenuService = new PlotTeleportMenuService();
    private final AnvilInputService anvilInputService = new AnvilInputService();

    public PlotCommand(PermissionService permissionService, PlayerProfileService profileService, PlotService plotService, SpawnService spawnService, PlotRuntimeModule plotRuntimeModule) {
        super("plot", permissionService, "p");
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.plotService = plotService;
        this.spawnService = spawnService;
        this.plotRuntimeModule = plotRuntimeModule;
        setCondition((sender, commandString) -> {
            if (!permissionService.hasPermission(sender, permission())) {
                if (commandString != null) {
                    sender.sendMessage(CommandMessages.error("You do not have permission (" + permission() + ")."));
                }
                return false;
            }
            String[] parts = commandString == null ? new String[0] : commandString.trim().split("\\s+");
            if (parts.length < 2) {
                return true;
            }
            String permission = SUBCOMMAND_PERMISSIONS.get(parts[1].toLowerCase());
            if (permission == null || permissionService.hasPermission(sender, permission)) {
                return true;
            }
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§c§l> §r§7You don't have permission to execute that subcommand!"));
            return false;
        });

        var claimLiteral = ArgumentType.Literal("claim");
        var claimAliasLiteral = ArgumentType.Literal("c");
        var unclaimLiteral = ArgumentType.Literal("unclaim");
        var clearLiteral = ArgumentType.Literal("clear");
        var teleportLiteral = ArgumentType.Literal("teleport");
        var teleportAliasLiteral = ArgumentType.Literal("tp");
        var autoclaimLiteral = ArgumentType.Literal("autoclaim");
        var autoClaimAliasLiteral = ArgumentType.Literal("auto");
        var autoClaimShortAliasLiteral = ArgumentType.Literal("a");
        var infoLiteral = ArgumentType.Literal("info");
        var aboutLiteral = ArgumentType.Literal("about");
        var listLiteral = ArgumentType.Literal("list");
        var renameLiteral = ArgumentType.Literal("rename");
        var setNameLiteral = ArgumentType.Literal("setname");
        var addLiteral = ArgumentType.Literal("add");
        var addMemberLiteral = ArgumentType.Literal("addmember");
        var trustLiteral = ArgumentType.Literal("trust");
        var removeLiteral = ArgumentType.Literal("remove");
        var removeMemberLiteral = ArgumentType.Literal("removemember");
        var untrustLiteral = ArgumentType.Literal("untrust");
        var blockLiteral = ArgumentType.Literal("block");
        var blockPlayerLiteral = ArgumentType.Literal("blockplayer");
        var unblockLiteral = ArgumentType.Literal("unblock");
        var unblockPlayerLiteral = ArgumentType.Literal("unblockplayer");
        var transferOwnershipLiteral = ArgumentType.Literal("transferownership");
        var transferLiteral = ArgumentType.Literal("transfer");
        var setOwnerLiteral = ArgumentType.Literal("setowner");
        var ownerArgument = ArgumentType.Word("owner");
        var playerArgument = ArgumentType.Word("player");
        var xArgument = ArgumentType.Integer("x");
        var zArgument = ArgumentType.Integer("z");
        var nameArgument = ArgumentType.StringArray("text");
        var helpLiteral = ArgumentType.Literal("help");
        var questionLiteral = ArgumentType.Literal("?");
        var helpArguments = ArgumentType.StringArray("helpArgs");
        var unknownSubcommandArgument = ArgumentType.Word("subcommand");
        var unknownArguments = ArgumentType.StringArray("subcommandArgs");

        setDefaultExecutor((sender, context) -> sendHelpPage(sender, List.of()));

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = plotService.plotAtPosition(spawnService.spawn(), player.getPosition()).orElse(null);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error("Please stand on a plot."));
                return;
            }
            try {
                PlotService.ClaimResult result = plotService.claimPlotAt(player.getUsername(), coordinate);
                switch (result.status()) {
                    case SUCCESS ->
                            sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                    case ALREADY_CLAIMED ->
                            sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                    case INVALID_USERNAME ->
                            sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, claimLiteral);
        addSyntax((sender, context) -> executeClaimCurrent(sender), claimAliasLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            try {
                PlotService.ClaimResult result = plotService.claimPlotAt(player.getUsername(), coordinate);
                switch (result.status()) {
                    case SUCCESS ->
                            sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                    case ALREADY_CLAIMED ->
                            sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                    case INVALID_USERNAME ->
                            sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, claimLiteral, xArgument, zArgument);
        addSyntax((sender, context) -> executeClaimAt(sender, context.get(xArgument), context.get(zArgument)), claimAliasLiteral, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            try {
                PlotService.ClaimResult result = plotService.claimPlot(player.getUsername());
                switch (result.status()) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                        teleportToPlotHome(player, result.coordinate());
                    }
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                    case ALREADY_CLAIMED ->
                            sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                    case INVALID_USERNAME ->
                            sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, autoclaimLiteral);
        addSyntax((sender, context) -> executeAutoClaim(sender), autoClaimAliasLiteral);
        addSyntax((sender, context) -> executeAutoClaim(sender), autoClaimShortAliasLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            var ownedPlots = plotService.listOwnedPlots(player.getUsername());
            if (ownedPlots.isEmpty()) {
                sender.sendMessage(CommandMessages.error("You don't have any plots yet. Do §c/plots §r§7and claim one with §c/p claim§r§7."));
                return;
            }
            openTeleportMenu(player, ownedPlots, plotService.listAccessiblePlots(player.getUsername()));
        }, teleportLiteral);
        addSyntax((sender, context) -> executeTeleportMenu(sender), teleportAliasLiteral);

        addSyntax((sender, context) -> sender.sendMessage(CommandMessages.error("Please enter either both plot coordinates or no coordinates.")), teleportLiteral, ownerArgument);
        addSyntax((sender, context) -> sender.sendMessage(CommandMessages.error("Please enter either both plot coordinates or no coordinates.")), teleportAliasLiteral, ownerArgument);

        addSyntax((sender, context) -> executeInfoCurrent(sender), infoLiteral);
        addSyntax((sender, context) -> executeInfoCurrent(sender), aboutLiteral);
        addSyntax((sender, context) -> executeInfoAt(sender, context.get(xArgument), context.get(zArgument)), infoLiteral, xArgument, zArgument);
        addSyntax((sender, context) -> executeInfoAt(sender, context.get(xArgument), context.get(zArgument)), aboutLiteral, xArgument, zArgument);

        addSyntax((sender, context) -> executeListPlots(sender, null), listLiteral);
        addSyntax((sender, context) -> executeListPlots(sender, context.get(playerArgument)), listLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            openPlotNamePrompt(player, coordinate, admin, "Plot Rename");
        }, renameLiteral);
        addSyntax((sender, context) -> executeRenameCurrent(sender, "Plot Rename"), setNameLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            openPlotNamePrompt(player, coordinate, admin, "Plot Rename");
        }, renameLiteral, nameArgument);
        addSyntax((sender, context) -> executeRenameCurrent(sender, "Plot Rename", context.get(nameArgument)), setNameLiteral, nameArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            openPlotNamePrompt(player, coordinate, admin, "Plot Rename");
        }, renameLiteral, nameArgument, xArgument, zArgument);
        addSyntax((sender, context) -> executeRenameAt(sender, context.get(xArgument), context.get(zArgument), "Plot Rename", context.get(nameArgument)), setNameLiteral, nameArgument, xArgument, zArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            openPlotNamePrompt(player, coordinate, admin, "Plot Rename");
        }, renameLiteral, xArgument, zArgument, nameArgument);
        addSyntax((sender, context) -> executeRenameAt(sender, context.get(xArgument), context.get(zArgument), "Plot Rename", context.get(nameArgument)), setNameLiteral, xArgument, zArgument, nameArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = resolveKnownPlayerName(context.get(playerArgument)).orElse(null);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to your plot members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on your plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = resolveKnownPlayerName(context.get(playerArgument)).orElse(null);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to plot " + coordinate.key() + " members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on this plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addLiteral, xArgument, zArgument, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = resolveKnownPlayerName(context.get(playerArgument)).orElse(null);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to plot " + coordinate.key() + " members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on this plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = resolveKnownPlayerName(context.get(playerArgument)).orElse(null);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was never connected."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to your plot members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on your plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addMemberLiteral, playerArgument);
        addSyntax((sender, context) -> executeAddMemberCurrent(sender, context.get(playerArgument)), trustLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to plot " + coordinate.key() + " members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on this plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addMemberLiteral, xArgument, zArgument, playerArgument);
        addSyntax((sender, context) -> executeAddMemberAt(sender, context.get(playerArgument), context.get(xArgument), context.get(zArgument)), trustLiteral, xArgument, zArgument, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, target) : plotService.addMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Added " + target + " to plot " + coordinate.key() + " members."));
                        notifyPlotMembershipAdded(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                    case USER_BLOCKED ->
                            sender.sendMessage(CommandMessages.error("That player is blocked on this plot."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, addMemberLiteral, playerArgument, xArgument, zArgument);
        addSyntax((sender, context) -> executeAddMemberAt(sender, context.get(playerArgument), context.get(xArgument), context.get(zArgument)), trustLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.removeMemberAnyAt(coordinate, target) : plotService.removeMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Removed " + target + " from your plot members."));
                        notifyPlotMembershipRemoved(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, removeLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.removeMemberAnyAt(coordinate, target) : plotService.removeMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Removed " + target + " from plot " + coordinate.key() + " members."));
                        notifyPlotMembershipRemoved(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, removeLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.removeMemberAnyAt(coordinate, target) : plotService.removeMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Removed " + target + " from your plot members."));
                        notifyPlotMembershipRemoved(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, removeMemberLiteral, playerArgument);
        addSyntax((sender, context) -> executeRemoveMemberCurrent(sender, context.get(playerArgument)), untrustLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.removeMemberAnyAt(coordinate, target) : plotService.removeMemberAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Removed " + target + " from plot " + coordinate.key() + " members."));
                        notifyPlotMembershipRemoved(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not a member."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update members."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, removeMemberLiteral, playerArgument, xArgument, zArgument);
        addSyntax((sender, context) -> executeRemoveMemberAt(sender, context.get(playerArgument), context.get(xArgument), context.get(zArgument)), untrustLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.blockUserAnyAt(coordinate, target) : plotService.blockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Blocked " + target + " on your plot."));
                        notifyPlotBlocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot block yourself."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, blockLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.blockUserAnyAt(coordinate, target) : plotService.blockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Blocked " + target + " on plot " + coordinate.key() + "."));
                        notifyPlotBlocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot block yourself."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, blockLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.blockUserAnyAt(coordinate, target) : plotService.blockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Blocked " + target + " on your plot."));
                        notifyPlotBlocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot block yourself."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, blockPlayerLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.blockUserAnyAt(coordinate, target) : plotService.blockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Blocked " + target + " on plot " + coordinate.key() + "."));
                        notifyPlotBlocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot block yourself."));
                    case ALREADY_SET -> sender.sendMessage(CommandMessages.info(target + " is already blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, blockPlayerLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.unblockUserAnyAt(coordinate, target) : plotService.unblockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Unblocked " + target + " on your plot."));
                        notifyPlotUnblocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unblockLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.unblockUserAnyAt(coordinate, target) : plotService.unblockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Unblocked " + target + " on plot " + coordinate.key() + "."));
                        notifyPlotUnblocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unblockLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.unblockUserAnyAt(coordinate, target) : plotService.unblockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Unblocked " + target + " on your plot."));
                        notifyPlotUnblocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot yet. Use /plot claim."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unblockPlayerLiteral, playerArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.unblockUserAnyAt(coordinate, target) : plotService.unblockUserAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Unblocked " + target + " on plot " + coordinate.key() + "."));
                        notifyPlotUnblocked(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case NOT_SET -> sender.sendMessage(CommandMessages.info(target + " is not blocked."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    default -> sender.sendMessage(CommandMessages.error("Could not update blocked users."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unblockPlayerLiteral, playerArgument, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.unclaimPlotAnyAt(coordinate) : plotService.unclaimPlotAt(player.getUsername(), coordinate);
                if (status == PlotService.UpdateStatus.NO_HOME_PLOT) {
                    sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own this plot."));
                    return;
                }
                plotRuntimeModule.clearPlot(coordinate);
                sender.sendMessage(CommandMessages.success("Unclaimed your home plot."));
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unclaimLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            boolean admin = isAdmin(player);
            try {
                PlotService.UpdateStatus status = admin ? plotService.unclaimPlotAnyAt(coordinate) : plotService.unclaimPlotAt(player.getUsername(), coordinate);
                if (status == PlotService.UpdateStatus.NO_HOME_PLOT) {
                    sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    return;
                }
                plotRuntimeModule.clearPlot(coordinate);
                sender.sendMessage(CommandMessages.success("Unclaimed plot " + coordinate.key() + "."));
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, unclaimLiteral, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            try {
                PlotService.UpdateStatus status = admin ? plotService.clearPlotAnyAt(coordinate) : plotService.clearPlotAt(player.getUsername(), coordinate);
                if (status == PlotService.UpdateStatus.NO_HOME_PLOT) {
                    sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own this plot."));
                    return;
                }
                plotRuntimeModule.clearPlot(coordinate);
                sender.sendMessage(CommandMessages.success("You cleared the plot at §b" + coordinate.key().replace(":", "§8:§b") + "§r§7."));
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, clearLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            boolean admin = isAdmin(player);
            try {
                PlotService.UpdateStatus status = admin ? plotService.clearPlotAnyAt(coordinate) : plotService.clearPlotAt(player.getUsername(), coordinate);
                if (status == PlotService.UpdateStatus.NO_HOME_PLOT) {
                    sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    return;
                }
                plotRuntimeModule.clearPlot(coordinate);
                sender.sendMessage(CommandMessages.success("You cleared the plot at §b" + coordinate.key().replace(":", "§8:§b") + "§r§7."));
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, clearLiteral, xArgument, zArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            boolean admin = isAdmin(player);
            PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = admin ? plotService.transferPlotAnyAt(coordinate, target) : plotService.transferPlotAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Transferred your home plot ownership to " + target + "."));
                        notifyPlotOwnershipTransferred(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own a plot to transfer."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You already own this plot."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("That player is already at their plot cap."));
                    default -> sender.sendMessage(CommandMessages.error("Could not transfer ownership."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, transferOwnershipLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = PlotCoordinate.of(context.get(xArgument), context.get(zArgument));
            String target = context.get(playerArgument);
            boolean admin = isAdmin(player);
            try {
                PlotService.UpdateStatus status = admin ? plotService.transferPlotAnyAt(coordinate, target) : plotService.transferPlotAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Transferred plot " + coordinate.key() + " ownership to " + target + "."));
                        notifyPlotOwnershipTransferred(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You already own this plot."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("That player is already at their plot cap."));
                    default -> sender.sendMessage(CommandMessages.error("Could not transfer ownership."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, transferOwnershipLiteral, xArgument, zArgument, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = plotService.ownedCoordinateAt(player.getUsername(), spawnService.spawn(), player.getPosition()).orElse(null);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error("You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = plotService.transferPlotAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Transferred your home plot ownership to " + target + "."));
                        notifyPlotOwnershipTransferred(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error("You do not own a plot to transfer."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You already own this plot."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("That player is already at their plot cap."));
                    default -> sender.sendMessage(CommandMessages.error("Could not transfer ownership."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, transferLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!ensurePlotWorld(player)) {
                return;
            }
            PlotCoordinate coordinate = plotService.ownedCoordinateAt(player.getUsername(), spawnService.spawn(), player.getPosition()).orElse(null);
            if (coordinate == null) {
                sender.sendMessage(CommandMessages.error("You are not standing on one of your plots."));
                return;
            }
            String target = context.get(playerArgument);
            try {
                PlotService.UpdateStatus status = plotService.transferPlotAt(player.getUsername(), coordinate, target);
                switch (status) {
                    case SUCCESS -> {
                        sender.sendMessage(CommandMessages.success("Transferred your home plot ownership to " + target + "."));
                        notifyPlotOwnershipTransferred(target, coordinate);
                    }
                    case NO_HOME_PLOT ->
                            sender.sendMessage(CommandMessages.error("You do not own a plot to transfer."));
                    case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                    case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You already own this plot."));
                    case AT_PLOT_CAP ->
                            sender.sendMessage(CommandMessages.error("That player is already at their plot cap."));
                    default -> sender.sendMessage(CommandMessages.error("Could not transfer ownership."));
                }
            } catch (UncheckedIOException exception) {
                sender.sendMessage(CommandMessages.error("Failed to save plot data."));
            }
        }, setOwnerLiteral, playerArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            int x = Math.max(-1000, Math.min(1000, context.get(xArgument)));
            int z = Math.max(-1000, Math.min(1000, context.get(zArgument)));
            PlotCoordinate coordinate = PlotCoordinate.of(x, z);
            teleportToPlotHome(player, coordinate);
            sender.sendMessage(CommandMessages.success("Teleported to plot " + coordinate.key() + "."));
        }, teleportLiteral, xArgument, zArgument);
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            int x = Math.max(-1000, Math.min(1000, context.get(xArgument)));
            int z = Math.max(-1000, Math.min(1000, context.get(zArgument)));
            PlotCoordinate coordinate = PlotCoordinate.of(x, z);
            teleportToPlotHome(player, coordinate);
            sender.sendMessage(CommandMessages.success("Teleported to plot " + coordinate.key() + "."));
        }, teleportAliasLiteral, xArgument, zArgument);
        addSyntax((sender, context) -> sendHelpPage(sender, List.of()), helpLiteral);
        addSyntax((sender, context) -> sendHelpPage(sender, List.of()), questionLiteral);
        addSyntax((sender, context) -> sendHelpPage(sender, java.util.Arrays.asList(context.get(helpArguments))), helpLiteral, helpArguments);
        addSyntax((sender, context) -> sendHelpPage(sender, java.util.Arrays.asList(context.get(helpArguments))), questionLiteral, helpArguments);
        addSyntax((sender, context) -> handleFallbackSubcommand(sender, context.get(unknownSubcommandArgument), List.of()), unknownSubcommandArgument);
        addSyntax((sender, context) -> handleFallbackSubcommand(sender, context.get(unknownSubcommandArgument), java.util.Arrays.asList(context.get(unknownArguments))), unknownSubcommandArgument, unknownArguments);
    }

    private void sendHelpPage(net.minestom.server.command.CommandSender sender, List<String> args) {
        LegacyMultiCommandHelp.sendHelp(sender, "plot", HELP_ENTRIES, permission -> permissionService.hasPermission(sender, permission), args);
    }

    private void handleFallbackSubcommand(net.minestom.server.command.CommandSender sender, String subcommand, List<String> args) {
        if (LegacyMultiCommandHelp.isHelpToken(subcommand)) {
            sendHelpPage(sender, args);
            return;
        }
        LegacyMultiCommandHelp.sendUnknownSubcommand(sender);
    }

    private static String ownedTeleportTitle(PlotService.PlotEntryView plot) {
        if (plot.entry().getName() == null || plot.entry().getName().isBlank()) {
            return "§r§7Unnamed plot at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r§7";
        }
        return "§r§7Plot §b" + plot.entry().getName() + " §r§7at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r§7";
    }

    private static String memberTeleportTitle(PlotService.PlotEntryView plot) {
        if (plot.entry().getName() == null || plot.entry().getName().isBlank()) {
            return "§r§8Unnamed plot at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r";
        }
        return "§r§8Plot §b" + plot.entry().getName() + " §r§8at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r";
    }

    private static String displayName(String name) {
        if (name == null || name.isBlank()) {
            return "(unnamed)";
        }
        return name;
    }

    private static String joinOrNone(java.util.List<String> names) {
        return names == null || names.isEmpty() ? "none" : String.join(", ", names);
    }

    private void executeClaimCurrent(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        PlotCoordinate coordinate = plotService.plotAtPosition(spawnService.spawn(), player.getPosition()).orElse(null);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error("Please stand on a plot."));
            return;
        }
        try {
            PlotService.ClaimResult result = plotService.claimPlotAt(player.getUsername(), coordinate);
            switch (result.status()) {
                case SUCCESS -> sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                case AT_PLOT_CAP -> sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                case ALREADY_CLAIMED -> sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
            }
        } catch (UncheckedIOException exception) {
            sender.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void executeClaimAt(net.minestom.server.command.CommandSender sender, int x, int z) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        PlotCoordinate coordinate = PlotCoordinate.of(x, z);
        try {
            PlotService.ClaimResult result = plotService.claimPlotAt(player.getUsername(), coordinate);
            switch (result.status()) {
                case SUCCESS -> sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                case AT_PLOT_CAP -> sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                case ALREADY_CLAIMED -> sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
            }
        } catch (UncheckedIOException exception) {
            sender.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void executeAutoClaim(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        try {
            PlotService.ClaimResult result = plotService.claimPlot(player.getUsername());
            switch (result.status()) {
                case SUCCESS -> {
                    sender.sendMessage(CommandMessages.success("You claimed the plot at §b" + result.coordinate().key().replace(":", "§8:§b") + "§r§7."));
                    teleportToPlotHome(player, result.coordinate());
                }
                case AT_PLOT_CAP -> sender.sendMessage(CommandMessages.error("You have reached your max plots of §c" + plotService.maxPlotsFor(player.getUsername()) + "§r§7."));
                case ALREADY_CLAIMED -> sender.sendMessage(CommandMessages.error("Someone has already claimed this plot!"));
                case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Could not resolve your profile for plot claiming."));
            }
        } catch (UncheckedIOException exception) {
            sender.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void executeTeleportMenu(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        var ownedPlots = plotService.listOwnedPlots(player.getUsername());
        if (ownedPlots.isEmpty()) {
            sender.sendMessage(CommandMessages.error("You don't have any plots yet. Do §c/plots §r§7and claim one with §c/p claim§r§7."));
            return;
        }
        openTeleportMenu(player, ownedPlots, plotService.listAccessiblePlots(player.getUsername()));
    }

    private void executeTeleportOwner(net.minestom.server.command.CommandSender sender, String owner) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        Optional<PlotService.PlotEntryView> home = plotService.getHomePlot(owner);
        if (home.isEmpty()) {
            sender.sendMessage(CommandMessages.error("Could not teleport you to that plot."));
            return;
        }
        teleportToPlotHome(player, home.get().coordinate());
        sender.sendMessage(CommandMessages.success("You have been teleported to plot §a" + home.get().coordinate().key().replace(":", "§8:§a") + "§r§7."));
    }

    private void executeInfoCurrent(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        PlotCoordinate coordinate = plotService.plotAtPosition(spawnService.spawn(), player.getPosition()).orElse(null);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error("Please stand on a plot."));
            return;
        }
        openPlotInfo(player, coordinate);
    }

    private void executeInfoAt(net.minestom.server.command.CommandSender sender, int x, int z) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        openPlotInfo(player, PlotCoordinate.of(x, z));
    }

    private void executeListPlots(net.minestom.server.command.CommandSender sender, String targetUsername) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        String target = targetUsername == null || targetUsername.isBlank() ? player.getUsername() : targetUsername;
        var ownedPlots = plotService.listOwnedPlots(target);
        if (ownedPlots.isEmpty()) {
            sender.sendMessage(CommandMessages.error(target.equalsIgnoreCase(player.getUsername()) ? "You don't have any plots." : "This player does not have any plots."));
            return;
        }
        int max = plotService.maxPlotsFor(target);
        List<String> lines = new ArrayList<>();
        lines.add("§8§l<--§bFN§8--> ");
        lines.add("§r§7§7 Plot list for §r§b" + target + "§r §8(" + ownedPlots.size() + " out of " + max + " max plots)");
        for (PlotService.PlotEntryView plot : ownedPlots) {
            String title = plot.entry().getName() == null || plot.entry().getName().isBlank()
                ? "Unnamed plot at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r"
                : "Plot §b" + plot.entry().getName() + "§r§7 at §b" + plot.coordinate().key().replace(":", "§8:§b") + "§r";
            lines.add("§r§8 > §r§7" + title);
        }
        lines.add("§r§8§l<--++-->⛏");
        bookMenuService.open(player, "Plots", lines);
    }

    private void executeRenameCurrent(net.minestom.server.command.CommandSender sender, String title) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        boolean admin = isAdmin(player);
        PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
            return;
        }
        openPlotNamePrompt(player, coordinate, admin, title);
    }

    private void executeRenameCurrent(net.minestom.server.command.CommandSender sender, String title, String[] nameParts) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        boolean admin = isAdmin(player);
        PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
            return;
        }
        applyPlotName(player, coordinate, admin, String.join(" ", nameParts));
    }

    private void executeRenameAt(net.minestom.server.command.CommandSender sender, int x, int z, String title, String[] nameParts) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        boolean admin = isAdmin(player);
        PlotCoordinate coordinate = PlotCoordinate.of(x, z);
        if (nameParts == null || nameParts.length == 0) {
            openPlotNamePrompt(player, coordinate, admin, title);
            return;
        }
        applyPlotName(player, coordinate, admin, String.join(" ", nameParts));
    }

    private void executeAddMemberCurrent(net.minestom.server.command.CommandSender sender, String target) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        boolean admin = isAdmin(player);
        PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
            return;
        }
        applyAddMember(sender, player, coordinate, target, admin, true);
    }

    private void executeAddMemberAt(net.minestom.server.command.CommandSender sender, String target, int x, int z) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        applyAddMember(sender, player, PlotCoordinate.of(x, z), target, isAdmin(player), false);
    }

    private void executeRemoveMemberCurrent(net.minestom.server.command.CommandSender sender, String target) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        boolean admin = isAdmin(player);
        PlotCoordinate coordinate = currentMutableCoordinate(player, admin);
        if (coordinate == null) {
            sender.sendMessage(CommandMessages.error(admin ? "Please stand on a plot." : "You are not standing on one of your plots."));
            return;
        }
        applyRemoveMember(sender, player, coordinate, target, admin, true);
    }

    private void executeRemoveMemberAt(net.minestom.server.command.CommandSender sender, String target, int x, int z) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        if (!ensurePlotWorld(player)) {
            return;
        }
        applyRemoveMember(sender, player, PlotCoordinate.of(x, z), target, isAdmin(player), false);
    }

    private void openPlotInfo(Player player, PlotCoordinate coordinate) {
        Optional<PlotService.PlotEntryView> currentPlot = plotService.getPlot(coordinate);
        if (currentPlot.isPresent()) {
            PlotService.PlotEntryView view = currentPlot.get();
            bookMenuService.open(player, "Plot Info", List.of(
                "§8§l<--§bFN§8--> ",
                "§r§7§7 Info for plot §b" + coordinate.key().replace(":", "§8:§b") + "§r",
                "§r§8 > §r§7Plot name: §r§b" + (view.entry().getName().isBlank() ? "§r§cunnamed" : displayName(view.entry().getName())),
                "§r§8 > §r§7Owner: §r§b" + view.entry().getOwner(),
                "§r§8 > §r§7Members: §r§b" + (view.entry().getMembers().isEmpty() ? "§cnone" : String.join("§r§7,§r§b ", view.entry().getMembers())),
                "§r§8§l<--++-->⛏"
            ));
            return;
        }
        player.sendMessage(CommandMessages.info("Current grid coordinate: " + coordinate.key()));
        player.sendMessage(CommandMessages.info("This plot is not claimed."));
    }

    private void applyAddMember(net.minestom.server.command.CommandSender sender, Player player, PlotCoordinate coordinate, String target, boolean admin, boolean currentPlot) {
        String resolvedTarget = resolveKnownPlayerName(target).orElse(null);
        if (resolvedTarget == null) {
            sender.sendMessage(CommandMessages.error("That player was never connected."));
            return;
        }
        try {
            PlotService.UpdateStatus status = admin ? plotService.addMemberAnyAt(coordinate, resolvedTarget) : plotService.addMemberAt(player.getUsername(), coordinate, resolvedTarget);
            switch (status) {
                case SUCCESS -> {
                    sender.sendMessage(CommandMessages.success((currentPlot ? "Added " + resolvedTarget + " to your plot members." : "Added " + resolvedTarget + " to plot " + coordinate.key() + " members.")));
                    notifyPlotMembershipAdded(resolvedTarget, coordinate);
                }
                case NO_HOME_PLOT -> sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : currentPlot ? "You do not own a plot yet. Use /plot claim." : "You do not own that plot."));
                case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot add yourself."));
                case USER_BLOCKED -> sender.sendMessage(CommandMessages.error(currentPlot ? "That player is blocked on your plot." : "That player is blocked on this plot."));
                case ALREADY_SET -> sender.sendMessage(CommandMessages.info(resolvedTarget + " is already a member."));
                case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                default -> sender.sendMessage(CommandMessages.error("Could not update members."));
            }
        } catch (UncheckedIOException exception) {
            sender.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void applyRemoveMember(net.minestom.server.command.CommandSender sender, Player player, PlotCoordinate coordinate, String target, boolean admin, boolean currentPlot) {
        String resolvedTarget = resolveKnownPlayerName(target).orElse(null);
        if (resolvedTarget == null) {
            sender.sendMessage(CommandMessages.error("That player was never connected."));
            return;
        }
        try {
            PlotService.UpdateStatus status = admin ? plotService.removeMemberAnyAt(coordinate, resolvedTarget) : plotService.removeMemberAt(player.getUsername(), coordinate, resolvedTarget);
            switch (status) {
                case SUCCESS -> {
                    sender.sendMessage(CommandMessages.success((currentPlot ? "Removed " + resolvedTarget + " from your plot members." : "Removed " + resolvedTarget + " from plot " + coordinate.key() + " members.")));
                    notifyPlotMembershipRemoved(resolvedTarget, coordinate);
                }
                case NO_HOME_PLOT -> sender.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : currentPlot ? "You do not own a plot yet. Use /plot claim." : "You do not own that plot."));
                case NOT_SET -> sender.sendMessage(CommandMessages.info(resolvedTarget + " is not a member."));
                case INVALID_USERNAME -> sender.sendMessage(CommandMessages.error("Choose a valid player name."));
                default -> sender.sendMessage(CommandMessages.error("Could not update members."));
            }
        } catch (UncheckedIOException exception) {
            sender.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void openPlotNamePrompt(Player player, PlotCoordinate coordinate, boolean admin, String title) {
        String currentName = plotService.getPlot(coordinate)
                .map(view -> view.entry().getName())
                .orElse("");
        anvilInputService.open(player, title, "§bEnter plot name", currentName == null ? "" : currentName, (source, input) ->
                applyPlotName(source, coordinate, admin, input)
        );
    }

    private void applyPlotName(Player player, PlotCoordinate coordinate, boolean admin, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            player.sendMessage(CommandMessages.error("Please enter a plot name."));
            return;
        }
        try {
            PlotService.UpdateStatus status = admin ? plotService.setPlotNameAnyAt(coordinate, name) : plotService.setPlotNameAt(player.getUsername(), coordinate, name);
            if (status == PlotService.UpdateStatus.NO_HOME_PLOT) {
                player.sendMessage(CommandMessages.error(admin ? "That plot is not claimed." : "You do not own that plot."));
                return;
            }
            player.sendMessage(CommandMessages.success("Set plot " + coordinate.key() + " name to '" + name + "'."));
            player.closeInventory();
        } catch (UncheckedIOException exception) {
            player.sendMessage(CommandMessages.error("Failed to save plot data."));
        }
    }

    private void teleportToPlotHome(Player player, PlotCoordinate coordinate) {
        var homePos = plotService.plotHomePosition(coordinate, spawnService.spawn(), player.getPosition());
        spawnService.teleportWithinSpawnInstance(player, homePos);
    }

    private void openTeleportMenu(Player player, List<PlotService.PlotEntryView> ownedPlots, List<PlotService.PlotEntryView> accessiblePlots) {
        List<PlotTeleportMenuService.Option> options = new ArrayList<>();
        for (PlotService.PlotEntryView plot : ownedPlots) {
            options.add(new PlotTeleportMenuService.Option(
                    plot.coordinate(),
                    ownedTeleportTitle(plot),
                    "§8[§bOwner§8]"
            ));
        }
        for (PlotService.PlotEntryView plot : accessiblePlots) {
            if (player.getUsername().equalsIgnoreCase(plot.entry().getOwner())) {
                continue;
            }
            options.add(new PlotTeleportMenuService.Option(
                    plot.coordinate(),
                    memberTeleportTitle(plot),
                    "§8[§bMember§8]"
            ));
        }
        plotTeleportMenuService.open(player, "§bplot teleport menu", options, (source, coordinate) -> {
            teleportToPlotHome(source, coordinate);
            source.sendMessage(CommandMessages.success("You have been teleported to plot §a" + coordinate.key().replace(":", "§8:§a") + "§r§7."));
        });
    }

    private boolean isAdmin(Player player) {
        return AdminModeService.isEnabled(profileService.getOrCreate(player));
    }

    private boolean ensurePlotWorld(Player player) {
        if (player.getInstance() == spawnService.instance()) {
            return true;
        }
        player.sendMessage(CommandMessages.error("Please execute this command in the plot world."));
        return false;
    }

    private void notifyPlotMembershipAdded(String targetUsername, PlotCoordinate coordinate) {
        Player target = ModerationCommandSupport.findOnlinePlayerIgnoreCase(targetUsername);
        PlotService.PlotEntryView plot = plotService.getPlot(coordinate).orElse(null);
        if (target == null || plot == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been added to §b" + plot.entry().getOwner() + "§7's plot at §b" + coordinate.key().replace(":", "§8:§b") + "§7."));
    }

    private void notifyPlotMembershipRemoved(String targetUsername, PlotCoordinate coordinate) {
        Player target = ModerationCommandSupport.findOnlinePlayerIgnoreCase(targetUsername);
        PlotService.PlotEntryView plot = plotService.getPlot(coordinate).orElse(null);
        if (target == null || plot == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been added to §b" + plot.entry().getOwner() + "§7's plot at §b" + coordinate.key().replace(":", "§8:§b") + "§7."));
    }

    private void notifyPlotBlocked(String targetUsername, PlotCoordinate coordinate) {
        Player target = ModerationCommandSupport.findOnlinePlayerIgnoreCase(targetUsername);
        PlotService.PlotEntryView plot = plotService.getPlot(coordinate).orElse(null);
        if (target == null || plot == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been blocked from §b" + plot.entry().getOwner() + "§7's plot at §b" + coordinate.key().replace(":", "§8:§b") + "§r§7."));
    }

    private void notifyPlotUnblocked(String targetUsername, PlotCoordinate coordinate) {
        Player target = ModerationCommandSupport.findOnlinePlayerIgnoreCase(targetUsername);
        PlotService.PlotEntryView plot = plotService.getPlot(coordinate).orElse(null);
        if (target == null || plot == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been unblocked from §b" + plot.entry().getOwner() + "§7's plot at §b" + coordinate.key().replace(":", "§8:§b") + "§r§7."));
    }

    private void notifyPlotOwnershipTransferred(String targetUsername, PlotCoordinate coordinate) {
        Player target = ModerationCommandSupport.findOnlinePlayerIgnoreCase(targetUsername);
        PlotService.PlotEntryView plot = plotService.getPlot(coordinate).orElse(null);
        if (target == null || plot == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been added to §b" + plot.entry().getOwner() + "§7's plot at §b" + coordinate.key().replace(":", "§8:§b") + "§7."));
    }

    private PlotCoordinate currentMutableCoordinate(Player player, boolean admin) {
        if (admin) {
            return plotService.plotAtPosition(spawnService.spawn(), player.getPosition()).orElse(null);
        }
        return plotService.ownedCoordinateAt(player.getUsername(), spawnService.spawn(), player.getPosition()).orElse(null);
    }

    private Optional<String> resolveKnownPlayerName(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Player online = ModerationCommandSupport.findOnlinePlayerByPrefixIgnoreCase(input);
        if (online != null) {
            return Optional.of(online.getUsername());
        }
        return profileService.findByUsername(input).map(UserProfile::getUsername);
    }

    @Override
    public String permission() {
        return "fallnight.command.plot";
    }

    @Override
    public String summary() {
        return "the main plot command";
    }

    @Override
    public String usage() {
        return "/plot [subcommand]";
    }
}
