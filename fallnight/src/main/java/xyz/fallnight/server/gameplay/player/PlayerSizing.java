package xyz.fallnight.server.gameplay.player;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;

public final class PlayerSizing {
    private static final int MIN_PERCENT = 50;
    private static final int MAX_PERCENT = 150;

    private PlayerSizing() {
    }

    public static int clampPercent(int sizePercent) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, sizePercent));
    }

    public static double scaleForPercent(int sizePercent) {
        return clampPercent(sizePercent) / 100d;
    }

    public static void apply(Player player, int sizePercent) {
        if (player == null) {
            return;
        }
        double scale = scaleForPercent(sizePercent);
        player.getAttribute(Attribute.SCALE).setBaseValue(scale);
        player.setBoundingBox(0.6d * scale, 1.8d * scale, 0.6d * scale);
    }
}
