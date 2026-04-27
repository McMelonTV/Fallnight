package xyz.fallnight.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class TrashService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§l§7Trash bin"));
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> {
            if (event.getInventory() != inventory) {
                return;
            }
            int count = 0;
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItemStack(slot);
                if (stack != null && stack.material() != Material.AIR) {
                    count += stack.amount();
                }
            }
            if (count > 0) {
                player.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You removed§b " + count + " §r§7items."));
            }
        });
        InventoryOpeners.replace(player, inventory);
    }
}
