package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.shop.ShopCategoryDefinition;
import xyz.fallnight.server.domain.shop.ShopOffer;
import xyz.fallnight.server.domain.shop.ShopPrice;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.ShopMenuService;
import xyz.fallnight.server.service.ShopService;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;

public final class ShopCommand extends FallnightCommand {
    private static final int LIST_LIMIT = 15;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private final PlayerProfileService profileService;
    private final ShopService shopService;
    private final ShopMenuService shopMenuService;

    public ShopCommand(PermissionService permissionService, PlayerProfileService profileService, ShopService shopService, ShopMenuService shopMenuService) {
        super("shop", permissionService);
        this.profileService = profileService;
        this.shopService = shopService;
        this.shopMenuService = shopMenuService;

        var listLiteral = ArgumentType.Literal("list");
        var priceLiteral = ArgumentType.Literal("price");
        var categoriesLiteral = ArgumentType.Literal("categories");
        var categoryLiteral = ArgumentType.Literal("category");
        var buyLiteral = ArgumentType.Literal("buy");
        var sellHandLiteral = ArgumentType.Literal("sellhand");
        var sellAllLiteral = ArgumentType.Literal("sellall");
        var materialArgument = ArgumentType.Word("material");
        var categoryArgument = ArgumentType.Word("category");
        var itemArgument = ArgumentType.Word("item");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            shopMenuService.open((Player) sender);
        });

    }

    @Override
    public String permission() {
        return "fallnight.command.shop";
    }

    @Override
    public String summary() {
        return "buy items";
    }

    @Override
    public String usage() {
        return "/shop";
    }

    private void sendCategories(net.minestom.server.command.CommandSender sender) {
        sender.sendMessage(CommandMessages.info("Shop categories:"));
        for (ShopCategoryDefinition category : shopService.categories()) {
            sender.sendMessage(CommandMessages.info("- " + category.id() + " (" + category.displayName() + ")"));
        }
    }

    private void sendCategory(net.minestom.server.command.CommandSender sender, String categoryId) {
        ShopCategoryDefinition category = shopService.category(categoryId).orElse(null);
        if (category == null) {
            sender.sendMessage(CommandMessages.error("Unknown shop category '" + categoryId + "'."));
            return;
        }
        sender.sendMessage(CommandMessages.info(category.displayName() + " offers:"));
        for (ShopOffer offer : category.offers()) {
            sender.sendMessage(CommandMessages.info("- " + offer.id() + " : " + offer.displayName() + " ($" + MONEY_FORMAT.format(offer.dollarPrice()) + (offer.prestigePrice() > 0 ? ", " + offer.prestigePrice() + "pp" : "") + ")"));
        }
    }

    private void buy(net.minestom.server.command.CommandSender sender, String categoryId, String offerId) {
        if (!ensurePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        UserProfile profile = profileService.getOrCreate(player);
        ShopService.PurchaseResult result = shopService.buy(player, profile, categoryId, offerId);
        if (!result.found()) {
            sender.sendMessage(CommandMessages.error("That shop item was not found."));
            return;
        }
        if (result.alreadyOwned()) {
            sender.sendMessage(CommandMessages.error("You have already purchased this."));
            return;
        }
        if (result.inventoryFull()) {
            sender.sendMessage(CommandMessages.error("You don't have enough space in your inventory to buy this item."));
            return;
        }
        if (result.deliveryFailed()) {
            sender.sendMessage(CommandMessages.error("That shop item is currently unavailable."));
            return;
        }
        if (!result.affordable()) {
            if (profile.getBalance() < result.offer().dollarPrice()) {
                sender.sendMessage(CommandMessages.error("You don't have enough money to buy this item."));
            } else if (profile.getPrestigePoints() < result.offer().prestigePrice()) {
                sender.sendMessage(CommandMessages.error("You don't have enough prestige points to buy this item."));
            }
            return;
        }
        profileService.save(profile);
        sender.sendMessage(CommandMessages.success(
            "You have bought " + shopName(result.offer()) + "§r§7 for " + priceName(result.offer()) + "§r§7."
        ));
    }

    private void sendPriceList(net.minestom.server.command.CommandSender sender) {
        List<ShopPrice> prices = shopService.listPrices();
        if (prices.isEmpty()) {
            sender.sendMessage(CommandMessages.error("No shop prices are configured."));
            return;
        }

        sender.sendMessage(CommandMessages.info("Shop prices (showing up to " + LIST_LIMIT + " of " + prices.size() + ")"));
        int shown = 0;
        for (ShopPrice price : prices) {
            sender.sendMessage(CommandMessages.info(
                renderMaterial(price.material()) + " -> $" + MONEY_FORMAT.format(price.price())
            ));
            shown++;
            if (shown >= LIST_LIMIT) {
                break;
            }
        }
    }

    private static Material resolveMaterial(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Material.fromKey(normalized);
    }

    private static String renderMaterial(Material material) {
        String key = material.key().asString();
        if (key.startsWith("minecraft:")) {
            return key.substring("minecraft:".length());
        }
        return key;
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
        String dollars = offer.dollarPrice() > 0d ? "§r§b$" + xyz.fallnight.server.util.NumberFormatter.shortNumberRounded(offer.dollarPrice()) : "";
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
}
