package xyz.fallnight.server.gameplay.koth;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.KothService;
import java.util.ArrayList;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.List;

public final class KothGameplayModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String SCORE_LINE_0 = "koth-0";
    private static final String SCORE_LINE_1 = "koth-1";
    private static final String SCORE_LINE_2 = "koth-2";
    private static final String SCORE_LINE_3 = "koth-3";
    private final KothService kothService;
    private final GangService gangService;
    private final Sidebar sidebar;
    private Task tickTask;
    private static final List<Long> ANNOUNCES = List.of(4L*3600, 3L*3600, 2L*3600, 3600L, 1800L, 600L, 300L, 60L, 15L, 3L, 2L, 1L);
    private int announceTicks;
    private boolean sidebarInitialized;

    public KothGameplayModule(KothService kothService, GangService gangService) {
        this.kothService = kothService;
        this.gangService = gangService;
        this.sidebar = new Sidebar(LEGACY.deserialize("§r§8[§l§aKOTH§r§8]"));
    }

    public void register() {
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(this::tickOnce)
            .repeat(TaskSchedule.tick(10))
            .schedule();
    }

    public void unregister() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        clearSidebar();
    }

    public void broadcastStart(KothService.StartResult result) {
        if (result.status() != KothService.StartStatus.SUCCESS || result.hill() == null) {
            return;
        }
        boolean spawnHill = result.hill().getWorld().equalsIgnoreCase(kothService.spawnWorldName());
        String location = spawnHill ? "Spawn PvP" : "Mine PvP";
        String hint = spawnHill
            ? "Go to the tree in the middle of spawn pvp to get there!"
            : "Follow the §a" + (result.hill().getPathHint() == null ? result.hill().getDisplayName() : result.hill().getPathHint()) + " §7path to get there!";
        broadcast(LEGACY.deserialize(
            "§8§l<-----§aKOTH§8----->§r\n §a§l> §r§a" + result.hill().getDisplayName() + " §7KOTH has started in " + location + "!§r\n §a§l> §r§7" + hint + "§r\n §a§8§l<-----++----->"
        ));
    }

    public void broadcastStop(KothService.StopResult result) {
        if (result.status() != KothService.StopStatus.SUCCESS) {
            return;
        }
        String name = result.stoppedHill() == null ? "unknown hill" : result.stoppedHill().getDisplayName();
        broadcast(CommandMessages.info("KOTH stopped for " + name + "."));
        clearSidebar();
    }

    private void tickOnce() {
        KothService.KothState currentState = kothService.activeHillState().orElse(null);
        if (currentState == null) {
            clearSidebar();
            tickAutoSchedule();
        } else {
            updateSidebar(currentState);
        }

        KothService.TickResult result = kothService.tickCapture(MinecraftServer.getConnectionManager().getOnlinePlayers());
        if (result.state() != KothService.TickState.WINNER || result.reward() == null || result.hill() == null) {
            if (currentState == null) {
                kothService.activeHillState().ifPresent(this::updateSidebar);
            }
            return;
        }

        KothService.RewardResult reward = result.reward();
        Gang winnerGang = gangService.findGangForUser(reward.winnerUsername()).orElse(null);
        String gangPrefix = winnerGang == null ? "" : "§r§8[§7" + winnerGang.getName() + "§r§8] ";
        broadcast(LEGACY.deserialize(
            "§8§l<-----§aKOTH§8----->§r\n §a§l> §r" + gangPrefix + "§r§a" + reward.winnerUsername() + " §7has won KOTH!§r\n §a§l> §r§7Thank you everyone who participated!§r\n §a§l> §r§7The next Mine PvP KOTH will start in 30 hours.§r\n §a§l> §r§7Spawn PvP KOTH will start in 6 hours.§r\n §a§8§l<-----++----->"
        ));
        clearSidebar();
        kothService.advanceScheduleAfterWin();
    }

    private void tickAutoSchedule() {
        announceTicks++;
        long diff = kothService.nextStartEpochSecond() - java.time.Instant.now().getEpochSecond();
        if (diff <= 0) {
            String hillId = kothService.resolveScheduledHillId();
            KothService.StartResult result = kothService.startEvent(hillId);
            if (result.status() == KothService.StartStatus.SUCCESS) {
                broadcastStart(result);
            }
            return;
        }
        if (ANNOUNCES.contains(diff) && announceTicks % 2 == 0) {
            String label = diff >= 3600 ? (diff / 3600) + (diff == 3600 ? " hour" : " hours")
                : diff >= 60 ? (diff / 60) + (diff == 60 ? " minute" : " minutes")
                : diff + (diff == 1 ? " second" : " seconds");
            broadcast(LEGACY.deserialize(
                "§r§8§l[§aKOTH§8]§r§7 " + (kothService.spawnNext() == 0 ? "Mine PvP" : "Spawn") + " KOTH is starting in §a" + label + "§r§7."
            ));
        }
    }

    public List<String> renderScoreboardLines() {
        return kothService.activeHillState().map(this::renderScoreboardLines).orElse(List.of());
    }

    public List<String> renderScoreboardLines(KothService.KothState state) {
        if (state == null || state.hill() == null) {
            return List.of();
        }
        int timeLeftTicks = Math.max(0, state.captureSeconds() - state.progressSeconds());
        List<String> lines = new ArrayList<>();
        lines.add("§7 ");
        lines.add("  §r§7" + state.hill().getDisplayName() + ": §r§a" + formatLegacyDuration(timeLeftTicks) + "   ");
        lines.add("§7");
        lines.add("");
        if (state.capturer() != null && !state.capturer().isBlank()) {
            lines.set(2, "  §r§7Capping: §r§a" + state.capturer() + "   ");
            Gang gang = gangService.findGangForUser(state.capturer()).orElse(null);
            if (gang != null) {
                lines.set(3, "  §r§7Gang: §r§a" + gang.getName() + "   ");
            }
        }
        return List.copyOf(lines);
    }

    private static String formatLegacyDuration(int halfSecondTicks) {
        long seconds = Math.max(0L, halfSecondTicks / 2L);
        if (seconds >= 3600L) {
            long hours = seconds / 3600L;
            return hours + (hours == 1L ? " hour" : " hours");
        }
        if (seconds >= 60L) {
            long minutes = seconds / 60L;
            return minutes + (minutes == 1L ? " minute" : " minutes");
        }
        return seconds + (seconds == 1L ? " second" : " seconds");
    }

    private void updateSidebar(KothService.KothState state) {
        ensureSidebarLines();
        List<String> lines = renderScoreboardLines(state);
        if (lines.isEmpty()) {
            clearSidebar();
            return;
        }
        sidebar.updateLineContent(SCORE_LINE_0, LEGACY.deserialize(lines.get(0)));
        sidebar.updateLineContent(SCORE_LINE_1, LEGACY.deserialize(lines.get(1)));
        sidebar.updateLineContent(SCORE_LINE_2, LEGACY.deserialize(lines.get(2)));
        sidebar.updateLineContent(SCORE_LINE_3, LEGACY.deserialize(lines.get(3)));
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!sidebar.getViewers().contains(player)) {
                sidebar.addViewer(player);
            }
        }
    }

    private void ensureSidebarLines() {
        if (sidebarInitialized) {
            return;
        }
        sidebar.createLine(new Sidebar.ScoreboardLine(SCORE_LINE_0, Component.empty(), 4, null));
        sidebar.createLine(new Sidebar.ScoreboardLine(SCORE_LINE_1, Component.empty(), 3, null));
        sidebar.createLine(new Sidebar.ScoreboardLine(SCORE_LINE_2, Component.empty(), 2, null));
        sidebar.createLine(new Sidebar.ScoreboardLine(SCORE_LINE_3, Component.empty(), 1, null));
        sidebarInitialized = true;
    }

    private void clearSidebar() {
        if (sidebar.getViewers().isEmpty()) {
            return;
        }
        for (Player viewer : new ArrayList<>(sidebar.getViewers())) {
            sidebar.removeViewer(viewer);
        }
    }

    private static void broadcast(net.kyori.adventure.text.Component component) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    public static void sendStartFeedback(CommandSender sender, KothService.StartResult result) {
        switch (result.status()) {
            case SUCCESS -> sender.sendMessage(CommandMessages.success("Started KOTH at " + result.hill().getDisplayName() + "."));
            case ALREADY_ACTIVE -> sender.sendMessage(CommandMessages.error(
                "KOTH is already active at " + (result.hill() == null ? "an unknown hill" : result.hill().getDisplayName()) + "."
            ));
            case HILL_NOT_FOUND -> sender.sendMessage(CommandMessages.error("Unknown hill id."));
        }
    }

    public static void sendStopFeedback(CommandSender sender, KothService.StopResult result) {
        switch (result.status()) {
            case SUCCESS -> sender.sendMessage(CommandMessages.success("Stopped the active KOTH event."));
            case NOT_ACTIVE -> sender.sendMessage(CommandMessages.error("No KOTH event is currently active."));
        }
    }
}
