package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.tag.TagDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class TagsMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Map<String, String> COLORS = createColors();

    private final PlayerProfileService profileService;
    private final TagService tagService;
    private final PermissionService permissionService;

    public TagsMenuService(PlayerProfileService profileService, TagService tagService, PermissionService permissionService) {
        this.profileService = profileService;
        this.tagService = tagService;
        this.permissionService = permissionService;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int page) {
        UserProfile profile = profileService.getOrCreate(player);
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bTag selector"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 22) {
                event.getPlayer().closeInventory();
                return;
            }
            if (slot == 20 && page > 0) {
                open(player, page - 1);
                return;
            }
            List<TagDefinition> unlocked = tagService.listUnlockedTags(profile);
            int totalPages = Math.max(1, (int) Math.ceil(unlocked.size() / 18D));
            if (slot == 24 && page + 1 < totalPages) {
                open(player, page + 1);
                return;
            }
            if (slot == 18 && permissionService.hasPermission(player, "fallnight.tag.colored")) {
                openColors(player);
                return;
            }
            if (slot == 26) {
                tagService.clearAppliedTag(profile);
                profile.getExtraData().put("tagColor", "");
                profileService.save(profile);
                player.sendMessage(CommandMessages.success("You have removed your tag."));
                renderTags(inventory, player, page);
                return;
            }
            int tagIndex = page * 18 + slot;
            if (slot < 0 || slot >= 18 || tagIndex >= unlocked.size()) {
                return;
            }
            TagDefinition definition = unlocked.get(tagIndex);
            tagService.setAppliedTag(profile, definition.id());
            profile.getExtraData().put("tagColor", "");
            profileService.save(profile);
            player.sendMessage(CommandMessages.success("You have changed your tag to §r" + definition.tag() + "§r§7."));
            renderTags(inventory, player, page);
        });
        renderTags(inventory, player, page);
        InventoryOpeners.replace(player, inventory);
    }

    private void openColors(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bTag color selector"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 26) {
                event.getPlayer().closeInventory();
                open(player, 0);
                return;
            }
            if (slot == 25) {
                profile.getExtraData().put("tagColor", "");
                profileService.save(profile);
                player.sendMessage(CommandMessages.success("You removed your tag color."));
                open(player, 0);
                return;
            }
            List<Map.Entry<String, String>> entries = new ArrayList<>(COLORS.entrySet());
            if (slot < 0 || slot >= entries.size()) {
                return;
            }
            Map.Entry<String, String> entry = entries.get(slot);
            profile.getExtraData().put("tagColor", entry.getValue());
            profileService.save(profile);
            player.sendMessage(CommandMessages.success("You changed your tag color to §r§" + entry.getValue() + entry.getKey() + "§r§7."));
            open(player, 0);
        });
        int slot = 0;
        for (Map.Entry<String, String> entry : COLORS.entrySet()) {
            inventory.setItemStack(slot++, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§" + entry.getValue() + entry.getKey() + "§r"))));
        }
        inventory.setItemStack(24, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§7Select a color to apply to your tag."))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Your tag will be in this color instead of the default color.")))));
        inventory.setItemStack(25, button(Material.BARRIER, "§0> §8Clear color §0<"));
        inventory.setItemStack(26, button(Material.PAPER, "§bBack"));
        InventoryOpeners.replace(player, inventory);
    }

    private void renderTags(Inventory inventory, Player player, int page) {
        inventory.clear();
        UserProfile profile = profileService.getOrCreate(player);
        List<TagDefinition> unlocked = tagService.listUnlockedTags(profile);
        Object current = profile.getExtraData().get("appliedTag");
        String applied = current instanceof String value ? value : "";
        int totalPages = Math.max(1, (int) Math.ceil(unlocked.size() / 18D));
        int start = Math.max(0, page) * 18;
        for (int i = 0; i < 18 && start + i < unlocked.size(); i++) {
            TagDefinition definition = unlocked.get(start + i);
            boolean selected = definition.id().equalsIgnoreCase(applied);
            inventory.setItemStack(i, ItemStack.of(selected ? Material.LIME_CONCRETE : Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(definition.tag() + "§r§8 tag"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§r§8[" + rarityColor(definition.rarity()) + rarityName(definition.rarity()) + "§r§8]")))));
        }
        if (permissionService.hasPermission(player, "fallnight.tag.colored")) {
            inventory.setItemStack(18, button(Material.BLUE_DYE, "§0> §8Change tag color §0<"));
        }
        if (page > 0) {
            inventory.setItemStack(20, button(Material.ARROW, "§bPrevious page"));
        }
        inventory.setItemStack(21, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Click on a tag to apply it."))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(
            LEGACY.deserialize("§r§7It will be shown before your name in chat."),
            LEGACY.deserialize("§7Donators can also choose a color to their own liking for the tag."),
            LEGACY.deserialize("§7Page §b" + (page + 1) + "§7/§b" + totalPages)
        ))));
        inventory.setItemStack(22, button(Material.BARRIER, "§cClose"));
        if (page + 1 < totalPages) {
            inventory.setItemStack(24, button(Material.ARROW, "§bNext page"));
        }
        inventory.setItemStack(26, button(Material.RED_STAINED_GLASS_PANE, "§0> §8Clear tag §0<"));
    }

    private static ItemStack button(Material material, String name) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)));
    }

    private static String rarityColor(int rarity) {
        return switch (rarity) {
            case 0 -> "§7";
            case 1 -> "§7";
            case 2 -> "§a";
            case 3 -> "§9";
            case 4 -> "§5";
            case 5 -> "§b";
            default -> "§7";
        };
    }

    private static String rarityName(int rarity) {
        return switch (rarity) {
            case 0 -> "common";
            case 1 -> "common";
            case 2 -> "uncommon";
            case 3 -> "rare";
            case 4 -> "very rare";
            case 5 -> "legendary";
            default -> "common";
        };
    }

    private static Map<String, String> createColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("Dark Red", "4");
        colors.put("Red", "c");
        colors.put("Gold", "6");
        colors.put("Yellow", "e");
        colors.put("Dark Green", "2");
        colors.put("Green", "a");
        colors.put("Aqua", "b");
        colors.put("Dark Aqua", "3");
        colors.put("Dark Blue", "1");
        colors.put("Blue", "9");
        colors.put("Light Purple", "d");
        colors.put("Dark Purple", "5");
        colors.put("White", "f");
        colors.put("Gray", "7");
        colors.put("Dark Gray", "8");
        colors.put("Black", "0");
        return colors;
    }
}
