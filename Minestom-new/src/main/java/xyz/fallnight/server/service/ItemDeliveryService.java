package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.domain.vault.PlayerVault;
import xyz.fallnight.server.domain.vault.VaultPage;
import java.util.Objects;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class ItemDeliveryService {
    private final VaultService vaultService;

    public ItemDeliveryService(VaultService vaultService) {
        this.vaultService = Objects.requireNonNull(vaultService, "vaultService");
    }

    public DeliveryResult deliver(Player player, UserProfile profile, ItemStack item) {
        if (player == null || profile == null || item == null || item.isAir() || item.amount() <= 0) {
            return DeliveryResult.failed();
        }
        if (player.getInventory().addItemStack(item)) {
            return DeliveryResult.inventory();
        }
        if (storeInVault(profile, item)) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize("§r§b§l> §r§7An item has been sent to your §b/vault §r§7because your inventory was full."));
            return DeliveryResult.vault();
        }
        return DeliveryResult.failed();
    }

    private boolean storeInVault(UserProfile profile, ItemStack item) {
        PlayerVault vault = vaultService.getOrCreate(profile.getUsername());
        int maxPages = vaultService.resolveAccessiblePageCount(profile, vault);
        ItemStack remaining = item;
        for (int pageNumber = 1; pageNumber <= maxPages && remaining.amount() > 0; pageNumber++) {
            VaultPage page = vault.page(pageNumber);
            remaining = mergeIntoPage(page, remaining);
            remaining = placeIntoPage(page, remaining);
            vault.setPage(pageNumber, page);
        }
        vaultService.save(vault);
        return remaining.isAir() || remaining.amount() <= 0;
    }

    private static ItemStack mergeIntoPage(VaultPage page, ItemStack item) {
        ItemStack remaining = item;
        for (int slot = 0; slot < VaultPage.STORAGE_SLOT_COUNT && remaining.amount() > 0; slot++) {
            ItemStack existing = page.getItem(slot);
            if (existing == null || existing.isAir() || !stackCompatible(existing, remaining)) {
                continue;
            }
            int space = existing.maxStackSize() - existing.amount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.amount());
            page.setItem(slot, existing.withAmount(existing.amount() + moved));
            int left = remaining.amount() - moved;
            remaining = left <= 0 ? ItemStack.AIR : remaining.withAmount(left);
        }
        return remaining;
    }

    private static ItemStack placeIntoPage(VaultPage page, ItemStack item) {
        ItemStack remaining = item;
        for (int slot = 0; slot < VaultPage.STORAGE_SLOT_COUNT && remaining.amount() > 0; slot++) {
            ItemStack existing = page.getItem(slot);
            if (existing != null && !existing.isAir()) {
                continue;
            }
            int moved = Math.min(remaining.maxStackSize(), remaining.amount());
            page.setItem(slot, remaining.withAmount(moved));
            int left = remaining.amount() - moved;
            remaining = left <= 0 ? ItemStack.AIR : remaining.withAmount(left);
        }
        return remaining;
    }

    private static boolean stackCompatible(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.isAir() || right.isAir()) {
            return false;
        }
        if (left.material() != right.material()) {
            return false;
        }
        return left.withAmount(1).equals(right.withAmount(1));
    }

    public record DeliveryResult(boolean deliveredToInventory, boolean deliveredToVault) {
        public static DeliveryResult inventory() { return new DeliveryResult(true, false); }
        public static DeliveryResult vault() { return new DeliveryResult(false, true); }
        public static DeliveryResult failed() { return new DeliveryResult(false, false); }

        public boolean success() {
            return deliveredToInventory || deliveredToVault;
        }
    }
}
