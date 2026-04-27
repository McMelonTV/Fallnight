package xyz.fallnight.server.service;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class RenameAnvilService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final AnvilInputService anvilInputService;

    public RenameAnvilService(AnvilInputService anvilInputService) {
        this.anvilInputService = anvilInputService;
    }

    public void open(Player player) {
        ItemStack hand = player.getItemInMainHand();
        if (hand.isAir()) {
            player.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please hold an item to rename."));
            return;
        }

        String initialName = hand.get(DataComponents.CUSTOM_NAME) != null 
            ? LEGACY.serialize(hand.get(DataComponents.CUSTOM_NAME))
            : "name";

        anvilInputService.open(player, "§bRename item", "§b§l> §r§7Item name", initialName, (sender, name) -> {
            ItemStack currentHand = sender.getItemInMainHand();
            if (!currentHand.isSimilar(hand)) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please hold an item to rename."));
                return;
            }

            if (name == null || name.length() < 3) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7The item name has to be longer than 3 characters."));
                return;
            }

            String clean = name.replace("§", "").trim();
            if (clean.isEmpty()) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7The item name must be visible."));
                return;
            }

            int xp = sender.getLevel();
            if (xp < 25) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You need at least 25 levels to rename an item."));
                return;
            }

            Material mat = currentHand.material();
            if (mat == Material.ENCHANTED_BOOK || mat == Material.GLOWSTONE || mat == Material.GLOWSTONE_DUST 
                || mat.name().contains("dye") || mat == Material.TRIPWIRE_HOOK) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You can't rename this item."));
                return;
            }

            sender.setLevel(xp - 25);
            ItemStack renamed = currentHand.with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name.replace("&", "§"))));
            sender.setItemInMainHand(renamed);
            sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You renamed the item in your hand to §r" + name + "§r§7 for §b25 levels§r§7."));
        });
    }
}
