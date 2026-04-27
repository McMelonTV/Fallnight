package xyz.fallnight.server.domain.koth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class KothHill {
    private String id;
    private String displayName;
    private String world;
    private int x;
    private int y;
    private int z;
    private int radius;
    private int captureSeconds;
    private double rewardMoney;
    private long rewardPrestige;
    private Integer x1;
    private Integer y1;
    private Integer z1;
    private Integer x2;
    private Integer y2;
    private Integer z2;
    private String pathHint;

    public KothHill() {
        this.id = "";
        this.displayName = "";
        this.world = "spawn";
        this.radius = 8;
        this.captureSeconds = 45;
        this.rewardMoney = 5_000d;
        this.rewardPrestige = 25L;
    }

    public KothHill(
        String id,
        String displayName,
        String world,
        int x,
        int y,
        int z,
        int radius,
        int captureSeconds,
        double rewardMoney,
        long rewardPrestige
    ) {
        this(id, displayName, world, x, y, z, radius, captureSeconds, rewardMoney, rewardPrestige, null, null, null, null, null, null, null);
    }

    public KothHill(
        String id,
        String displayName,
        String world,
        int x,
        int y,
        int z,
        int radius,
        int captureSeconds,
        double rewardMoney,
        long rewardPrestige,
        Integer x1,
        Integer y1,
        Integer z1,
        Integer x2,
        Integer y2,
        Integer z2,
        String pathHint
    ) {
        this.id = normalizeId(id);
        this.displayName = sanitizeDisplayName(displayName, id);
        this.world = sanitizeWorld(world);
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(1, radius);
        this.captureSeconds = Math.max(1, captureSeconds);
        this.rewardMoney = Math.max(0d, rewardMoney);
        this.rewardPrestige = Math.max(0L, rewardPrestige);
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.pathHint = sanitizePathHint(pathHint);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = normalizeId(id);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = sanitizeDisplayName(displayName, id);
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = sanitizeWorld(world);
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(1, radius);
    }

    public int getCaptureSeconds() {
        return captureSeconds;
    }

    public void setCaptureSeconds(int captureSeconds) {
        this.captureSeconds = Math.max(1, captureSeconds);
    }

    public double getRewardMoney() {
        return rewardMoney;
    }

    public void setRewardMoney(double rewardMoney) {
        this.rewardMoney = Math.max(0d, rewardMoney);
    }

    public long getRewardPrestige() {
        return rewardPrestige;
    }

    public void setRewardPrestige(long rewardPrestige) {
        this.rewardPrestige = Math.max(0L, rewardPrestige);
    }

    public Integer getX1() {
        return x1;
    }

    public void setX1(Integer x1) {
        this.x1 = x1;
    }

    public Integer getY1() {
        return y1;
    }

    public void setY1(Integer y1) {
        this.y1 = y1;
    }

    public Integer getZ1() {
        return z1;
    }

    public void setZ1(Integer z1) {
        this.z1 = z1;
    }

    public Integer getX2() {
        return x2;
    }

    public void setX2(Integer x2) {
        this.x2 = x2;
    }

    public Integer getY2() {
        return y2;
    }

    public void setY2(Integer y2) {
        this.y2 = y2;
    }

    public Integer getZ2() {
        return z2;
    }

    public void setZ2(Integer z2) {
        this.z2 = z2;
    }

    public String getPathHint() {
        return pathHint;
    }

    public void setPathHint(String pathHint) {
        this.pathHint = sanitizePathHint(pathHint);
    }

    public boolean usesBounds() {
        return x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null;
    }

    public boolean contains(double x, double y, double z) {
        if (usesBounds()) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        double radiusSquared = (double) radius * radius;
        return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared;
    }

    public KothHill normalizedCopy() {
        return new KothHill(
            id,
            displayName,
            world,
            x,
            y,
            z,
            radius,
            captureSeconds,
            rewardMoney,
            rewardPrestige,
            x1,
            y1,
            z1,
            x2,
            y2,
            z2,
            pathHint
        );
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replace(' ', '_');
    }

    private static String sanitizeDisplayName(String displayName, String fallbackId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        if (fallbackId != null && !fallbackId.isBlank()) {
            return fallbackId;
        }
        return "Hill";
    }

    private static String sanitizeWorld(String world) {
        if (world == null || world.isBlank()) {
            return "spawn";
        }
        return world.trim();
    }

    private static String sanitizePathHint(String pathHint) {
        if (pathHint == null || pathHint.isBlank()) {
            return null;
        }
        return pathHint.trim();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof KothHill other)) {
            return false;
        }
        return x == other.x
            && y == other.y
            && z == other.z
            && radius == other.radius
            && captureSeconds == other.captureSeconds
            && Double.compare(other.rewardMoney, rewardMoney) == 0
            && rewardPrestige == other.rewardPrestige
            && Objects.equals(x1, other.x1)
            && Objects.equals(y1, other.y1)
            && Objects.equals(z1, other.z1)
            && Objects.equals(x2, other.x2)
            && Objects.equals(y2, other.y2)
            && Objects.equals(z2, other.z2)
            && Objects.equals(pathHint, other.pathHint)
            && Objects.equals(id, other.id)
            && Objects.equals(displayName, other.displayName)
            && Objects.equals(world, other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, world, x, y, z, radius, captureSeconds, rewardMoney, rewardPrestige, x1, y1, z1, x2, y2, z2, pathHint);
    }
}
