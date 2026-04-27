package xyz.fallnight.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class PagedTextMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int ITEMS_PER_PAGE = 45;

    private final Map<Inventory, Session> sessions = new ConcurrentHashMap<>();

    public void open(Player player, String title, List<String> lines) {
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize(title));
        Session session = new Session(title, List.copyOf(lines), 1);
        sessions.put(inventory, session);
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            Session current = sessions.get(inventory);
            if (current == null) {
                return;
            }
            if (event.getSlot() == 48) {
                render(inventory, current.withPage(Math.max(1, current.page() - 1)));
            } else if (event.getSlot() == 50) {
                render(inventory, current.withPage(current.page() + 1));
            } else if (event.getSlot() == 53) {
                event.getPlayer().closeInventory();
            }
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> sessions.remove(inventory));
        render(inventory, session);
        InventoryOpeners.replace(player, inventory);
    }

    private void render(Inventory inventory, Session session) {
        sessions.put(inventory, session);
        inventory.clear();
        int start = (session.page() - 1) * ITEMS_PER_PAGE;
        if (start >= session.lines().size()) {
            start = 0;
            session = session.withPage(1);
            sessions.put(inventory, session);
        }
        int end = Math.min(session.lines().size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            inventory.setItemStack(i - start, lineItem(session.lines().get(i)));
        }
        inventory.setItemStack(48, button(Material.PAPER, "§r§bPrevious page"));
        inventory.setItemStack(50, button(Material.PAPER, "§r§bNext page"));
        inventory.setItemStack(53, button(Material.BARRIER, "§cClose"));
    }

    private static ItemStack lineItem(String line) {
        return ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(line == null || line.isBlank() ? "§7-" : line)));
    }

    private static ItemStack button(Material material, String text) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(text)));
    }

    private record Session(String title, List<String> lines, int page) {
        Session withPage(int nextPage) {
            return new Session(title, new ArrayList<>(lines), Math.max(1, nextPage));
        }
    }
}
