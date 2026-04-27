package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.crate.CrateDefinition;
import xyz.fallnight.server.domain.crate.CrateReward;
import xyz.fallnight.server.domain.crate.WeightedCrateReward;
import xyz.fallnight.server.util.NumberFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class CrateMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##");
    private final CrateService crateService;
    private final PagedTextMenuService pagedTextMenuService;
    private final PlayerProfileService profileService;
    private final LegacyCustomItemService customItemService = new LegacyCustomItemService();

    public CrateMenuService(CrateService crateService, PagedTextMenuService pagedTextMenuService) {
        this(crateService, null, pagedTextMenuService);
    }

    public CrateMenuService(CrateService crateService, PlayerProfileService profileService, PagedTextMenuService pagedTextMenuService) {
        this.crateService = crateService;
        this.profileService = profileService;
        this.pagedTextMenuService = pagedTextMenuService;
    }

    private static InventoryType inventoryType(int count) {
        return count <= 5 ? InventoryType.HOPPER : count <= 27 ? InventoryType.CHEST_3_ROW : InventoryType.CHEST_6_ROW;
    }

    private static String formatReward(CrateReward reward) {
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            return reward.description();
        }
        if (reward.randomTagCount() > 0) {
            return reward.randomTagCount() + "x " + reward.description();
        }
        if (reward.grantedTag() != null && !reward.grantedTag().isBlank()) {
            return "1x " + reward.description();
        }
        if (reward.forgedBookCount() > 0) {
            return reward.description();
        }
        if (reward.money() > 0D && reward.prestigePoints() > 0L) {
            return NumberFormatter.currency(reward.money()) + " and " + reward.prestigePoints() + " PP";
        }
        if (reward.money() > 0D) {
            return NumberFormatter.currency(reward.money());
        }
        return reward.prestigePoints() + " PP";
    }

    private static Material fallbackMaterial(CrateReward reward) {
        if (reward.money() > 0D) {
            return Material.GOLD_INGOT;
        }
        if (reward.prestigePoints() > 0L) {
            return Material.NETHER_STAR;
        }
        if (reward.forgedBookCount() > 0) {
            return Material.ENCHANTED_BOOK;
        }
        if (reward.randomTagCount() > 0 || (reward.grantedTag() != null && !reward.grantedTag().isBlank())) {
            return Material.NAME_TAG;
        }
        return Material.CHEST;
    }

    private static int crateNumericId(String crateId) {
        return switch (crateId.toLowerCase(java.util.Locale.ROOT)) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            case "vote" -> 99;
            case "koth" -> 120;
            default -> 99;
        };
    }

    public void open(Player player) {
        List<CrateDefinition> crates = crateService.allCrates();
        Inventory inventory = new Inventory(inventoryType(crates.size()), LEGACY.deserialize("§bCrates"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot < 0 || slot >= crates.size()) {
                return;
            }
            openCrate(event.getPlayer(), crates.get(slot));
        });
        for (int i = 0; i < crates.size(); i++) {
            CrateDefinition crate = crates.get(i);
            inventory.setItemStack(i, ItemStack.of(Material.TRIPWIRE_HOOK).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§b§l" + crate.displayName() + "§r§8 crate"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(
                    LEGACY.deserialize("§r§7Click on a crate to view its drop chances")
            ))));
        }
        InventoryOpeners.replace(player, inventory);
    }

    public void openBrowser(Player player) {
        List<CrateDefinition> crates = crateService.allCrates();
        Inventory inventory = new Inventory(inventoryType(crates.size()), LEGACY.deserialize("§bCrates"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot < 0 || slot >= crates.size()) {
                return;
            }
            if (event.getPlayer() instanceof Player clicker) {
                openRewards(clicker, crates.get(slot));
            }
        });
        for (int i = 0; i < crates.size(); i++) {
            CrateDefinition crate = crates.get(i);
            inventory.setItemStack(i, ItemStack.of(Material.TRIPWIRE_HOOK).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§b§l" + crate.displayName() + "§r§8 crate"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(
                    LEGACY.deserialize("§r§7Click on a crate to view its drop chances")
            ))));
        }
        InventoryOpeners.replace(player, inventory);
    }

    public void openCrate(Player player, CrateDefinition crate) {
        if (!consumeHeldKey(player, crate.id())) {
            player.sendMessage(CommandMessages.error("You do not have any '" + crate.id() + "' keys."));
            return;
        }
        var profile = crateServiceProfile(player);
        CrateService.OpenResult result = crateService.openWithExternalKey(crate.id(), player, profile);
        player.sendMessage(CommandMessages.success(
                "Opened '" + result.crateId() + "' and received " + formatReward(result.reward()) + ". Remaining keys: " + result.remainingKeys() + "."
        ));
    }

    private xyz.fallnight.server.domain.user.UserProfile crateServiceProfile(Player player) {
        if (profileService == null) {
            throw new IllegalStateException("profileService required for opening crates");
        }
        return profileService.getOrCreate(player);
    }

    public void openRewards(Player player, CrateDefinition crate) {
        Inventory inventory = new Inventory(inventoryType(crate.rewards().size()), LEGACY.deserialize("§l§o§b" + crate.displayName() + "§r§8 crate items"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        double totalWeight = crate.rewards().stream().mapToInt(WeightedCrateReward::weight).sum();
        for (int i = 0; i < crate.rewards().size(); i++) {
            WeightedCrateReward weightedReward = crate.rewards().get(i);
            inventory.setItemStack(i, displayReward(weightedReward, totalWeight));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private ItemStack displayReward(WeightedCrateReward weighted, double totalWeight) {
        CrateReward reward = weighted.reward();
        ItemStack base = ItemStack.AIR;
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            base = customItemService.createFromCustomId(reward.customItemId()).orElse(ItemStack.AIR);
        }
        if (base.isAir()) {
            base = ItemStack.of(fallbackMaterial(reward), reward.randomTagCount() > 0 ? reward.randomTagCount() : 1);
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§7Drop chance: §b" + PERCENT_FORMAT.format((weighted.weight() / totalWeight) * 100D) + "%"));
        return base.with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r" + reward.description()))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore));
    }

    private boolean consumeHeldKey(Player player, String crateId) {
        ItemStack held = player.getItemInMainHand();
        if (held == null || held.isAir()) {
            return false;
        }
        if (customItemService.customItemId(held) != 20) {
            return false;
        }
        var customData = held.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        Integer keyType = customData.getTag(net.minestom.server.tag.Tag.Integer("cratekey"));
        if (keyType == null || keyType.intValue() != crateNumericId(crateId)) {
            return false;
        }
        int nextAmount = held.amount() - 1;
        player.setItemInMainHand(nextAmount <= 0 ? ItemStack.AIR : held.withAmount(nextAmount));
        return true;
    }
}
