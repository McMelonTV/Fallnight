package xyz.fallnight.server.service;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;

public final class InventoryOpeners {
    private InventoryOpeners() {
    }

    public static void replace(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        if (player.getOpenInventory() != null) {
            player.closeInventory();
        }
        player.openInventory(inventory);
    }
}
