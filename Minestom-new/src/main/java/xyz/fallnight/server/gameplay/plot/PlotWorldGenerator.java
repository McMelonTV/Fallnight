package xyz.fallnight.server.gameplay.plot;

import xyz.fallnight.server.service.PlotService;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.Generator;

public final class PlotWorldGenerator implements Generator {
    @Override
    public void generate(net.minestom.server.instance.generator.GenerationUnit unit) {
        Block wallBlock = plotWallBlock();
        Block wallTopBlock = plotWallTopBlock();
        unit.modifier().fillHeight(0, 1, Block.BEDROCK);
        var start = unit.absoluteStart();
        var end = unit.absoluteEnd();
        for (int x = start.blockX(); x < end.blockX(); x++) {
            for (int z = start.blockZ(); z < end.blockZ(); z++) {
                Type type = typeAt(x, z);
                for (int y = 1; y < 64; y++) {
                    unit.modifier().setBlock(x, y, z, type == Type.WALL ? wallBlock : Block.DIRT);
                }
                unit.modifier().setBlock(x, 64, z, type == Type.PLOT ? Block.GRASS_BLOCK : type == Type.ROAD ? Block.DIRT_PATH : wallBlock);
                if (type == Type.WALL) {
                    unit.modifier().setBlock(x, 65, z, wallTopBlock);
                }
            }
        }
    }

    private static Block plotWallBlock() {
        Block block = Block.fromKey("minecraft:polished_andesite");
        return block != null ? block : Block.STONE_BRICKS;
    }

    private static Block plotWallTopBlock() {
        Block slab = Block.fromKey("minecraft:polished_andesite_slab");
        if (slab != null) {
            return slab.withProperty("type", "top");
        }
        return Block.STONE_BRICK_SLAB.withProperty("type", "top");
    }

    private static Type typeAt(int x, int z) {
        Type tx = singleCoordType(x);
        Type tz = singleCoordType(z);
        if ((tz == Type.WALL && tx != Type.ROAD) || (tx == Type.WALL && tz != Type.ROAD)) {
            return Type.WALL;
        }
        if (tz == Type.ROAD || tx == Type.ROAD) {
            return Type.ROAD;
        }
        return Type.PLOT;
    }

    private static Type singleCoordType(int coord) {
        int r = Math.floorMod(coord, PlotService.GRID_SPACING);
        if (r > PlotService.PLOT_SIZE + 4) return Type.ROAD;
        if (r > PlotService.PLOT_SIZE + 3) return Type.WALL;
        if (r > 3) return Type.PLOT;
        if (r > 2) return Type.WALL;
        return Type.ROAD;
    }

    private enum Type {
        PLOT,
        WALL,
        ROAD
    }
}
