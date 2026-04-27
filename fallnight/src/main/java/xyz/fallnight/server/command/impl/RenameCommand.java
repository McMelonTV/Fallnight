package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.AnvilInputService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.tag.Tag;

public final class RenameCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Tag<String> SIGNED_TAG = Tag.String("signed");
    private final AnvilInputService anvilInputService = new AnvilInputService();

    public RenameCommand(PermissionService permissionService) {
        super("renameitem", permissionService);

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }

            ItemStack held = player.getItemInMainHand();
            if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
                sender.sendMessage(CommandMessages.error("Please hold an item to rename."));
                return;
            }

            String initialName = held.get(DataComponents.CUSTOM_NAME) == null ? "name" : LEGACY.serialize(held.get(DataComponents.CUSTOM_NAME));
            anvilInputService.open(player, "§bRename item", "§b§l> §r§7Item name", initialName, (submitter, input) -> renameHeldItem(submitter, held, input));
        });
    }

    private void renameHeldItem(Player player, ItemStack original, String input) {
            ItemStack held = player.getItemInMainHand();
            if (held == null || held.material() == Material.AIR || held.amount() <= 0 || !held.isSimilar(original)) {
                player.sendMessage(CommandMessages.error("Please hold an item to rename."));
                return;
            }
            String name = input == null ? "" : input.trim();
            if (name.isBlank()) {
                player.sendMessage(CommandMessages.error("Please enter a name for your item."));
                return;
            }
            if (name.length() < 3) {
                player.sendMessage(CommandMessages.error("The item name has to be longer than 3 characters."));
                return;
            }
            if (isBlocked(held.material())) {
                player.sendMessage(CommandMessages.error("You can't rename this item."));
                return;
            }
            if (player.getLevel() < 25) {
                player.sendMessage(CommandMessages.error("You need at least 25 levels to rename an item."));
                return;
            }
            Component customName = LEGACY.deserialize(name.replace('&', '§'));
            if (PlainTextComponentSerializer.plainText().serialize(customName).trim().isBlank()) {
                player.sendMessage(CommandMessages.error("The item name must be visible."));
                return;
            }

            player.setItemInMainHand(sign(held.withCustomName(xyz.fallnight.server.util.ItemTextStyles.itemText(customName)), player.getUsername()));
            player.setLevel(Math.max(0, player.getLevel() - 25));
            player.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You renamed the item in your hand to §r" + name.replace('&', '§') + "§r§7 for §b25 levels§r§7."));
    }

    @Override
    public String permission() {
        return "fallnight.command.renameitem";
    }

    @Override
    public String summary() {
        return "rename an item";
    }

    @Override
    public String usage() {
        return "/renameitem";
    }

    private static boolean isBlocked(Material material) {
        return material == Material.ENCHANTED_BOOK
            || material == Material.GLOWSTONE
            || material == Material.GLOWSTONE_DUST
            || material.name().toLowerCase(Locale.ROOT).contains("dye")
            || material == Material.TRIPWIRE_HOOK;
    }

    private static ItemStack sign(ItemStack item, String signer) {
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§7Signed by: §b" + signer));
        List<Component> existing = item.get(DataComponents.LORE);
        if (existing != null) {
            lore.addAll(existing);
        }
        CustomData customData = item.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            customData = CustomData.EMPTY;
        }
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore)).with(DataComponents.CUSTOM_DATA, customData.withTag(SIGNED_TAG, signer));
    }
}
