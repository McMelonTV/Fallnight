package xyz.fallnight.server.gameplay.item;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.registry.RegistryKey;

public final class EnchantmentBookModule {
    private final LegacyCustomItemService customItemService;
    private final EventNode<Event> eventNode;

    public EnchantmentBookModule(LegacyCustomItemService customItemService) {
        this.customItemService = customItemService;
        this.eventNode = EventNode.all("enchantment-book-gameplay");
    }

    public void register() {
        eventNode.addListener(InventoryPreClickEvent.class, this::onInventoryPreClick);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    private void onInventoryPreClick(InventoryPreClickEvent event) {
        if (!(event.getInventory() instanceof PlayerInventory inventory)) {
            return;
        }
        ItemStack clicked = event.getClickedItem();
        ItemStack cursor = inventory.getCursorItem();
        if (clicked == null || cursor == null || clicked.isAir() || cursor.isAir()) {
            return;
        }

        if (customItemService.isEnchantmentBook(clicked) && customItemService.isEnchantmentBook(cursor)) {
            combineBooks(event, inventory, clicked, cursor);
            return;
        }
        if (customItemService.isEnchantmentBook(cursor) && !customItemService.isEnchantmentBook(clicked)) {
            applyBook(event, inventory, clicked, cursor);
        }
    }

    private void combineBooks(InventoryPreClickEvent event, PlayerInventory inventory, ItemStack clicked, ItemStack cursor) {
        int clickedVariant = customItemService.enchantBookVariant(clicked);
        int cursorVariant = customItemService.enchantBookVariant(cursor);
        int clickedLevel = customItemService.enchantBookLevel(clicked);
        int cursorLevel = customItemService.enchantBookLevel(cursor);
        if (clickedVariant <= 0 || cursorVariant <= 0) {
            return;
        }
        if (clickedVariant != cursorVariant) {
            event.getPlayer().sendMessage(CommandMessages.error("You can only combine books with the same enchantment type."));
            event.setCancelled(true);
            return;
        }
        if (clickedLevel != cursorLevel) {
            event.getPlayer().sendMessage(CommandMessages.error("You can only combine books with the same enchantment level."));
            event.setCancelled(true);
            return;
        }
        int maxLevel = FallnightCustomEnchantRegistry.byLegacyId(clickedVariant)
            .map(FallnightCustomEnchantRegistry.Definition::maxLevel)
            .orElse(10);
        int nextLevel = Math.min(maxLevel, clickedLevel + 1);
        if (nextLevel == clickedLevel) {
            event.getPlayer().sendMessage(CommandMessages.error("This enchantment already reached the max level."));
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        inventory.setItemStack(event.getSlot(), customItemService.enchantmentBookByVariantLevel(clickedVariant, nextLevel));
        inventory.setCursorItem(ItemStack.AIR);
        event.getPlayer().sendMessage(CommandMessages.success("You successfully leveled up the enchantment."));
    }

    private void applyBook(InventoryPreClickEvent event, PlayerInventory inventory, ItemStack clicked, ItemStack cursor) {
        int variant = customItemService.enchantBookVariant(cursor);
        int level = customItemService.enchantBookLevel(cursor);
        if (variant <= 0 || level <= 0) {
            return;
        }
        String enchantId = customItemService.enchantIdForVariant(variant);
        if (!hasEnchantableType(clicked)) {
            event.getPlayer().sendMessage(CommandMessages.error("Cannot apply this enchantment to that item."));
            event.setCancelled(true);
            return;
        }
        if (!customItemService.canApplyStoredEnchant(clicked, enchantId)) {
            event.getPlayer().sendMessage(CommandMessages.error("That item is not compatible with the enchantment."));
            event.setCancelled(true);
            return;
        }
        int requiredLevels = FallnightCustomEnchantRegistry.byId(enchantId)
            .map(definition -> definition.applyCost(level))
            .orElse(Math.max(1, level));
        if (event.getPlayer().getLevel() < requiredLevels) {
            event.getPlayer().sendMessage(CommandMessages.error("You can't afford the §b" + requiredLevels + " XP levels §r§7required to apply this enchant."));
            event.setCancelled(true);
            return;
        }

        EnchantmentList current = clicked.get(DataComponents.ENCHANTMENTS);
        EnchantmentList existing = current == null ? EnchantmentList.EMPTY : current;
        int currentLevel = existing.level(RegistryKey.unsafeOf(fullEnchantKey(enchantId)));
        int maxLevel = FallnightCustomEnchantRegistry.byId(enchantId)
            .map(FallnightCustomEnchantRegistry.Definition::maxLevel)
            .orElse(Integer.MAX_VALUE);
        if (currentLevel >= maxLevel && currentLevel == level) {
            event.getPlayer().sendMessage(CommandMessages.error("That item already has the highest level of the enchantment."));
            event.setCancelled(true);
            return;
        }
        int appliedLevel = currentLevel == level ? level + 1 : Math.max(currentLevel, level);
        if (appliedLevel <= currentLevel) {
            event.getPlayer().sendMessage(CommandMessages.error("That item already has a higher level of the enchantment applied."));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        ItemStack updated = FallnightCustomEnchantRegistry.isCustomEnchantId(enchantId)
            ? customItemService.withCustomEnchant(clicked, enchantId, appliedLevel)
            : clicked.with(DataComponents.ENCHANTMENTS, existing.with(RegistryKey.unsafeOf(fullEnchantKey(enchantId)), appliedLevel));
        inventory.setItemStack(event.getSlot(), updated);
        inventory.setCursorItem(ItemStack.AIR);
        event.getPlayer().setLevel(event.getPlayer().getLevel() - requiredLevels);
        String displayName = FallnightCustomEnchantRegistry.byId(enchantId)
            .map(FallnightCustomEnchantRegistry.Definition::displayName)
            .orElse(enchantId);
        event.getPlayer().sendMessage(CommandMessages.success("You successfully applied §r§b" + displayName + " " + appliedLevel + "§r§7 to the item."));
    }

    private static String fullEnchantKey(String enchantId) {
        return enchantId.startsWith("minecraft:") ? enchantId : "minecraft:" + enchantId;
    }

    private static boolean hasEnchantableType(ItemStack item) {
        String name = item.material().name().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith("sword")
            || name.endsWith("pickaxe")
            || name.endsWith("axe")
            || name.endsWith("shovel")
            || name.endsWith("bow")
            || name.endsWith("helmet")
            || name.endsWith("chestplate")
            || name.endsWith("leggings")
            || name.endsWith("boots");
    }
}
