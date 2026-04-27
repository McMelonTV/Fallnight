package xyz.fallnight.server.command.framework;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CommandMessages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private CommandMessages() {
    }

    public static Component info(String message) {
        return LEGACY.deserialize("§b§l> §r§7" + message);
    }

    public static Component error(String message) {
        return LEGACY.deserialize("§c§l> §r§7" + message);
    }

    public static Component success(String message) {
        return LEGACY.deserialize("§b§l> §r§7" + message);
    }
}
