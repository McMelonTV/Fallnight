package xyz.fallnight.server.service;

import xyz.fallnight.server.command.framework.CommandMessages;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerAnvilInputEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.type.AnvilInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class PayMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final Map<AnvilInventory, String> targetByInventory = new ConcurrentHashMap<>();
    private final Map<AnvilInventory, String> inputByInventory = new ConcurrentHashMap<>();

    public PayMenuService(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    public void open(Player player) {
        Inventory inventory = new Inventory(InventoryType.CHEST_3_ROW, LEGACY.deserialize("§b/pay"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            int slot = event.getSlot();
            List<Player> players = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .toList();
            if (slot >= 0 && slot < players.size()) {
                openAmount(player, players.get(slot).getUsername());
            }
        });
        int slot = 0;
        for (Player target : net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            inventory.setItemStack(slot++, ItemStack.of(Material.PLAYER_HEAD).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§b" + target.getUsername()))));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private void openAmount(Player player, String target) {
        AnvilInventory inventory = new AnvilInventory(LEGACY.deserialize("§b/pay"));
        targetByInventory.put(inventory, target);
        inventory.setRepairCost((short) 0);
        inventory.setItemStack(0, ItemStack.of(Material.PAPER).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§b§l> §r§7Money"))));
        inventory.eventNode().addListener(PlayerAnvilInputEvent.class, event -> {
            if (event.getInventory() != inventory) return;
            inputByInventory.put(inventory, event.getInput());
            inventory.setItemStack(2, ItemStack.of(Material.EMERALD).with(DataComponents.CUSTOM_NAME, xyz.fallnight.server.util.ItemTextStyles.itemText(LEGACY.deserialize("§aConfirm payment"))));
        });
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> {
            event.setCancelled(true);
            if (event.getSlot() != 2) return;
            apply(event.getPlayer(), inventory);
        });
        inventory.eventNode().addListener(InventoryCloseEvent.class, event -> {
            targetByInventory.remove(inventory);
            inputByInventory.remove(inventory);
        });
        InventoryOpeners.replace(player, inventory);
    }

    private void apply(Player player, AnvilInventory inventory) {
        String targetName = targetByInventory.get(inventory);
        String rawAmount = inputByInventory.getOrDefault(inventory, "").trim();
        if (targetName == null || rawAmount.isBlank()) {
            player.sendMessage(CommandMessages.error("Please enter a valid amount of money."));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (NumberFormatException exception) {
            player.sendMessage(CommandMessages.error("Please enter a valid amount of money."));
            return;
        }
        amount = wholeDollarAmount(amount);
        if (amount < 0d) {
            player.sendMessage(CommandMessages.error("Please enter a valid amount of money."));
            return;
        }
        Player target = net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
        if (target == null) {
            player.sendMessage(CommandMessages.error("Player not found."));
            return;
        }
        var sourceProfile = profileService.getOrCreate(player);
        if (amount > 0D && !sourceProfile.withdraw(amount)) {
            player.sendMessage(CommandMessages.error("You don't have enough money for this transaction."));
            return;
        }
        var targetProfile = profileService.getOrCreate(target);
        if (amount > 0D) {
            targetProfile.deposit(amount);
        }
        profileService.save(sourceProfile);
        if (targetProfile != sourceProfile) {
            profileService.save(targetProfile);
        }
        player.sendMessage(CommandMessages.success("You paid §e$" + legacyMoney(amount) + "§r§7 to §e" + target.getUsername() + "§r§7."));
        if (target != player) {
            target.sendMessage(CommandMessages.success("§e" + player.getUsername() + " §r§7paid you §e$" + legacyMoney(amount) + "§r§7."));
        }
        player.closeInventory();
    }

    private static String legacyMoney(double amount) {
        return Math.rint(amount) == amount ? Long.toString((long) amount) : new DecimalFormat("#,##0.##").format(amount);
    }

    private static double wholeDollarAmount(double requestedAmount) {
        return BigDecimal.valueOf(requestedAmount)
            .setScale(0, RoundingMode.DOWN)
            .doubleValue();
    }
}
