package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.util.Locale;

public final class KeyAllCommand extends FallnightCommand {
    private final CrateService crateService;
    private final PlayerProfileService profileService;
    private final ItemDeliveryService itemDeliveryService;
    private final LegacyCustomItemService customItemService;

    public KeyAllCommand(PermissionService permissionService, CrateService crateService, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        super("keyall", permissionService);
        this.crateService = crateService;
        this.profileService = profileService;
        this.itemDeliveryService = itemDeliveryService;
        this.customItemService = new LegacyCustomItemService();

        var crateIdArgument = ArgumentType.Word("crateId");
        var amountArgument = ArgumentType.Word("amount");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String crateId = context.get(crateIdArgument);
            grantAll(sender, crateId, 1);
        }, crateIdArgument);

        addSyntax((sender, context) -> {
            String crateId = context.get(crateIdArgument);
            String amount = context.get(amountArgument);
            grantAll(sender, crateId, amount);
        }, crateIdArgument, amountArgument);
    }

    private static Integer crateNumericId(String crateId) {
        return switch (crateId.toLowerCase(Locale.ROOT)) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            case "vote" -> 99;
            case "koth" -> 120;
            default -> null;
        };
    }

    @Override
    public String permission() {
        return "fallnight.command.keyall";
    }

    @Override
    public String summary() {
        return "Give crate keys to all online players.";
    }

    @Override
    public String usage() {
        return "/keyall <crateId> [amount]";
    }

    private void grantAll(net.minestom.server.command.CommandSender sender, String crateId, int amount) {
        if (crateId == null || !crateId.chars().allMatch(Character::isDigit)) {
            sender.sendMessage(CommandMessages.error("Please enter a valid ID."));
            return;
        }
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
            sender.sendMessage(CommandMessages.error("That crate doesnt exist."));
            return;
        }
        String displayName = crateService.findCrate(normalizedCrateId).map(crate -> crate.displayName()).orElse(normalizedCrateId);

        int affected = 0;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            var profile = profileService.getOrCreate(player);
            boolean delivered = customItemService.createById(20, amount, crateNumericId(normalizedCrateId))
                    .map(item -> itemDeliveryService.deliver(player, profile, item).success())
                    .orElse(false);
            if (!delivered) {
                continue;
            }
            affected++;
        }
        if (affected > 0) {
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage(CommandMessages.info("Everyone has been given §b" + amount + "§7 §b" + displayName + "§7 " + (amount > 1 ? "keys." : "key.")))
            );
            return;
        }
        sender.sendMessage(CommandMessages.error("Failed to deliver the requested keys to any online players."));
    }

    private void grantAll(net.minestom.server.command.CommandSender sender, String crateId, String amountText) {
        if (amountText == null || !amountText.chars().allMatch(Character::isDigit)) {
            sender.sendMessage(CommandMessages.error("Please enter a valid key count."));
            return;
        }
        grantAll(sender, crateId, Integer.parseInt(amountText));
    }
}
