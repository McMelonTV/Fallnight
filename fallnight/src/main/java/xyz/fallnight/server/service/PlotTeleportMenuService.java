package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.plot.PlotCoordinate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class PlotTeleportMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int ITEMS_PER_PAGE = 45;

    private final Map<Inventory, Session> sessions = new ConcurrentHashMap<>();

    private static ItemStack optionItem(Option option) {
        return ItemStack.of(Material.ENDER_PEARL).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(option.title()))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize(option.subtitle()))));
    }

    private static ItemStack button(Material material, String text) {
        return ItemStack.of(material).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize(text)));
    }

    public void open(Player player, String title, List<Option> options, BiConsumer<Player, PlotCoordinate> onSelect) {
        Inventory inventory = new Inventory(InventoryType.CHEST_6_ROW, LEGACY.deserialize(title));
        Session session = new Session(title, List.copyOf(options), 1, onSelect);
        sessions.put(inventory, session);
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            Session current = sessions.get(inventory);
            if (current == null || !(event.getPlayer() instanceof Player clicker)) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 48) {
                render(inventory, current.withPage(Math.max(1, current.page() - 1)));
                return;
            }
            if (slot == 50) {
                render(inventory, current.withPage(current.page() + 1));
                return;
            }
            if (slot == 53) {
                clicker.closeInventory();
                return;
            }
            int index = (current.page() - 1) * ITEMS_PER_PAGE + slot;
            if (slot < 0 || slot >= ITEMS_PER_PAGE || index < 0 || index >= current.options().size()) {
                return;
            }
            Option option = current.options().get(index);
            clicker.closeInventory();
            current.onSelect().accept(clicker, option.coordinate());
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> sessions.remove(inventory));
        render(inventory, session);
        InventoryOpeners.replace(player, inventory);
    }

    private void render(Inventory inventory, Session session) {
        sessions.put(inventory, session);
        inventory.clear();
        int start = (session.page() - 1) * ITEMS_PER_PAGE;
        if (start >= session.options().size()) {
            start = 0;
            session = session.withPage(1);
            sessions.put(inventory, session);
        }
        int end = Math.min(session.options().size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            inventory.setItemStack(i - start, optionItem(session.options().get(i)));
        }
        inventory.setItemStack(48, button(Material.PAPER, "§r§bPrevious page"));
        inventory.setItemStack(50, button(Material.PAPER, "§r§bNext page"));
        inventory.setItemStack(53, button(Material.BARRIER, "§cClose"));
    }

    public record Option(PlotCoordinate coordinate, String title, String subtitle) {
    }

    private record Session(String title, List<Option> options, int page, BiConsumer<Player, PlotCoordinate> onSelect) {
        Session withPage(int nextPage) {
            return new Session(title, options, Math.max(1, nextPage), onSelect);
        }
    }
}
