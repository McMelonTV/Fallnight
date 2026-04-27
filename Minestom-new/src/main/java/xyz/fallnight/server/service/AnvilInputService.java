package xyz.fallnight.server.service;

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

public final class AnvilInputService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final Map<AnvilInventory, Session> sessions = new ConcurrentHashMap<>();

    public void open(Player player, String title, String prompt, String initialText, SubmitHandler submitHandler) {
        AnvilInventory inventory = new AnvilInventory(LEGACY.deserialize(title));
        inventory.setRepairCost((short) 0);
        String display = initialText == null || initialText.isBlank() ? prompt : initialText;
        inventory.setItemStack(0, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(display))));
        Session session = new Session(initialText == null ? "" : initialText, submitHandler);
        sessions.put(inventory, session);
        if (initialText != null && !initialText.isBlank()) {
            inventory.setItemStack(2, ItemStack.of(Material.EMERALD).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§aConfirm"))));
        }
        inventory.eventNode().addListener(PlayerAnvilInputEvent.class, event -> {
            if (event.getInventory() != inventory) {
                return;
            }
            Session current = sessions.get(inventory);
            if (current == null) {
                return;
            }
            sessions.put(inventory, current.withInput(event.getInput()));
            inventory.setItemStack(2, ItemStack.of(Material.EMERALD).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§aConfirm"))));
        });
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() != 2) {
                return;
            }
            Session current = sessions.get(inventory);
            if (current == null) {
                return;
            }
            current.submitHandler().submit(event.getPlayer(), current.input());
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> sessions.remove(inventory));
        InventoryOpeners.replace(player, inventory);
    }

    @FunctionalInterface
    public interface SubmitHandler {
        void submit(Player player, String input);
    }

    private record Session(String input, SubmitHandler submitHandler) {
        Session withInput(String nextInput) {
            return new Session(nextInput == null ? "" : nextInput, submitHandler);
        }
    }
}
