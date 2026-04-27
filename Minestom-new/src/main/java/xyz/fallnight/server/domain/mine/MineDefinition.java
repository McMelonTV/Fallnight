package xyz.fallnight.server.domain.mine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MineDefinition {
    private int id;
    private String name;
    private String world;
    private MineRegion region;
    private List<String> blocks;
    private Map<String, Double> prices;
    private boolean disabled;
    private Integer spawnX;
    private Integer spawnY;
    private Integer spawnZ;

    public MineDefinition() {
        this.blocks = new ArrayList<>();
        this.prices = new LinkedHashMap<>();
    }

    public MineDefinition(int id, String name, String world, MineRegion region) {
        this();
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.world = Objects.requireNonNull(world, "world");
        this.region = Objects.requireNonNull(region, "region");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    @JsonIgnore
    public MineRegion getRegion() {
        return region;
    }

    public void setRegion(MineRegion region) {
        this.region = region;
    }

    public List<String> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    @JsonProperty("blocks")
    public void setBlocks(Object blockData) {
        this.blocks = parseBlocks(blockData);
    }

    public Map<String, Double> getPrices() {
        return Collections.unmodifiableMap(prices);
    }

    public void setPrices(Map<String, Double> prices) {
        this.prices = prices == null ? new LinkedHashMap<>() : new LinkedHashMap<>(prices);
    }

    @JsonProperty("isDisabled")
    @JsonAlias("disabled")
    public boolean isDisabled() {
        return disabled;
    }

    @JsonProperty("isDisabled")
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
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

    @JsonProperty("spawnX")
    public Integer getSpawnX() {
        return spawnX;
    }

    @JsonProperty("spawnX")
    public void setSpawnX(Integer spawnX) {
        this.spawnX = spawnX;
    }

    @JsonProperty("spawnY")
    public Integer getSpawnY() {
        return spawnY;
    }

    @JsonProperty("spawnY")
    public void setSpawnY(Integer spawnY) {
        this.spawnY = spawnY;
    }

    @JsonProperty("spawnZ")
    public Integer getSpawnZ() {
        return spawnZ;
    }

    @JsonProperty("spawnZ")
    public void setSpawnZ(Integer spawnZ) {
        this.spawnZ = spawnZ;
    }

    public int effectiveSpawnX() {
        return spawnX == null ? getX1() : spawnX;
    }

    public int effectiveSpawnY() {
        if (spawnY != null) {
            return spawnY;
        }
        return region == null ? getY1() : region.maxY() + 1;
    }

    public int effectiveSpawnZ() {
        return spawnZ == null ? getZ1() : spawnZ;
    }

    @JsonIgnore
    public int topTeleportY() {
        return region == null ? effectiveSpawnY() + 1 : region.maxY() + 1;
    }

    public boolean contains(int x, int y, int z, String worldName) {
        return world != null && world.equalsIgnoreCase(worldName) && region != null && region.contains(x, y, z);
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
        this.region = new MineRegion(
            x1 == null ? currentX1 : x1,
            y1 == null ? currentY1 : y1,
            z1 == null ? currentZ1 : z1,
            x2 == null ? currentX2 : x2,
            y2 == null ? currentY2 : y2,
            z2 == null ? currentZ2 : z2
        );
    }

    private static List<String> parseBlocks(Object blockData) {
        List<String> list = new ArrayList<>();
        if (blockData instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                addBlock(list, value);
            }
            return list;
        }

        if (blockData instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String namespace = normalize(entry.getKey());
                int weight = weight(entry.getValue());
                if (namespace == null) {
                    namespace = normalize(entry.getValue());
                    weight = weight(entry.getKey());
                }
                if (namespace == null) {
                    continue;
                }
                for (int i = 0; i < Math.max(1, weight); i++) {
                    list.add(namespace);
                }
            }
            return list;
        }

        addBlock(list, blockData);
        return list;
    }

    private static void addBlock(List<String> list, Object raw) {
        String namespace = normalize(raw);
        if (namespace != null) {
            list.add(namespace);
        }
    }

    private static String normalize(Object raw) {
        if (!(raw instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static int weight(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }
}
