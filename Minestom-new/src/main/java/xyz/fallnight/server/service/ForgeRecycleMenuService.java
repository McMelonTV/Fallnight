package xyz.fallnight.server.service;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class ForgeRecycleMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final LegacyCustomItemService customItemService;
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;

    public ForgeRecycleMenuService(LegacyCustomItemService customItemService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        this.customItemService = customItemService;
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.HOPPER, LEGACY.deserialize("§l§7Recycle inventory"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> {
            if (!(event.getPlayer() instanceof Player closingPlayer)) {
                return;
            }
            int recycled = 0;
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItemStack(slot);
                if (item == null || item.isAir() || item.amount() <= 0) {
                    continue;
                }
                if (customItemService.customItemId(item) != 0) {
                    recycled += item.amount();
                } else {
                    closingPlayer.getInventory().addItemStack(item);
                }
            }
            closingPlayer.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You recycled §b" + recycled + " §r§7items."));
        });
        InventoryOpeners.replace(player, inventory);
    }
}