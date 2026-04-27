package xyz.fallnight.server.command;

import xyz.fallnight.server.AppConfigLoader;
import xyz.fallnight.server.AppConfigWriter;
import xyz.fallnight.server.ServerConfig;
import xyz.fallnight.server.WorldAccessService;
import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.command.impl.AchievementsCommand;
import xyz.fallnight.server.command.impl.AdminCommand;
import xyz.fallnight.server.command.impl.CheckIpCommand;
import xyz.fallnight.server.command.impl.ClearLagCommand;
import xyz.fallnight.server.command.impl.ConvertWorldCommand;
import xyz.fallnight.server.command.impl.CustomEnchantCommand;
import xyz.fallnight.server.command.impl.DisenchantCommand;
import xyz.fallnight.server.command.impl.EnchantCommand;
import xyz.fallnight.server.command.impl.EnchantmentListCommand;
import xyz.fallnight.server.command.impl.EvalCommand;
import xyz.fallnight.server.command.impl.FixCommand;
import xyz.fallnight.server.command.impl.ForgeCommand;
import xyz.fallnight.server.command.impl.IdCommand;
import xyz.fallnight.server.command.impl.FnItemCommand;
import xyz.fallnight.server.command.impl.PerformanceCommand;
import xyz.fallnight.server.command.impl.PlotCommand;
import xyz.fallnight.server.command.impl.PlotsCommand;
import xyz.fallnight.server.command.impl.PluginsCommand;
import xyz.fallnight.server.command.impl.RenameCommand;
import xyz.fallnight.server.command.impl.RenameWorldCommand;
import xyz.fallnight.server.command.impl.SeasonResetCommand;
import xyz.fallnight.server.command.impl.SoftRestartCommand;
import xyz.fallnight.server.command.impl.SudoCommand;
import xyz.fallnight.server.command.impl.SuperlistCommand;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.gameplay.mine.MineGameplayIntegration;
import xyz.fallnight.server.gameplay.mine.MineRuntimeService;
import xyz.fallnight.server.gameplay.player.PlayerSizing;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.AchievementsMenuService;
import xyz.fallnight.server.service.AliasLookupService;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.EnchantmentListMenuService;
import xyz.fallnight.server.service.ForgeMenuService;
import xyz.fallnight.server.service.ForgeService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.KothService;
import xyz.fallnight.server.service.LeaderboardMenuService;
import xyz.fallnight.server.service.LeaderboardService;
import xyz.fallnight.server.service.InventoryOpeners;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.NickMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.PvpZoneService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.service.SizeMenuService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.service.TrashService;
import xyz.fallnight.server.service.VaultService;
import xyz.fallnight.server.service.WorldLabelService;
import xyz.fallnight.server.util.NumberFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class LegacyCompatCommands {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyCompatCommands.class);
    private static final DateTimeFormatter NOTE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final Map<String, String> LEGACY_BLOCK_MAP = loadLegacyBlockMap();

    private LegacyCompatCommands() {
    }

    public static List<Command> createAll(
            PermissionService permissionService,
            PlayerProfileService profileService,
            ModerationSanctionsService moderationSanctionsService,
            RankService rankService,
            GangService gangService,
            VaultService vaultService,
            ItemDeliveryService itemDeliveryService,
            AuctionService auctionService,
            CrateService crateService,
            MineService mineService,
            DefaultWorldService defaultWorldService,
            SpawnService spawnService,
            WorldAccessService worldAccessService,
            PvpZoneService pvpZoneService,
            KothService kothService,
            SpawnService plotWorldService,
            SpawnService pvpMineWorldService,
            PlotService plotService,
            PlotRuntimeModule plotRuntimeModule,
            WorldLabelService worldLabelService,
            Path dataRoot,
            boolean devServer,
            Set<String> alreadyImplemented
    ) {
        List<Command> commands = new ArrayList<>();
        add(commands, alreadyImplemented, balanceTop(permissionService, profileService));
        add(commands, alreadyImplemented, leaderboard(permissionService, profileService));
        add(commands, alreadyImplemented, nick(permissionService, profileService));
        add(commands, alreadyImplemented, nickList(permissionService, profileService));
        add(commands, alreadyImplemented, afk(permissionService, profileService));
        add(commands, alreadyImplemented, me(permissionService));
        add(commands, alreadyImplemented, ignoreAll(permissionService, profileService));
        add(commands, alreadyImplemented, block(permissionService, profileService));
        add(commands, alreadyImplemented, unblock(permissionService, profileService));
        add(commands, alreadyImplemented, blockList(permissionService, profileService));
        add(commands, alreadyImplemented, note(permissionService, profileService));
        add(commands, alreadyImplemented, commandSpy(permissionService, profileService));
        add(commands, alreadyImplemented, vanish(permissionService, profileService));

        add(commands, alreadyImplemented, teleport(permissionService));
        add(commands, alreadyImplemented, inventorySee(permissionService, profileService));
        add(commands, alreadyImplemented, trash(permissionService));
        add(commands, alreadyImplemented, size(permissionService, profileService));
        add(commands, alreadyImplemented, feed(permissionService));
        add(commands, alreadyImplemented, fly(permissionService, profileService));

        add(commands, alreadyImplemented, regenerate(permissionService, mineService));
        add(commands, alreadyImplemented, regenerateAll(permissionService, mineService));
        add(commands, alreadyImplemented, disableMine(permissionService, mineService));
        add(commands, alreadyImplemented, enableMine(permissionService, mineService));
        add(commands, alreadyImplemented, clearMine(permissionService, mineService));

        add(commands, alreadyImplemented, setMaxPlots(permissionService, profileService));
        add(commands, alreadyImplemented, setMaxAuc(permissionService, profileService));
        add(commands, alreadyImplemented, setMaxVault(permissionService, profileService));

        add(commands, alreadyImplemented, world(permissionService, worldAccessService));
        add(commands, alreadyImplemented, survival(permissionService));
        add(commands, alreadyImplemented, spectator(permissionService));
        add(commands, alreadyImplemented, setSpawn(permissionService, defaultWorldService, spawnService, plotWorldService, pvpMineWorldService, worldAccessService));

        add(commands, alreadyImplemented, new PlotCommand(permissionService, profileService, plotService, plotWorldService, plotRuntimeModule));
        add(commands, alreadyImplemented, new PlotsCommand(permissionService, plotService, plotWorldService));
        add(commands, alreadyImplemented, new PluginsCommand(permissionService));
        add(commands, alreadyImplemented, new CheckIpCommand(permissionService, profileService));
        add(commands, alreadyImplemented, new SudoCommand(permissionService));
        if (devServer) {
            add(commands, alreadyImplemented, new EvalCommand(permissionService));
        }
        add(commands, alreadyImplemented, new SoftRestartCommand(permissionService));
        add(commands, alreadyImplemented, new ClearLagCommand(permissionService));
        add(commands, alreadyImplemented, new RenameWorldCommand(permissionService, spawnService, plotWorldService, pvpMineWorldService, worldLabelService, worldAccessService, mineService, pvpZoneService, kothService));
        add(commands, alreadyImplemented, new ConvertWorldCommand(permissionService));
        add(commands, alreadyImplemented, setBlockCommand(permissionService));
        add(commands, alreadyImplemented, new PerformanceCommand(permissionService));
        add(commands, alreadyImplemented, new AdminCommand(permissionService, profileService));
        add(commands, alreadyImplemented, new SeasonResetCommand(permissionService, profileService, rankService, gangService, plotService, plotRuntimeModule, auctionService, vaultService, dataRoot.resolve("plots")));
        add(commands, alreadyImplemented, achievements(permissionService, profileService));

        add(commands, alreadyImplemented, new CustomEnchantCommand(permissionService));
        add(commands, alreadyImplemented, new EnchantCommand(permissionService));
        add(commands, alreadyImplemented, new EnchantmentListCommand(permissionService, new EnchantmentListMenuService()));
        add(commands, alreadyImplemented, new ForgeCommand(permissionService, profileService, new ForgeMenuService(new ForgeService(new LegacyCustomItemService(), profileService, itemDeliveryService), profileService, itemDeliveryService)));
        add(commands, alreadyImplemented, new DisenchantCommand(permissionService, profileService, itemDeliveryService));
        add(commands, alreadyImplemented, new FixCommand(permissionService));
        add(commands, alreadyImplemented, new RenameCommand(permissionService));
        add(commands, alreadyImplemented, new FnItemCommand(permissionService));
        add(commands, alreadyImplemented, new IdCommand(permissionService));

        add(commands, alreadyImplemented, alias(permissionService, profileService, moderationSanctionsService));
        add(commands, alreadyImplemented, new SuperlistCommand(permissionService, profileService));

        return List.copyOf(commands);
    }

    private static void add(List<Command> commands, Set<String> alreadyImplemented, Command command) {
        for (String name : command.getNames()) {
            if (alreadyImplemented.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        commands.add(command);
    }

    private static Command simple(PermissionService permissionService, String name, String message, String... aliases) {
        Command command = base(permissionService, name, aliases);
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(CommandMessages.info(message)));
        return command;
    }

    private static Command balanceTop(PermissionService permissionService, PlayerProfileService profileService) {
        LeaderboardService leaderboardService = new LeaderboardService(profileService, 30);
        LeaderboardMenuService menuService = new LeaderboardMenuService(leaderboardService);
        Command command = base(permissionService, "baltop", "topmoney");
        var categoryArgument = ArgumentType.Word("category");
        command.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                menuService.open(player, LeaderboardService.Type.BALANCE, "Top: money");
                return;
            }
            sendLeaderboard(sender, leaderboardService, LeaderboardService.Type.BALANCE, "Balance top:");
        });
        command.addSyntax((sender, context) -> {
            String input = context.get(categoryArgument).toLowerCase(Locale.ROOT);
            LeaderboardService.Type type = switch (input) {
                case "baltop", "balance" -> LeaderboardService.Type.BALANCE;
                case "earntop", "earnings", "money" -> LeaderboardService.Type.EARNINGS;
                case "breaktop", "blocks", "broken" -> LeaderboardService.Type.BLOCK_BREAKS;
                case "minetop", "mine" -> LeaderboardService.Type.MINE;
                case "killtop", "kills" -> LeaderboardService.Type.KILLS;
                case "kdtop", "kdr", "kd" -> LeaderboardService.Type.KDR;
                default -> null;
            };
            if (type == null) {
                sender.sendMessage(CommandMessages.error("Unknown leaderboard category. Use balance, earnings, blocks, mine, kills, or kdr."));
                return;
            }
            sendLeaderboard(sender, leaderboardService, type, input + ":");
        }, categoryArgument);
        return command;
    }

    private static Command leaderboard(PermissionService permissionService, PlayerProfileService profileService) {
        LeaderboardService leaderboardService = new LeaderboardService(profileService, 30);
        LeaderboardMenuService menuService = new LeaderboardMenuService(leaderboardService);
        Command command = base(permissionService, "leaderboard", "top");
        var categoryArgument = ArgumentType.Word("category");
        command.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                menuService.openCategories(player);
                return;
            }
            sender.sendMessage(CommandMessages.info("Leaderboard categories: baltop, earntop, breaktop, minetop, killtop, kdtop"));
        });
        command.addSyntax((sender, context) -> {
            String input = context.get(categoryArgument).toLowerCase(Locale.ROOT);
            LeaderboardService.Type type = switch (input) {
                case "baltop", "balance" -> LeaderboardService.Type.BALANCE;
                case "earntop", "earnings", "money" -> LeaderboardService.Type.EARNINGS;
                case "breaktop", "blocks", "broken" -> LeaderboardService.Type.BLOCK_BREAKS;
                case "minetop", "mine" -> LeaderboardService.Type.MINE;
                case "killtop", "kills" -> LeaderboardService.Type.KILLS;
                case "kdtop", "kdr", "kd" -> LeaderboardService.Type.KDR;
                default -> null;
            };
            if (type == null) {
                sender.sendMessage(CommandMessages.error("Unknown leaderboard category. Use baltop, earntop, breaktop, minetop, killtop, or kdtop."));
                return;
            }
            if (sender instanceof Player player) {
                menuService.open(player, type, input + ":");
                return;
            }
            sendLeaderboard(sender, leaderboardService, type, input + ":");
        }, categoryArgument);
        return command;
    }

    private static void sendLeaderboard(net.minestom.server.command.CommandSender sender, LeaderboardService leaderboardService, LeaderboardService.Type type, String title) {
        leaderboardService.regenerateAll();
        List<UserProfile> top = leaderboardService.top(type).stream().limit(30).toList();
        if (top.isEmpty()) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l> §r§7The leaderboards are still regenerating, please try again later!"));
            return;
        }
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(leaderboardTitle(type)));
        int place = 1;
        for (UserProfile profile : top) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(leaderboardEntry(type, leaderboardService, profile, place)));
            place++;
        }
    }

    private static String leaderboardTitle(LeaderboardService.Type type) {
        return switch (type) {
            case BALANCE -> "§bTop: money";
            case EARNINGS -> "§bTop: total earned money";
            case BLOCK_BREAKS -> "§bTop: blocks broken";
            case MINE -> "§bTop: rank";
            case KILLS -> "§bTop: kills";
            case KDR -> "§bTop: K/D Ratio";
        };
    }

    private static String leaderboardEntry(LeaderboardService.Type type, LeaderboardService leaderboardService, UserProfile profile, int place) {
        return switch (type) {
            case BALANCE ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b$" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + "§8]§r";
            case EARNINGS ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b$" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + "§8]§r";
            case BLOCK_BREAKS ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + " blocks§8]§r";
            case MINE ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§7" + toRoman(profile.getPrestige()) + "§r§8§l-§r§7" + mineTag(profile.getMineRank()) + "§8]§r";
            case KILLS ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + (long) leaderboardService.value(type, profile) + " kills§8]§r";
            case KDR ->
                    " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + roundKdr(leaderboardService.value(type, profile)) + " K/D§8]§r";
        };
    }

    private static String roundKdr(double value) {
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private static String mineTag(int rankId) {
        int rank = Math.max(1, rankId);
        return String.valueOf((char) ('A' + Math.min(25, rank - 1)));
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

    private static Command nick(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "nick", "nickname");
        var targetArgument = ArgumentType.Word("player");
        var nickArgument = ArgumentType.StringArray("nickname");
        NickMenuService nickMenuService = new NickMenuService(profileService);
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.info("Use /nick <name> as a player."));
                return;
            }
            nickMenuService.open(player);
        });
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("This command can only be used by players."));
                return;
            }
            String nickname = String.join(" ", context.get(nickArgument)).trim();
            UserProfile profile = profileService.getOrCreate(player);
            if (nickname.isBlank() || nickname.equalsIgnoreCase("clear")) {
                profile.getExtraData().remove("nickname");
                profileService.save(profile);
                sender.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been cleared."));
                return;
            }
            if (nickname.length() > 20) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a shorter nickname."));
            }
            profile.getExtraData().put("nickname", nickname);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been set to §b" + nickname + "§r§7."));
        }, nickArgument);
        command.addSyntax((sender, context) -> {
            String targetName = context.get(targetArgument);
            Player target = findOnlinePlayerIgnoreCase(targetName);
            if (target == null) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§c§l> §r§7Player with name §c" + targetName + "§r§7 not found."));
                return;
            }
            if (!permissionService.hasPermission(sender, "fallnight.command.nick.others")) {
                sender.sendMessage(CommandMessages.error("You do not have permission to nickname others."));
                return;
            }
            String nickname = String.join(" ", context.get(nickArgument)).trim();
            UserProfile profile = profileService.getOrCreate(target);
            if (nickname.equalsIgnoreCase("clear")) {
                profile.getExtraData().remove("nickname");
                profileService.save(profile);
                sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You cleared §b" + target.getUsername() + "§r§7's nickname."));
                target.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been cleared."));
                return;
            }
            if (nickname.length() > 20) {
                target.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a shorter nickname."));
            }
            profile.getExtraData().put("nickname", nickname);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You set §b" + target.getUsername() + "§r§7's nickname to §b" + nickname + "§r§7."));
            target.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been set to §b" + nickname + "§r§7."));
        }, targetArgument, nickArgument);
        return command;
    }

    private static Command nickList(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "nicklist");
        command.setDefaultExecutor((sender, context) -> {
            List<String> entries = new ArrayList<>();
            for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                UserProfile profile = profileService.getOrCreate(online);
                if (readBoolean(profile, "vanish", false)) {
                    continue;
                }
                Object value = profile.getExtraData().get("nickname");
                if (value instanceof String nickname && !nickname.isBlank()) {
                    entries.add("§r§b" + online.getUsername() + " §r§7->§b " + nickname.replace('&', '§'));
                }
            }
            StringBuilder message = new StringBuilder("§8§l<--§bFN§8--> \n§r§7 Nick list");
            for (String entry : entries) {
                message.append("\n").append(entry);
            }
            message.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message.toString()));
        });
        return command;
    }

    private static Command afk(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "afk");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            boolean afk = !readBoolean(profile, "afk", false);
            profile.getExtraData().put("afk", afk);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize(afk ? "§r§b§l>§r§7 You are now AFK." : "§r§b§l>§r§7 You are no longer AFK."));
        });
        return command;
    }

    private static Command me(PermissionService permissionService) {
        Command command = base(permissionService, "me");
        var messageArgument = ArgumentType.StringArray("message");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter an action to perform in chat.")));
        command.addSyntax((sender, context) -> {
            String actorName = sender instanceof Player player ? player.getUsername() : "CONSOLE";
            String message = String.join(" ", context.get(messageArgument)).trim();
            if (message.isBlank()) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter an action to perform in chat."));
                return;
            }
            String emote = "§r§8* §r§b" + actorName + "§r§7 " + message + "⛏";
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(target ->
                    target.sendMessage(LEGACY.deserialize(emote))
            );
        }, messageArgument);
        return command;
    }

    private static Command ignoreAll(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "ignoreall", "blockall");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            boolean enabled = !readBoolean(profile, "ignoreAll", false);
            profile.getExtraData().put("ignoreAll", enabled);
            profileService.save(profile);
            sender.sendMessage(CommandMessages.success(enabled ? "You are now ignoring all chat." : "You are no longer ignoring all chat."));
        });
        return command;
    }

    private static Command block(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "block", "ignore");
        var targetArgument = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Please enter a target to unblock."));
        });
        command.addSyntax((sender, context) -> {
            String target = normalizeName(context.get(targetArgument));
            if (target.isEmpty()) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Please enter a target to unblock."));
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            Player onlineTarget = findOnlinePlayerIgnoreCase(target);
            UserProfile targetProfile = onlineTarget != null
                    ? profileService.getOrCreate(onlineTarget)
                    : profileService.findByUsername(target).orElse(null);
            if (targetProfile == null) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Player with name §c" + target + "§r§7 was never connected."));
                return;
            }
            String targetName = targetProfile.getUsername();
            UserProfile profile = profileService.getOrCreate(player);
            LinkedHashSet<String> blocked = blockedNames(profile);
            if (!blocked.add(targetName.toLowerCase(Locale.ROOT))) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7You have already blocked this user."));
                return;
            }
            LinkedHashSet<String> storedBlocked = blockedNamesDisplay(profile);
            storedBlocked.add(targetName);
            storeBlockedNames(profile, storedBlocked);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You blocked §b" + targetName + "§r§7."));
        }, targetArgument);
        return command;
    }

    private static Command unblock(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "unblock", "unignore");
        var targetArgument = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Please enter a target to block.")));
        command.addSyntax((sender, context) -> {
            String target = normalizeName(context.get(targetArgument));
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            Player onlineTarget = findOnlinePlayerIgnoreCase(target);
            UserProfile targetProfile = onlineTarget != null
                    ? profileService.getOrCreate(onlineTarget)
                    : profileService.findByUsername(target).orElse(null);
            if (targetProfile == null) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Player with name §c" + target + "§r§7 was never connected."));
                return;
            }
            String targetName = targetProfile.getUsername();
            UserProfile profile = profileService.getOrCreate(player);
            LinkedHashSet<String> blocked = blockedNames(profile);
            if (!blocked.remove(targetName.toLowerCase(Locale.ROOT))) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7You haven't blocked this user."));
                return;
            }
            storeBlockedNames(profile, blocked);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You unblocked §b" + targetName + "§r§7."));
        }, targetArgument);
        return command;
    }

    private static Command blockList(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "blocklist", "ignorelist");
        var targetArgument = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            LinkedHashSet<String> blocked = blockedNamesDisplay(profile);
            if (blocked.isEmpty()) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l> §r§7You haven't blocked anyone."));
                return;
            }
            StringBuilder message = new StringBuilder("§8§l<--§bFN§8--> \n§r§7§7 Your list of blocked users§r");
            for (String blockedUser : blocked) {
                message.append("\n§r§8 > §r§b").append(blockedUser);
            }
            message.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message.toString()));
        });
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            UserProfile senderProfile = profileService.getOrCreate(player);
            if (!readBoolean(senderProfile, "adminMode", false)) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            String targetName = context.get(targetArgument);
            Player target = findOnlinePlayerIgnoreCase(targetName);
            if (target == null) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§c§l> §r§7That player was not found."));
                return;
            }
            UserProfile targetProfile = profileService.getOrCreate(target);
            LinkedHashSet<String> blocked = blockedNamesDisplay(targetProfile);
            if (blocked.isEmpty()) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l> §r§b" + target.getUsername() + " hasn't blocked anyone."));
                return;
            }
            StringBuilder message = new StringBuilder("§8§l<--§bFN§8--> \n§r§7§b " + target.getUsername() + "§7's list of blocked users§r");
            for (String blockedUser : blocked) {
                message.append("\n§r§8 > §r§b").append(blockedUser);
            }
            message.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message.toString()));
        }, targetArgument);
        return command;
    }

    private static Command note(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "note");
        var instrumentArgument = ArgumentType.Integer("instrument").min(0).max(15);
        var noteArgument = ArgumentType.Integer("note").min(0).max(24);
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c> §r§7An error has occured.")));
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            try {
                int instrument = context.get(instrumentArgument);
                int note = context.get(noteArgument);
                float pitch = (float) Math.pow(2d, (note - 12) / 12d);
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key(noteInstrumentKey(instrument)),
                        net.kyori.adventure.sound.Sound.Source.MASTER,
                        1f,
                        pitch
                ));
                sender.sendMessage(LEGACY.deserialize("§r§c> §r§7Executed successfully."));
            } catch (Throwable throwable) {
                sender.sendMessage(LEGACY.deserialize("§r§c> §r§7An error has occured."));
            }
        }, instrumentArgument, noteArgument);
        return command;
    }

    private static Command commandSpy(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "commandspy", "cspy");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("This command can only be used by players."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            boolean enabled = !readBoolean(profile, "commandSpy", false);
            profile.getExtraData().put("commandSpy", enabled);
            profileService.save(profile);
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§b§l>§r§7 Commandspy is now §b" + (enabled ? "enabled" : "disabled") + "§7."));
        });
        return command;
    }

    private static Command vanish(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "vanish");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("This command can only be used by players."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            boolean enabled = !readBoolean(profile, "vanish", false);
            profile.getExtraData().put("vanish", enabled);
            profileService.save(profile);
            player.setInvisible(enabled);
            player.setAutoViewable(!enabled);
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(other -> {
                if (!other.getUuid().equals(player.getUuid())) {
                    other.sendMessage(LegacyComponentSerializer.legacySection().deserialize(enabled ? "§r§8§l[§b-§8]§r §7" + player.getUsername() : "§r§8§l[§b+§8]§r §7" + player.getUsername()));
                }
            });
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§b§l>§r§7 Vanish is now §b" + (enabled ? "enabled" : "disabled") + "§7."));
        });
        return command;
    }

    private static Command teleport(PermissionService permissionService) {
        Command command = base(permissionService, "teleport", "tp");
        var targetArgument = ArgumentType.Word("player");
        var destArgument = ArgumentType.Word("destination");
        var xArgument = ArgumentType.Word("x");
        var yArgument = ArgumentType.Word("y");
        var zArgument = ArgumentType.Word("z");
        var yawArgument = ArgumentType.Double("yaw");
        var pitchArgument = ArgumentType.Double("pitch");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You have given incorrect arguments for this command.")));
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player to teleport to."));
                return;
            }
            String targetName = context.get(targetArgument);
            Player target = findOnlinePlayerIgnoreCase(targetName);
            if (target == null || target.getInstance() == null) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Could not find player §c" + targetName + "§r§7."));
                return;
            }
            player.setInstance(target.getInstance(), target.getPosition());
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + player.getUsername() + "§r§7 to §b" + target.getUsername() + "§r§7."));
        }, targetArgument);
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player to teleport to."));
                return;
            }
            double x = parseRelative(player.getPosition().x(), context.get(xArgument), -30_000_000, 30_000_000);
            double y = parseRelative(player.getPosition().y(), context.get(yArgument), 0, 256);
            double z = parseRelative(player.getPosition().z(), context.get(zArgument), -30_000_000, 30_000_000);
            player.teleport(new net.minestom.server.coordinate.Pos(x, y, z, player.getPosition().yaw(), player.getPosition().pitch()));
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + player.getUsername() + "§r§7 to §b" + Math.round(x * 100) / 100.0 + "§7, §b" + Math.round(y * 100) / 100.0 + "§7, §b" + Math.round(z * 100) / 100.0 + "§r§7."));
        }, xArgument, yArgument, zArgument);
        command.addSyntax((sender, context) -> {
            String first = context.get(targetArgument);
            String second = context.get(destArgument);
            Player origin = findOnlinePlayerIgnoreCase(first);
            Player destination = findOnlinePlayerIgnoreCase(second);
            if (origin == null || destination == null || destination.getInstance() == null) {
                String missing = origin == null ? first : second;
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Could not find player §c" + missing + "§r§7."));
                return;
            }
            origin.setInstance(destination.getInstance(), destination.getPosition());
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + origin.getUsername() + "§r§7 to §b" + destination.getUsername() + "§r§7."));
        }, targetArgument, destArgument);
        command.addSyntax((sender, context) -> {
            Player target = findOnlinePlayerIgnoreCase(context.get(targetArgument));
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Could not find player §c" + context.get(targetArgument) + "§r§7."));
                return;
            }
            double x = parseRelative(target.getPosition().x(), context.get(xArgument), -30_000_000, 30_000_000);
            double y = parseRelative(target.getPosition().y(), context.get(yArgument), 0, 256);
            double z = parseRelative(target.getPosition().z(), context.get(zArgument), -30_000_000, 30_000_000);
            target.teleport(new net.minestom.server.coordinate.Pos(x, y, z, target.getPosition().yaw(), target.getPosition().pitch()));
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + target.getUsername() + "§r§7 to §b" + Math.round(x * 100) / 100.0 + "§7, §b" + Math.round(y * 100) / 100.0 + "§7, §b" + Math.round(z * 100) / 100.0 + "§r§7."));
        }, targetArgument, xArgument, yArgument, zArgument);
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player to teleport to."));
                return;
            }
            double x = parseRelative(player.getPosition().x(), context.get(xArgument), -30_000_000, 30_000_000);
            double y = parseRelative(player.getPosition().y(), context.get(yArgument), 0, 256);
            double z = parseRelative(player.getPosition().z(), context.get(zArgument), -30_000_000, 30_000_000);
            player.teleport(new net.minestom.server.coordinate.Pos(x, y, z, context.get(yawArgument).floatValue(), context.get(pitchArgument).floatValue()));
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + player.getUsername() + "§r§7 to §b" + Math.round(x * 100) / 100.0 + "§7, §b" + Math.round(y * 100) / 100.0 + "§7, §b" + Math.round(z * 100) / 100.0 + "§r§7."));
        }, xArgument, yArgument, zArgument, yawArgument, pitchArgument);
        command.addSyntax((sender, context) -> {
            Player target = findOnlinePlayerIgnoreCase(context.get(targetArgument));
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Could not find player §c" + context.get(targetArgument) + "§r§7."));
                return;
            }
            double x = parseRelative(target.getPosition().x(), context.get(xArgument), -30_000_000, 30_000_000);
            double y = parseRelative(target.getPosition().y(), context.get(yArgument), 0, 256);
            double z = parseRelative(target.getPosition().z(), context.get(zArgument), -30_000_000, 30_000_000);
            target.teleport(new net.minestom.server.coordinate.Pos(x, y, z, context.get(yawArgument).floatValue(), context.get(pitchArgument).floatValue()));
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7Teleported §b" + target.getUsername() + "§r§7 to §b" + Math.round(x * 100) / 100.0 + "§7, §b" + Math.round(y * 100) / 100.0 + "§7, §b" + Math.round(z * 100) / 100.0 + "§r§7."));
        }, targetArgument, xArgument, yArgument, zArgument, yawArgument, pitchArgument);
        return command;
    }

    private static Command inventorySee(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "inventorysee", "invsee", "seeinv");
        var targetArgument = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player.")));
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player viewer)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            String targetName = context.get(targetArgument);
            Player target = findOnlinePlayerIgnoreCase(targetName);
            String displayName = targetName;
            Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize("§l§o§b" + displayName + "§r§8's inventory"));
            inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
            if (target != null) {
                displayName = target.getUsername();
                fillOnlineInventoryView(inventory, target);
            } else {
                UserProfile profile = profileService.findOfflineByUsername(targetName).orElse(null);
                if (profile == null) {
                    sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 That player has never connected."));
                    return;
                }
                displayName = profile.getUsername();
                inventory.setTitle(LEGACY.deserialize("§l§o§b" + displayName + "§r§8's inventory"));
                fillSnapshotInventoryView(inventory, profile);
            }
            inventory.setTitle(LEGACY.deserialize("§l§o§b" + displayName + "§r§8's inventory"));
            fillInventorySeePane(inventory);
            InventoryOpeners.replace(viewer, inventory);
        }, targetArgument);
        return command;
    }

    private static Command trash(PermissionService permissionService) {
        Command command = base(permissionService, "trash", "dispose");
        TrashService trashService = new TrashService();
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("This command can only be used by players."));
                return;
            }
            trashService.open(player);
        });
        return command;
    }

    private static Command size(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "size");
        var sizeArgument = ArgumentType.Integer("size");
        SizeMenuService sizeMenuService = new SizeMenuService(profileService);
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            sizeMenuService.open(player);
        });
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please execute this command ingame."));
                return;
            }
            int size = context.get(sizeArgument);
            if (size < 50) {
                sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Your size can't be lower than §b50§7."));
                return;
            }
            if (size > 150) {
                sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Your size can't be higher than §b150§7."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            int clampedSize = PlayerSizing.clampPercent(size);
            PlayerSizing.apply(player, clampedSize);
            profile.getExtraData().put("playerSize", clampedSize);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Your set your size to §b" + clampedSize + "§7."));
        }, sizeArgument);
        return command;
    }

    private static Command feed(PermissionService permissionService) {
        Command command = base(permissionService, "feed");
        var targetArgument = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            if (!feedReady(player)) {
                sender.sendMessage(CommandMessages.error("That you still have to wait §c" + feedCooldownRemaining(player) + " seconds§r§7 before you can use this command again."));
                return;
            }
            player.setFood(20);
            player.setFoodSaturation(15f);
            markFeed(player);
            sender.sendMessage(CommandMessages.success("You have been fed."));
        });
        command.addSyntax((sender, context) -> {
            Player executor = sender instanceof Player player ? player : null;
            if (executor != null && !feedReady(executor)) {
                sender.sendMessage(CommandMessages.error("That you still have to wait §c" + feedCooldownRemaining(executor) + " seconds§r§7 before you can use this command again."));
                return;
            }
            if (!permissionService.hasPermission(sender, "fallnight.command.feed.others")) {
                if (executor == null) {
                    sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                    return;
                }
                executor.setFood(20);
                executor.setFoodSaturation(15f);
                markFeed(executor);
                executor.sendMessage(CommandMessages.success("You have been fed."));
                return;
            }
            Player target = findOnlinePlayerIgnoreCase(context.get(targetArgument));
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 That player was not found."));
                return;
            }
            target.setFood(20);
            target.setFoodSaturation(15f);
            if (executor != null) {
                markFeed(executor);
            }
            sender.sendMessage(CommandMessages.success("You fed §b" + target.getUsername() + "§r§7."));
            target.sendMessage(CommandMessages.success("You have been fed."));
        }, targetArgument);
        return command;
    }

    private static Command fly(PermissionService permissionService, PlayerProfileService profileService) {
        Command command = base(permissionService, "fly", "flight");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            boolean enabled = !readBoolean(profile, "fly", false);
            profile.getExtraData().put("fly", enabled);
            profileService.save(profile);
            player.setAllowFlying(enabled);
            if (!enabled && player.isFlying()) {
                player.setFlying(false);
            }
            sender.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Flight mode is now §b" + (enabled ? "enabled" : "disabled") + "§r§7."));
        });
        var targetArgument = ArgumentType.Word("player");
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            Player target = player;
            if (permissionService.hasPermission(sender, "fallnight.command.fly.others")) {
                target = findOnlinePlayerIgnoreCase(context.get(targetArgument));
                if (target == null) {
                    sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Player with name §c" + context.get(targetArgument) + "§r§7 not found."));
                    return;
                }
            }
            UserProfile profile = profileService.getOrCreate(target);
            boolean enabled = !readBoolean(profile, "fly", false);
            profile.getExtraData().put("fly", enabled);
            profileService.save(profile);
            target.setAllowFlying(enabled);
            if (!enabled && target.isFlying()) {
                target.setFlying(false);
            }
            if (!target.getUuid().equals(player.getUuid())) {
                sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You §b" + (enabled ? "enabled" : "disabled") + "§r§7 flight mode for §b" + target.getUsername() + "§r§7."));
            }
            target.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Flight mode is now §b" + (enabled ? "enabled" : "disabled") + "§r§7."));
        }, targetArgument);
        return command;
    }

    private static Command regenerate(PermissionService permissionService, MineService mineService) {
        Command command = base(permissionService, "regenerate", "regen");
        var mineArg = ArgumentType.Word("mine");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.info("Usage: /regenerate <mine>"));
                return;
            }
            Optional<MineRuntimeService> runtime = MineGameplayIntegration.runtimeService();
            if (runtime.isEmpty() || player.getInstance() == null) {
                sender.sendMessage(CommandMessages.error("Mine runtime is not available."));
                return;
            }
            MineDefinition mine = runtime.get().findMineAt(player.getPosition(), player.getInstance()).orElse(null);
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("You are not inside a mine."));
                return;
            }
            runtime.get().regenerateMine(mine, "command");
            sender.sendMessage(CommandMessages.success("Regenerated mine " + mine.getName() + "."));
        });
        command.addSyntax((sender, context) -> {
            Optional<MineRuntimeService> runtime = MineGameplayIntegration.runtimeService();
            if (runtime.isEmpty()) {
                sender.sendMessage(CommandMessages.error("Mine runtime is not available."));
                return;
            }
            MineDefinition mine = resolveMine(mineService, context.get(mineArg));
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("Mine was not found."));
                return;
            }
            runtime.get().regenerateMine(mine, "command");
            sender.sendMessage(CommandMessages.success("Regenerated mine " + mine.getName() + "."));
        }, mineArg);
        return command;
    }

    private static Command regenerateAll(PermissionService permissionService, MineService mineService) {
        Command command = base(permissionService, "regenerateall", "regenall");
        command.setDefaultExecutor((sender, context) -> {
            Optional<MineRuntimeService> runtime = MineGameplayIntegration.runtimeService();
            if (runtime.isEmpty()) {
                sender.sendMessage(CommandMessages.error("Mine runtime is not available."));
                return;
            }
            int count = 0;
            for (MineDefinition mine : mineService.allMines()) {
                runtime.get().regenerateMine(mine, "command-all");
                count++;
            }
            sender.sendMessage(CommandMessages.success("Regenerated " + count + " mine(s)."));
        });
        return command;
    }

    private static Command disableMine(PermissionService permissionService, MineService mineService) {
        return mineToggle(permissionService, mineService, "disablemine", true);
    }

    private static Command enableMine(PermissionService permissionService, MineService mineService) {
        return mineToggle(permissionService, mineService, "enablemine", false);
    }

    private static Command mineToggle(PermissionService permissionService, MineService mineService, String name, boolean disabled) {
        Command command = base(permissionService, name);
        var mineArg = ArgumentType.Word("mine");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(CommandMessages.info("Usage: /" + name + " <mine>")));
        command.addSyntax((sender, context) -> {
            MineDefinition mine = resolveMine(mineService, context.get(mineArg));
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("Mine was not found."));
                return;
            }
            mine.setDisabled(disabled);
            mineService.save(mine);
            sender.sendMessage(CommandMessages.success((disabled ? "Disabled " : "Enabled ") + "mine " + mine.getName() + "."));
        }, mineArg);
        return command;
    }

    private static Command clearMine(PermissionService permissionService, MineService mineService) {
        Command command = base(permissionService, "clearmine");
        var mineArg = ArgumentType.Word("mine");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(CommandMessages.info("Usage: /clearmine <mine>")));
        command.addSyntax((sender, context) -> {
            Optional<MineRuntimeService> runtime = MineGameplayIntegration.runtimeService();
            if (runtime.isEmpty()) {
                sender.sendMessage(CommandMessages.error("Mine runtime is not available."));
                return;
            }
            MineDefinition mine = resolveMine(mineService, context.get(mineArg));
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("Mine was not found."));
                return;
            }
            runtime.get().clearMine(mine);
            sender.sendMessage(CommandMessages.success("Cleared mine " + mine.getName() + "."));
        }, mineArg);
        return command;
    }

    private static Command setMaxPlots(PermissionService permissionService, PlayerProfileService profileService) {
        return profileLimit(permissionService, profileService, "setmaxplots", "maxPlots", "max plot count");
    }

    private static Command setMaxAuc(PermissionService permissionService, PlayerProfileService profileService) {
        return profileLimit(permissionService, profileService, "setmaxauc", "maxAuctionListings", "max auction item count");
    }

    private static Command setMaxVault(PermissionService permissionService, PlayerProfileService profileService) {
        return profileLimit(permissionService, profileService, "setmaxvaults", "maxVaults", "max vault count");
    }

    private static Command profileLimit(
            PermissionService permissionService,
            PlayerProfileService profileService,
            String commandName,
            String key,
            String displayName,
            String... aliases
    ) {
        Command command = base(permissionService, commandName, aliases);
        var playerArg = ArgumentType.Word("player");
        var valueArg = ArgumentType.Integer("value");
        command.setDefaultExecutor((sender, context) ->
                sender.sendMessage(CommandMessages.info("Usage: /" + commandName + " <player> <value>"))
        );
        command.addSyntax((sender, context) -> {
            String playerName = context.get(playerArg);
            int value = context.get(valueArg);
            UserProfile profile = profileService.findByUsername(playerName).orElse(null);
            if (profile == null) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7That player was never connected."));
                return;
            }
            profile.getExtraData().put(key, value);
            profileService.save(profile);
            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Set §b" + profile.getUsername() + "§7's " + displayName + " to §b" + value + "§7."));
            Player target = findOnlinePlayerIgnoreCase(profile.getUsername());
            if (target != null && (sender instanceof Player player ? !player.getUuid().equals(target.getUuid()) : true)) {
                target.sendMessage(LEGACY.deserialize("§b§l> §r§7Your " + displayName + " has been changed to §b" + value + "§7."));
            }
        }, playerArg, valueArg);
        return command;
    }

    private static void grantCrateKeys(
            CommandSender sender,
            PlayerProfileService profileService,
            CrateService crateService,
            ItemDeliveryService itemDeliveryService,
            String crateInput,
            String targetInput,
            int amount
    ) {
        String crateId = normalizeName(crateInput).toLowerCase(Locale.ROOT);
        if (crateId.isBlank()) {
            sender.sendMessage(CommandMessages.error("Please provide a crate id."));
            return;
        }
        if (crateService.findCrate(crateId).isEmpty()) {
            String available = crateService.allCrates().stream()
                    .map(definition -> definition.id().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
            sender.sendMessage(CommandMessages.error("Unknown crate '" + crateId + "'. Available: " + available));
            return;
        }

        String targetName = normalizeName(targetInput);
        if (targetName.isBlank()) {
            sender.sendMessage(CommandMessages.error("Please provide a target player."));
            return;
        }

        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
        if (online == null) {
            sender.sendMessage(CommandMessages.error("Please player could not be found online."));
            return;
        }
        UserProfile profile = profileService.getOrCreate(online);
        xyz.fallnight.server.service.LegacyCustomItemService items = new xyz.fallnight.server.service.LegacyCustomItemService();
        boolean delivered = items.createById(20, amount, crateNumericId(crateId))
                .map(item -> itemDeliveryService.deliver(online, profile, item).success())
                .orElse(false);
        if (!delivered) {
            sender.sendMessage(CommandMessages.error("Failed to deliver the key item to " + online.getUsername() + "."));
            return;
        }
        profileService.save(profile);
        sender.sendMessage(CommandMessages.success("Granted " + amount + " " + crateId + " key(s) to " + profile.getUsername() + "."));
    }

    private static int crateNumericId(String crateId) {
        return switch (crateId.toLowerCase(Locale.ROOT)) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            case "vote" -> 99;
            case "koth" -> 120;
            default -> 99;
        };
    }

    @SuppressWarnings("unchecked")
    private static void assignRank(UserProfile profile, String rankId) {
        Object rawComponent = profile.getExtraData().get("rankComponent");
        Map<String, Object> component;
        if (rawComponent instanceof Map<?, ?> rawMap) {
            component = (Map<String, Object>) rawMap;
        } else {
            component = new java.util.LinkedHashMap<>();
            profile.getExtraData().put("rankComponent", component);
        }

        Object rawRanks = component.get("ranks");
        Map<String, Object> ranks;
        if (rawRanks instanceof Map<?, ?> rawMap) {
            ranks = (Map<String, Object>) rawMap;
        } else {
            ranks = new java.util.LinkedHashMap<>();
            component.put("ranks", ranks);
        }

        ranks.clear();
        ranks.put(rankId, Map.of("expires", -1, "hidden", false));
    }

    @SuppressWarnings("unchecked")
    private static void clearAssignedRanks(UserProfile profile) {
        Object rawComponent = profile.getExtraData().get("rankComponent");
        if (!(rawComponent instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> component = (Map<String, Object>) rawMap;
        Object rawRanks = component.get("ranks");
        if (rawRanks instanceof Map<?, ?> ranks) {
            ((Map<?, ?>) ranks).clear();
        }
    }

    private static Command world(PermissionService permissionService, WorldAccessService worldAccessService) {
        Command command = base(permissionService, "world");
        var worldArg = ArgumentType.Word("world");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            sender.sendMessage(LEGACY.deserialize("§r§4§l>§r§7 Please enter a world to transfer to."));
        });
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            String world = context.get(worldArg).trim();
            SpawnService targetWorld = worldAccessService.resolve(world).orElse(null);
            if (targetWorld != null) {
                targetWorld.teleportToSpawn(player);
                sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You have been transferred to world §b" + targetWorld.worldName() + "§r§7."));
                return;
            }
            sender.sendMessage(LEGACY.deserialize("§r§4§l>§r§7 That world does not exist."));
        }, worldArg);
        return command;
    }

    private static Command survival(PermissionService permissionService) {
        Command command = base(permissionService, "survival");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            player.setGameMode(GameMode.SURVIVAL);
            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Your gamemode has been set to survival."));
        });
        return command;
    }

    private static Command spectator(PermissionService permissionService) {
        Command command = base(permissionService, "spectator");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7Your gamemode has been set to spectator."));
        });
        return command;
    }

    private static Command setSpawn(PermissionService permissionService, DefaultWorldService defaultWorldService, SpawnService spawnService, SpawnService plotWorldService, SpawnService pvpMineWorldService, WorldAccessService worldAccessService) {
        Command command = base(permissionService, "setspawn");
        command.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("This command can only be used by players."));
                return;
            }
            SpawnService currentWorld = worldAccessService.resolveCurrent(player);
            currentWorld.setSpawn(player.getPosition());
            worldAccessService.persistSpawn(currentWorld, player.getPosition());
            defaultWorldService.setCurrentWorld(currentWorld);
            var respawnPoint = SpawnService.normalizedSpawn(defaultWorldService.currentWorld().spawn());
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(online -> online.setRespawnPoint(respawnPoint));
            try {
                ServerConfig current = AppConfigLoader.load(LOGGER);
                AppConfigWriter.save(
                        AppConfigWriter.withSpawn(current, currentWorld.worldName(), player.getPosition()),
                        AppConfigLoader.writableConfigPath()
                );
                sender.sendMessage(CommandMessages.success("Set the server spawn to your current position."));
            } catch (Exception exception) {
                sender.sendMessage(CommandMessages.error("Updated the live spawn, but failed to persist the spawn config."));
            }
        });
        return command;
    }

    private static Command alias(PermissionService permissionService, PlayerProfileService profileService, ModerationSanctionsService moderationSanctionsService) {
        AliasLookupService aliasLookupService = new AliasLookupService(profileService);
        Command command = base(permissionService, "alias");
        var playerArg = ArgumentType.Word("player");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r §7Please enter a player to alias.")));
        command.addSyntax((sender, context) -> {
            var result = aliasLookupService.findAliases(context.get(playerArg));
            if (result.target() == null) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 That player has never connected."));
                return;
            }
            if (result.matches().isEmpty()) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("Alias for " + result.target() + "\n"));
                return;
            }
            StringBuilder message = new StringBuilder("Alias for ").append(result.target()).append("\n");
            for (int index = 0; index < result.matches().size(); index++) {
                var match = result.matches().get(index);
                if (index > 0) {
                    message.append("\n");
                }
                message.append(moderationSanctionsService.isBanned(match.username()) ? "§c" + match.username() + "§r" : match.username());
            }
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message.toString()));
        }, playerArg);
        return command;
    }

    private static Command setBlockCommand(PermissionService permissionService) {
        Command command = base(permissionService, "setblock");
        var idArg = ArgumentType.Integer("id");
        var metaArg = ArgumentType.Integer("meta");

        command.setDefaultExecutor((sender, context) -> sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("enter an id")));

        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            Block block = legacyNumericBlock(context.get(idArg), 0);
            if (block == null) {
                sender.sendMessage(CommandMessages.error("Unknown legacy block id '" + context.get(idArg) + "'."));
                return;
            }
            var pos = player.getPosition();
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(pos.toString()));
            player.getInstance().setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), block);
        }, idArg);

        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            int id = context.get(idArg);
            int meta = context.get(metaArg);
            Block block = legacyNumericBlock(id, meta);
            if (block == null) {
                sender.sendMessage(CommandMessages.error("Unknown legacy block '" + id + ":" + meta + "'."));
                return;
            }
            var pos = player.getPosition();
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(pos.toString()));
            player.getInstance().setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), block);
        }, idArg, metaArg);
        return command;
    }

    private static Block legacyNumericBlock(int id, int meta) {
        String namespaced = LEGACY_BLOCK_MAP.get(id + "," + meta);
        if (namespaced == null) {
            namespaced = LEGACY_BLOCK_MAP.get(id + ",0");
        }
        if (namespaced == null) {
            return null;
        }
        return Block.fromKey(namespaced);
    }

    private static Map<String, String> loadLegacyBlockMap() {
        try (var stream = LegacyCompatCommands.class.getClassLoader().getResourceAsStream("legacy-block-map.csv")) {
            if (stream == null) {
                return Map.of();
            }
            Map<String, String> mappings = new HashMap<>();
            String text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String rawLine : text.split("\\R")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 3);
                if (parts.length != 3) {
                    continue;
                }
                mappings.put(parts[0].trim() + "," + parts[1].trim(), parts[2].trim());
            }
            return Map.copyOf(mappings);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static Command achievements(PermissionService permissionService, PlayerProfileService profileService) {
        AchievementService achievementService = new AchievementService(profileService);
        return new AchievementsCommand(permissionService, profileService, achievementService, new AchievementsMenuService(profileService, achievementService));
    }

    private static Command base(PermissionService permissionService, String name, String... aliases) {
        Command command = new Command(name, aliases);
        command.setCondition((sender, commandString) -> {
            String permission = "fallnight.command." + name.toLowerCase(Locale.ROOT);
            if (permissionService.hasPermission(sender, permission)) {
                return true;
            }
            if (commandString != null) {
                sender.sendMessage(CommandMessages.error("You do not have permission (" + permission + ")."));
            }
            return false;
        });
        return command;
    }

    private static Player findOnlinePlayerIgnoreCase(String username) {
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

    private static MineDefinition resolveMine(MineService mineService, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            int id = Integer.parseInt(input.trim());
            return mineService.find(id).orElse(null);
        } catch (NumberFormatException ignored) {
            return mineService.findByName(input.trim());
        }
    }

    private static void clearPlayerInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItemStack(slot, ItemStack.AIR);
        }
        player.setItemInMainHand(ItemStack.AIR);
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private static boolean readBoolean(UserProfile profile, String key, boolean fallback) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("off") || text.equalsIgnoreCase("no")) {
                return false;
            }
        }
        return fallback;
    }

    private static LinkedHashSet<String> blockedNames(UserProfile profile) {
        return new LinkedHashSet<>(stringList(profile.getExtraData().get("blockedPlayers")).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList()));
    }

    private static void storeBlockedNames(UserProfile profile, LinkedHashSet<String> blocked) {
        profile.getExtraData().put("blockedPlayers", List.copyOf(blocked));
    }

    private static LinkedHashSet<String> blockedNamesDisplay(UserProfile profile) {
        return new LinkedHashSet<>(stringList(profile.getExtraData().get("blockedPlayers")));
    }

    private static boolean feedReady(Player player) {
        Object value = player.getTag(net.minestom.server.tag.Tag.Long("feedCooldownAt"));
        long last = value instanceof Long l ? l : 0L;
        return last + 60 <= (System.currentTimeMillis() / 1000L);
    }

    private static long feedCooldownRemaining(Player player) {
        Object value = player.getTag(net.minestom.server.tag.Tag.Long("feedCooldownAt"));
        long last = value instanceof Long l ? l : 0L;
        return Math.max(0L, (last + 60) - (System.currentTimeMillis() / 1000L));
    }

    private static void markFeed(Player player) {
        player.setTag(net.minestom.server.tag.Tag.Long("feedCooldownAt"), System.currentTimeMillis() / 1000L);
    }

    private static String noteInstrumentKey(int instrument) {
        return switch (instrument) {
            case 1 -> "block.note_block.basedrum";
            case 2 -> "block.note_block.snare";
            case 3 -> "block.note_block.hat";
            case 4 -> "block.note_block.bass";
            case 5 -> "block.note_block.flute";
            case 6 -> "block.note_block.bell";
            case 7 -> "block.note_block.guitar";
            case 8 -> "block.note_block.chime";
            case 9 -> "block.note_block.xylophone";
            case 10 -> "block.note_block.iron_xylophone";
            case 11 -> "block.note_block.cow_bell";
            case 12 -> "block.note_block.didgeridoo";
            case 13 -> "block.note_block.bit";
            case 14 -> "block.note_block.banjo";
            case 15 -> "block.note_block.pling";
            default -> "block.note_block.harp";
        };
    }

    private static void collectOnlineInventory(List<String> lines, List<String> armor, Player target) {
        for (net.minestom.server.entity.EquipmentSlot slot : net.minestom.server.entity.EquipmentSlot.armors()) {
            ItemStack stack = target.getInventory().getEquipment(slot, target.getHeldSlot());
            if (stack != null && !stack.isAir() && stack.amount() > 0) {
                armor.add(slot.name().toLowerCase(Locale.ROOT) + ": " + describeStack(stack));
            }
        }
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            ItemStack stack = target.getInventory().getItemStack(slot);
            if (stack == null || stack.material() == Material.AIR || stack.amount() <= 0) {
                continue;
            }
            lines.add("slot " + slot + ": " + describeStack(stack));
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectSnapshotInventory(List<String> lines, List<String> armor, UserProfile profile) {
        Object rawArmor = profile.getExtraData().get("armorSnapshot");
        if (rawArmor instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    Object slotValue = map.get("armorSlot");
                    String slot = slotValue == null ? "armor" : String.valueOf(slotValue);
                    armor.add(slot + ": " + snapshotDescription(map));
                }
            }
        }
        Object rawInventory = profile.getExtraData().get("inventorySnapshot");
        if (rawInventory instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    Object slot = map.get("slot");
                    lines.add("slot " + slot + ": " + snapshotDescription(map));
                }
            }
        }
    }

    private static void fillInventorySeePane(Inventory inventory) {
        ItemStack pane = ItemStack.of(Material.RED_STAINED_GLASS_PANE);
        for (int slot = 36; slot < 45; slot++) {
            inventory.setItemStack(slot, pane);
        }
    }

    private static void fillOnlineInventoryView(Inventory inventory, Player target) {
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            ItemStack stack = target.getInventory().getItemStack(slot);
            if (stack == null || stack.isAir() || stack.amount() <= 0) {
                continue;
            }
            int viewSlot = inventorySeeSlot(slot);
            if (viewSlot >= 0) {
                inventory.setItemStack(viewSlot, stack);
            }
        }
        placeArmor(inventory,
                target.getInventory().getEquipment(net.minestom.server.entity.EquipmentSlot.HELMET, target.getHeldSlot()),
                target.getInventory().getEquipment(net.minestom.server.entity.EquipmentSlot.CHESTPLATE, target.getHeldSlot()),
                target.getInventory().getEquipment(net.minestom.server.entity.EquipmentSlot.LEGGINGS, target.getHeldSlot()),
                target.getInventory().getEquipment(net.minestom.server.entity.EquipmentSlot.BOOTS, target.getHeldSlot())
        );
    }

    private static void fillSnapshotInventoryView(Inventory inventory, UserProfile profile) {
        Object rawInventory = profile.getExtraData().get("inventorySnapshot");
        if (rawInventory instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object slotRaw = map.get("slot");
                if (!(slotRaw instanceof Number number)) {
                    continue;
                }
                int viewSlot = inventorySeeSlot(number.intValue());
                if (viewSlot >= 0) {
                    inventory.setItemStack(viewSlot, PlayerProfileService.deserializeSnapshotItem(map));
                }
            }
        }
        ItemStack helmet = ItemStack.AIR;
        ItemStack chest = ItemStack.AIR;
        ItemStack leggings = ItemStack.AIR;
        ItemStack boots = ItemStack.AIR;
        Object rawArmor = profile.getExtraData().get("armorSnapshot");
        if (rawArmor instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object slotRaw = map.get("armorSlot");
                if (!(slotRaw instanceof String slot)) {
                    continue;
                }
                ItemStack stack = PlayerProfileService.deserializeSnapshotItem(map);
                switch (slot.toLowerCase(Locale.ROOT)) {
                    case "helmet" -> helmet = stack;
                    case "chestplate" -> chest = stack;
                    case "leggings" -> leggings = stack;
                    case "boots" -> boots = stack;
                    default -> {
                    }
                }
            }
        }
        placeArmor(inventory, helmet, chest, leggings, boots);
    }

    private static void placeArmor(Inventory inventory, ItemStack helmet, ItemStack chest, ItemStack leggings, ItemStack boots) {
        inventory.setItemStack(45, helmet == null ? ItemStack.AIR : helmet);
        inventory.setItemStack(46, chest == null ? ItemStack.AIR : chest);
        inventory.setItemStack(47, leggings == null ? ItemStack.AIR : leggings);
        inventory.setItemStack(48, boots == null ? ItemStack.AIR : boots);
    }

    private static int inventorySeeSlot(int slot) {
        if (slot < 0 || slot >= 36) {
            return -1;
        }
        return slot < 9 ? slot + 27 : slot - 9;
    }

    private static String snapshotDescription(Map<?, ?> map) {
        Object nameValue = map.get("name");
        if (nameValue == null) {
            nameValue = map.get("material");
        }
        String name = nameValue == null ? "unknown" : String.valueOf(nameValue);
        Object amount = map.get("amount");
        if (amount == null) {
            amount = 1;
        }
        return name + " x" + amount;
    }

    private static String describeStack(ItemStack stack) {
        var customName = stack.get(net.minestom.server.component.DataComponents.CUSTOM_NAME);
        String name = customName == null
                ? stack.material().name().toLowerCase(Locale.ROOT)
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);
        return name + " x" + stack.amount();
    }

    private static double parseRelative(double original, String input, double min, double max) {
        if (input == null || input.isBlank()) {
            return original;
        }
        double value;
        String trimmed = input.trim();
        if (trimmed.startsWith("~")) {
            String suffix = trimmed.substring(1).trim();
            value = original + (suffix.isEmpty() ? 0D : Double.parseDouble(suffix));
        } else {
            value = Double.parseDouble(trimmed);
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                String value = String.valueOf(entry).trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return values;
        }
        if (raw instanceof Set<?> set) {
            List<String> values = new ArrayList<>();
            for (Object entry : set) {
                if (entry == null) {
                    continue;
                }
                String value = String.valueOf(entry).trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return values;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }
}
