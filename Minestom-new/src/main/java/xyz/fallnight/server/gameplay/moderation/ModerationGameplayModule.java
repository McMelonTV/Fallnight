package xyz.fallnight.server.gameplay.moderation;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.persistence.moderation.JsonModerationSanctionsRepository;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.ModerationTimeFormatter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class ModerationGameplayModule {
    private final ModerationSanctionsService sanctionsService;
    private final PlayerProfileService profileService;
    private final EventNode<Event> eventNode;
    private final Set<String> trackedMutedPlayers;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private Task muteSweepTask;

    public ModerationGameplayModule(ModerationSanctionsService sanctionsService, PlayerProfileService profileService) {
        this.sanctionsService = sanctionsService;
        this.profileService = profileService;
        this.eventNode = EventNode.all("moderation-gameplay");
        this.trackedMutedPlayers = new HashSet<>();
    }

    public static ModerationGameplayModule createDefaults(Path dataRoot, PlayerProfileService profileService) {
        ModerationSanctionsService sanctionsService = new ModerationSanctionsService(
            new JsonModerationSanctionsRepository(dataRoot.resolve("bans.json")),
            profileService
        );
        return new ModerationGameplayModule(sanctionsService, profileService);
    }

    public void register() {
        try {
            sanctionsService.load();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onPlayerConfiguration);
        eventNode.addListener(PlayerChatEvent.class, this::onPlayerChat);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        muteSweepTask = MinecraftServer.getSchedulerManager()
            .buildTask(this::tickMuteExpirations)
            .repeat(TaskSchedule.seconds(1))
            .schedule();
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        if (muteSweepTask != null) {
            muteSweepTask.cancel();
        }
    }

    public void saveAll() {
        try {
            sanctionsService.saveAll();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public ModerationSanctionsService sanctionsService() {
        return sanctionsService;
    }

    private void onPlayerConfiguration(AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();
        Optional<PlayerBan> ban = sanctionsService.activeBan(player.getUsername());
        if (ban.isEmpty()) {
            return;
        }

        player.kick(CommandMessages.error(banKickMessage(ban.get())));
    }

    private void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();

        if (sanctionsService.activeMute(player.getUsername()).isPresent()) {
            event.setCancelled(true);
            player.sendMessage(LEGACY.deserialize("§c§l>§r§7 You can't talk while muted!"));
            return;
        }

        if (sanctionsService.isGlobalMuteEnabled() && !AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            event.setCancelled(true);
        }
    }

    private void tickMuteExpirations() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            String key = player.getUsername().toLowerCase(java.util.Locale.ROOT);
            boolean muted = sanctionsService.activeMute(player.getUsername()).isPresent();
            if (muted) {
                trackedMutedPlayers.add(key);
                continue;
            }
            if (trackedMutedPlayers.remove(key)) {
                player.sendMessage(CommandMessages.info("You are no longer muted."));
            }
        }
    }

    private static String banKickMessage(PlayerBan ban) {
        StringBuilder builder = new StringBuilder("You are banned. Reason: ").append(ban.reason());
        if (ban.isTemporary()) {
            builder
                .append(". Remaining: ")
                .append(ModerationTimeFormatter.remaining(ban.remaining(Instant.now())));
        }
        return builder.toString();
    }
}
