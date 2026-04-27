package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerAnvilInputEvent;
import net.minestom.server.inventory.type.AnvilInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class NickMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final Map<AnvilInventory, String> pendingInputs = new ConcurrentHashMap<>();

    public NickMenuService(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    public void open(Player player) {
        AnvilInventory inventory = new AnvilInventory(LEGACY.deserialize("§b/nick"));
        inventory.setRepairCost((short) 0);
        inventory.setItemStack(0, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§7Please enter the nickname you want to give yourself. Enter 'clear' or nothing to remove your current nickname."))));
        inventory.eventNode().addListener(PlayerAnvilInputEvent.class, event -> {
            if (event.getInventory() != inventory) {
                return;
            }
            pendingInputs.put(inventory, event.getInput());
            inventory.setItemStack(2, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§b§l> §r§7Nickname"))));
        });
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() != 2) {
                return;
            }
            apply(event.getPlayer(), pendingInputs.getOrDefault(inventory, ""));
            event.getPlayer().closeInventory();
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> pendingInputs.remove(inventory));
        InventoryOpeners.replace(player, inventory);
    }

    private void apply(Player player, String rawInput) {
        String nickname = rawInput == null ? "" : rawInput.trim();
        var profile = profileService.getOrCreate(player);
        if (nickname.isBlank() || nickname.equalsIgnoreCase("clear")) {
            profile.getExtraData().remove("nickname");
            profileService.save(profile);
            player.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been cleared."));
            return;
        }
        if (nickname.length() > 20) {
            player.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a shorter nickname."));
        }
        profile.getExtraData().put("nickname", nickname);
        profileService.save(profile);
        player.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 Your nickname has been set to §b" + nickname + "§r§7."));
    }
}
