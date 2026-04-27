package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import net.minestom.server.component.DataComponents;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class FnItemCommand extends FallnightCommand {
    private final LegacyCustomItemService customItemService;

    public FnItemCommand(PermissionService permissionService) {
        super("fnitem", permissionService, "customitem");
        this.customItemService = new LegacyCustomItemService();

        var itemArg = ArgumentType.Word("item");
        var amountArg = ArgumentType.Integer("amount").min(1).max(64);
        var extraArg = ArgumentType.Integer("extra");
        var targetArg = ArgumentType.Word("target");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> give(sender, context.get(itemArg), 1, null, null), itemArg);
        addSyntax((sender, context) -> give(sender, context.get(itemArg), context.get(amountArg), null, null), itemArg, amountArg);
        addSyntax((sender, context) -> give(sender, context.get(itemArg), context.get(amountArg), context.get(extraArg), null), itemArg, amountArg, extraArg);
        addSyntax((sender, context) -> give(sender, context.get(itemArg), context.get(amountArg), context.get(extraArg), context.get(targetArg)), itemArg, amountArg, extraArg, targetArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.fnitem";
    }

    @Override
    public String summary() {
        return "give a custom item";
    }

    @Override
    public String usage() {
        return "/fnitem <id|name> [amount] [extraData] [target]";
    }

    private void give(net.minestom.server.command.CommandSender sender, String itemInput, int amount, Integer extraData, String targetInput) {
        Player target;
        if (targetInput == null || targetInput.isBlank()) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Console usage: /fnitem <id|name> [amount] [extraData] <target>"));
                return;
            }
            target = player;
        } else {
            target = findOnlinePlayerIgnoreCase(targetInput);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was not found."));
                return;
            }
        }

        ItemStack item = createItem(itemInput, amount, extraData);
        if (item == null) {
            sender.sendMessage(CommandMessages.error("That item could not be found."));
            return;
        }

        target.getInventory().addItemStack(item);
        if (sender == target) {
            sender.sendMessage(CommandMessages.success("You have been given §b" + item.amount() + "x " + itemDisplay(item) + "§r§7."));
        } else {
            sender.sendMessage(CommandMessages.success("You have given §b" + item.amount() + "x " + itemDisplay(item) + "§r§7 to §b" + target.getUsername() + "§r§7."));
        }
    }

    private ItemStack createItem(String itemInput, int amount, Integer extraData) {
        try {
            int id = Integer.parseInt(itemInput.trim());
            return customItemService.createById(id, amount, extraData).orElse(null);
        } catch (NumberFormatException ignored) {
            return customItemService.createByName(itemInput, amount, extraData).orElse(null);
        }
    }

    private static String itemDisplay(ItemStack item) {
        var customName = item.get(DataComponents.CUSTOM_NAME);
        return customName == null
            ? item.material().key().asString()
            : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);
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
