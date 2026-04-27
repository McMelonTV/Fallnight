package xyz.fallnight.server.gameplay.gang;

import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.service.GangService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;

public final class GangChatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(GangChatModule.class);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final GangService gangService;
    private final EventNode<Event> eventNode;

    public GangChatModule(GangService gangService) {
        this.gangService = gangService;
        this.eventNode = EventNode.all("gang-chat");
    }

    public void register() {
        eventNode.addListener(PlayerChatEvent.class, event -> {
            String raw = event.getRawMessage();
            if (!(raw.startsWith(".gc ") || raw.equalsIgnoreCase(".gc") || raw.startsWith(".gangchat ") || raw.equalsIgnoreCase(".gangchat"))) {
                return;
            }
            Player player = event.getPlayer();
            Optional<Gang> gang = gangService.findGangForUser(player.getUsername());
            if (gang.isEmpty()) {
                return;
            }
            event.setCancelled(true);
            String[] parts = raw.split(" ", 2);
            String message = parts.length < 2 ? "" : parts[1];
            String line = "§2[" + gang.get().getName() + "] §r§1" + player.getUsername() + "§r§8: §r§7" + message + "⛏";
            for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (gangService.findGangForUser(online.getUsername()).filter(g -> g.equals(gang.get())).isPresent()) {
                    online.sendMessage(LEGACY.deserialize(line));
                }
            }
            LOGGER.info("§8[{}] §4{}§r§8: §r§7{}", gang.get().getName(), player.getUsername(), message);
        });
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }
}
