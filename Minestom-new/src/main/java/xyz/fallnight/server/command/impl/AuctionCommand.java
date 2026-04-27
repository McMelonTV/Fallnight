package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.auction.AuctionClaim;
import xyz.fallnight.server.domain.auction.AuctionListing;
import xyz.fallnight.server.service.AnvilInputService;
import xyz.fallnight.server.service.AuctionMenuService;
import xyz.fallnight.server.service.AuctionSellMenuService;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AuctionCommand extends FallnightCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final AuctionService auctionService;
    private final AuctionMenuService auctionMenuService;
    private final AuctionSellMenuService auctionSellMenuService;
    private final ItemDeliveryService itemDeliveryService;
    private final PlayerProfileService profileService;
    private final PermissionService permissionService;

    public AuctionCommand(PermissionService permissionService, AuctionService auctionService, AuctionMenuService auctionMenuService, ItemDeliveryService itemDeliveryService, PlayerProfileService profileService) {
        super("auction", permissionService, "auc", "ah");
        this.permissionService = permissionService;
        this.auctionService = auctionService;
        this.auctionMenuService = auctionMenuService;
        this.auctionSellMenuService = new AuctionSellMenuService(auctionService, profileService, new AnvilInputService());
        this.itemDeliveryService = itemDeliveryService;
        this.profileService = profileService;

        var listLiteral = ArgumentType.Literal("list");
        var sellLiteral = ArgumentType.Literal("sell");
        var addLiteral = ArgumentType.Literal("add");
        var buyLiteral = ArgumentType.Literal("buy");
        var mineLiteral = ArgumentType.Literal("mine");
        var reclaimLiteral = ArgumentType.Literal("reclaim");

        var priceArgument = ArgumentType.Word("price");
        var countArgument = ArgumentType.Integer("count").min(1).max(64);
        var idArgument = ArgumentType.Word("id");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(CommandMessages.error("Please execute this command ingame."));
                return;
            }
            auctionMenuService.openMarket((Player) sender);
        });

        addSyntax((sender, context) -> {
            if (!ensureSellPermission(sender)) {
                return;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(CommandMessages.error("Please execute this command ingame."));
                return;
            }
            auctionSellMenuService.open((Player) sender);
        }, sellLiteral);
        addSyntax((sender, context) -> {
            if (!ensureSellPermission(sender)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Please execute this command ingame."));
                return;
            }
            Integer price = parseDirectPrice(sender, context.get(priceArgument));
            if (price == null) {
                return;
            }
            auctionSellMenuService.openWithPrice(player, price);
        }, sellLiteral, priceArgument);
        addSyntax((sender, context) -> {
            if (!ensureSellPermission(sender)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Please execute this command ingame."));
                return;
            }
            Integer price = parseDirectPrice(sender, context.get(priceArgument));
            if (price == null) {
                return;
            }
            handleSell(player, sender, price, context.get(countArgument));
        }, sellLiteral, priceArgument, countArgument);
    }

    private static String describeItem(AuctionListing listing) {
        return describeItem(listing.getItem().toItemStack());
    }

    private static String describeItem(ItemStack item) {
        net.kyori.adventure.text.Component customName = item.get(net.minestom.server.component.DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return item.amount() + "x " + LEGACY.serialize(customName).replace("§r", "");
        }
        return item.amount() + "x " + item.material().name().toLowerCase().replace('_', ' ');
    }

    private static String remainingText(AuctionListing listing, Instant now) {
        if (listing.getExpireAt() == null) {
            return "expires unknown";
        }
        if (!listing.getExpireAt().isAfter(now)) {
            return "expired";
        }
        Duration remaining = Duration.between(now, listing.getExpireAt());
        long totalMinutes = Math.max(0L, remaining.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return "expires in " + hours + "h " + minutes + "m";
    }

    private static String nullSafe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }

    private static boolean hasInventorySpace(Player player) {
        for (int slot = 0; slot < player.getInventory().getInnerSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (stack == null || stack.isAir()) {
                return true;
            }
        }
        return false;
    }

    private static String stripAmount(ItemStack item) {
        net.kyori.adventure.text.Component customName = item.get(net.minestom.server.component.DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return LEGACY.serialize(customName).replace("§r", "");
        }
        return item.material().name().toLowerCase().replace('_', ' ');
    }

    private static String legacyMoney(double amount) {
        return Math.rint(amount) == amount ? Long.toString((long) amount) : MONEY_FORMAT.format(amount);
    }

    private void notifySeller(AuctionListing listing) {
        var seller = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(listing.getSeller());
        if (seller != null) {
            seller.sendMessage(CommandMessages.success(
                    "§b" + listing.getItem().toItemStack().amount() + "x §r§7of your §b"
                            + stripAmount(listing.getItem().toItemStack())
                            + "§r§7 have been sold for §b$" + legacyMoney(listing.getPrice()) + "§r§7."
            ));
        }
    }

    @Override
    public String permission() {
        return "fallnight.command.auction";
    }

    @Override
    public String summary() {
        return "open the auction";
    }

    @Override
    public String usage() {
        return "/auction [sell] [price] [count]";
    }

    private boolean ensureSellPermission(net.minestom.server.command.CommandSender sender) {
        if (permissionService.hasPermission(sender, "fallnight.command.auction.sell")) {
            return true;
        }
        sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You don't have permission to execute that subcommand!"));
        return false;
    }

    private void handleSell(Player player, net.minestom.server.command.CommandSender sender, int price, Integer countArgument) {
        int maxListings = maxListingsFor(player.getUsername());
        if (auctionService.activeListingCount(player.getUsername()) >= maxListings) {
            sender.sendMessage(CommandMessages.error("You have already reached your maximum auction items of " + maxListings + "."));
            return;
        }

        ItemStack held = player.getItemInMainHand();
        if (held == null || held.material() == Material.AIR || held.amount() <= 0) {
            sender.sendMessage(CommandMessages.error("You can't sell air."));
            return;
        }

        int count = countArgument == null ? held.amount() : countArgument;
        if (count <= 0) {
            sender.sendMessage(CommandMessages.error("Please enter a valid numeric item count."));
            return;
        }
        if (count > held.maxStackSize()) {
            sender.sendMessage(CommandMessages.error("You can't sell more than " + held.maxStackSize() + " items for this item type."));
            return;
        }
        if (count > held.amount()) {
            sender.sendMessage(CommandMessages.error("You can't sell more items than you are holding!"));
            return;
        }

        ItemStack sold = held.withAmount(count);
        if (count == held.amount()) {
            player.setItemInMainHand(ItemStack.AIR);
        } else {
            player.setItemInMainHand(held.withAmount(held.amount() - count));
        }
        auctionService.createListing(player.getUsername(), sold, price);
        sender.sendMessage(CommandMessages.success(
                "You are now auctioning off §b" + describeItem(sold) + " §r§7for §b$" + price + "§r§7."
        ));
    }

    private Integer parseDirectPrice(net.minestom.server.command.CommandSender sender, String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            sender.sendMessage(CommandMessages.error("Please enter a valid price for your item."));
            return null;
        }
        try {
            double parsed = Double.parseDouble(rawPrice.trim());
            if (parsed < 0D) {
                sender.sendMessage(CommandMessages.error("Please enter a valid price for your item."));
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException exception) {
            sender.sendMessage(CommandMessages.error("Please enter a valid price for your item."));
            return null;
        }
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

    private void sendMarket(net.minestom.server.command.CommandSender sender) {
        auctionService.expireListings();
        List<AuctionListing> listings = auctionService.listActive();
        if (listings.isEmpty()) {
            sender.sendMessage(CommandMessages.info("Auction house is empty."));
            return;
        }

        sender.sendMessage(CommandMessages.info("Auction listings (" + listings.size() + ")"));
        int shown = 0;
        Instant now = Instant.now();
        for (AuctionListing listing : listings) {
            sender.sendMessage(CommandMessages.info(
                    "#" + listing.getId()
                            + " | " + describeItem(listing)
                            + " | $" + MONEY_FORMAT.format(listing.getPrice())
                            + " | seller " + listing.getSeller()
                            + " | " + remainingText(listing, now)
            ));
            shown++;
            if (shown >= 20) {
                break;
            }
        }

        if (listings.size() > shown) {
            sender.sendMessage(CommandMessages.info("Showing " + shown + " of " + listings.size() + " listings."));
        }
    }

    private void sendMine(Player player) {
        auctionService.expireListings();
        List<AuctionListing> owned = auctionService.listOwned(player.getUsername());
        List<AuctionClaim> claims = auctionService.listClaims(player.getUsername());

        if (owned.isEmpty()) {
            player.sendMessage(CommandMessages.info("You have no active listings."));
        } else {
            player.sendMessage(CommandMessages.info("Your listings (" + owned.size() + ")"));
            int shown = 0;
            Instant now = Instant.now();
            for (AuctionListing listing : owned) {
                player.sendMessage(CommandMessages.info(
                        "#" + listing.getId() + " | " + describeItem(listing) + " | $" + MONEY_FORMAT.format(listing.getPrice()) + " | " + remainingText(listing, now)
                ));
                shown++;
                if (shown >= 10) {
                    break;
                }
            }
        }

        if (claims.isEmpty()) {
            player.sendMessage(CommandMessages.info("No pending auction claims."));
            return;
        }

        player.sendMessage(CommandMessages.info("Pending claims (" + claims.size() + ")"));
        int shown = 0;
        for (AuctionClaim claim : claims) {
            ItemStack item = claim.getItem().toItemStack();
            player.sendMessage(CommandMessages.info(
                    "From listing " + nullSafe(claim.getListingId())
                            + " | " + describeItem(item)
                            + " | " + claim.getReason()
                            + " | " + TIME_FORMAT.format(claim.getCreatedAt())
            ));
            shown++;
            if (shown >= 10) {
                break;
            }
        }
    }
}
