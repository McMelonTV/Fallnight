package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public final class ConvertWorldCommand extends FallnightCommand {
    public ConvertWorldCommand(PermissionService permissionService) {
        super("convertworld", permissionService);

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                return;
            }
            if (player.getInstance() == null) {
                sender.sendMessage(CommandMessages.error("You are not in an instance."));
                return;
            }
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§cCONVERTING CURRENT WORLD"));
            convertLoadedChunks(player.getInstance());
        });
    }

    private static void convertLoadedChunks(Instance instance) {
        int minY = instance.getCachedDimensionType().minY();
        int maxY = instance.getCachedDimensionType().maxY();

        for (Chunk chunk : instance.getChunks()) {
            int chunkBaseX = chunk.getChunkX() * 16;
            int chunkBaseZ = chunk.getChunkZ() * 16;
            for (int x = 0; x < 16; x++) {
                int worldX = chunkBaseX + x;
                for (int z = 0; z < 16; z++) {
                    int worldZ = chunkBaseZ + z;
                    for (int y = minY; y < maxY; y++) {
                        Block current = instance.getBlock(worldX, y, worldZ);
                        Block specialReplacement = specialReplacement(current);
                        if (specialReplacement != null && !current.compare(specialReplacement)) {
                            instance.setBlock(worldX, y, worldZ, specialReplacement);
                            continue;
                        }
                    }
                }
            }
        }
    }

    private static Block specialReplacement(Block current) {
        if (current == null) {
            return null;
        }
        if (current.name().equals("minecraft:podzol")) {
            return Block.DIRT;
        }
        if (current.name().equals("minecraft:smooth_stone_slab") && "bottom".equals(current.properties().get("type"))) {
            return Block.SMOOTH_STONE;
        }
        return null;
    }

    @Override
    public String permission() {
        return "fallnight.command.convertworld";
    }

    @Override
    public String summary() {
        return "Convert deprecated block states in loaded chunks.";
    }

    @Override
    public String usage() {
        return "/convertworld";
    }

}
