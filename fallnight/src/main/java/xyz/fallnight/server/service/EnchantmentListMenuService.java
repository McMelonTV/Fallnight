package xyz.fallnight.server.service;

import java.util.ArrayList;
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

public final class EnchantmentListMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_1_ROW, LEGACY.deserialize("§bEnchantment list"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot >= 0 && slot < 6) {
                openRarity(event.getPlayer(), slot);
            }
        });
        String[] names = {"Common", "Uncommon", "Rare", "Very Rare", "Mythic", "Legendary"};
        Material[] materials = {Material.WHITE_DYE, Material.LIME_DYE, Material.LAPIS_LAZULI, Material.PURPLE_DYE, Material.DIAMOND, Material.NETHER_STAR};
        for (int i = 0; i < names.length; i++) {
            inventory.setItemStack(i, ItemStack.of(materials[i]).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(rarityColor(i) + names[i]))));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private void openRarity(Player player, int rarityIndex) {
        String[] names = {"Common", "Uncommon", "Rare", "Very Rare", "Mythic", "Legendary"};
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize(names[rarityIndex] + "enchants"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        int slot = 0;
        for (FallnightCustomEnchantRegistry.Definition def : FallnightCustomEnchantRegistry.registeredDefaults()) {
            if (rarityBucket(def) != rarityIndex) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.empty());
            lore.add(LEGACY.deserialize(rarityColor(rarityIndex) + def.displayName()));
            lore.add(LEGACY.deserialize("§r§7Description: §b" + def.description()));
            lore.add(LEGACY.deserialize("§r§7Max level: §b" + def.maxLevel()));
            lore.add(LEGACY.deserialize("§r§7Compatible: §b" + def.compatibility()));
            inventory.setItemStack(slot++, ItemStack.of(Material.ENCHANTED_BOOK).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(rarityColor(rarityIndex) + def.displayName()))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore)));
            if (slot >= 54) {
                break;
            }
        }
        inventory.setItemStack(53, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§fHere is a list of the available " + names[rarityIndex] + " enchantments currently in the server."))));
        InventoryOpeners.replace(player, inventory);
    }

    private static int rarityBucket(FallnightCustomEnchantRegistry.Definition def) {
        return def.rarityBucket();
    }

    private static String rarityColor(int rarityIndex) {
        return switch (rarityIndex) {
            case 0 -> "§7";
            case 1 -> "§a";
            case 2 -> "§c";
            case 3 -> "§4";
            case 4 -> "§5";
            default -> "§l§6";
        };
    }
}
