package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.util.NumberFormatter;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class LeaderboardMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final LeaderboardService leaderboardService;

    public LeaderboardMenuService(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public void openCategories(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_1_ROW, LEGACY.deserialize("§bLeaderboards"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 0 -> open(player, LeaderboardService.Type.BALANCE);
                case 1 -> open(player, LeaderboardService.Type.EARNINGS);
                case 2 -> open(player, LeaderboardService.Type.BLOCK_BREAKS);
                case 3 -> open(player, LeaderboardService.Type.MINE);
                case 4 -> open(player, LeaderboardService.Type.KILLS);
                case 5 -> open(player, LeaderboardService.Type.KDR);
                default -> {
                }
            }
        });
        inventory.setItemStack(0, button(Material.GOLD_INGOT, "§rBalance"));
        inventory.setItemStack(1, button(Material.EMERALD, "§rTotal money earned"));
        inventory.setItemStack(2, button(Material.DIAMOND_PICKAXE, "§rTotal blocks broken"));
        inventory.setItemStack(3, button(Material.STONE, "§rHighest mine"));
        inventory.setItemStack(4, button(Material.DIAMOND_SWORD, "§rMost kills"));
        inventory.setItemStack(5, button(Material.TOTEM_OF_UNDYING, "§rHighest K/D ratio"));
        inventory.setItemStack(8, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§fSelect the category you want to view."))));
        InventoryOpeners.replace(player, inventory);
    }

    public void open(Player player, LeaderboardService.Type type) {
        leaderboardService.regenerateAll();
        List<UserProfile> top = leaderboardService.top(type).stream().limit(30).toList();
        if (top.isEmpty()) {
            player.sendMessage(LEGACY.deserialize("§r§c§l> §r§7The leaderboards are still regenerating, please try again later!"));
            return;
        }
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize(title(type)));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        for (int i = 0; i < top.size(); i++) {
            UserProfile profile = top.get(i);
            inventory.setItemStack(i, entry(type, profile, i + 1));
        }
        inventory.setItemStack(53, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(description(type)))));
        InventoryOpeners.replace(player, inventory);
    }

    public void open(Player player, LeaderboardService.Type type, String ignoredTitle) {
        open(player, type);
    }

    private ItemStack entry(LeaderboardService.Type type, UserProfile profile, int place) {
        Material material = switch (type) {
            case BALANCE -> Material.GOLD_INGOT;
            case EARNINGS -> Material.EMERALD;
            case BLOCK_BREAKS -> Material.DIAMOND_PICKAXE;
            case MINE -> Material.STONE;
            case KILLS -> Material.DIAMOND_SWORD;
            case KDR -> Material.TOTEM_OF_UNDYING;
        };
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(entryText(type, profile, place))));
    }

    private String title(LeaderboardService.Type type) {
        return switch (type) {
            case BALANCE -> "§bTop: money";
            case EARNINGS -> "§bTop: total earned money";
            case BLOCK_BREAKS -> "§bTop: blocks broken";
            case MINE -> "§bTop: rank";
            case KILLS -> "§bTop: kills";
            case KDR -> "§bTop: K/D Ratio";
        };
    }

    private String description(LeaderboardService.Type type) {
        return switch (type) {
            case BALANCE -> "§r§fA list of the players who currently have the most amount of money.";
            case EARNINGS -> "§r§fA list of the players who have earned the most money (by mining).";
            case BLOCK_BREAKS -> "§r§fA list of the players who currently have mined the largest amount of blocks.";
            case MINE -> "§r§fA list of the players who have the highest ranks.";
            case KILLS -> "§r§fA list of the players who currently have the most kills.";
            case KDR -> "§r§fA list of the players who currently have the kill to death ratio. Only deaths by other players count.";
        };
    }

    private String entryText(LeaderboardService.Type type, UserProfile profile, int place) {
        return switch (type) {
            case BALANCE -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b$" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + "§8]§r";
            case EARNINGS -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b$" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + "§8]§r";
            case BLOCK_BREAKS -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + NumberFormatter.shortNumberRounded(leaderboardService.value(type, profile)) + " blocks§8]§r";
            case MINE -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§7" + toRoman(profile.getPrestige()) + "§r§8§l-§r§7" + mineTag(profile) + "§8]§r";
            case KILLS -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + (long) leaderboardService.value(type, profile) + " kills§8]§r";
            case KDR -> " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§b" + roundKdr(leaderboardService.value(type, profile)) + " K/D§8]§r";
        };
    }

    private static String roundKdr(double value) {
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private static String mineTag(UserProfile profile) {
        int rank = Math.max(1, profile.getMineRank());
        return String.valueOf((char) ('A' + Math.min(25, rank - 1)));
    }

    private static String toRoman(int value) {
        int number = Math.max(1, value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                builder.append(numerals[i]);
                number -= values[i];
            }
        }
        return builder.toString();
    }

    private static ItemStack button(Material material, String title) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(title)));
    }
}
