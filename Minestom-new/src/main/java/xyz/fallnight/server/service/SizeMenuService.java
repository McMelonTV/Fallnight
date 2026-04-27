package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.gameplay.player.PlayerSizing;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class SizeMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int[] PRESETS = {50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150};
    private final PlayerProfileService profileService;
    private final AnvilInputService anvilInputService;

    public SizeMenuService(PlayerProfileService profileService) {
        this.profileService = profileService;
        this.anvilInputService = new AnvilInputService();
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_2_ROW, LEGACY.deserialize("§b/size"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 17) {
                event.getPlayer().closeInventory();
                return;
            }
            if (slot == 15) {
                event.getPlayer().closeInventory();
                openCustomInput(event.getPlayer());
                return;
            }
            if (slot < 0 || slot >= PRESETS.length) {
                return;
            }
            apply(event.getPlayer(), PRESETS[slot]);
            render(inventory, event.getPlayer());
        });
        render(inventory, player);
        InventoryOpeners.replace(player, inventory);
    }

    private void openCustomInput(Player player) {
        anvilInputService.open(player, "§b/size", "Enter size (50-150)", "100", (p, input) -> {
            try {
                int size = Integer.parseInt(input.trim());
                if (size < 50) {
                    p.sendMessage(LEGACY.deserialize("§b§l> §r§7Your size can't be lower than §b50§7."));
                    return;
                }
                if (size > 150) {
                    p.sendMessage(LEGACY.deserialize("§b§l> §r§7Your size can't be higher than §b150§7."));
                    return;
                }
                apply(p, size);
            } catch (NumberFormatException e) {
                p.sendMessage(CommandMessages.error("Please enter a valid number."));
            }
        });
    }

    private void render(Inventory inventory, Player player) {
        inventory.clear();
        Object raw = profileService.getOrCreate(player).getExtraData().get("playerSize");
        int current = raw instanceof Number n ? n.intValue() : 100;
        for (int i = 0; i < PRESETS.length; i++) {
            int value = PRESETS[i];
            boolean selected = value == current;
            inventory.setItemStack(i, ItemStack.of(selected ? Material.LIME_CONCRETE : Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize((selected ? "§a" : "§b") + value + "%"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Click to set your size")))));
        }
        inventory.setItemStack(15, ItemStack.of(Material.OAK_SIGN).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§eCustom Size"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Enter a custom value (50-150)")))));
        inventory.setItemStack(16, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§7Please select a value you want your size to be."))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§8(100 for default size)")))));
        inventory.setItemStack(17, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§cClose"))));
    }

    private void apply(Player player, int size) {
        int clampedSize = PlayerSizing.clampPercent(size);
        PlayerSizing.apply(player, clampedSize);
        profileService.getOrCreate(player).getExtraData().put("playerSize", clampedSize);
        profileService.save(profileService.getOrCreate(player));
        player.sendMessage(CommandMessages.success("Your set your size to §b" + (clampedSize / 100d) + "§7."));
    }
}