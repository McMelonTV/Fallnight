package xyz.fallnight.server.gameplay.player;

import xyz.fallnight.server.ServerConfig;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;

public final class LoginGateModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ServerConfig config;
    private final EventNode<Event> eventNode;

    public LoginGateModule(ServerConfig config) {
        this.config = config;
        this.eventNode = EventNode.all("login-gate");
    }

    public void register() {
        eventNode.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            if (config.maintenanceMode()) {
                String username = event.getUsername() == null ? "" : event.getUsername().trim().toLowerCase(Locale.ROOT);
                boolean whitelisted = config.maintenanceWhitelist().stream()
                    .map(name -> name.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(username::equals);
                if (whitelisted) {
                    return;
                }
                event.getConnection().kick(LEGACY.deserialize("§8[§bFallnight§8]\n§7The server is currently undergoing maintenance.\n§7Contact us at: §bdiscord.fallnight.xyz"));
                return;
            }
            if (MinecraftServer.getConnectionManager().getOnlinePlayers().size() >= config.maxPlayers()) {
                event.getConnection().kick(LEGACY.deserialize("§8[§bFallnight§8]\n§7The server is currently full."));
            }
        });
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }
}
