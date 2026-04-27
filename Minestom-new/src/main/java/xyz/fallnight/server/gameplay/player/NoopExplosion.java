package xyz.fallnight.server.gameplay.player;

import java.util.List;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Explosion;
import net.minestom.server.instance.Instance;

public final class NoopExplosion extends Explosion {
    public NoopExplosion(float centerX, float centerY, float centerZ, float strength) {
        super(centerX, centerY, centerZ, strength);
    }

    @Override
    protected List<Point> prepare(Instance instance) {
        return List.of();
    }

    @Override
    public void apply(Instance instance) {
    }
}
