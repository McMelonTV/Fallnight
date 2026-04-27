package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
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

public final class AuctionSellMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final AuctionService auctionService;
    private final PlayerProfileService profileService;
    private final AnvilInputService anvilInputService;

    public AuctionSellMenuService(AuctionService auctionService, PlayerProfileService profileService, AnvilInputService anvilInputService) {
        this.auctionService = auctionService;
        this.profileService = profileService;
        this.anvilInputService = anvilInputService;
    }

    private static String describeItem(ItemStack item) {
        Component customName = item.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return item.amount() + "x " + LEGACY.serialize(customName).replace("§r", "");
        }
        return item.amount() + "x " + item.material().name().toLowerCase().replace('_', ' ');
    }

    private static String legacyMoney(double amount) {
        return Long.toString((long) amount);
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.material() == Material.AIR || item.amount() <= 0;
    }

    private static boolean validateSellCount(Player player, ItemStack held, int count) {
        if (count <= 0) {
            player.sendMessage(CommandMessages.error("Please enter a valid item count."));
            return false;
        }
        if (count > held.maxStackSize()) {
            player.sendMessage(CommandMessages.error("You can't sell more than " + held.maxStackSize() + " items for this item type."));
            return false;
        }
        if (count > held.amount()) {
            player.sendMessage(CommandMessages.error("You can't sell more items than you are holding!"));
            return false;
        }
        return true;
    }

    public void open(Player player) {
        int maxListings = maxListingsFor(player.getUsername());
        if (auctionService.activeListingCount(player.getUsername()) >= maxListings) {
            player.sendMessage(CommandMessages.error("You have already reached your maximum auction items of " + maxListings + "."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (isAir(held)) {
            player.sendMessage(CommandMessages.error("You can't sell air."));
            return;
        }

        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§b/auction sell"));
        inventory.setItemStack(4, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Enter a price and how much of the items in your hand you cant to sell in pv."))));
        inventory.setItemStack(11, amountButton(1, held.amount()));
        inventory.setItemStack(13, amountButton(Math.max(1, held.amount() / 2), held.amount()));
        inventory.setItemStack(15, amountButton(held.amount(), held.amount()));
        inventory.setItemStack(21, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§bCustom count"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Enter any amount up to held stack")))));
        inventory.setItemStack(22, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§cClose"))));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 22) {
                event.getPlayer().closeInventory();
                return;
            }
            if (event.getSlot() == 21) {
                openCountPrompt(event.getPlayer());
                return;
            }
            if (event.getSlot() == 11) {
                openPricePrompt(event.getPlayer(), 1);
                return;
            }
            if (event.getSlot() == 13) {
                openPricePrompt(event.getPlayer(), Math.max(1, event.getPlayer().getItemInMainHand().amount() / 2));
                return;
            }
            if (event.getSlot() == 15) {
                openPricePrompt(event.getPlayer(), event.getPlayer().getItemInMainHand().amount());
            }
        });
        InventoryOpeners.replace(player, inventory);
    }

    public void openWithPrice(Player player, int price) {
        int maxListings = maxListingsFor(player.getUsername());
        if (auctionService.activeListingCount(player.getUsername()) >= maxListings) {
            player.sendMessage(CommandMessages.error("You have already reached your maximum auction items of " + maxListings + "."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (isAir(held)) {
            player.sendMessage(CommandMessages.error("You can't sell air."));
            return;
        }
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§b/auction sell"));
        inventory.setItemStack(4, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7Select the amount to sell for §b$" + legacyMoney(price)))));
        inventory.setItemStack(11, amountButton(1, held.amount()));
        inventory.setItemStack(13, amountButton(Math.max(1, held.amount() / 2), held.amount()));
        inventory.setItemStack(15, amountButton(held.amount(), held.amount()));
        inventory.setItemStack(21, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§bCustom count"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Enter any amount up to held stack")))));
        inventory.setItemStack(22, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§cClose"))));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 22) {
                event.getPlayer().closeInventory();
                return;
            }
            if (event.getSlot() == 21) {
                openCountPromptForPrice(event.getPlayer(), price);
                return;
            }
            if (event.getSlot() == 11) {
                applySale(event.getPlayer(), 1, Integer.toString(price));
                return;
            }
            if (event.getSlot() == 13) {
                applySale(event.getPlayer(), Math.max(1, event.getPlayer().getItemInMainHand().amount() / 2), Integer.toString(price));
                return;
            }
            if (event.getSlot() == 15) {
                applySale(event.getPlayer(), event.getPlayer().getItemInMainHand().amount(), Integer.toString(price));
            }
        });
        InventoryOpeners.replace(player, inventory);
    }

    private void openCountPrompt(Player player) {
        anvilInputService.open(player, "Auction Count", "§r§b§l> §r§7Item count", "", (source, rawInput) -> {
            int count;
            try {
                count = Integer.parseInt((rawInput == null ? "" : rawInput).trim());
            } catch (NumberFormatException exception) {
                source.sendMessage(CommandMessages.error("Please enter a valid item count."));
                return;
            }
            ItemStack held = source.getItemInMainHand();
            if (isAir(held)) {
                source.sendMessage(CommandMessages.error("You can't sell air."));
                return;
            }
            if (!validateSellCount(source, held, count)) {
                return;
            }
            openPricePrompt(source, count);
        });
    }

    private void openCountPromptForPrice(Player player, int price) {
        anvilInputService.open(player, "Auction Count", "§r§b§l> §r§7Item count", "", (source, rawInput) -> {
            int count;
            try {
                count = Integer.parseInt((rawInput == null ? "" : rawInput).trim());
            } catch (NumberFormatException exception) {
                source.sendMessage(CommandMessages.error("Please enter a valid item count."));
                return;
            }
            ItemStack held = source.getItemInMainHand();
            if (isAir(held)) {
                source.sendMessage(CommandMessages.error("You can't sell air."));
                return;
            }
            if (!validateSellCount(source, held, count)) {
                return;
            }
            applySale(source, count, Integer.toString(price));
        });
    }

    private void openPricePrompt(Player player, int count) {
        anvilInputService.open(player, "Auction Price", "§r§b§l> §r§7Price", "", (source, rawInput) -> applySale(source, count, rawInput));
    }

    private void applySale(Player player, int count, String rawInput) {
        int price;
        try {
            price = (int) Double.parseDouble((rawInput == null ? "" : rawInput).trim());
        } catch (NumberFormatException exception) {
            player.sendMessage(CommandMessages.error("Please enter a valid item price."));
            return;
        }
        if (price < 1) {
            player.sendMessage(CommandMessages.error("Please enter a valid item price."));
            return;
        }
        ItemStack held = player.getItemInMainHand();
        if (isAir(held)) {
            player.sendMessage(CommandMessages.error("You can't sell air."));
            return;
        }
        if (!validateSellCount(player, held, count)) {
            return;
        }
        ItemStack sold = held.withAmount(count);
        player.setItemInMainHand(count == held.amount() ? ItemStack.AIR : held.withAmount(held.amount() - count));
        auctionService.createListing(player.getUsername(), sold, price);
        player.sendMessage(CommandMessages.success(
                "You are now auctioning off §b" + describeItem(sold) + " §r§7for §b$" + legacyMoney(price) + "§r§7."
        ));
        player.closeInventory();
    }

    private int maxListingsFor(String username) {
        var profile = profileService.getOrCreateByUsername(username);
        Object raw = profile.getExtraData().get("maxAuctionListings");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private ItemStack amountButton(int amount, int heldAmount) {
        String label = amount >= heldAmount ? "Sell all" : "Sell x" + amount;
        return ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§b" + label))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Held: §f" + heldAmount))));
    }
}
