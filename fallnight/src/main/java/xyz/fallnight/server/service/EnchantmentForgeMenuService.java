package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.user.UserProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.List;

public final class EnchantmentForgeMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public void open(Player player, UserProfile profile, ForgeService forgeService) {
        Inventory inventory = new Inventory(InventoryType.HOPPER, LEGACY.deserialize("§bEnchantment Forge"));
        inventory.setItemStack(0, button(Material.ENCHANTED_BOOK, "§r§9Normal §r§8enchantment", List.of("§r§125 Magic dust")));
        inventory.setItemStack(1, button(Material.NETHER_STAR, "§r§9High-end §r§8enchantment", List.of("§r§140 Magic dust")));
        inventory.setItemStack(4, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Here you can spend magic dust to forge a random enchantment. If you forge a high-end enchantment you will have a higher chance to get a better enchantment. You're still able to get all enchants from both types."))));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 0) {
                forge(player, profile, forgeService, false);
                player.closeInventory();
            } else if (event.getSlot() == 1) {
                forge(player, profile, forgeService, true);
                player.closeInventory();
            }
        });
        InventoryOpeners.replace(player, inventory);
    }

    private void forge(Player player, UserProfile profile, ForgeService forgeService, boolean highEnd) {
        ForgeService.EnchantForgeResult result = forgeService.forgeEnchantment(player, profile, highEnd);
        if (!result.affordable()) {
            player.sendMessage(CommandMessages.error("You don't have enough magic dust to forge this enchantment."));
            return;
        }
        player.sendMessage(CommandMessages.success("You have forged §r" + enchantName(result) + "§r§7."));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return ItemStack.of(material)
            .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)))
            .with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore.stream().<Component>map(LEGACY::deserialize).toList()));
    }

    private static String enchantName(ForgeService.EnchantForgeResult result) {
        String color = switch (result.enchant().rarityBucket()) {
            case 0 -> "§7";
            case 1 -> "§a";
            case 2 -> "§c";
            case 3 -> "§4";
            case 4 -> "§5";
            default -> "§l§6";
        };
        String suffix = result.enchant().maxLevel() <= 1 ? "" : " " + toRoman(result.level());
        return color + result.enchant().displayName() + suffix;
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(number);
        };
    }
}
