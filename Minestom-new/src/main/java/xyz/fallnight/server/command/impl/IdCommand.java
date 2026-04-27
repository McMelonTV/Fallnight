package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;

public final class IdCommand extends FallnightCommand {
    public IdCommand(PermissionService permissionService) {
        super("id", permissionService);

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            Block target = targetBlock(player, 20);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("Block not found."));
                return;
            }

            sender.sendMessage(CommandMessages.info("Block id:§c" + target.key().asString() + "§7 meta:§c0"));
        });
    }

    private static Block targetBlock(Player player, int maxDistance) {
        if (player.getInstance() == null) {
            return null;
        }
        double yaw = Math.toRadians(player.getPosition().yaw());
        double pitch = Math.toRadians(player.getPosition().pitch());
        double x = player.getPosition().x();
        double y = player.getPosition().y() + player.getEyeHeight();
        double z = player.getPosition().z();
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        for (double distance = 0D; distance <= maxDistance; distance += 0.25D) {
            Block block = player.getInstance().getBlock(
                (int) Math.floor(x + dx * distance),
                (int) Math.floor(y + dy * distance),
                (int) Math.floor(z + dz * distance)
            );
            if (block != null && block != Block.AIR && !block.isAir()) {
                return block;
            }
        }
        return null;
    }

    @Override
    public String permission() {
        return "fallnight.command.id";
    }

    @Override
    public String summary() {
        return "see an id";
    }

    @Override
    public String usage() {
        return "/id";
    }
}
