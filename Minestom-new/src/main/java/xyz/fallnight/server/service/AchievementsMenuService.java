package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.achievement.AchievementStatus;
import xyz.fallnight.server.domain.user.UserProfile;
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

public final class AchievementsMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PlayerProfileService profileService;
    private final AchievementService achievementService;

    public AchievementsMenuService(PlayerProfileService profileService, AchievementService achievementService) {
        this.profileService = profileService;
        this.achievementService = achievementService;
    }

    public void open(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        List<AchievementStatus> statuses = achievementService.statuses(profile);
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§8Achievements"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        for (int i = 0; i < statuses.size(); i++) {
            inventory.setItemStack(i, display(statuses.get(i)));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private static ItemStack display(AchievementStatus status) {
        Material material = status.unlocked() ? Material.GREEN_TERRACOTTA : Material.RED_TERRACOTTA;
        String lore = status.unlocked()
            ? "§r§7" + status.definition().description()
            : "§r§8[§cLocked§8]";
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§r§b" + status.definition().title()))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(lore))));
    }
}
