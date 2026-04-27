package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.util.ItemTextStyles;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class MineTopMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LeaderboardService leaderboardService;
    private final MineRankService mineRankService;

    public MineTopMenuService(LeaderboardService leaderboardService, MineRankService mineRankService) {
        this.leaderboardService = leaderboardService;
        this.mineRankService = mineRankService;
    }

    public void open(Player player) {
        leaderboardService.regenerateAll();
        List<UserProfile> top = leaderboardService.top(LeaderboardService.Type.MINE).stream().limit(30).toList();
        if (top.isEmpty()) {
            player.sendMessage(LEGACY.deserialize("§r§c§l> §r§7The leaderboards are still regenerating, please try again later!"));
            return;
        }
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize("§bTop: rank"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        for (int i = 0; i < top.size(); i++) {
            inventory.setItemStack(i, entry(top.get(i), i + 1));
        }
        inventory.setItemStack(53, ItemStack.of(Material.PAPER)
            .with(DataComponents.CUSTOM_NAME, ItemTextStyles.itemText(LEGACY.deserialize("§r§fA list of the players who have the highest ranks."))));
        InventoryOpeners.replace(player, inventory);
    }

    public List<Component> renderLines() {
        leaderboardService.regenerateAll();
        List<UserProfile> top = leaderboardService.top(LeaderboardService.Type.MINE).stream().limit(30).toList();
        List<Component> lines = new ArrayList<>();
        if (top.isEmpty()) {
            lines.add(LEGACY.deserialize("§r§c§l> §r§7The leaderboards are still regenerating, please try again later!"));
            return List.copyOf(lines);
        }
        lines.add(LEGACY.deserialize("§bTop: rank"));
        lines.add(LEGACY.deserialize("§r§fA list of the players who have the highest ranks."));
        for (int i = 0; i < top.size(); i++) {
            lines.add(LEGACY.deserialize(entryText(top.get(i), i + 1)));
        }
        return List.copyOf(lines);
    }

    private ItemStack entry(UserProfile profile, int place) {
        return ItemStack.of(Material.STONE)
            .with(DataComponents.CUSTOM_NAME, ItemTextStyles.itemText(LEGACY.deserialize(entryText(profile, place))));
    }

    private String entryText(UserProfile profile, int place) {
        return " §b" + place + "§r§8>§r§7 " + profile.getUsername() + "§r§8 [§7"
            + toRoman(profile.getPrestige())
            + "§r§8§l-§r§7"
            + mineTag(profile.getMineRank())
            + "§8]§r";
    }

    private String mineTag(int mineRankId) {
        return mineRankService.find(mineRankId)
            .map(MineRank::getTag)
            .orElse("A");
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
}
