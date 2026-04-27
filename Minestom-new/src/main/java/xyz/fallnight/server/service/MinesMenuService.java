package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.mine.MineDefinition;
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

public final class MinesMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final MineRankService mineRankService;

    public MinesMenuService(PlayerProfileService profileService, MineService mineService, MineRankService mineRankService) {
        this.profileService = profileService;
        this.mineService = mineService;
        this.mineRankService = mineRankService;
    }

    public void open(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        List<MineDefinition> mines = mineService.allMines();
        Inventory inventory = new Inventory(inventoryType(mines.size()), LEGACY.deserialize("§bMine teleporter"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot < 0 || slot >= mines.size()) {
                return;
            }
            handleMineClick(event.getPlayer(), profileService.getOrCreate(event.getPlayer()), mines.get(slot));
        });
        for (int i = 0; i < mines.size(); i++) {
            inventory.setItemStack(i, displayMine(profile, mines.get(i)));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private void handleMineClick(Player player, UserProfile profile, MineDefinition mine) {
        if (mine == null) {
            player.sendMessage(CommandMessages.error("That mine was not found."));
            return;
        }
        if (hasAccess(profile, mine)) {
            teleport(player, mine);
            return;
        }
        if (mine.isDisabled()) {
            player.sendMessage(CommandMessages.error("You don't have access to this mine."));
            return;
        }
        if (mine.getId() == profile.getMineRank() + 1) {
            long cost = mineRankService.rankUpPrice(profile.getMineRank(), profile.getPrestige());
            String rankTag = mineRankService.find(mine.getId()).map(xyz.fallnight.server.domain.mine.MineRank::getTag).orElse(mine.getName());
            if (!profile.withdraw(cost)) {
                player.sendMessage(CommandMessages.error("You don't have enough money to rank up to §c" + rankTag + "§r§7. You require §c$" + NumberFormatter.shortNumberRounded(cost) + "§r§7."));
                return;
            }
            profile.setMineRank(mine.getId());
            profileService.save(profile);
            player.sendMessage(CommandMessages.success("You have been ranked up to §b" + rankTag + "§r§7 for §b$" + NumberFormatter.shortNumberRounded(cost) + "§r§7!"));
            teleport(player, mine);
            return;
        }
        player.sendMessage(CommandMessages.error("You don't have access to this mine."));
    }

    private static void teleport(Player player, MineDefinition mine) {
        var target = new net.minestom.server.coordinate.Pos(mine.effectiveSpawnX() + 0.5, mine.effectiveSpawnY(), mine.effectiveSpawnZ() + 0.5);
        if (player.getInstance() != null) {
            player.getInstance().loadChunk(target.chunkX(), target.chunkZ()).join();
        }
        player.teleport(target);
        player.sendMessage(CommandMessages.info("You have been teleported to mine §b" + mine.getName() + "§r§7."));
    }

    private ItemStack displayMine(UserProfile profile, MineDefinition mine) {
        Material material = hasAccess(profile, mine) ? Material.LIME_CONCRETE : mine.isDisabled() ? Material.GRAY_CONCRETE : Material.RED_CONCRETE;
        String status;
        if (hasAccess(profile, mine)) {
            status = "§8[§aUnlocked§8]";
        } else if (mine.isDisabled()) {
            status = "§8[§7Disabled§8]";
        } else if (mineRankService.find(mine.getId()).isPresent()) {
            long price = mineRankService.rankUpPrice(mine.getId() - 1, profile.getPrestige());
            status = "§8[§c$" + NumberFormatter.shortNumberRounded(price) + "§8]";
        } else {
            status = "§8[§cLocked§8]";
        }
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§b" + mine.getName() + " §8mine"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(status))));
    }

    private static boolean hasAccess(UserProfile profile, MineDefinition mine) {
        if (AdminModeService.isEnabled(profile)) {
            return true;
        }
        return !mine.isDisabled() && profile.getMineRank() >= mine.getId();
    }

    private static InventoryType inventoryType(int count) {
        return count <= 9 ? InventoryType.CHEST_1_ROW : count <= 18 ? InventoryType.CHEST_2_ROW : count <= 27 ? InventoryType.CHEST_3_ROW : count <= 36 ? InventoryType.CHEST_4_ROW : InventoryType.CHEST_6_ROW;
    }
}
