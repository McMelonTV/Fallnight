package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class PerformanceCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public PerformanceCommand(PermissionService permissionService) {
        super("performance", permissionService);

        setDefaultExecutor((sender, context) -> {
            long end = System.nanoTime() + 1_000_000_000L;
            long iterations = 0L;
            while (System.nanoTime() < end) {
                ThreadLocalRandom.current().nextInt(-9999, 10000);
                iterations++;
            }
            sender.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 The performance test scored §b" + iterations + "§7."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.performance";
    }

    @Override
    public String summary() {
        return "do a server performance test";
    }

    @Override
    public String usage() {
        return "/performance";
    }
}
