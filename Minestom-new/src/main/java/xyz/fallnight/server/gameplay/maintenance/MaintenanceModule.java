package xyz.fallnight.server.gameplay.maintenance;

import xyz.fallnight.server.command.framework.CommandMessages;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class MaintenanceModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static MaintenanceModule active;
    private static final long RESTART_SECONDS = 24 * 3601L;
    private static final long CLEAR_SECONDS = 610L;
    private static final List<Long> RESTART_ANNOUNCES = List.of(24L*3600, 12L*3600, 11L*3600, 10L*3600, 9L*3600, 8L*3600, 7L*3600, 6L*3600, 5L*3600, 4L*3600, 3L*3600, 2L*3600, 3600L, 1800L, 900L, 600L, 300L, 180L, 120L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);
    private static final List<Long> CLEAR_ANNOUNCES = List.of(900L, 600L, 300L, 60L, 30L, 10L, 5L, 1L);

    private final Deque<Long> restartQueue = new ArrayDeque<>(RESTART_ANNOUNCES);
    private final Deque<Long> clearQueue = new ArrayDeque<>(CLEAR_ANNOUNCES);
    private long restartRemaining = RESTART_SECONDS;
    private long clearRemaining = CLEAR_SECONDS;
    private Task task;
    private String relaunchCommand;
    private String transferHost;
    private int transferPort;

    public void register() {
        active = this;
        task = MinecraftServer.getSchedulerManager().buildTask(this::tick).repeat(TaskSchedule.seconds(1)).schedule();
    }

    public void unregister() {
        active = null;
        if (task != null) {
            task.cancel();
        }
    }

    public static boolean scheduleRestart(long seconds) {
        if (active == null) {
            return false;
        }
        active.restartRemaining = Math.max(1L, seconds);
        active.restartQueue.clear();
        for (Long announce : RESTART_ANNOUNCES) {
            if (announce <= active.restartRemaining) {
                active.restartQueue.addLast(announce);
            }
        }
        return true;
    }

    public static void configureRelaunch(String relaunchCommand) {
        if (active != null) {
            active.relaunchCommand = relaunchCommand;
        }
    }

    public static void configureTransferTarget(String host, int port) {
        if (active != null) {
            active.transferHost = host;
            active.transferPort = port;
        }
    }

    private void tick() {
        restartRemaining--;
        clearRemaining--;

        while (!restartQueue.isEmpty() && restartQueue.peekFirst() > restartRemaining) {
            restartQueue.removeFirst();
        }
        if (!restartQueue.isEmpty() && restartQueue.peekFirst() == restartRemaining) {
            long announce = restartQueue.removeFirst();
            broadcast("The server is restarting in " + timeText(announce) + ".");
        }
        while (!clearQueue.isEmpty() && clearQueue.peekFirst() >= clearRemaining) {
            long announce = clearQueue.removeFirst();
            if (announce == clearRemaining) {
                broadcast("All item entities clearing in " + timeText(announce) + ".");
            }
        }

        if (clearRemaining <= 0) {
            broadcast("Now clearing all item entities...");
            clearItemEntities();
            clearRemaining = CLEAR_SECONDS;
            clearQueue.clear();
            clearQueue.addAll(CLEAR_ANNOUNCES);
        }

        if (restartRemaining <= 0) {
            broadcast("The server is now restarting...");
            transferPlayers();
            relaunch();
            MinecraftServer.stopCleanly();
        }
    }

    private void transferPlayers() {
        if (transferHost == null || transferHost.isBlank() || transferPort <= 0) {
            return;
        }
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
            player.sendMessage(CommandMessages.info("The server is restarting..."));
            player.getPlayerConnection().transfer(transferHost, transferPort);
        });
    }

    private void relaunch() {
        if (relaunchCommand == null || relaunchCommand.isBlank()) {
            return;
        }
        try {
            new ProcessBuilder("bash", "-lc", relaunchCommand).start();
        } catch (IOException ignored) {
        }
    }

    public static int clearItemEntities() {
        int removed = 0;
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity entity : instance.getEntities()) {
                if (entity instanceof ItemEntity) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private void broadcast(String message) {
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
            player.sendMessage(LEGACY.deserialize("§r§8[§bFN§8]§r§7 " + message))
        );
    }

    private static String timeText(long seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        if (seconds >= 60 && seconds % 60 == 0) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
