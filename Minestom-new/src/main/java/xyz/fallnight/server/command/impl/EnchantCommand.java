package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class EnchantCommand extends FallnightCommand {
    public EnchantCommand(PermissionService permissionService) {
        super("enchant", permissionService, "applyenchant");

        var playerArg = ArgumentType.Word("player");
        var enchantArg = ArgumentType.Word("enchantment");
        var levelArg = ArgumentType.Integer("level").min(1).max(10);

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> applyEnchant(sender, sender instanceof Player player ? player : null, context.get(enchantArg), 1), enchantArg);
        addSyntax((sender, context) -> applyEnchant(sender, sender instanceof Player player ? player : null, context.get(enchantArg), context.get(levelArg)), enchantArg, levelArg);
        addSyntax((sender, context) -> applyEnchant(sender, findOnlinePlayerIgnoreCase(context.get(playerArg)), context.get(enchantArg), 1), playerArg, enchantArg);
        addSyntax((sender, context) -> applyEnchant(sender, findOnlinePlayerIgnoreCase(context.get(playerArg)), context.get(enchantArg), context.get(levelArg)), playerArg, enchantArg, levelArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.enchant";
    }

    @Override
    public String summary() {
        return "enchant an item";
    }

    @Override
    public String usage() {
        return "/enchant [player]";
    }

    private void applyEnchant(net.minestom.server.command.CommandSender sender, Player player, String rawEnchant, int level) {
        if (player == null) {
            sender.sendMessage(CommandMessages.error("The target player could not be found."));
            return;
        }

        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("Please hold an item to enchant."));
            return;
        }

        String enchantId = EnchantCommandSupport.normalizeId(rawEnchant);
        if (!EnchantCommandSupport.isKnown(enchantId)) {
            sender.sendMessage(CommandMessages.error("Unknown enchantment '" + rawEnchant + "'. Use /enchantmentlist."));
            return;
        }

        ItemStack updated = EnchantCommandSupport.withEnchant(held, enchantId, level);
        player.setItemInMainHand(updated);
        sender.sendMessage(CommandMessages.success("Successfully enchanted an item for §b" + player.getUsername() + "§r§7."));
    }

    private static Player findOnlinePlayerIgnoreCase(String username) {
        Player exact = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username);
        if (exact != null) {
            return exact;
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }
}
