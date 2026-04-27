package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.auction.AuctionClaim;
import xyz.fallnight.server.domain.auction.AuctionListing;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class AuctionMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final Sound CLICK_SOUND = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1.0f, 1.0f);

    private final AuctionService auctionService;
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;
    private final Map<Inventory, Session> sessions = new ConcurrentHashMap<>();

    public AuctionMenuService(AuctionService auctionService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        this.auctionService = auctionService;
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;
    }

    public void openMarket(net.minestom.server.entity.Player player) {
        open(player, View.MARKET, 1);
    }

    public void openOwned(net.minestom.server.entity.Player player) {
        open(player, View.OWNED, 1);
    }

    private void open(net.minestom.server.entity.Player player, View view, int page) {
        if (view == View.MARKET && auctionService.listActive().isEmpty() && auctionService.listClaims(player.getUsername()).isEmpty()) {
            player.sendMessage(CommandMessages.error("There are no items being actioned."));
            return;
        }
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize("§8Auction"));
        Session session = new Session(player.getUsername(), view, Math.max(1, page));
        sessions.put(inventory, session);
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> onClick(event, inventory));
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> sessions.remove(inventory));
        render(inventory, session, player);
        InventoryOpeners.replace(player, inventory);
    }

    private void onClick(InventoryPreClickEvent event, Inventory inventory) {
        Session session = sessions.get(inventory);
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        int slot = event.getSlot();
        if (slot >= 45) {
            handleControl(player, inventory, session, slot);
            return;
        }
        List<Entry> entries = entries(session.view(), player.getUsername());
        int index = (session.page() - 1) * 45 + slot;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry entry = entries.get(index);
        switch (entry.kind()) {
            case LISTING -> handleListingClick(player, inventory, session, entry.listing());
            case CLAIM -> handleClaimClick(player, inventory, session, entry.claim());
        }
    }

    private void handleControl(net.minestom.server.entity.Player player, Inventory inventory, Session session, int slot) {
        switch (slot) {
            case 45 -> render(inventory, session.withView(View.OWNED).withPage(1), player);
            case 46 -> render(inventory, session.withView(View.CLAIMS).withPage(1), player);
            case 48 -> render(inventory, session.withPage(Math.max(1, session.page() - 1)), player);
            case 50 -> render(inventory, session.withPage(session.page() + 1), player);
            case 53 -> render(inventory, session.withView(View.MARKET).withPage(1), player);
            default -> {
            }
        }
    }

    private void handleListingClick(net.minestom.server.entity.Player player, Inventory inventory, Session session, AuctionListing listing) {
        if (listing == null) {
            return;
        }
        if (session.view() == View.OWNED) {
            if (!hasInventorySpace(player)) {
                player.sendMessage(CommandMessages.error("You need a free slot to reclaim items."));
                return;
            }
            auctionService.cancelListing(player.getUsername(), listing.getId()).ifPresent(cancelled -> {
                ItemStack item = cancelled.getItem().toItemStack();
                if (!player.getInventory().addItemStack(item)) {
                    auctionService.restoreListing(cancelled);
                    player.sendMessage(CommandMessages.error("Could not return that item to your inventory."));
                    return;
                }
                player.sendMessage(CommandMessages.success("You removed your §b" + describeItem(item) + "§r§7 from the auction."));
                player.playSound(CLICK_SOUND);
                render(inventory, session, player);
            });
            return;
        }
        if (listing.getSeller().equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(CommandMessages.error("You can't buy your own items."));
            return;
        }
        if (!canAfford(player, listing)) {
            player.sendMessage(CommandMessages.error("You don't have enough money to buy that item!"));
            return;
        }
        if (!hasInventorySpace(player)) {
            player.sendMessage(CommandMessages.error("You need a free slot to buy items."));
            return;
        }
        openConfirm(player, listing.getId());
    }

    private void handleClaimClick(net.minestom.server.entity.Player player, Inventory inventory, Session session, AuctionClaim claim) {
        if (claim == null) {
            return;
        }
        if (!hasInventorySpace(player)) {
            player.sendMessage(CommandMessages.error("You need a free slot to reclaim items."));
            return;
        }
        auctionService.reclaimClaim(player.getUsername(), claim.getListingId()).ifPresent(reclaimed -> {
            ItemStack item = reclaimed.getItem().toItemStack();
            if (!player.getInventory().addItemStack(item)) {
                auctionService.restoreClaim(player.getUsername(), reclaimed);
                player.sendMessage(CommandMessages.error("Could not return that item to your inventory."));
                return;
            }
            player.sendMessage(CommandMessages.success("You reclaimed your §b" + describeItem(item) + "§r§7."));
            player.playSound(CLICK_SOUND);
            render(inventory, session, player);
        });
    }

    private void openConfirm(net.minestom.server.entity.Player player, String listingId) {
        AuctionListing listing = auctionService.findById(listingId).orElse(null);
        if (listing == null) {
            player.sendMessage(CommandMessages.error("That item is expired or has already been sold!"));
            return;
        }
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bAuction confirm"));
        inventory.setItemStack(11, ItemStack.of(Material.GREEN_STAINED_GLASS_PANE).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§8Confirm purchase"))));
        inventory.setItemStack(15, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§8Cancel"))));
        inventory.setItemStack(13, decorateConfirm(listing));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 11) {
                if (!canAfford(player, listing)) {
                    player.sendMessage(CommandMessages.error("You don't have enough money to buy that item!"));
                    player.closeInventory();
                    return;
                }
                if (!hasInventorySpace(player)) {
                    player.sendMessage(CommandMessages.error("You need a free slot to buy items."));
                    player.closeInventory();
                    return;
                }
                var result = auctionService.buyListing(listingId, player.getUsername());
                if (result.status() == AuctionService.PurchaseStatus.SUCCESS && result.listing() != null) {
                    ItemStack item = result.listing().getItem().toItemStack();
                    if (!player.getInventory().addItemStack(item)) {
                        auctionService.rollbackPurchase(result.listing(), player.getUsername());
                        player.sendMessage(CommandMessages.error("Could not deliver that item, so the purchase was cancelled."));
                        player.closeInventory();
                        return;
                    }
                    player.sendMessage(CommandMessages.success("You bought §b" + describeItem(item) + " §r§7for §b$" + legacyMoney(result.listing().getPrice()) + "§r§7."));
                    var seller = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(result.listing().getSeller());
                    if (seller != null) {
                        seller.sendMessage(CommandMessages.success("§b" + result.listing().getItem().toItemStack().amount() + "x §r§7of your §b" + stripAmount(result.listing().getItem().toItemStack()) + "§r§7 have been sold for §b$" + legacyMoney(result.listing().getPrice()) + "§r§7."));
                    }
                } else if (result.status() == AuctionService.PurchaseStatus.INSUFFICIENT_BALANCE) {
                    player.sendMessage(CommandMessages.error("You don't have enough money to buy that item!"));
                } else if (result.status() == AuctionService.PurchaseStatus.CANNOT_BUY_OWN) {
                    player.sendMessage(CommandMessages.error("You can't buy your own items."));
                } else {
                    player.sendMessage(CommandMessages.error("That item is expired or has already been sold!"));
                }
                player.closeInventory();
            }
            if (event.getSlot() == 15) {
                player.closeInventory();
            }
        });
        InventoryOpeners.replace(player, inventory);
    }

    private void render(Inventory inventory, Session session, net.minestom.server.entity.Player player) {
        sessions.put(inventory, session);
        inventory.clear();
        List<Entry> entries = entries(session.view(), player.getUsername());
        boolean hasOwned = !auctionService.listOwned(player.getUsername()).isEmpty();
        boolean hasClaims = !auctionService.listClaims(player.getUsername()).isEmpty();
        int start = (session.page() - 1) * 45;
        int end = Math.min(entries.size(), start + 45);
        for (int slot = 0; slot + start < end; slot++) {
            Entry entry = entries.get(start + slot);
            inventory.setItemStack(slot, entry.kind() == EntryKind.CLAIM
                ? decorateClaim(entry.claim())
                : session.view() == View.OWNED ? decorateOwned(entry.listing()) : decorateListing(entry.listing()));
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItemStack(slot, button(Material.RED_STAINED_GLASS_PANE, "§r§c/"));
        }
        if (session.view() != View.OWNED && hasOwned) {
            inventory.setItemStack(45, button(Material.CHEST, "§r§bView owned items"));
        }
        if (session.view() != View.CLAIMS && hasClaims) {
            inventory.setItemStack(46, button(Material.CHEST, "§r§bView expired items"));
        }
        inventory.setItemStack(48, session.page() > 1 ? button(Material.PAPER, "§r§bPrevious page") : button(Material.BARRIER, "§r§c/"));
        inventory.setItemStack(49, button(Material.PAPER, pageCounter(session, entries.size())));
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / 45d));
        inventory.setItemStack(50, session.page() < totalPages ? button(Material.PAPER, "§r§bNext page") : button(Material.BARRIER, "§r§c/"));
        inventory.setItemStack(53, session.view() == View.MARKET
            ? ItemStack.of(Material.PAPER)
                .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§7You can sell items")))
                .with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.<Component>of(
                    LEGACY.deserialize("§r§7here with:"),
                    LEGACY.deserialize("§r§b/ah sell")
                )))
            : button(Material.BARRIER, "§r§bGo back"));
    }

    private List<Entry> entries(View view, String username) {
        return switch (view) {
            case MARKET -> auctionService.listActive().stream().map(Entry::listing).toList();
            case OWNED -> auctionService.listOwned(username).stream().map(Entry::listing).toList();
            case CLAIMS -> auctionService.listClaims(username).stream().map(Entry::claim).toList();
        };
    }

    private static ItemStack decorateListing(AuctionListing listing) {
        ItemStack item = listing.getItem().toItemStack();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§8[§7Seller: §b" + listing.getSeller() + "§r§8]"));
        lore.add(LEGACY.deserialize("§r§8[§7Price: §b$" + legacyMoney(listing.getPrice()) + "§r§8]"));
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore));
    }

    private static ItemStack decorateConfirm(AuctionListing listing) {
        ItemStack item = listing.getItem().toItemStack();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§7Are you sure you want to buy §b" + describeItem(item) + " §r§7for §b$" + legacyMoney(listing.getPrice()) + "§r§7?"));
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore));
    }

    private static ItemStack decorateOwned(AuctionListing listing) {
        ItemStack item = listing.getItem().toItemStack();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§8[§7Seller: §bYou§r§8]"));
        lore.add(LEGACY.deserialize("§r§8[§bClick to remove§r§8]"));
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore));
    }

    private static ItemStack decorateClaim(AuctionClaim claim) {
        ItemStack item = claim.getItem().toItemStack();
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§r§8[§7Seller: §bYou§r§8]"));
        lore.add(LEGACY.deserialize("§r§8[§bClick to remove§8]"));
        return item.with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore));
    }

    private static ItemStack button(Material material, String title) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(title)));
    }

    private static boolean hasInventorySpace(net.minestom.server.entity.Player player) {
        for (int slot = 0; slot < player.getInventory().getInnerSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (stack == null || stack.isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean canAfford(net.minestom.server.entity.Player player, AuctionListing listing) {
        return listing != null && profileService.getOrCreate(player).getBalance() >= listing.getPrice();
    }

    private static String describeItem(ItemStack item) {
        Component customName = item.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return item.amount() + "x " + LEGACY.serialize(customName).replace("§r", "");
        }
        return item.amount() + "x " + titleCase(item.material().name());
    }

    private static String stripAmount(ItemStack item) {
        Component customName = item.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return LEGACY.serialize(customName).replace("§r", "");
        }
        return titleCase(item.material().name());
    }

    private static String pageCounter(Session session, int totalEntries) {
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / 45d));
        return "§r§bpage " + session.page() + "§8/§b" + totalPages;
    }

    private static String legacyMoney(double amount) {
        return Math.rint(amount) == amount ? Long.toString((long) amount) : MONEY.format(amount);
    }

    private static String titleCase(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private enum View { MARKET, OWNED, CLAIMS }

    private record Session(String username, View view, int page) {
        Session withView(View next) { return new Session(username, next, page); }
        Session withPage(int nextPage) { return new Session(username, view, Math.max(1, nextPage)); }
    }

    private enum EntryKind { LISTING, CLAIM }

    private record Entry(EntryKind kind, AuctionListing listing, AuctionClaim claim) {
        static Entry listing(AuctionListing listing) { return new Entry(EntryKind.LISTING, listing, null); }
        static Entry claim(AuctionClaim claim) { return new Entry(EntryKind.CLAIM, null, claim); }
    }
}
