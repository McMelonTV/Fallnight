package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.shop.ShopCategoryDefinition;
import xyz.fallnight.server.domain.shop.ShopOffer;
import xyz.fallnight.server.domain.shop.ShopPrice;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.shop.ShopPriceRepository;
import xyz.fallnight.server.persistence.shop.YamlShopPriceRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class ShopService {
    private static final String PURCHASED_SHOP_ITEMS = "shopPurchases";

    private final ShopPriceRepository repository;
    private final LegacyCustomItemService customItemService;
    private final RankService rankService;
    private volatile Map<Material, Double> prices;
    private final Map<String, ShopCategoryDefinition> categories;

    public static ShopService fromDataRoot(Path dataRoot, RankService rankService) {
        return new ShopService(new YamlShopPriceRepository(dataRoot.resolve("prices.yml")), rankService);
    }

    public ShopService(ShopPriceRepository repository, RankService rankService) {
        this.repository = repository;
        this.rankService = rankService;
        this.customItemService = new LegacyCustomItemService();
        this.prices = Map.of();
        this.categories = new LinkedHashMap<>();
        registerDefaults();
    }

    public void loadPrices() {
        prices = repository.loadPrices();
    }

    public List<ShopPrice> listPrices() {
        List<ShopPrice> listed = new ArrayList<>();
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            listed.add(new ShopPrice(entry.getKey(), entry.getValue()));
        }
        listed.sort(Comparator.comparing(price -> price.material().key().asString()));
        return List.copyOf(listed);
    }

    public OptionalDouble priceFor(Material material) {
        if (material == null) {
            return OptionalDouble.empty();
        }
        Double value = prices.get(material);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public List<ShopCategoryDefinition> categories() {
        return List.copyOf(categories.values());
    }

    public Optional<ShopCategoryDefinition> category(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categories.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<ShopOffer> offer(String category, String offerId) {
        return category(category).flatMap(def -> def.offers().stream().filter(offer -> offer.id().equalsIgnoreCase(offerId)).findFirst());
    }

    public Optional<ItemStack> previewItem(ShopOffer offer) {
        if (offer == null) {
            return Optional.empty();
        }
        if (offer.customItemId() == null || offer.customItemId().isBlank()) {
            return Optional.empty();
        }
        return createShopItem(offer.customItemId());
    }

    public PurchaseResult buy(Player player, UserProfile profile, String categoryId, String offerId) {
        ShopOffer offer = offer(categoryId, offerId).orElse(null);
        if (offer == null) {
            return PurchaseResult.notFound();
        }
        if (offer.oneTime() && hasPurchased(profile, categoryId, offer.id())) {
            return PurchaseResult.alreadyPurchased(offer);
        }
        if (profile.getBalance() < offer.dollarPrice() || profile.getPrestigePoints() < offer.prestigePrice()) {
            return PurchaseResult.cannotAfford(offer);
        }

        ItemStack shopItem = null;
        if (offer.giveItem() && offer.customItemId() != null) {
            shopItem = createShopItem(offer.customItemId()).orElse(null);
            if (shopItem == null) {
                return PurchaseResult.deliveryFailed(offer);
            }
            if (!hasEmptyInventorySlot(player)) {
                return PurchaseResult.inventoryFull(offer);
            }
            if (!player.getInventory().addItemStack(shopItem)) {
                return PurchaseResult.inventoryFull(offer);
            }
        }
        if (!canApplyAction(profile, offer)) {
            return PurchaseResult.deliveryFailed(offer);
        }
        profile.withdraw(offer.dollarPrice());
        profile.setPrestigePoints(profile.getPrestigePoints() - offer.prestigePrice());
        applyAction(profile, offer);
        markPurchased(profile, categoryId, offer.id());
        return PurchaseResult.success(offer);
    }

    public SaleResult sellHandItem(UserProfile profile, Player player) {
        if (profile == null || player == null) {
            return SaleResult.empty();
        }
        ItemStack stack = player.getItemInMainHand();
        if (!isSellable(stack)) {
            return SaleResult.empty();
        }
        double perItem = prices.get(stack.material());
        int amount = stack.amount();
        double earned = perItem * amount;
        player.setItemInMainHand(ItemStack.AIR);
        profile.deposit(earned);
        return new SaleResult(1, amount, earned);
    }

    public SaleResult sellAllPricedInventoryItems(UserProfile profile, Player player) {
        if (profile == null || player == null) {
            return SaleResult.empty();
        }
        var inventory = player.getInventory();
        int stacksSold = 0;
        int itemsSold = 0;
        double earned = 0d;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (!isSellable(stack)) {
                continue;
            }
            double perItem = prices.get(stack.material());
            int amount = stack.amount();
            inventory.setItemStack(slot, ItemStack.AIR);
            stacksSold++;
            itemsSold += amount;
            earned += perItem * amount;
        }
        if (earned > 0d) {
            profile.deposit(earned);
        }
        return new SaleResult(stacksSold, itemsSold, earned);
    }

    private boolean isSellable(ItemStack stack) {
        return stack != null && stack.material() != Material.AIR && stack.amount() > 0 && prices.containsKey(stack.material());
    }

    @SuppressWarnings("unchecked")
    private boolean hasPurchased(UserProfile profile, String category, String offer) {
        Object raw = profile.getExtraData().get(PURCHASED_SHOP_ITEMS);
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        String key = category.toLowerCase(Locale.ROOT) + "//" + offer.toLowerCase(Locale.ROOT);
        return list.stream().map(String::valueOf).anyMatch(value -> value.equalsIgnoreCase(key));
    }

    @SuppressWarnings("unchecked")
    private void markPurchased(UserProfile profile, String category, String offer) {
        Object raw = profile.getExtraData().get(PURCHASED_SHOP_ITEMS);
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> existing) {
            existing.forEach(item -> list.add(String.valueOf(item)));
        }
        String key = category.toLowerCase(Locale.ROOT) + "//" + offer.toLowerCase(Locale.ROOT);
        if (!list.contains(key)) {
            list.add(key);
        }
        profile.getExtraData().put(PURCHASED_SHOP_ITEMS, list);
    }

    private boolean canApplyAction(UserProfile profile, ShopOffer offer) {
        if (offer.actionKey() == null || offer.actionValue() == null) {
            return true;
        }
        if ("rank".equals(offer.actionKey())) {
            return rankService.findById(offer.actionValue()).isPresent();
        }
        return true;
    }

    private void applyAction(UserProfile profile, ShopOffer offer) {
        if (offer.actionKey() == null || offer.actionValue() == null) {
            return;
        }
        switch (offer.actionKey()) {
            case "rank" -> rankService.assignRank(profile, offer.actionValue(), -1L, false);
            case "vault" -> profile.getExtraData().put("maxVaults", readInt(profile.getExtraData().get("maxVaults"), 1) + Integer.parseInt(offer.actionValue()));
            case "plot" -> profile.getExtraData().put("maxPlots", readInt(profile.getExtraData().get("maxPlots"), 1) + Integer.parseInt(offer.actionValue()));
            case "auction" -> profile.getExtraData().put("maxAuctionListings", readInt(profile.getExtraData().get("maxAuctionListings"), 1) + Integer.parseInt(offer.actionValue()));
        }
    }

    private int readInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void registerDefaults() {
        categories.put("ranks", new ShopCategoryDefinition("ranks", "Ranks", List.of(
            offer("mercenary", "Mercenary rank", 0, 100_000, true, "rank", "mercenary"),
            offer("warrior", "Warrior rank", 0, 200_000, true, "rank", "warrior"),
            offer("knight", "Knight rank", 0, 300_000, true, "rank", "knight"),
            offer("lord", "Lord rank", 0, 400_000, true, "rank", "lord")
        )));
        categories.put("upgrades", new ShopCategoryDefinition("upgrades", "Upgrades", List.of(
            upgrade("vault0", "Extra vault 1", 7500, 0, "vault", "1"),
            upgrade("vault1", "Extra vault 2", 75000, 0, "vault", "1"),
            upgrade("vault2", "Extra vault 3", 15000, 500, "vault", "1"),
            upgrade("vault3", "Extra vault 4", 15000, 2500, "vault", "1"),
            upgrade("plot0", "Extra plot 1", 7500, 0, "plot", "1"),
            upgrade("plot1", "Extra plot 2", 75000, 0, "plot", "1"),
            upgrade("plot2", "Extra plot 3", 15000, 500, "plot", "1"),
            upgrade("plot3", "Extra plot 4", 15000, 2500, "plot", "1"),
            upgrade("auction0", "Extra auction slot 1", 7500, 0, "auction", "1"),
            upgrade("auction1", "Extra auction slot 2", 75000, 0, "auction", "1"),
            upgrade("auction2", "Extra auction slot 3", 15000, 500, "auction", "1"),
            upgrade("auction3", "Extra auction slot 4", 15000, 2500, "auction", "1")
        )));
        categories.put("pvp", new ShopCategoryDefinition("pvp", "PvP Items", List.of(
            new ShopOffer("10", "Golden Apple", "", "material:minecraft:golden_apple:1", 1500, 0, true, false, null, null),
            new ShopOffer("20", "8 Golden Apples", "", "material:minecraft:golden_apple:8", 12000, 0, true, false, null, null),
            new ShopOffer("30", "32 Golden Apples", "", "material:minecraft:golden_apple:32", 48000, 0, true, false, null, null)
        )));
        registerSimpleColorCategory("wool", "Wool", List.of(
            "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool",
            "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool", "red_wool", "black_wool"
        ), 50, 1600, 4);
        registerSimpleColorCategory("glass", "Glass", List.of(
            "white_stained_glass", "orange_stained_glass", "magenta_stained_glass", "light_blue_stained_glass", "yellow_stained_glass", "lime_stained_glass", "pink_stained_glass", "gray_stained_glass",
            "light_gray_stained_glass", "cyan_stained_glass", "purple_stained_glass", "blue_stained_glass", "brown_stained_glass", "green_stained_glass", "red_stained_glass", "black_stained_glass", "glass"
        ), 50, 1600, 4);
        registerSimpleColorCategory("terracotta", "Terracotta", List.of(
            "white_terracotta", "orange_terracotta", "magenta_terracotta", "light_blue_terracotta", "yellow_terracotta", "lime_terracotta", "pink_terracotta", "gray_terracotta",
            "light_gray_terracotta", "cyan_terracotta", "purple_terracotta", "blue_terracotta", "brown_terracotta", "green_terracotta", "red_terracotta", "black_terracotta"
        ), 50, 1600, 4);
        registerSimpleColorCategory("concrete", "Concrete", List.of(
            "white_concrete", "orange_concrete", "magenta_concrete", "light_blue_concrete", "yellow_concrete", "lime_concrete", "pink_concrete", "gray_concrete",
            "light_gray_concrete", "cyan_concrete", "purple_concrete", "blue_concrete", "brown_concrete", "green_concrete", "red_concrete", "black_concrete"
        ), 50, 1600, 4);
        registerSimpleColorCategory("concretepowder", "Concrete Powder", List.of(
            "white_concrete_powder", "orange_concrete_powder", "magenta_concrete_powder", "light_blue_concrete_powder", "yellow_concrete_powder", "lime_concrete_powder", "pink_concrete_powder", "gray_concrete_powder",
            "light_gray_concrete_powder", "cyan_concrete_powder", "purple_concrete_powder", "blue_concrete_powder", "brown_concrete_powder", "green_concrete_powder", "red_concrete_powder", "black_concrete_powder"
        ), 50, 1600, 4);
        registerFixedCategory("stone", "Stone Blocks", combine(
            pair("0", "stone", 20, 640), pair("2", "stone_stairs", 20, 640), pair("4", "stone_slab", 20, 640), pair("6", "cobblestone", 20, 640), pair("8", "cobblestone_stairs", 20, 640),
            pair("10", "cobblestone_slab", 20, 640), pair("12", "stone_bricks", 40, 1280), pair("14", "mossy_stone_bricks", 40, 1280), pair("16", "cracked_stone_bricks", 40, 1280), pair("18", "chiseled_stone_bricks", 40, 1280),
            pair("20", "stone_brick_stairs", 40, 1280), pair("22", "mossy_stone_brick_stairs", 40, 1280), pair("24", "smooth_stone_slab", 40, 1280)
        ));
        registerFixedCategory("wood", "Wood", combine(
            pair("0", "oak_log", 80, 2580), pair("2", "spruce_log", 80, 2580), pair("4", "birch_log", 80, 2580), pair("6", "jungle_log", 80, 2580), pair("8", "acacia_log", 80, 2580), pair("10", "dark_oak_log", 80, 2580),
            pair("12", "stripped_oak_log", 80, 2580), pair("14", "stripped_spruce_log", 80, 2580), pair("16", "stripped_birch_log", 80, 2580), pair("18", "stripped_jungle_log", 80, 2580), pair("20", "stripped_acacia_log", 80, 2580), pair("22", "stripped_dark_oak_log", 80, 2580)
        ));
        registerFixedCategory("nature", "Nature", combine(
            pair("0", "dirt", 2, 64), pair("2", "grass_block", 2, 64), pair("4", "oak_leaves", 4, 128), pair("6", "spruce_leaves", 4, 128), pair("8", "birch_leaves", 4, 128), pair("10", "jungle_leaves", 4, 128), pair("12", "acacia_leaves", 4, 128), pair("14", "dark_oak_leaves", 4, 128)
        ));
        registerFixedCategory("nether", "Nether", combine(
            pair("0", "quartz_block", 50, 1600), pair("2", "quartz_stairs", 50, 1600), pair("4", "smooth_quartz_stairs", 50, 1600), pair("6", "quartz_slab", 50, 1600), pair("8", "smooth_quartz_slab", 50, 1600),
            pair("10", "nether_quartz_ore", 50, 1600), pair("12", "nether_bricks", 50, 1600), pair("14", "nether_brick_fence", 50, 1600), pair("16", "netherrack", 50, 1600)
        ));
    }

    private void registerSimpleColorCategory(String id, String display, List<String> materials, double singlePrice, double stackPrice, int startId) {
        List<ShopOffer> offers = new ArrayList<>();
        int index = startId;
        for (String material : materials) {
            offers.add(new ShopOffer(Integer.toString(index++), title(material), "", "material:minecraft:" + material + ":1", singlePrice, 0, true, false, null, null));
            offers.add(new ShopOffer(Integer.toString(index++), "32 " + title(material), "", "material:minecraft:" + material + ":32", stackPrice, 0, true, false, null, null));
        }
        categories.put(id, new ShopCategoryDefinition(id, display, offers));
    }

    private void registerFixedCategory(String id, String display, List<ShopOffer> offers) {
        categories.put(id, new ShopCategoryDefinition(id, display, offers));
    }

    @SafeVarargs
    private final List<ShopOffer> combine(List<ShopOffer>... groups) {
        List<ShopOffer> offers = new ArrayList<>();
        for (List<ShopOffer> group : groups) {
            offers.addAll(group);
        }
        return offers;
    }

    private List<ShopOffer> pair(String id, String materialKey, double singlePrice, double stackPrice) {
        int base = Integer.parseInt(id);
        return List.of(
            new ShopOffer(Integer.toString(base), title(materialKey), "", "material:minecraft:" + materialKey + ":1", singlePrice, 0, true, false, null, null),
            new ShopOffer(Integer.toString(base + 1), "32 " + title(materialKey), "", "material:minecraft:" + materialKey + ":32", stackPrice, 0, true, false, null, null)
        );
    }

    private String title(String materialKey) {
        String[] parts = materialKey.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private Optional<ItemStack> createShopItem(String spec) {
        if (spec == null || spec.isBlank()) {
            return Optional.empty();
        }
        if (spec.startsWith("material:")) {
            String[] parts = spec.split(":");
            if (parts.length < 4) {
                return Optional.empty();
            }
            Material material = Material.fromKey(parts[1] + ":" + parts[2]);
            if (material == null) {
                return Optional.empty();
            }
            int amount;
            try {
                amount = Integer.parseInt(parts[3]);
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            return Optional.of(ItemStack.of(material, amount));
        }
        return customItemService.createFromCustomId(spec);
    }

    private boolean hasEmptyInventorySlot(Player player) {
        if (player == null) {
            return false;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getInnerSize(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack == null || stack.material() == Material.AIR || stack.amount() <= 0) {
                return true;
            }
        }
        return false;
    }

    private ShopOffer offer(String id, String displayName, double dollars, long prestige, boolean oneTime, String actionKey, String actionValue) {
        return new ShopOffer(id, displayName, "Get a donator rank for a season", null, dollars, prestige, false, oneTime, actionKey, actionValue);
    }

    private ShopOffer upgrade(String id, String displayName, double dollars, long prestige, String actionKey, String actionValue) {
        return new ShopOffer(id, displayName, displayName, null, dollars, prestige, false, true, actionKey, actionValue);
    }

    public record SaleResult(int stacksSold, int itemsSold, double earned) {
        public static SaleResult empty() {
            return new SaleResult(0, 0, 0d);
        }
        public boolean soldAnything() {
            return stacksSold > 0;
        }
    }

    public record PurchaseResult(boolean success, boolean found, boolean affordable, boolean alreadyOwned, boolean inventoryFull, boolean deliveryFailed, ShopOffer offer) {
        static PurchaseResult success(ShopOffer offer) { return new PurchaseResult(true, true, true, false, false, false, offer); }
        static PurchaseResult notFound() { return new PurchaseResult(false, false, false, false, false, false, null); }
        static PurchaseResult cannotAfford(ShopOffer offer) { return new PurchaseResult(false, true, false, false, false, false, offer); }
        static PurchaseResult alreadyPurchased(ShopOffer offer) { return new PurchaseResult(false, true, true, true, false, false, offer); }
        static PurchaseResult inventoryFull(ShopOffer offer) { return new PurchaseResult(false, true, true, false, true, false, offer); }
        static PurchaseResult deliveryFailed(ShopOffer offer) { return new PurchaseResult(false, true, true, false, false, true, offer); }
    }
}
