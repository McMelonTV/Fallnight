package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.gameplay.koth.KothGameplayModule;
import xyz.fallnight.server.service.KothService;
import java.util.Optional;
import net.minestom.server.command.CommandSender;

public final class KothCommand extends FallnightCommand {
    private static final String ADMIN_PERMISSION = "fallnight.command.koth.admin";

    private final PermissionService permissionService;
    private final KothService kothService;
    private final KothGameplayModule gameplayModule;

    public KothCommand(PermissionService permissionService, KothService kothService, KothGameplayModule gameplayModule) {
        super("koth", permissionService);
        this.permissionService = permissionService;
        this.kothService = kothService;
        this.gameplayModule = gameplayModule;

        setDefaultExecutor((sender, context) -> sendStatus(sender));
    }

    @Override
    public String permission() {
        return "fallnight.command.koth";
    }

    @Override
    public String summary() {
        return "get info about koth";
    }

    @Override
    public String usage() {
        return "/koth";
    }

    private void sendStatus(CommandSender sender) {
        Optional<KothService.KothState> active = kothService.activeHillState();
        if (active.isEmpty()) {
            long remaining = Math.max(0L, kothService.nextStartEpochSecond() - java.time.Instant.now().getEpochSecond());
            if (kothService.spawnNext() == 1) {
                sender.sendMessage(CommandMessages.info("The next Spawn KOTH event is in §b" + formatDuration(remaining) + "§7."));
            } else {
                sender.sendMessage(CommandMessages.info("The next Mine PvP KOTH event is in §b" + formatDuration(remaining) + "§7."));
            }
        } else {
            KothService.KothState state = active.get();
            String location = state.hill().getWorld().equalsIgnoreCase(kothService.spawnWorldName()) ? "Spawn PvP" : "Mine PvP";
            sender.sendMessage(CommandMessages.info(
                "§b" + state.hill().getDisplayName() + " §7KOTH is currently occurring at §b" + location + "§7!"
            ));
        }
    }

    private static String formatDuration(long seconds) {
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

    private void listHills(CommandSender sender) {
        var hills = kothService.hills();
        if (hills.isEmpty()) {
            sender.sendMessage(CommandMessages.info("No KOTH hills configured."));
            return;
        }

        sender.sendMessage(CommandMessages.info("KOTH hills (" + hills.size() + ")"));
        for (var hill : hills) {
            sender.sendMessage(CommandMessages.info(
                "- " + hill.getId() + " (" + hill.getDisplayName() + ")"
                    + " @ " + hill.getWorld() + " " + hill.getX() + "," + hill.getY() + "," + hill.getZ()
                    + " r=" + hill.getRadius() + " capture=" + hill.getCaptureSeconds() + "s"
            ));
        }
    }

    private void startKoth(CommandSender sender, String hillId) {
        if (!ensureAdmin(sender)) {
            return;
        }

        KothService.StartResult result = kothService.startEvent(hillId);
        KothGameplayModule.sendStartFeedback(sender, result);
        gameplayModule.broadcastStart(result);
    }

    private void stopKoth(CommandSender sender) {
        if (!ensureAdmin(sender)) {
            return;
        }

        KothService.StopResult result = kothService.stopEvent();
        KothGameplayModule.sendStopFeedback(sender, result);
        gameplayModule.broadcastStop(result);
    }

    private void setNext(CommandSender sender, String id) {
        if (!ensureAdmin(sender)) {
            return;
        }

        if (!kothService.setNextHill(id)) {
            sender.sendMessage(CommandMessages.error("Unknown hill id."));
            return;
        }
        sender.sendMessage(CommandMessages.success("Next KOTH hill set to " + id + "."));
    }

    private boolean ensureAdmin(CommandSender sender) {
        if (permissionService.hasPermission(sender, ADMIN_PERMISSION)) {
            return true;
        }

        sender.sendMessage(CommandMessages.error("You do not have permission (" + ADMIN_PERMISSION + ")."));
        return false;
    }
}
