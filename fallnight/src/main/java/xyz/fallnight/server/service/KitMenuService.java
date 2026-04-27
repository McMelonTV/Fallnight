package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.kit.KitDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class KitMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final KitService kitService;
    private final PlayerProfileService profileService;
    private final PermissionService permissionService;

    public KitMenuService(KitService kitService, PlayerProfileService profileService, PermissionService permissionService) {
        this.kitService = kitService;
        this.profileService = profileService;
        this.permissionService = permissionService;
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_2_ROW, LEGACY.deserialize("§bKits"));
        inventory.setItemStack(16, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§fSelect a kit to equip."))));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 17) {
                event.getPlayer().closeInventory();
                return;
            }
            List<KitDefinition> kits = kitService.listKits();
            if (slot < 0 || slot >= kits.size()) {
                return;
            }
            Player clicked = event.getPlayer();
            UserProfile profile = profileService.getOrCreate(clicked);
            KitService.ClaimResult result = kitService.claimKit(clicked, profile, kits.get(slot).id(), permissionService);
            switch (result.status()) {
                case SUCCESS -> clicked.sendMessage(CommandMessages.success("Claimed /kit " + result.kit().id() + "."));
                case INVALID_KIT -> clicked.sendMessage(CommandMessages.error("That kit does not exist."));
                case ON_COOLDOWN -> clicked.sendMessage(CommandMessages.error("That kit is still on cooldown."));
                case NO_PERMISSION -> clicked.sendMessage(CommandMessages.error("You don't have permission to claim that kit."));
                case INVENTORY_FULL -> clicked.sendMessage(CommandMessages.error("You don't have enough space in your inventory to claim this kit."));
            }
            render(inventory, clicked);
        });
        render(inventory, player);
        InventoryOpeners.replace(player, inventory);
    }

    private void render(Inventory inventory, Player player) {
        inventory.clear();
        inventory.setItemStack(17, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§cClose"))));
        UserProfile profile = profileService.getOrCreate(player);
        List<KitDefinition> kits = kitService.listKits();
        for (int i = 0; i < kits.size() && i < 17; i++) {
            KitDefinition kit = kits.get(i);
            boolean allowed = permissionService.hasPermission(player, kit.permission());
            long remaining = kitService.remainingCooldownSeconds(profile, kit.id());
            String status = !allowed ? "§cLocked" : remaining > 0L ? "§c" + formatDuration(remaining) : "§aClaim";
            inventory.setItemStack(i, ItemStack.of(allowed && remaining <= 0L ? Material.LIME_CONCRETE : Material.RED_CONCRETE).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§b§l§o" + kit.displayName() + " §r§8§okit§r"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(status)))));
        }
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h" + (minutes > 0L ? " " + minutes + "m" : "");
        }
        if (minutes > 0L) {
            return minutes + "m" + (remSeconds > 0L ? " " + remSeconds + "s" : "");
        }
        return remSeconds + "s";
    }
}
