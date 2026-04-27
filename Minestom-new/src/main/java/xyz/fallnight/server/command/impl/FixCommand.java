package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class FixCommand extends FallnightCommand {
    private final LegacyCustomItemService customItemService = new LegacyCustomItemService();

    public FixCommand(PermissionService permissionService) {
        super("fix", permissionService, "repair");

        setDefaultExecutor((sender, context) -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }

            ItemStack held = player.getItemInMainHand();
            if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
                sender.sendMessage(CommandMessages.error("Hold an item in your main hand first."));
                return;
            }

            if (!customItemService.isDurabilityItem(held)) {
                sender.sendMessage(CommandMessages.success("You can't repair this item."));
                return;
            }

            player.setItemInMainHand(customItemService.applyDamage(held, -customItemService.currentDamage(held)));
            sender.sendMessage(CommandMessages.success("You have repaired the item in your hand."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.fix";
    }

    @Override
    public String summary() {
        return "repair an item";
    }

    @Override
    public String usage() {
        return "/fix";
    }
}
