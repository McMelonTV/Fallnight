package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class CustomEnchantCommand extends FallnightCommand {
    private final LegacyCustomItemService customItemService;

    public CustomEnchantCommand(PermissionService permissionService) {
        super("customenchant", permissionService);
        this.customItemService = new LegacyCustomItemService();

        var legacyIdArg = ArgumentType.Integer("id").min(1).max(1000);
        var levelArg = ArgumentType.Integer("level").min(1).max(10);

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> applyLegacy(sender, context.get(legacyIdArg), context.get(levelArg)), legacyIdArg, levelArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.customenchant";
    }

    @Override
    public String summary() {
        return "apply custom enchants";
    }

    @Override
    public String usage() {
        return "/customenchant <id> <level>";
    }

    private void applyLegacy(net.minestom.server.command.CommandSender sender, int legacyId, int level) {
        FallnightCustomEnchantRegistry.Definition definition = FallnightCustomEnchantRegistry.byLegacyId(legacyId).orElse(null);
        if (definition == null || !definition.registeredByDefault()) {
            sender.sendMessage(CommandMessages.error("Enchantment not found."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("Hold an item in your main hand first."));
            return;
        }
        ItemStack updated = customItemService.withCustomEnchant(held, definition.id(), level);
        player.setItemInMainHand(updated);
        sender.sendMessage(CommandMessages.success("Applied " + definition.displayName() + " " + level + "."));
    }
}
