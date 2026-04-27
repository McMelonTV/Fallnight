package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.shop.ShopCategoryDefinition;
import xyz.fallnight.server.domain.shop.ShopOffer;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.util.NumberFormatter;
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
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class ShopMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final ShopService shopService;
    private final PlayerProfileService profileService;
    private final Map<Inventory, Session> sessions = new ConcurrentHashMap<>();

    public ShopMenuService(ShopService shopService, PlayerProfileService profileService) {
        this.shopService = shopService;
        this.profileService = profileService;
    }

    public void open(Player player) {
        open(player, View.CATEGORIES, null, 1);
    }

    private void open(Player player, View view, String categoryId, int page) {
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize("§8Shop"));
        Session session = new Session(view, categoryId, Math.max(1, page));
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
        Player player = event.getPlayer();
        int slot = event.getSlot();
        if (slot >= 45) {
            handleControl(player, inventory, session, slot);
            return;
        }
        if (session.view() == View.CATEGORIES) {
            List<ShopCategoryDefinition> categories = shopService.categories();
            int index = (session.page() - 1) * 45 + slot;
            if (index < 0 || index >= categories.size()) {
                return;
            }
            render(inventory, session.withCategory(categories.get(index).id()).withView(View.OFFERS).withPage(1), player);
            player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
            return;
        }
        ShopCategoryDefinition category = shopService.category(session.categoryId()).orElse(null);
        if (category == null) {
            return;
        }
        int index = (session.page() - 1) * 45 + slot;
        if (index < 0 || index >= category.offers().size()) {
            return;
        }
        ShopOffer offer = category.offers().get(index);
        UserProfile profile = profileService.getOrCreate(player);
        var result = shopService.buy(player, profile, category.id(), offer.id());
        if (!result.success()) {
            if (result.alreadyOwned()) {
                player.sendMessage(CommandMessages.error("You have already purchased this."));
            } else if (result.inventoryFull()) {
                player.sendMessage(CommandMessages.error("You don't have enough space in your inventory to buy this item."));
            } else if (result.deliveryFailed()) {
                player.sendMessage(CommandMessages.error("That shop item is currently unavailable."));
            } else if (!result.affordable()) {
                if (profile.getBalance() < offer.dollarPrice()) {
                    player.sendMessage(CommandMessages.error("You don't have enough money to buy this item."));
                } else if (profile.getPrestigePoints() < offer.prestigePrice()) {
                    player.sendMessage(CommandMessages.error("You don't have enough prestige points to buy this item."));
                }
            }
            return;
        }
        profileService.save(profile);
        player.sendMessage(CommandMessages.success("You have bought " + shopName(offer) + "§r§7 for " + priceName(offer) + "§r§7."));
        player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
        render(inventory, session, player);
    }

    private void handleControl(Player player, Inventory inventory, Session session, int slot) {
        switch (slot) {
            case 48 -> {
                render(inventory, session.withPage(Math.max(1, session.page() - 1)), player);
                player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
            }
            case 49 -> {
                if (session.view() == View.OFFERS) {
                    render(inventory, session.withView(View.CATEGORIES).withCategory(null).withPage(1), player);
                    player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
                }
            }
            case 50 -> {
                render(inventory, session.withPage(session.page() + 1), player);
                player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
            }
            case 53 -> {
                player.closeInventory();
                player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f));
            }
            default -> {
            }
        }
    }

    private void render(Inventory inventory, Session session, Player player) {
        sessions.put(inventory, session);
        inventory.clear();
        if (session.view() == View.CATEGORIES) {
            renderCategories(inventory, session);
        } else {
            renderOffers(inventory, session, player);
        }
        inventory.setItemStack(48, button(Material.PAPER, "§bPrevious page"));
        if (session.view() == View.OFFERS) {
            inventory.setItemStack(49, button(Material.BARRIER, "§cReturn"));
        }
        inventory.setItemStack(50, button(Material.PAPER, "§bNext page"));
        inventory.setItemStack(53, button(Material.BARRIER, "§cExit shop"));
    }

    private void renderCategories(Inventory inventory, Session session) {
        List<ShopCategoryDefinition> categories = shopService.categories();
        int start = (session.page() - 1) * 45;
        int end = Math.min(categories.size(), start + 45);
        for (int slot = 0; slot + start < end; slot++) {
            ShopCategoryDefinition category = categories.get(start + slot);
            inventory.setItemStack(slot, ItemStack.of(categoryIcon(category.id())).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r" + category.displayName()))));
        }
    }

    private void renderOffers(Inventory inventory, Session session, Player player) {
        ShopCategoryDefinition category = shopService.category(session.categoryId()).orElse(null);
        if (category == null) {
            return;
        }
        UserProfile profile = profileService.getOrCreate(player);
        int start = (session.page() - 1) * 45;
        int end = Math.min(category.offers().size(), start + 45);
        for (int slot = 0; slot + start < end; slot++) {
            ShopOffer offer = category.offers().get(start + slot);
            ItemStack display = shopService.previewItem(offer).orElse(ItemStack.of(Material.PAPER));
            List<Component> lore = new ArrayList<>();
            if (!offer.description().isBlank()) {
                lore.add(LEGACY.deserialize("§r§8§l[§r§7" + offer.description() + "§8§l]§r"));
            }
            if (offer.oneTime() && hasPurchased(profile, category.id(), offer.id())) {
                lore.add(LEGACY.deserialize("§r§8§l[§r§bAlready Purchased§8§l]§r"));
            } else {
                if (offer.dollarPrice() > 0d) {
                    lore.add(LEGACY.deserialize("§r§8§l[§r§7Price: §b" + NumberFormatter.shortNumberRounded(offer.dollarPrice()) + "$§8§l]§r."));
                }
                if (offer.prestigePrice() > 0L) {
                    lore.add(LEGACY.deserialize("§r§8§l[§r§7Price: §b" + offer.prestigePrice() + "PP§8§l]§r"));
                }
            }
            inventory.setItemStack(slot, display.with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r" + offer.displayName()))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(lore)));
        }
    }

    private boolean hasPurchased(UserProfile profile, String categoryId, String offerId) {
        Object raw = profile.getExtraData().get("shopPurchases");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        String key = categoryId.toLowerCase(Locale.ROOT) + "//" + offerId.toLowerCase(Locale.ROOT);
        return list.stream().map(String::valueOf).anyMatch(value -> value.equalsIgnoreCase(key));
    }

    private static ItemStack button(Material material, String title) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(title)));
    }

    private static String shopName(ShopOffer offer) {
        if (offer.customItemId() == null || offer.customItemId().isBlank()) {
            return "§b" + offer.displayName();
        }

        String[] parts = offer.customItemId().split(":");
        if (parts.length >= 4 && "material".equalsIgnoreCase(parts[0])) {
            int amount = 1;
            try {
                amount = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
            return "§b" + amount + "x §r" + offer.displayName() + "§r";
        }
        return offer.displayName();
    }

    private static String priceName(ShopOffer offer) {
        String dollars = offer.dollarPrice() > 0d ? "§r§b$" + NumberFormatter.shortNumberRounded(offer.dollarPrice()) : "";
        String prestige = offer.prestigePrice() > 0L ? "§b" + offer.prestigePrice() + "PP§r" : "";
        if (!dollars.isEmpty() && !prestige.isEmpty()) {
            return dollars + " §r§7and " + prestige;
        }
        if (!dollars.isEmpty()) {
            return dollars;
        }
        if (!prestige.isEmpty()) {
            return prestige;
        }
        return "§7Free";
    }

    private static Material categoryIcon(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "ranks" -> Material.NAME_TAG;
            case "upgrades" -> Material.NETHER_STAR;
            case "pvp" -> Material.GOLDEN_APPLE;
            case "wool" -> Material.WHITE_WOOL;
            case "glass" -> Material.GLASS;
            case "terracotta" -> Material.TERRACOTTA;
            case "concrete" -> Material.WHITE_CONCRETE;
            case "concretepowder" -> Material.WHITE_CONCRETE_POWDER;
            case "stone" -> Material.STONE;
            case "wood" -> Material.OAK_LOG;
            case "nature" -> Material.GRASS_BLOCK;
            case "nether" -> Material.QUARTZ_BLOCK;
            default -> Material.CHEST;
        };
    }

    private enum View { CATEGORIES, OFFERS }

    private record Session(View view, String categoryId, int page) {
        Session withView(View next) { return new Session(next, categoryId, page); }
        Session withCategory(String nextCategory) { return new Session(view, nextCategory, page); }
        Session withPage(int nextPage) { return new Session(view, categoryId, Math.max(1, nextPage)); }
    }
}
