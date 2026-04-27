package xyz.fallnight.server.gameplay.player;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.BookMenuService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import java.util.stream.Collectors;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.Material;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.WrittenBookContent;

public final class ItemRestrictionModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final SpawnService plotWorldService;
    private final LegacyCustomItemService customItemService;
    private final EventNode<Event> eventNode;

    public ItemRestrictionModule(PlayerProfileService profileService, SpawnService plotWorldService) {
        this.profileService = profileService;
        this.plotWorldService = plotWorldService;
        this.customItemService = new LegacyCustomItemService();
        this.eventNode = EventNode.all("item-restrictions");
    }

    public void register() {
        eventNode.addListener(ItemDropEvent.class, event -> {
            if (AdminModeService.isEnabled(profileService.getOrCreate(event.getPlayer()))) {
                return;
            }
            if (canDropAt(event.getPlayer())) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(CommandMessages.error("Dropping items is disabled outside of the plotworld."));
        });
        eventNode.addListener(InventoryPreClickEvent.class, event -> {
            ItemStack item = event.getClickedItem();
            if (item == null || item.isAir()) {
                return;
            }
            if (item.amount() > item.maxStackSize()) {
                int slot = event.getSlot();
                if (slot >= 0) {
                    event.getInventory().setItemStack(slot, clampStack(item));
                }
                event.setCancelled(true);
            }
        });
        eventNode.addListener(PlayerFinishItemUseEvent.class, event -> {
            Material material = event.getItemStack().material();
            if (material != Material.GOLDEN_APPLE && material != Material.ENCHANTED_GOLDEN_APPLE) {
                return;
            }
            var profile = profileService.getOrCreate(event.getPlayer());
            profile.getExtraData().put("gappleCooldownAt", System.currentTimeMillis() / 1000L);
            profileService.save(profile);
        });
        eventNode.addListener(PlayerUseItemEvent.class, event -> {
            if (customItemService.customItemId(event.getItemStack()) == 14) {
                openGuideBook(event.getPlayer(), event.getItemStack());
                event.setCancelled(true);
                return;
            }
            Material material = event.getItemStack().material();
            if (material != Material.GOLDEN_APPLE && material != Material.ENCHANTED_GOLDEN_APPLE) {
                return;
            }
            if (canUseGapple(event.getPlayer())) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendActionBar(LEGACY.deserialize(
                "§c§l> §r§7Please wait " + gappleSecondsRemaining(event.getPlayer()) + " seconds before consuming another gapple"
            ));
        });
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    public boolean canUseGapple(net.minestom.server.entity.Player player) {
        var profile = profileService.getOrCreate(player);
        Object value = profile.getExtraData().get("gappleCooldownAt");
        long at = value instanceof Number number ? number.longValue() : 0L;
        return at + 15 <= (System.currentTimeMillis() / 1000L);
    }

    public int gappleSecondsRemaining(net.minestom.server.entity.Player player) {
        var profile = profileService.getOrCreate(player);
        Object value = profile.getExtraData().get("gappleCooldownAt");
        long at = value instanceof Number number ? number.longValue() : 0L;
        long remaining = (at + 15) - (System.currentTimeMillis() / 1000L);
        return (int) Math.max(0L, remaining);
    }

    public boolean canDropAt(net.minestom.server.entity.Player player) {
        return player != null && player.getInstance() == plotWorldService.instance();
    }

    public ItemStack clampStack(ItemStack item) {
        if (item == null || item.isAir() || item.amount() <= item.maxStackSize()) {
            return item;
        }
        return item.withAmount(item.maxStackSize());
    }

    private void openGuideBook(net.minestom.server.entity.Player player, ItemStack item) {
        WrittenBookContent content = item.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return;
        }
        Book book = Book.book(
            Component.text(content.title().text()),
            Component.text(content.author()),
            content.pages().stream().map(page -> page.text()).collect(Collectors.toList())
        );
        player.openBook(book);
    }

}
