package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
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

public final class LotteryMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final LotteryService lotteryService;
    private final AnvilInputService anvilInputService;

    public LotteryMenuService(LotteryService lotteryService, PlayerProfileService profileService) {
        this.lotteryService = lotteryService;
        this.anvilInputService = new AnvilInputService();
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§bLottery"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() == 11) {
                buy(event.getPlayer(), 1);
                render(inventory);
            } else if (event.getSlot() == 13) {
                buy(event.getPlayer(), 5);
                render(inventory);
            } else if (event.getSlot() == 15) {
                buy(event.getPlayer(), 10);
                render(inventory);
            } else if (event.getSlot() == 21) {
                openCustomAmount(event.getPlayer());
            } else if (event.getSlot() == 22) {
                event.getPlayer().closeInventory();
            }
        });
        render(inventory);
        InventoryOpeners.replace(player, inventory);
    }

    private void buy(Player player, int amount) {
        LotteryService.PurchaseResult result = lotteryService.buyTickets(player.getUsername(), amount);
        switch (result.status()) {
            case SUCCESS -> player.sendMessage(CommandMessages.success(
                "You bought §b" + result.purchasedTickets() + " §r§7tickets for §b" + NumberFormatter.currency(result.totalCost()) + "§r§7."
            ));
            case INVALID_PLAYER -> player.sendMessage(LEGACY.deserialize("§r§c> §r§7Something went wrong while trying to buy tickets."));
            case INVALID_AMOUNT -> player.sendMessage(LEGACY.deserialize("§r§c> §r§7Please enter a correct ticket count."));
            case INSUFFICIENT_BALANCE -> player.sendMessage(LEGACY.deserialize("§r§c> §r§7You can't afford that."));
        }
    }

    public void openCustomAmount(Player player) {
        openCustomAmount(player, "");
    }

    public void openCustomAmount(Player player, String initialText) {
        anvilInputService.open(player, "§bLottery §7- buy tickets", "§b> §r§7Ticket count", initialText, (source, input) -> {
            int amount;
            try {
                amount = Integer.parseInt((input == null ? "" : input).trim());
            } catch (NumberFormatException exception) {
                source.sendMessage(LEGACY.deserialize("§r§c> §r§7Please enter a correct ticket count."));
                return;
            }
            buy(source, amount);
            source.closeInventory();
        });
    }

    private void render(Inventory inventory) {
        inventory.clear();
        LotteryService.LotteryStatus status = lotteryService.status();
        inventory.setItemStack(4, ItemStack.of(Material.NETHER_STAR).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§bLottery Jackpot"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(
            LEGACY.deserialize("§r§7Welcome to the lottery! Here you can buy tickets for §b$100000 each§r§7. The more you buy, the higher your chances of winning the jackpot."),
            LEGACY.deserialize("§r§b" + status.totalTickets() + " §r§7tickets have currently been sold."),
            LEGACY.deserialize("§r§7The jackpot for this lottery is §r§b$" + (long) status.jackpotPool() + "§r§7."),
            LEGACY.deserialize("§r§7The next draw is in §b" + formatDuration(status.remainingDrawSeconds()) + "§r§7.")
        ))));
        inventory.setItemStack(11, buyButton(1));
        inventory.setItemStack(13, buyButton(5));
        inventory.setItemStack(15, buyButton(10));
        inventory.setItemStack(21, ItemStack.of(Material.NAME_TAG).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("Buy tickets"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§r§7Please select the amount of lottery tickets you want to buy. Each ticket costs §b$100000§r§7 and will add §b$75000 §r§7to the jackpot. Buying more tickets will increase your chance of winning. You can also §b/vote§r§7 to get a free ticket!")))));
        inventory.setItemStack(22, ItemStack.of(Material.BARRIER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("Close"))));
    }

    private ItemStack buyButton(int amount) {
        double cost = amount * LotteryService.DEFAULT_TICKET_PRICE;
        return ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("Buy tickets"))).with(DataComponents.LORE, xyz.fallnight.server.util.ItemTextStyles.itemLore(List.of(LEGACY.deserialize("§7Cost: §b" + NumberFormatter.currency(cost)))));
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + remSeconds + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + remSeconds + "s";
        }
        return remSeconds + "s";
    }
}
