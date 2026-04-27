package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class ForgeMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ForgeService forgeService;
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;
    private final LegacyCustomItemService customItemService;

    public ForgeMenuService(ForgeService forgeService, PlayerProfileService profileService) {
        this(forgeService, profileService, null);
    }

    public ForgeMenuService(ForgeService forgeService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        this.forgeService = forgeService;
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;
        this.customItemService = new LegacyCustomItemService();
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_1_ROW, LEGACY.deserialize("§bItem Forge"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 0 -> openCategories(event.getPlayer());
                case 1 -> openEnchantForge(event.getPlayer());
                case 2 -> openDisenchantInfo(event.getPlayer());
                case 3 -> openRepairInfo(event.getPlayer());
                default -> {
                }
            }
        });
        inventory.setItemStack(0, button(Material.ANVIL, "§8Forge a new item"));
        inventory.setItemStack(1, button(Material.ENCHANTED_BOOK, "§8Forge an enchantment"));
        inventory.setItemStack(2, button(Material.BOOK, "§8Remove enchantments"));
        inventory.setItemStack(3, button(Material.IRON_INGOT, "§8Repair an item"));
        InventoryOpeners.replace(player, inventory);
    }

    private void openCategories(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bItem Forge"));
        List<ForgeService.ForgeCategory> categories = forgeService.categories();
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot < 0 || slot >= categories.size()) {
                return;
            }
            openCategory(event.getPlayer(), categories.get(slot));
        });
        for (int i = 0; i < categories.size(); i++) {
            inventory.setItemStack(i, button(Material.CHEST, categories.get(i).displayName()));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private void openCategory(Player player, ForgeService.ForgeCategory category) {
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize("§bItem Forge - " + category.displayName()));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot < 0 || slot >= category.recipes().size()) {
                return;
            }
            var profile = profileService.getOrCreate(event.getPlayer());
            var result = forgeService.craft(event.getPlayer(), profile, category.recipes().get(slot).name());
            if (result.success()) {
                profileService.save(profile);
                event.getPlayer().sendMessage(CommandMessages.success("You forged a " + displayName(result.item()) + "§r§7."));
            } else if (!result.affordable()) {
                event.getPlayer().sendMessage(CommandMessages.error("You cannot afford to forge this."));
            }
        });
        for (int i = 0; i < category.recipes().size() && i < 54; i++) {
            var recipe = category.recipes().get(i);
            ItemStack item = new LegacyCustomItemService().createFromCustomId(recipe.customId()).orElse(ItemStack.of(Material.CHEST));
            inventory.setItemStack(i, item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(recipe.price().display())))));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private void openEnchantForge(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_1_ROW, LEGACY.deserialize("§bEnchantment Forge"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            boolean high = event.getSlot() == 1;
            if (event.getSlot() != 0 && event.getSlot() != 1) {
                return;
            }
            UserProfile profile = profileService.getOrCreate(event.getPlayer());
            var result = forgeService.forgeEnchantment(event.getPlayer(), profile, high);
            if (result.affordable()) {
                profileService.save(profile);
                event.getPlayer().sendMessage(CommandMessages.success("You have forged §r" + enchantName(result) + "§r§7."));
            } else {
                event.getPlayer().sendMessage(CommandMessages.error("You don't have enough magic dust to forge this enchantment."));
            }
        });
        inventory.setItemStack(0, button(Material.ENCHANTED_BOOK, "§r§9Normal §r§8enchantment", List.of("§r§125 Magic dust")));
        inventory.setItemStack(1, button(Material.NETHER_STAR, "§r§9High-end §r§8enchantment", List.of("§r§140 Magic dust")));
        inventory.setItemStack(4, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Here you can spend magic dust to forge a random enchantment. If you forge a high-end enchantment you will have a higher chance to get a better enchantment. You're still able to get all enchants from both types."))));
        InventoryOpeners.replace(player, inventory);
    }

    private void openDisenchantInfo(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_1_ROW, LEGACY.deserialize("§bItem disenchantment"));
        DisenchantPreview preview = previewDisenchant(player);
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 5) {
                event.getPlayer().closeInventory();
                return;
            }
            if (event.getSlot() != 3) {
                return;
            }
            if (!preview.valid()) {
                event.getPlayer().sendMessage(CommandMessages.error("Please hold an item with enchantments."));
                return;
            }
            if (!preview.affordable()) {
                event.getPlayer().sendMessage(CommandMessages.error("You can't afford to disenchant this."));
                return;
            }
            ItemStack held = event.getPlayer().getItemInMainHand();
            ItemStack updated = customItemService.clearCustomEnchants(held);
            event.getPlayer().setItemInMainHand(updated);
            UserProfile profile = profileService.getOrCreate(event.getPlayer());
            for (ItemStack book : preview.books()) {
                if (itemDeliveryService != null) {
                    itemDeliveryService.deliver(event.getPlayer(), profile, book);
                } else {
                    event.getPlayer().getInventory().addItemStack(book);
                }
            }
            payDisenchant(event.getPlayer(), preview.magicDust(), preview.xpLevels());
            profileService.save(profile);
            event.getPlayer().sendMessage(CommandMessages.success(
                "You successfully disenchanted your item for " + preview.magicDust() + " magicdust and " + preview.xpLevels() + " levels."
            ));
            event.getPlayer().closeInventory();
        });
        inventory.setItemStack(4, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7This will remove all of the enchantments of the item in your hand and give the enchantments to you. Doing this will come at a cost based on what enchantments you have."))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(
            preview.valid()
                ? "§r§7For the item you are holding, it will cost §b" + preview.magicDust() + " magicdust §r§7and §b" + preview.xpLevels() + " levels§r§7."
                : "§r§7Hold an enchanted custom item in your hand to disenchant it."
        )))));
        inventory.setItemStack(3, button(Material.EMERALD_BLOCK, "§8Confirm disenchanting"));
        inventory.setItemStack(5, button(Material.REDSTONE_BLOCK, "§8Cancel"));
        InventoryOpeners.replace(player, inventory);
    }

    private void openRepairInfo(Player player) {
        Inventory inventory = new Inventory(InventoryType.HOPPER, LEGACY.deserialize("§8Item Forge"));
        ForgeService.RepairResult preview = forgeService.previewRepair(player);
        ItemStack held = player.getItemInMainHand();
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 4) {
                ForgeService.RepairResult result = forgeService.repair(event.getPlayer());
                if (!result.validItem()) {
                    event.getPlayer().sendMessage(CommandMessages.error("Hold a damaged custom item in your main hand first."));
                    return;
                }
                if (!result.affordable()) {
                    event.getPlayer().sendMessage(CommandMessages.error("You don't have any repair resources for that item."));
                    return;
                }
                event.getPlayer().sendMessage(CommandMessages.success("You successfully repaired your item using " + result.display() + "."));
                event.getPlayer().closeInventory();
            }
        });
        inventory.setItemStack(0, held == null || held.isAir() ? button(Material.BARRIER, "§r§cHold a damaged item") : held.withAmount(1));
        inventory.setItemStack(1, button(Material.RED_STAINED_GLASS_PANE, "§r§c<-- Item", List.of("§r§cResources -->")));
        inventory.setItemStack(2, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(preview.validItem()
            ? (preview.affordable() ? "§r§7Resources: §b" + preview.display() : "§r§cNo repair resources available")
            : "§r§cHold a damaged custom item in your hand"))));
        inventory.setItemStack(3, button(Material.RED_STAINED_GLASS_PANE, "§r§c<-- Resources", List.of("§r§aConfirm -->")));
        inventory.setItemStack(4, button(Material.EMERALD_BLOCK, "§8Confirm repair"));
        InventoryOpeners.replace(player, inventory);
    }

    private static ItemStack button(Material material, String title) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(title)));
    }

    private static ItemStack button(Material material, String title, List<String> loreLines) {
        return ItemStack.of(material)
            .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(title)))
            .with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(loreLines.stream().<Component>map(LEGACY::deserialize).toList()));
    }

    private static String displayName(ItemStack item) {
        if (item == null) {
            return "";
        }
        Component component = item.get(DataComponents.CUSTOM_NAME);
        return component == null ? "" : LEGACY.serialize(component);
    }

    private static String enchantName(ForgeService.EnchantForgeResult result) {
        if (result == null || result.enchant() == null) {
            return "";
        }
        String color = switch (rarityBucket(result.enchant())) {
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

    private static int rarityBucket(FallnightCustomEnchantRegistry.Definition definition) {
        return definition.rarityBucket();
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

    private DisenchantPreview previewDisenchant(Player player) {
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.isAir()) {
            return new DisenchantPreview(false, false, 0, 0, List.of());
        }
        Map<String, Integer> customEnchants = customItemService.customEnchants(held);
        if (customEnchants.isEmpty()) {
            return new DisenchantPreview(false, false, 0, 0, List.of());
        }
        int magicDust = 0;
        int xpLevels = 0;
        List<ItemStack> books = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : customEnchants.entrySet()) {
            int level = Math.max(1, entry.getValue());
            int[] rarityCost = rarityCost(entry.getKey());
            magicDust += rarityCost[0] * level;
            xpLevels += rarityCost[1] * level;
            int legacyId = FallnightCustomEnchantRegistry.byId(entry.getKey())
                .map(FallnightCustomEnchantRegistry.Definition::legacyId)
                .orElse(101);
            books.add(customItemService.enchantmentBookByVariantLevel(legacyId, level));
        }
        boolean affordable = countMagicDust(player) >= magicDust && player.getLevel() >= xpLevels;
        return new DisenchantPreview(true, affordable, magicDust, xpLevels, List.copyOf(books));
    }

    private static int[] rarityCost(String enchantId) {
        return FallnightCustomEnchantRegistry.byId(enchantId)
            .map(definition -> new int[] {definition.disenchantMagicDustCost(), definition.disenchantXpCost()})
            .orElse(new int[] {14, 7});
    }

    private int countMagicDust(Player player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getInnerSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (customItemService.customItemId(stack) == 2 && stack.amount() > 0) {
                total += stack.amount();
            }
        }
        return total;
    }

    private void payDisenchant(Player player, int magicDust, int xpLevels) {
        int remaining = Math.max(0, magicDust);
        for (int slot = 0; slot < player.getInventory().getInnerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (customItemService.customItemId(stack) != 2 || stack.amount() <= 0) {
                continue;
            }
            int used = Math.min(remaining, stack.amount());
            int next = stack.amount() - used;
            player.getInventory().setItemStack(slot, next <= 0 ? ItemStack.AIR : stack.withAmount(next));
            remaining -= used;
        }
        player.setLevel(Math.max(0, player.getLevel() - xpLevels));
    }

    private record DisenchantPreview(boolean valid, boolean affordable, int magicDust, int xpLevels, List<ItemStack> books) {
    }
}
