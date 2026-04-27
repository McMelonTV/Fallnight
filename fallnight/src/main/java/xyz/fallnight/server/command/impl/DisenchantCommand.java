package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.InventoryOpeners;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class DisenchantCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final LegacyCustomItemService customItemService = new LegacyCustomItemService();
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;

    public DisenchantCommand(PermissionService permissionService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        super("disenchant", permissionService);
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;

        var enchantArg = ArgumentType.Word("enchantment");

        setDefaultExecutor((sender, context) -> openConfirm(sender));
        addSyntax((sender, context) -> removeOne(sender, context.get(enchantArg)), enchantArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.disenchant";
    }

    @Override
    public String summary() {
        return "disenchant an item";
    }

    @Override
    public String usage() {
        return "/disenchant";
    }

    private void clearAll(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("Hold an item in your main hand first."));
            return;
        }
        Map<String, Integer> customEnchants = customItemService.customEnchants(held);
        if (customEnchants.isEmpty()) {
            sender.sendMessage(CommandMessages.error("Please hold an item with enchantments."));
            return;
        }
        DisenchantCost cost = cost(customEnchants);
        if (!canAfford(player, cost)) {
            sender.sendMessage(CommandMessages.error("You can't afford to disenchant this."));
            return;
        }
        List<ItemStack> books = toBooks(customEnchants);
        ItemStack updated = customItemService.clearCustomEnchants(held);
        player.setItemInMainHand(updated);
        for (ItemStack book : books) {
            itemDeliveryService.deliver(player, profileService.getOrCreate(player), book);
        }
        pay(player, cost);
        sender.sendMessage(CommandMessages.success(
            "You successfully disenchanted your item for " + cost.magicDust() + " magicdust and " + cost.xpLevels() + " levels."
        ));
    }

    private void openConfirm(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("Please hold an item to disenchant."));
            return;
        }
        Map<String, Integer> customEnchants = customItemService.customEnchants(held);
        if (customEnchants.isEmpty()) {
            sender.sendMessage(CommandMessages.error("Please hold an item with enchantments."));
            return;
        }
        DisenchantCost cost = cost(customEnchants);
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bItem disenchantment"));
        inventory.setItemStack(11, ItemStack.of(Material.EMERALD_BLOCK).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§8Confirm disenchanting"))));
        inventory.setItemStack(15, ItemStack.of(Material.REDSTONE_BLOCK).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§8Cancel"))));
        inventory.setItemStack(13, ItemStack.of(Material.PAPER)
            .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Remove all custom enchantments")))
            .with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(
                LEGACY.deserialize("§r§7Cost: §b" + cost.magicDust() + " magicdust §r§7and §b" + cost.xpLevels() + " levels"),
                LEGACY.deserialize("§r§7The enchantments will be returned as books.")
            ))));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 11) {
                clearAll(player);
                player.closeInventory();
            } else if (event.getSlot() == 15) {
                player.closeInventory();
            }
        });
        InventoryOpeners.replace(player, inventory);
    }

    private void removeOne(net.minestom.server.command.CommandSender sender, String rawEnchant) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("Hold an item in your main hand first."));
            return;
        }
        String enchantId = EnchantCommandSupport.normalizeId(rawEnchant);
        Map<String, Integer> customEnchants = customItemService.customEnchants(held);
        Integer level = customEnchants.get(enchantId);
        if (level == null || level <= 0) {
            sender.sendMessage(CommandMessages.error("Unknown enchantment '" + rawEnchant + "'. Use /enchantmentlist."));
            return;
        }
        DisenchantCost cost = cost(Map.of(enchantId, level));
        if (!canAfford(player, cost)) {
            sender.sendMessage(CommandMessages.error("You can't afford to disenchant this."));
            return;
        }
        ItemStack book = bookFor(enchantId, level);
        ItemStack updated = customItemService.removeCustomEnchant(held, enchantId);
        player.setItemInMainHand(updated);
        itemDeliveryService.deliver(player, profileService.getOrCreate(player), book);
        pay(player, cost);
        sender.sendMessage(CommandMessages.success(
            "You successfully disenchanted " + enchantId + " for " + cost.magicDust() + " magicdust and " + cost.xpLevels() + " levels."
        ));
    }

    private List<ItemStack> toBooks(Map<String, Integer> customEnchants) {
        List<ItemStack> books = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : customEnchants.entrySet()) {
            books.add(bookFor(entry.getKey(), entry.getValue()));
        }
        return books;
    }

    private ItemStack bookFor(String enchantId, int level) {
        int legacyId = FallnightCustomEnchantRegistry.byId(enchantId)
            .map(FallnightCustomEnchantRegistry.Definition::legacyId)
            .orElse(101);
        return customItemService.enchantmentBookByVariantLevel(legacyId, Math.max(1, level));
    }

    private static DisenchantCost cost(Map<String, Integer> customEnchants) {
        int magicDust = 0;
        int xpLevels = 0;
        for (Map.Entry<String, Integer> entry : customEnchants.entrySet()) {
            int level = Math.max(1, entry.getValue());
            int[] rarityCost = rarityCost(entry.getKey());
            magicDust += rarityCost[0] * level;
            xpLevels += rarityCost[1] * level;
        }
        return new DisenchantCost(magicDust, xpLevels);
    }

    private static int[] rarityCost(String enchantId) {
        return FallnightCustomEnchantRegistry.byId(enchantId)
            .map(definition -> new int[] {definition.disenchantMagicDustCost(), definition.disenchantXpCost()})
            .orElse(new int[] {14, 7});
    }

    private static boolean canAfford(Player player, DisenchantCost cost) {
        return player.getLevel() >= cost.xpLevels() && countMagicDust(player) >= cost.magicDust();
    }

    private static void pay(Player player, DisenchantCost cost) {
        removeMagicDust(player, cost.magicDust());
        player.setLevel(Math.max(0, player.getLevel() - cost.xpLevels()));
    }

    private static int countMagicDust(Player player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getInnerSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (stack != null && stack.amount() > 0 && stack.material() == Material.BLUE_DYE) {
                total += stack.amount();
            }
        }
        return total;
    }

    private static void removeMagicDust(Player player, int amount) {
        int remaining = Math.max(0, amount);
        for (int slot = 0; slot < player.getInventory().getInnerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (stack == null || stack.material() != Material.BLUE_DYE || stack.amount() <= 0) {
                continue;
            }
            int used = Math.min(remaining, stack.amount());
            int next = stack.amount() - used;
            player.getInventory().setItemStack(slot, next <= 0 ? ItemStack.AIR : stack.withAmount(next));
            remaining -= used;
        }
    }

    private record DisenchantCost(int magicDust, int xpLevels) {
    }
}
