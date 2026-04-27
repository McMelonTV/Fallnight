package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.LegacyMultiCommandHelp;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.domain.gang.GangMemberRole;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.AnvilInputService;
import xyz.fallnight.server.service.BookMenuService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class GangCommand extends FallnightCommand {
    private static final double CREATE_COST = 50_000d;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy 'at' hh:mm")
            .withZone(ZoneId.systemDefault());
    private static final List<LegacyMultiCommandHelp.HelpEntry> HELP_ENTRIES = List.of(
            new LegacyMultiCommandHelp.HelpEntry("accept", "accept an invite", "fallnight.command.gang.accept"),
            new LegacyMultiCommandHelp.HelpEntry("ally", "ally with another gang", "fallnight.command.gang.ally"),
            new LegacyMultiCommandHelp.HelpEntry("create", "create a gang", "fallnight.command.gang.create"),
            new LegacyMultiCommandHelp.HelpEntry("demote", "demote a gang member", "fallnight.command.gang.demote"),
            new LegacyMultiCommandHelp.HelpEntry("disband", "disband a gang", "fallnight.command.gang.disband"),
            new LegacyMultiCommandHelp.HelpEntry("enemy", "become an enemy with a gang", "fallnight.command.gang.enemy"),
            new LegacyMultiCommandHelp.HelpEntry("forcekick", "forcefully kick a gang member", "fallnight.command.gang.forcekick"),
            new LegacyMultiCommandHelp.HelpEntry("info", "display info for a gang", "fallnight.command.gang.info"),
            new LegacyMultiCommandHelp.HelpEntry("invite", "invite someone to your gang", "fallnight.command.gang.invite"),
            new LegacyMultiCommandHelp.HelpEntry("kick", "kick a gang member", "fallnight.command.gang.kick"),
            new LegacyMultiCommandHelp.HelpEntry("leave", "leave your gang", "fallnight.command.gang.leave"),
            new LegacyMultiCommandHelp.HelpEntry("list", "list of gangs", "fallnight.command.gang.list"),
            new LegacyMultiCommandHelp.HelpEntry("promote", "promote a gang member", "fallnight.command.gang.promote"),
            new LegacyMultiCommandHelp.HelpEntry("setdescription", "set your gang description", "fallnight.command.gang.setdescription")
    );

    private final GangService gangService;
    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final AchievementService achievementService;
    private final PagedTextMenuService pagedTextMenuService = new PagedTextMenuService();
    private final BookMenuService bookMenuService = new BookMenuService();
    private final AnvilInputService anvilInputService = new AnvilInputService();

    public GangCommand(PermissionService permissionService, GangService gangService, PlayerProfileService profileService) {
        super("gang", permissionService, "g", "clan");
        this.permissionService = permissionService;
        this.gangService = gangService;
        this.profileService = profileService;
        this.achievementService = new AchievementService(profileService);

        var createLiteral = ArgumentType.Literal("create");
        var createAliasLiteral = ArgumentType.Literal("make");
        var createShortAliasLiteral = ArgumentType.Literal("c");
        var infoLiteral = ArgumentType.Literal("info");
        var listLiteral = ArgumentType.Literal("list");
        var leaveLiteral = ArgumentType.Literal("leave");
        var disbandLiteral = ArgumentType.Literal("disband");
        var inviteLiteral = ArgumentType.Literal("invite");
        var acceptLiteral = ArgumentType.Literal("accept");
        var kickLiteral = ArgumentType.Literal("kick");
        var promoteLiteral = ArgumentType.Literal("promote");
        var demoteLiteral = ArgumentType.Literal("demote");
        var allyLiteral = ArgumentType.Literal("ally");
        var allyWithLiteral = ArgumentType.Literal("allywith");
        var enemyLiteral = ArgumentType.Literal("enemy");
        var enemyWithLiteral = ArgumentType.Literal("enemywith");
        var setDescriptionLiteral = ArgumentType.Literal("setdescription");
        var setDescLiteral = ArgumentType.Literal("setdesc");
        var forceKickLiteral = ArgumentType.Literal("forcekick");

        var nameArgument = ArgumentType.Word("name");
        var playerArgument = ArgumentType.Word("player");
        var textArgument = ArgumentType.StringArray("text");
        var helpLiteral = ArgumentType.Literal("help");
        var questionLiteral = ArgumentType.Literal("?");
        var helpArguments = ArgumentType.StringArray("helpArgs");
        var unknownSubcommandArgument = ArgumentType.Word("subcommand");
        var unknownArguments = ArgumentType.StringArray("subcommandArgs");

        setDefaultExecutor((sender, context) -> sendHelpPage(sender, List.of()));

        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.create", () -> handleCreate(sender, context.get(nameArgument))), createLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.create", () -> handleCreate(sender, context.get(nameArgument))), createAliasLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.create", () -> handleCreate(sender, context.get(nameArgument))), createShortAliasLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.list", () -> sendGangList(sender)), listLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.info", () -> showNamedGang(sender, context.get(nameArgument))), infoLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.info", () -> showOwnGang(sender, context)), infoLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.leave", () -> handleLeave(sender)), leaveLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.disband", () -> handleDisband(sender, null)), disbandLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.disband", () -> handleDisband(sender, context.get(nameArgument))), disbandLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.invite", () -> handleInvite(sender, context.get(playerArgument))), inviteLiteral, playerArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.accept", () -> handleAccept(sender, context.get(nameArgument))), acceptLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.kick", () -> handleKick(sender, context.get(playerArgument))), kickLiteral, playerArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.promote", () -> handlePromote(sender, context.get(playerArgument))), promoteLiteral, playerArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.demote", () -> handleDemote(sender, context.get(playerArgument))), demoteLiteral, playerArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.ally", () -> handleAlly(sender, context.get(nameArgument))), allyLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.ally", () -> handleAlly(sender, context.get(nameArgument))), allyWithLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.enemy", () -> handleEnemy(sender, context.get(nameArgument))), enemyLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.enemy", () -> handleEnemy(sender, context.get(nameArgument))), enemyWithLiteral, nameArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.setdescription", () -> openDescriptionPrompt(sender)), setDescriptionLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.setdescription", () -> openDescriptionPrompt(sender)), setDescLiteral);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.setdescription", () -> handleDescription(sender, String.join(" ", context.get(textArgument)).trim())), setDescriptionLiteral, textArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.setdescription", () -> handleDescription(sender, String.join(" ", context.get(textArgument)).trim())), setDescLiteral, textArgument);
        addSyntax((sender, context) -> withSubcommandPermission(sender, "fallnight.command.gang.forcekick", () -> handleForceKick(sender, context.get(nameArgument), context.get(playerArgument))), forceKickLiteral, nameArgument, playerArgument);
        addSyntax((sender, context) -> sendHelpPage(sender, List.of()), helpLiteral);
        addSyntax((sender, context) -> sendHelpPage(sender, List.of()), questionLiteral);
        addSyntax((sender, context) -> sendHelpPage(sender, java.util.Arrays.asList(context.get(helpArguments))), helpLiteral, helpArguments);
        addSyntax((sender, context) -> sendHelpPage(sender, java.util.Arrays.asList(context.get(helpArguments))), questionLiteral, helpArguments);
        addSyntax((sender, context) -> handleFallbackSubcommand(sender, context.get(unknownSubcommandArgument), List.of()), unknownSubcommandArgument);
        addSyntax((sender, context) -> handleFallbackSubcommand(sender, context.get(unknownSubcommandArgument), java.util.Arrays.asList(context.get(unknownArguments))), unknownSubcommandArgument, unknownArguments);
    }

    private void sendHelpPage(net.minestom.server.command.CommandSender sender, List<String> args) {
        LegacyMultiCommandHelp.sendHelp(sender, "gang", HELP_ENTRIES, permission -> permissionService.hasPermission(sender, permission), args);
    }

    private void handleFallbackSubcommand(net.minestom.server.command.CommandSender sender, String subcommand, List<String> args) {
        if (LegacyMultiCommandHelp.isHelpToken(subcommand)) {
            sendHelpPage(sender, args);
            return;
        }
        LegacyMultiCommandHelp.sendUnknownSubcommand(sender);
    }

    private static String membersByRole(Gang gang, GangMemberRole role) {
        StringJoiner joiner = new StringJoiner("§7, §b");
        for (String member : gang.getMembers()) {
            if (gang.roleOf(member) == role) {
                joiner.add(member);
            }
        }
        return joiner.length() == 0 ? "" : joiner.toString();
    }

    private static String alliesList(Gang gang) {
        return gang.getAllies().isEmpty() ? "" : String.join("§7, §b", gang.getAllies());
    }

    private static Player onlinePlayer(String username) {
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

    private static String defaultValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @Override
    public String permission() {
        return "fallnight.command.gang";
    }

    @Override
    public String summary() {
        return "the main gang command";
    }

    @Override
    public String usage() {
        return "/gang [subcommand]";
    }

    private void withSubcommandPermission(net.minestom.server.command.CommandSender sender, String permission, Runnable action) {
        if (!permissionService.hasPermission(sender, permission)) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You don't have permission to execute that subcommand!"));
            return;
        }
        action.run();
    }

    private void handleCreate(net.minestom.server.command.CommandSender sender, String name) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        var profile = profileService.getOrCreate(player);
        if (gangService.findGangForUser(player.getUsername()).isPresent()) {
            sender.sendMessage(CommandMessages.error("You are already in a gang."));
            return;
        }
        if (profile.getBalance() < CREATE_COST) {
            sender.sendMessage(CommandMessages.error("You need §b50000$ §7in order to create a gang."));
            return;
        }
        if (name == null || name.isBlank()) {
            sender.sendMessage(CommandMessages.error("Please enter a name for your gang."));
            return;
        }
        var result = gangService.createGang(name, player.getUsername());
        switch (result.status()) {
            case SUCCESS -> {
                profile.withdraw(CREATE_COST);
                profileService.save(profile);
                achievementService.onGangJoined(player, profile);
                sender.sendMessage(CommandMessages.success("You created a gang named §b" + result.gang().getName() + "§r§7."));
            }
            case INVALID_NAME -> {
                if (name.length() < 3 || name.length() > 15) {
                    sender.sendMessage(CommandMessages.error("A gang name should be at least §b3§r§7 characters long and cannot be longer than §b15§r§7 characters."));
                } else {
                    sender.sendMessage(CommandMessages.error("A gang name must only contain alphanumeric characters."));
                }
            }
            case ALREADY_IN_GANG ->
                    sender.sendMessage(CommandMessages.error("You are already in a gang. Use /gang leave first."));
            case GANG_ALREADY_EXISTS ->
                    sender.sendMessage(CommandMessages.error("A gang with that name already exists."));
            default -> sender.sendMessage(CommandMessages.error("Could not create gang right now."));
        }
    }

    private void sendGangList(net.minestom.server.command.CommandSender sender) {
        List<Gang> gangs = gangService.allGangs();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("§r§fHere is a list of all gangs on the server.");
        for (Gang gang : gangs) {
            lines.add("§b > §7" + gang.getName());
        }
        pagedTextMenuService.open(player, "§bGang list", lines);
    }

    private void showNamedGang(net.minestom.server.command.CommandSender sender, String name) {
        Gang gang = gangService.findByName(name).orElse(null);
        if (gang == null) {
            sender.sendMessage(CommandMessages.error("No such gang exists."));
            return;
        }
        sendGangInfo(sender, gang);
    }

    private void showOwnGang(net.minestom.server.command.CommandSender sender, net.minestom.server.command.builder.CommandContext context) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        Gang gang = gangService.findGangForUser(player.getUsername()).orElse(null);
        if (gang == null) {
            openNoGangUi(player);
            return;
        }
        sendGangInfo(player, gang);
    }

    private void openNoGangUi(Player player) {
        bookMenuService.open(player, "Gang", List.of(
                "§6Gang Commands",
                "§7create <name>",
                "§7list | info [name]",
                "§7invite <player> | accept <name>",
                "§7kick/promote/demote <player>",
                "§7ally/enemy <name>",
                "§7setdescription",
                "§7Use /gang list to browse gangs"
        ));
    }

    private void handleLeave(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        var result = gangService.leaveGang(((Player) sender).getUsername());
        switch (result.status()) {
            case SUCCESS -> {
                broadcastToGang(result.gang(), "§r§b§l> §r§b" + ((Player) sender).getUsername() + " §r§7 has left the gang.", null);
                sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7 You have left the gang."));
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case LEADER_CANNOT_LEAVE_WITH_MEMBERS ->
                    sender.sendMessage(CommandMessages.error("You cannot leave while being leader."));
            default -> sender.sendMessage(CommandMessages.error("Could not leave gang right now."));
        }
    }

    private void handleDisband(net.minestom.server.command.CommandSender sender, String targetGangName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        boolean adminMode = AdminModeService.isEnabled(profileService.getOrCreate(player));
        var result = gangService.disbandGang(player.getUsername(), targetGangName, adminMode);
        switch (result.status()) {
            case SUCCESS -> sender.sendMessage(CommandMessages.success("Your gang has been disbanded."));
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case GANG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("No such gang exists."));
            case NOT_GANG_LEADER ->
                    sender.sendMessage(CommandMessages.error("Only the gang leader can disband the gang."));
            default -> sender.sendMessage(CommandMessages.error("Could not disband gang right now."));
        }
    }

    private void handleInvite(net.minestom.server.command.CommandSender sender, String playerName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        Player target = onlinePlayer(playerName);
        if (target == null) {
            sender.sendMessage(CommandMessages.error("That player is not online."));
            return;
        }
        var result = gangService.invitePlayer(player.getUsername(), target.getUsername());
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You have invited §b" + target.getUsername() + "§r§7 to your gang."));
                target.sendMessage(CommandMessages.success("§b" + result.gang().getName() + "§r§7 has invited you, do §b/gang accept " + result.gang().getName() + "§7 to accept the invite."));
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_OFFICER ->
                    sender.sendMessage(CommandMessages.error("You must be a officer or above to invite a player."));
            case TARGET_ALREADY_IN_GANG ->
                    sender.sendMessage(CommandMessages.error("That player is already in your gang."));
            case INVITE_ALREADY_EXISTS ->
                    sender.sendMessage(CommandMessages.error("That player has already been invited."));
            default -> sender.sendMessage(CommandMessages.error("Could not invite that player."));
        }
    }

    private void handleAccept(net.minestom.server.command.CommandSender sender, String gangName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        var result = gangService.acceptInvite(player.getUsername(), gangName);
        switch (result.status()) {
            case SUCCESS -> {
                achievementService.onGangJoined(player, profileService.getOrCreate(player));
                sender.sendMessage(CommandMessages.success("You have joined §b" + result.gang().getName() + "§r§7."));
                broadcastToGang(result.gang(), "§r§b§l> §r§b" + player.getUsername() + "§r§7 has joined the gang.", null);
            }
            case ALREADY_IN_GANG -> sender.sendMessage(CommandMessages.error("You are already in a gang."));
            case GANG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("That gang does not exist."));
            case NOT_INVITED -> sender.sendMessage(CommandMessages.error("You were not invited to that gang."));
            case GANG_FULL -> sender.sendMessage(CommandMessages.error("That gang is full!."));
            default -> sender.sendMessage(CommandMessages.error("Could not join that gang."));
        }
    }

    private void handleKick(net.minestom.server.command.CommandSender sender, String targetName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player actor = (Player) sender;
        var result = gangService.kickMember(actor.getUsername(), targetName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You have kicked §b" + result.subject() + "§r§7 from your gang."));
                Player target = onlinePlayer(result.subject());
                if (target != null) {
                    target.sendMessage(CommandMessages.success("You have been kicked from your gang."));
                }
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_OFFICER ->
                    sender.sendMessage(CommandMessages.error("You must be a officer or above to kick a gang member."));
            case TARGET_NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("That player is not in the gang."));
            case ONLY_LEADER_CAN_KICK_OFFICER ->
                    sender.sendMessage(CommandMessages.error("Only the leader can kick an officer."));
            case TARGET_IS_LEADER -> sender.sendMessage(CommandMessages.error("You cannot kick the leader."));
            default -> sender.sendMessage(CommandMessages.error("Could not kick that player."));
        }
    }

    private void handlePromote(net.minestom.server.command.CommandSender sender, String targetName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        var result = gangService.promoteMember(((Player) sender).getUsername(), targetName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You have promoted " + result.subject() + "."));
                sendRoleUpdate(result.gang(), result.subject(), true);
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_GANG_LEADER ->
                    sender.sendMessage(CommandMessages.error("You must be the leader to promote a gang member."));
            case TARGET_NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("That player is not in the gang."));
            case CANNOT_PROMOTE -> sender.sendMessage(CommandMessages.error("You cannot promote that member further."));
            default -> sender.sendMessage(CommandMessages.error("Could not promote that player."));
        }
    }

    private void handleDemote(net.minestom.server.command.CommandSender sender, String targetName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        var result = gangService.demoteMember(((Player) sender).getUsername(), targetName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You have demoted " + result.subject() + "."));
                sendRoleUpdate(result.gang(), result.subject(), false);
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_GANG_LEADER ->
                    sender.sendMessage(CommandMessages.error("You must be the leader to demote a gang member."));
            case TARGET_NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("That player is not in the gang."));
            case ALREADY_LOWEST_RANK ->
                    sender.sendMessage(CommandMessages.error("You cannot demote a recruit, kick them instead."));
            case CANNOT_DEMOTE -> sender.sendMessage(CommandMessages.error("You cannot demote yourself."));
            default -> sender.sendMessage(CommandMessages.error("Could not demote that player."));
        }
    }

    private void handleAlly(net.minestom.server.command.CommandSender sender, String gangName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        var result = gangService.ally(player.getUsername(), gangName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You are now allied with " + gangName + "."));
                Player targetLeader = onlinePlayer(result.subject());
                if (targetLeader != null) {
                    targetLeader.sendMessage(CommandMessages.info("You are now allied with " + result.gang().getName() + "."));
                }
            }
            case ALLY_REQUEST_SENT -> {
                sender.sendMessage(CommandMessages.success("You have asked to ally with " + gangName + "."));
                Player targetLeader = onlinePlayer(result.subject());
                if (targetLeader != null) {
                    targetLeader.sendMessage(CommandMessages.info(result.gang().getName() + " has asked to ally with you. Use /gang ally " + result.gang().getName() + " to accept."));
                }
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_GANG_LEADER -> sender.sendMessage(CommandMessages.error("You must be the leader to ally."));
            case GANG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("That gang does not exist."));
            case CANNOT_TARGET_SELF -> sender.sendMessage(CommandMessages.error("You cannot ally with your own gang."));
            case ALREADY_ALLIED -> sender.sendMessage(CommandMessages.error("You are already allied with that gang."));
            case TARGET_LEADER_OFFLINE ->
                    sender.sendMessage(CommandMessages.error("The leader of that gang is offline. Wait for them to come online to ally."));
            default -> sender.sendMessage(CommandMessages.error("Could not process that ally request."));
        }
    }

    private void handleEnemy(net.minestom.server.command.CommandSender sender, String gangName) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        var result = gangService.enemy(player.getUsername(), gangName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You are now enemies with " + gangName + "."));
                Player targetLeader = onlinePlayer(result.subject());
                if (targetLeader != null) {
                    targetLeader.sendMessage(CommandMessages.info("You are now enemies with " + result.gang().getName() + "."));
                }
            }
            case NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_GANG_LEADER ->
                    sender.sendMessage(CommandMessages.error("You must be the leader to enemy another gang."));
            case GANG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("That gang does not exist."));
            case ALREADY_ENEMIES ->
                    sender.sendMessage(CommandMessages.error("You are already enemies with that gang."));
            default -> sender.sendMessage(CommandMessages.error("Could not update enemy status."));
        }
    }

    private void handleDescription(net.minestom.server.command.CommandSender sender, String description) {
        if (!ensurePlayer(sender)) {
            return;
        }
        applyDescription((Player) sender, description);
    }

    private void openDescriptionPrompt(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }
        openDescriptionPrompt((Player) sender);
    }

    private void openDescriptionPrompt(Player player) {
        anvilInputService.open(player, "Gang Description", "§bEnter description", "", this::applyDescription);
    }

    private void applyDescription(Player player, String description) {
        String sanitized = description == null ? "" : description.trim();
        var result = gangService.setDescription(player.getUsername(), sanitized);
        switch (result.status()) {
            case SUCCESS ->
                    player.sendMessage(CommandMessages.success("You set your gang description to §b" + result.gang().getDescription() + "§r§7."));
            case NOT_IN_GANG -> player.sendMessage(CommandMessages.error("You are not in a gang."));
            case NOT_GANG_LEADER ->
                    player.sendMessage(CommandMessages.error("You must be the leader of a gang to set its description."));
            default -> player.sendMessage(CommandMessages.error("Could not update your gang description."));
        }
    }

    private void handleForceKick(net.minestom.server.command.CommandSender sender, String gangName, String targetName) {
        var result = gangService.forceKick(gangName, targetName);
        switch (result.status()) {
            case SUCCESS -> {
                sender.sendMessage(CommandMessages.success("You have kicked " + result.subject() + " from the " + result.gang().getName() + " gang."));
                Player target = onlinePlayer(result.subject());
                if (target != null) {
                    target.sendMessage(CommandMessages.info("You have been kicked from the " + result.gang().getName() + " gang."));
                }
            }
            case GANG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("That gang could not be found."));
            case INVALID_PLAYER -> sender.sendMessage(CommandMessages.error("That player has never connected."));
            case TARGET_NOT_IN_GANG -> sender.sendMessage(CommandMessages.error("That player is not in that gang."));
            case TARGET_IS_LEADER -> sender.sendMessage(CommandMessages.error("You cannot kick the leader."));
            default -> sender.sendMessage(CommandMessages.error("Could not force kick that player."));
        }
    }

    private void sendGangInfo(net.minestom.server.command.CommandSender sender, Gang gang) {
        Instant createdAt = gang.getCreationDate() == null ? Instant.now() : gang.getCreationDate();
        String string = "§8§l<--§bFN§8--> \n§r§7§7 Info for gang §b" + gang.getName() + "§r"
                + "\n§r§8 > §r§7ID: §b" + gang.getId()
                + "\n§r§8 > §r§7Leader: §b" + defaultValue(gang.leader(), "")
                + "\n§r§8 > §r§7Description: §b" + defaultValue(gang.getDescription(), "")
                + "\n§r§8 > §r§7Date of creation: §b" + CREATED_FORMAT.format(createdAt)
                + "\n§r§8 > §r§7Officers: §b" + membersByRole(gang, GangMemberRole.OFFICER)
                + "\n§r§8 > §r§7Members: §b" + membersByRole(gang, GangMemberRole.MEMBER)
                + "\n§r§8 > §r§7Recruits: §b" + membersByRole(gang, GangMemberRole.RECRUIT)
                + "\n§r§8 > §r§7Allies: §b" + alliesList(gang)
                + "\n§r§8§l<--++-->⛏";
        sender.sendMessage(LEGACY.deserialize(string));
    }

    private void sendRoleUpdate(Gang gang, String username, boolean promoted) {
        Player target = onlinePlayer(username);
        if (target == null) {
            return;
        }
        GangMemberRole role = gang.roleOf(username);
        if (role == null) {
            return;
        }
        target.sendMessage(CommandMessages.info("You have been " + (promoted ? "promoted" : "demoted") + " to " + role.name().toLowerCase() + "."));
    }

    private void broadcastToGang(Gang gang, String message, String exceptUsername) {
        for (String member : gang.getMembers()) {
            if (member.equalsIgnoreCase(exceptUsername)) {
                continue;
            }
            Player target = onlinePlayer(member);
            if (target != null) {
                target.sendMessage(LEGACY.deserialize(message));
            }
        }
    }
}
