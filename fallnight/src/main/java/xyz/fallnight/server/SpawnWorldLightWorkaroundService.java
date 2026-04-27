package xyz.fallnight.server;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnWorldLightWorkaroundService {
    private static final int MAX_PROJECTION_DISTANCE = 4;
    private static final int[] NEIGHBOR_OFFSETS = {
            1, 0, 0,
            -1, 0, 0,
            0, 1, 0,
            0, -1, 0,
            0, 0, 1,
            0, 0, -1
    };

    private final Set<Point> overlayPositions = ConcurrentHashMap.newKeySet();

    public void register(InstanceContainer instance) {
        applyToLoadedChunks(instance);
        if (instance.eventNode() != null) {
            instance.eventNode().addListener(InstanceChunkLoadEvent.class, event -> {
                Chunk chunk = instance.getChunk(event.getChunkX(), event.getChunkZ());
                if (chunk != null) {
                    applyToChunk(instance, chunk);
                }
            });
        }
    }

    public void clear(InstanceContainer instance) {
        if (overlayPositions.isEmpty()) {
            return;
        }
        Set<Chunk> affectedChunks = new HashSet<>();
        for (Point point : overlayPositions) {
            instance.setBlock(point.blockX(), point.blockY(), point.blockZ(), Block.AIR);
            Chunk chunk = instance.getChunkAt(point);
            if (chunk != null) {
                affectedChunks.add(chunk);
            }
        }
        overlayPositions.clear();
        if (!affectedChunks.isEmpty()) {
            LightingChunk.relight(instance, affectedChunks);
        }
    }

    private void applyToLoadedChunks(InstanceContainer instance) {
        for (Chunk chunk : instance.getChunks()) {
            applyToChunk(instance, chunk);
        }
    }

    private void applyToChunk(InstanceContainer instance, Chunk chunk) {
        if (chunk == null) {
            return;
        }
        Set<Chunk> affectedChunks = new HashSet<>();
        int minY = instance.getCachedDimensionType().minY();
        int maxY = minY + instance.getCachedDimensionType().height() - 1;
        int startX = chunk.getChunkX() * Chunk.CHUNK_SIZE_X;
        int startZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE_Z;
        for (int localX = 0; localX < Chunk.CHUNK_SIZE_X; localX++) {
            for (int localZ = 0; localZ < Chunk.CHUNK_SIZE_Z; localZ++) {
                int worldX = startX + localX;
                int worldZ = startZ + localZ;
                for (int y = minY; y <= maxY; y++) {
                    Block block = instance.getBlock(worldX, y, worldZ);
                    int emission = block.registry().lightEmission();
                    if (emission <= 0 || isSyntheticLight(block)) {
                        continue;
                    }
                    injectVisibleLight(instance, worldX, y, worldZ, emission, affectedChunks);
                }
            }
        }
        if (!affectedChunks.isEmpty()) {
            LightingChunk.relight(instance, affectedChunks);
        }
    }

    private void injectVisibleLight(InstanceContainer instance, int x, int y, int z, int emission, Set<Chunk> affectedChunks) {
        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i += 3) {
            int stepX = NEIGHBOR_OFFSETS[i];
            int stepY = NEIGHBOR_OFFSETS[i + 1];
            int stepZ = NEIGHBOR_OFFSETS[i + 2];
            for (int distance = 1; distance <= MAX_PROJECTION_DISTANCE; distance++) {
                int targetX = x + stepX * distance;
                int targetY = y + stepY * distance;
                int targetZ = z + stepZ * distance;
                Point target = new Vec(targetX, targetY, targetZ);
                Chunk affected = instance.getChunkAt(target);
                if (affected == null) {
                    break;
                }

                Block targetBlock = instance.getBlock(targetX, targetY, targetZ);
                if (targetBlock.isAir()) {
                    if (instance.getBlockLight(targetX, targetY, targetZ) == 0 && !overlayPositions.contains(target)) {
                        int level = Math.max(1, Math.min(15, emission - distance));
                        Block lightBlock = Block.LIGHT.withProperty("level", Integer.toString(level));
                        instance.setBlock(targetX, targetY, targetZ, lightBlock);
                        overlayPositions.add(target);
                        affectedChunks.add(affected);
                    }
                    break;
                }

                if (!isPassThroughDecorative(targetBlock, stepX, stepY, stepZ)) {
                    break;
                }
            }
        }
    }

    private static boolean isSyntheticLight(Block block) {
        return block.compare(Block.LIGHT);
    }

    private static boolean isPassThroughDecorative(Block block, int stepX, int stepY, int stepZ) {
        if (block.isAir() || isSyntheticLight(block)) {
            return true;
        }
        String key = block.key().asString();
        if (key.contains("glass")
                || key.contains("pane")
                || key.contains("slab")
                || key.contains("stairs")
                || key.contains("trapdoor")
                || key.contains("bars")
                || key.contains("chain")
                || key.contains("wall")) {
            return true;
        }
        BlockFace face = faceFor(stepX, stepY, stepZ);
        return face != null && !block.registry().occlusionShape().isOccluded(Block.AIR.registry().occlusionShape(), face);
    }

    private static BlockFace faceFor(int stepX, int stepY, int stepZ) {
        if (stepX > 0) return BlockFace.EAST;
        if (stepX < 0) return BlockFace.WEST;
        if (stepY > 0) return BlockFace.TOP;
        if (stepY < 0) return BlockFace.BOTTOM;
        if (stepZ > 0) return BlockFace.SOUTH;
        if (stepZ < 0) return BlockFace.NORTH;
        return null;
    }
}
