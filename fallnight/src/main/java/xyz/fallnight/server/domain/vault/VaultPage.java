package xyz.fallnight.server.domain.vault;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class VaultPage {
    public static final int SLOT_COUNT = 54;
    public static final int STORAGE_SLOT_COUNT = 45;

    private final Map<Integer, ItemStack> itemsBySlot;

    public VaultPage() {
        this.itemsBySlot = new LinkedHashMap<>();
    }

    public ItemStack getItem(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.AIR;
        }
        return itemsBySlot.getOrDefault(slot, ItemStack.AIR);
    }

    public void setItem(int slot, ItemStack item) {
        if (!isValidSlot(slot)) {
            return;
        }
        if (item == null || item.material() == Material.AIR) {
            itemsBySlot.remove(slot);
            return;
        }
        itemsBySlot.put(slot, item);
    }

    public Map<Integer, ItemStack> items() {
        return Map.copyOf(itemsBySlot);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    public static boolean isStorageSlot(int slot) {
        return slot >= 0 && slot < STORAGE_SLOT_COUNT;
    }
}
