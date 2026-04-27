package xyz.fallnight.server.gameplay.community;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerStartSneakingEvent;
import net.minestom.server.event.player.PlayerStopSneakingEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommunityBehaviorModule {
    private static final long AFK_AFTER_SECONDS = 300L;
    private static final long KICK_AFTER_SECONDS = 600L;
    private static final double SPAM_BLOCK_THRESHOLD = 110D;
    private static final Sound CHAT_POP_SOUND = Sound.sound(Key.key("minecraft:entity.item.pickup"), Sound.Source.PLAYER, 1.0f, 2.0f);
    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityBehaviorModule.class);

    private final PlayerProfileService profileService;
    private final PermissionService permissionService;
    private final EventNode<Event> eventNode;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
    private Task afkTask;
    private long lastTickAtMillis;

    public CommunityBehaviorModule(PlayerProfileService profileService, PermissionService permissionService) {
        this.profileService = profileService;
        this.permissionService = permissionService;
        this.eventNode = EventNode.all("community-behavior");
    }

    public void register() {
        eventNode.addListener(PlayerMoveEvent.class, this::onMove);
        eventNode.addListener(PlayerCommandEvent.class, this::onCommand);
        eventNode.addListener(PlayerChatEvent.class, this::onChat);
        eventNode.addListener(PlayerBlockBreakEvent.class, event -> markActive(event.getPlayer(), true));
        eventNode.addListener(PlayerBlockInteractEvent.class, event -> markActive(event.getPlayer(), true));
        eventNode.addListener(PlayerStartSneakingEvent.class, event -> markActive(event.getPlayer(), true));
        eventNode.addListener(PlayerStopSneakingEvent.class, event -> markActive(event.getPlayer(), true));
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        afkTask = MinecraftServer.getSchedulerManager()
            .buildTask(this::tickAfkAndSpam)
            .repeat(TaskSchedule.seconds(1))
            .schedule();
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        if (afkTask != null) {
            afkTask.cancel();
        }
    }

    private void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        markActive(player, true);
    }

    private void onCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String command = event.getCommand();
        event.setCommand(lowercaseRoot(command));
        notifyCommandSpy(player, event.getCommand());
        String root = commandRoot(event.getCommand());
        if (!root.equals("afk")) {
            markActive(player, true);
        }
    }

    private void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getRawMessage();
        player.playSound(CHAT_POP_SOUND);
        markActive(player, true);

        if (handleStaffChat(event, player, message)) {
            return;
        }

        if (isSpam(player, message)) {
            event.setCancelled(true);
            player.sendMessage(legacy.deserialize("§r§c§l> §r§7Please slow down your chat speed!"));
            return;
        }

        notifyAfkMentions(player, message);
    }

    private boolean handleStaffChat(PlayerChatEvent event, Player player, String message) {
        String trimmed = message.trim();
        if (!(trimmed.startsWith(".sc ") || trimmed.equals(".sc") || trimmed.startsWith(".staffchat ") || trimmed.equals(".staffchat"))) {
            return false;
        }
        if (!permissionService.hasPermission(player, "fallnight.staffchat.send")) {
            return false;
        }
        event.setCancelled(true);
        String payload = trimmed.contains(" ") ? trimmed.substring(trimmed.indexOf(' ') + 1).trim() : "";
        LOGGER.info("§8[STAFF] §4{}§r§8: §r§7{}", player.getUsername(), payload);
        String line = "§8[SC] §r§4" + player.getUsername() + "§r§8: §r§7" + payload + "⛏";
        for (Player receiver : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (permissionService.hasPermission(receiver, "fallnight.staffchat.see")) {
                receiver.sendMessage(legacy.deserialize(line));
            }
        }
        return true;
    }

    private boolean isSpam(Player player, String message) {
        var profile = profileService.getOrCreate(player);
        double spamScore = readDouble(profile.getExtraData().get("spamScore"));
        spamScore += 10D;
        String lastMessage = stringValue(profile.getExtraData().get("lastMessage"));
        if (message.equals(lastMessage)) {
            spamScore += 35D;
        }
        long now = System.currentTimeMillis();
        spamScore += 50D;
        spamScore = Math.min(spamScore, 250D);
        profile.getExtraData().put("spamScore", spamScore);
        profile.getExtraData().put("lastMessage", message);
        profile.getExtraData().put("lastMessageAt", now);
        profileService.save(profile);
        return spamScore >= SPAM_BLOCK_THRESHOLD;
    }

    private void notifyAfkMentions(Player sender, String message) {
        String[] words = message.split("\\s+");
        for (Player target : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!afkEnabled(target)) {
                continue;
            }
            String usernameMention = "@" + target.getUsername();
            String displayMention = "@" + displayName(target);
            for (String word : words) {
                if (word.equalsIgnoreCase(usernameMention) || word.equalsIgnoreCase(displayMention)) {
                    sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§b§l>§r§7 Player §b" + target.getUsername() + " §r§7 is currently afk."));
                    break;
                }
            }
        }
    }

    private void notifyCommandSpy(Player source, String command) {
        LOGGER.info("§8[C-SPY] §r§9{} §8>§r§7 /{}", source.getUsername(), command);
        String line = "§9" + source.getUsername() + " §8>§r§7 /" + command + "⛏";
        for (Player receiver : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (receiver.getUuid().equals(source.getUuid())) {
                continue;
            }
            if (!commandSpyEnabled(receiver)) {
                continue;
            }
            receiver.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(line));
        }
    }

    private void tickAfkAndSpam() {
        long nowMillis = System.currentTimeMillis();
        double secondsElapsed = tickElapsedSeconds(nowMillis);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            var profile = profileService.getOrCreate(player);
            double spamScore = Math.max(0D, readDouble(profile.getExtraData().get("spamScore")) - 75D * secondsElapsed);
            profile.getExtraData().put("spamScore", spamScore);

            long lastActivityAt = readLong(profile.getExtraData().get("lastActivityAt"));
            if (lastActivityAt <= 0L) {
                profile.getExtraData().put("lastActivityAt", nowSeconds);
                profileService.save(profile);
                continue;
            }

            boolean afk = readBoolean(profile.getExtraData().get("afk"));
            if (!afk && lastActivityAt + AFK_AFTER_SECONDS < nowSeconds) {
                profile.getExtraData().put("afk", true);
                player.sendMessage(legacy.deserialize("§r§b§l>§r§7 You are now AFK."));
                afk = true;
            }
            if (afk && lastActivityAt + KICK_AFTER_SECONDS < nowSeconds) {
                player.kick(legacy.deserialize("§r§b§l>§r§7 You have been kicked for going AFK for too long."));
            }
            profileService.save(profile);
        }
    }

    private void markActive(Player player, boolean clearAfk) {
        var profile = profileService.getOrCreate(player);
        profile.getExtraData().put("lastActivityAt", System.currentTimeMillis() / 1000L);
        if (clearAfk && readBoolean(profile.getExtraData().get("afk"))) {
            profile.getExtraData().put("afk", false);
            player.sendMessage(legacy.deserialize("§r§b§l>§r§7 You are no longer AFK."));
        }
        profileService.save(profile);
    }

    private double tickElapsedSeconds(long nowMillis) {
        if (lastTickAtMillis <= 0L) {
            lastTickAtMillis = nowMillis - 1_000L;
        }
        double elapsed = Math.max(0D, (nowMillis - lastTickAtMillis) / 1_000D);
        lastTickAtMillis = nowMillis;
        return elapsed;
    }

    private boolean afkEnabled(Player player) {
        return readBoolean(profileService.getOrCreate(player).getExtraData().get("afk"));
    }

    private boolean commandSpyEnabled(Player player) {
        return readBoolean(profileService.getOrCreate(player).getExtraData().get("commandSpy"));
    }

    private static String lowercaseRoot(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
        int space = trimmed.indexOf(' ');
        if (space == -1) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.substring(0, space).toLowerCase(Locale.ROOT) + trimmed.substring(space);
    }

    private static String commandRoot(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
        int space = trimmed.indexOf(' ');
        return (space == -1 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    private static boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return false;
    }

    private static long readLong(Object value) {
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

    private static double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String displayName(Player player) {
        Object nickname = profileService.getOrCreate(player).getExtraData().get("nickname");
        if (nickname instanceof String value && !value.isBlank()) {
            return value.replace('&', '§');
        }
        return player.getUsername();
    }
}
