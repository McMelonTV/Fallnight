package xyz.fallnight.server.domain.pvp;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class PvpZone {
    private String name;
    private String world;
    private PvpZoneRegion region;
    private boolean safe;

    public PvpZone() {
    }

    public PvpZone(String name, String world, PvpZoneRegion region) {
        this.name = Objects.requireNonNull(name, "name");
        this.world = Objects.requireNonNull(world, "world");
        this.region = Objects.requireNonNull(region, "region");
    }

    public boolean isSafe() {
        return safe;
    }

    @JsonProperty("safe")
    @JsonAlias({"safeZone", "safezone"})
    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    @JsonAlias({"id", "zone"})
    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    @JsonProperty("world")
    @JsonAlias({"worldName", "level"})
    public void setWorld(String world) {
        this.world = world;
    }

    public PvpZoneRegion getRegion() {
        return region;
    }

    public void setRegion(PvpZoneRegion region) {
        this.region = region;
    }

    @JsonProperty("x1")
    public int getX1() {
        return region == null ? 0 : region.x1();
    }

    @JsonProperty("x1")
    public void setX1(int x1) {
        setRegionCoord(x1, null, null, null, null, null);
    }

    @JsonProperty("y1")
    public int getY1() {
        return region == null ? 0 : region.y1();
    }

    @JsonProperty("y1")
    public void setY1(int y1) {
        setRegionCoord(null, y1, null, null, null, null);
    }

    @JsonProperty("z1")
    public int getZ1() {
        return region == null ? 0 : region.z1();
    }

    @JsonProperty("z1")
    public void setZ1(int z1) {
        setRegionCoord(null, null, z1, null, null, null);
    }

    @JsonProperty("x2")
    public int getX2() {
        return region == null ? 0 : region.x2();
    }

    @JsonProperty("x2")
    public void setX2(int x2) {
        setRegionCoord(null, null, null, x2, null, null);
    }

    @JsonProperty("y2")
    public int getY2() {
        return region == null ? 0 : region.y2();
    }

    @JsonProperty("y2")
    public void setY2(int y2) {
        setRegionCoord(null, null, null, null, y2, null);
    }

    @JsonProperty("z2")
    public int getZ2() {
        return region == null ? 0 : region.z2();
    }

    @JsonProperty("z2")
    public void setZ2(int z2) {
        setRegionCoord(null, null, null, null, null, z2);
    }

    @JsonProperty("pos1")
    @JsonAlias({"loc1", "point1", "location1"})
    public void setPos1(Object raw) {
        ParsedPoint point = ParsedPoint.parse(raw);
        if (point == null) {
            return;
        }
        setRegionCoord(point.x, point.y, point.z, null, null, null);
        if (world == null && point.world != null) {
            world = point.world;
        }
    }

    @JsonProperty("pos2")
    @JsonAlias({"loc2", "point2", "location2"})
    public void setPos2(Object raw) {
        ParsedPoint point = ParsedPoint.parse(raw);
        if (point == null) {
            return;
        }
        setRegionCoord(null, null, null, point.x, point.y, point.z);
        if (world == null && point.world != null) {
            world = point.world;
        }
    }

    public boolean contains(int x, int y, int z, String worldName) {
        if (region == null) {
            return false;
        }
        if (worldName != null && world != null && !world.equalsIgnoreCase(worldName)) {
            return false;
        }
        return region.contains(x, y, z);
    }

    private void setRegionCoord(
        Integer x1,
        Integer y1,
        Integer z1,
        Integer x2,
        Integer y2,
        Integer z2
    ) {
        int currentX1 = region == null ? 0 : region.x1();
        int currentY1 = region == null ? 0 : region.y1();
        int currentZ1 = region == null ? 0 : region.z1();
        int currentX2 = region == null ? 0 : region.x2();
        int currentY2 = region == null ? 0 : region.y2();
        int currentZ2 = region == null ? 0 : region.z2();
        this.region = new PvpZoneRegion(
            x1 == null ? currentX1 : x1,
            y1 == null ? currentY1 : y1,
            z1 == null ? currentZ1 : z1,
            x2 == null ? currentX2 : x2,
            y2 == null ? currentY2 : y2,
            z2 == null ? currentZ2 : z2
        );
    }

    private static final class ParsedPoint {
        private final int x;
        private final int y;
        private final int z;
        private final String world;

        private ParsedPoint(int x, int y, int z, String world) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }

        private static ParsedPoint parse(Object raw) {
            if (raw instanceof Map<?, ?> map) {
                Integer x = intValue(map.get("x"));
                Integer y = intValue(map.get("y"));
                Integer z = intValue(map.get("z"));
                String world = stringValue(map.get("world"));
                if (x == null || y == null || z == null) {
                    return null;
                }
                return new ParsedPoint(x, y, z, world);
            }
            return null;
        }

        private static Integer intValue(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return (int) Math.round(Double.parseDouble(text.trim()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static String stringValue(Object value) {
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            return text;
        }
    }
}
