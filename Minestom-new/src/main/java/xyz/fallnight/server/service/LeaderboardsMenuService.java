package xyz.fallnight.server.service;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class LeaderboardsMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BalTopMenuService balTopMenuService;
    private final EarnTopMenuService earnTopMenuService;
    private final BreakTopMenuService breakTopMenuService;
    private final MineTopMenuService mineTopMenuService;
    private final KillTopMenuService killTopMenuService;
    private final KDRatioTopMenuService kdRatioTopMenuService;

    public LeaderboardsMenuService(
            BalTopMenuService balTopMenuService,
            EarnTopMenuService earnTopMenuService,
            BreakTopMenuService breakTopMenuService,
            MineTopMenuService mineTopMenuService,
            KillTopMenuService killTopMenuService,
            KDRatioTopMenuService kdRatioTopMenuService) {
        this.balTopMenuService = balTopMenuService;
        this.earnTopMenuService = earnTopMenuService;
        this.breakTopMenuService = breakTopMenuService;
        this.mineTopMenuService = mineTopMenuService;
        this.killTopMenuService = killTopMenuService;
        this.kdRatioTopMenuService = kdRatioTopMenuService;
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bLeaderboards"));

        inventory.setItemStack(4, ItemStack.of(Material.PAPER)
                .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§fSelect the category you want to view."))));

        inventory.setItemStack(10, button("§rBalance"));
        inventory.setItemStack(11, button("§rTotal money earned"));
        inventory.setItemStack(12, button("§rTotal blocks broken"));
        inventory.setItemStack(13, button("§rHighest mine"));
        inventory.setItemStack(14, button("§rMost kills"));
        inventory.setItemStack(15, button("§rHighest K/D ratio"));

        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            Player clicked = event.getPlayer();
            
            switch (slot) {
                case 10 -> balTopMenuService.open(clicked);
                case 11 -> earnTopMenuService.open(clicked);
                case 12 -> breakTopMenuService.open(clicked);
                case 13 -> mineTopMenuService.open(clicked);
                case 14 -> killTopMenuService.open(clicked);
                case 15 -> kdRatioTopMenuService.open(clicked);
            }
        });

        InventoryOpeners.replace(player, inventory);
    }

    private static ItemStack button(String name) {
        return ItemStack.of(Material.PAPER)
                .with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(name)));
    }
}
