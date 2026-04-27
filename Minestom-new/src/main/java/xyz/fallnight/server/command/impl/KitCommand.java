package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.kit.KitDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.KitService;
import xyz.fallnight.server.service.KitMenuService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.Locale;
import net.minestom.server.component.DataComponents;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class KitCommand extends FallnightCommand {
    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final KitService kitService;
    private final KitMenuService kitMenuService;
    private final LegacyCustomItemService customItemService;

    public KitCommand(PermissionService permissionService, PlayerProfileService profileService, KitService kitService, KitMenuService kitMenuService) {
        super("kit", permissionService);
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.kitService = kitService;
        this.kitMenuService = kitMenuService;
        this.customItemService = new LegacyCustomItemService();

        var kitArgument = ArgumentType.Word("id");

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            kitMenuService.open((Player) sender);
        });

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            String kitId = context.get(kitArgument);
            KitService.ClaimResult result = kitService.claimKit(player, profile, kitId, permissionService);
            switch (result.status()) {
                case SUCCESS -> sender.sendMessage(CommandMessages.success(
                    "Claimed /kit " + result.kit().id() + " and received " + rewardSummary(result.kit(), customItemService) + "."
                ));
                case INVALID_KIT -> sender.sendMessage(CommandMessages.error(
                    "Unknown kit '" + kitId + "'. Use /kit to see available kits."
                ));
                case ON_COOLDOWN -> sender.sendMessage(CommandMessages.error(
                    "Kit '" + result.kit().id() + "' is on cooldown for " + formatDuration(result.remainingCooldownSeconds()) + "."
                ));
                case NO_PERMISSION -> sender.sendMessage(CommandMessages.error(
                    "You don't have permission to claim that kit."
                ));
                case INVENTORY_FULL -> sender.sendMessage(CommandMessages.error(
                    "You don't have enough space in your inventory to claim this kit."
                ));
            }
        }, kitArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.kit";
    }

    @Override
    public String summary() {
        return "get a kit";
    }

    @Override
    public String usage() {
        return "/kit";
    }

    private void sendKitList(Player player, UserProfile profile) {
        player.sendMessage(CommandMessages.info("Available kits:"));
        for (KitDefinition kit : kitService.listKits()) {
            long remaining = kitService.remainingCooldownSeconds(profile, kit.id());
            String status = !permissionService.hasPermission(player, kit.permission())
                ? "locked"
                : remaining <= 0L ? "ready" : "cooldown " + formatDuration(remaining);
            player.sendMessage(CommandMessages.info(
                "- " + kit.id() + " | every " + formatDuration(kit.cooldownSeconds()) + " | " + status
            ));
        }
        player.sendMessage(CommandMessages.info("Use /kit <id> to claim a kit."));
    }

    private static String rewardSummary(KitDefinition kit, LegacyCustomItemService customItemService) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (xyz.fallnight.server.domain.kit.KitReward reward : kit.rewards()) {
            net.minestom.server.item.ItemStack item = customItemService.createFromCustomId(reward.customItemId())
                .orElse(net.minestom.server.item.ItemStack.AIR);
            if (index > 0) {
                builder.append(", ");
            }
            net.kyori.adventure.text.Component name = item.get(DataComponents.CUSTOM_NAME);
            builder.append(name == null
                ? reward.customItemId().toLowerCase(Locale.ROOT)
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name));
            index++;
            if (index >= 3 && kit.rewards().size() > 3) {
                builder.append(", ...");
                break;
            }
        }
        return builder.toString();
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remSeconds = seconds % 60L;

        if (hours > 0L) {
            if (minutes > 0L) {
                return hours + "h " + minutes + "m";
            }
            return hours + "h";
        }
        if (minutes > 0L) {
            if (remSeconds > 0L) {
                return minutes + "m " + remSeconds + "s";
            }
            return minutes + "m";
        }
        return remSeconds + "s";
    }
}
