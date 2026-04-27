package xyz.fallnight.server.domain.mine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class MineRegion {
    private final int x1;
    private final int y1;
    private final int z1;
    private final int x2;
    private final int y2;
    private final int z2;

    @JsonCreator
    public MineRegion(
        @JsonProperty("x1") @JsonAlias("minX") int x1,
        @JsonProperty("y1") @JsonAlias("minY") int y1,
        @JsonProperty("z1") @JsonAlias("minZ") int z1,
        @JsonProperty("x2") @JsonAlias("maxX") int x2,
        @JsonProperty("y2") @JsonAlias("maxY") int y2,
        @JsonProperty("z2") @JsonAlias("maxZ") int z2
    ) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    public int x1() {
        return x1;
    }

    public int y1() {
        return y1;
    }

    public int z1() {
        return z1;
    }

    public int x2() {
        return x2;
    }

    public int y2() {
        return y2;
    }

    public int z2() {
        return z2;
    }

    public int minX() {
        return Math.min(x1, x2);
    }

    public int maxX() {
        return Math.max(x1, x2);
    }

    public int minY() {
        return Math.min(y1, y2);
    }

    public int maxY() {
        return Math.max(y1, y2);
    }

    public int minZ() {
        return Math.min(z1, z2);
    }

    public int maxZ() {
        return Math.max(z1, z2);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX() && x <= maxX()
            && y >= minY() && y <= maxY()
            && z >= minZ() && z <= maxZ();
    }

    public long volume() {
        return (long) (maxX() - minX() + 1)
            * (maxY() - minY() + 1)
            * (maxZ() - minZ() + 1);
    }
}
