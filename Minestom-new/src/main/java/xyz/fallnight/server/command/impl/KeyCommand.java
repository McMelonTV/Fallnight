package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.crate.CrateDefinition;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;

import java.util.Locale;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class KeyCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final CrateService crateService;
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;
    private final LegacyCustomItemService customItemService;

    public KeyCommand(PermissionService permissionService, CrateService crateService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        super("key", permissionService);
        this.crateService = crateService;
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;
        this.customItemService = new LegacyCustomItemService();

        var playerArgument = ArgumentType.Word("player");
        var crateIdArgument = ArgumentType.Word("crateId");
        var amountArgument = ArgumentType.Word("amount");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a target.")));
        addSyntax((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a crate ID.")), playerArgument);
        addSyntax((sender, context) -> {
            String playerName = context.get(playerArgument);
            String crateId = context.get(crateIdArgument);
            grant(sender, playerName, crateId, 1);
        }, playerArgument, crateIdArgument);

        addSyntax((sender, context) -> {
            String playerName = context.get(playerArgument);
            String crateId = context.get(crateIdArgument);
            grant(sender, playerName, crateId, context.get(amountArgument));
        }, playerArgument, crateIdArgument, amountArgument);
    }

    private static Integer crateNumericId(String crateId) {
        return switch (crateId.toLowerCase(Locale.ROOT)) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            case "koth" -> 120;
            case "vote" -> 99;
            default -> null;
        };
    }

    @Override
    public String permission() {
        return "fallnight.command.key";
    }

    @Override
    public String summary() {
        return "give someone a key";
    }

    @Override
    public String usage() {
        return "/key <target> <key> [count]";
    }

    private void grant(net.minestom.server.command.CommandSender sender, String playerName, String crateId, int amount) {
        grant(sender, playerName, crateId, Integer.toString(amount));
    }

    private void grant(net.minestom.server.command.CommandSender sender, String playerName, String crateId, String amountText) {
        if (crateId == null || crateId.isBlank()) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a crate ID."));
            return;
        }
        if (!crateId.chars().allMatch(Character::isDigit)) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a valid ID."));
            return;
        }
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(playerName);
        if (online == null) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please player could not be found."));
            return;
        }
        if (!amountText.chars().allMatch(Character::isDigit)) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 Please enter a valid key count."));
            return;
        }
        int amount = Integer.parseInt(amountText);
        String normalizedCrateId = switch (Integer.parseInt(crateId)) {
            case 10 -> "iron";
            case 20 -> "gold";
            case 30 -> "diamond";
            case 40 -> "emerald";
            case 50 -> "netherrite";
            case 99 -> "vote";
            case 120 -> "koth";
            default -> null;
        };
        if (normalizedCrateId == null) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 That crate doesnt exist."));
            return;
        }

        var profile = profileService.getOrCreate(online);
        CrateDefinition crate = crateService.findCrate(normalizedCrateId).orElse(null);
        boolean delivered = customItemService.createById(20, amount, crateNumericId(normalizedCrateId))
                .map(item -> itemDeliveryService.deliver(online, profile, item).success())
                .orElse(false);
        if (!delivered || crate == null) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 That crate doesnt exist."));
            return;
        }
        sender.sendMessage(LEGACY.deserialize("§r§b§l>§r§b " + online.getUsername() + " §r§7has been given §b" + amount + "§7 §b" + crate.displayName() + "§7 " + (amount > 1 ? "keys." : "key.")));
        online.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 You have been given §b" + amount + "§7 §b" + crate.displayName() + "§7 " + (amount > 1 ? "keys." : "key.")));
    }
}
