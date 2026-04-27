package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.DirectMessageService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.LegacyTextFormatter;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class TellCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final DirectMessageService directMessageService;
    private final PlayerProfileService profileService;
    private final ModerationSanctionsService sanctionsService;

    public TellCommand(
        PermissionService permissionService,
        DirectMessageService directMessageService,
        PlayerProfileService profileService,
        ModerationSanctionsService sanctionsService
    ) {
        super("tell", permissionService, "w", "msg");
        this.directMessageService = directMessageService;
        this.profileService = profileService;
        this.sanctionsService = sanctionsService;

        var playerArgument = ArgumentType.Word("player");
        var messageArgument = ArgumentType.StringArray("message");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player to send your message to.")));
        addSyntax((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a message to send.")), playerArgument);
        addSyntax((sender, context) -> {
            Player source = sender instanceof Player player ? player : null;
            if (source != null && sanctionsService.isMuted(source.getUsername())) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You are muted!"));
                return;
            }
            String targetName = context.get(playerArgument);
            Player target = ModerationCommandSupport.findOnlinePlayerByPrefixIgnoreCase(targetName);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Player §b" + targetName + "§r§7 was not found."));
                return;
            }

            if (source != null && source.getUuid().equals(target.getUuid())) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You can't send a message to yourself!"));
                return;
            }

            var targetProfile = profileService.getOrCreate(target);
            if (isAfk(targetProfile)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7The target player is currently AFK."));
            }
            if (source != null) {
                var sourceProfile = profileService.getOrCreate(source);
                if (!AdminModeService.isEnabled(sourceProfile) && hasBlocked(targetProfile, source.getUsername())) {
                    sender.sendMessage(LEGACY.deserialize("§c§l> §r§7You are unable to send messages to this player."));
                    return;
                }
            }

            String message = String.join(" ", context.get(messageArgument)).trim();
            if (message.isBlank()) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a message to send."));
                return;
            }

            String sourceName = source == null ? "CONSOLE" : displayName(source);
            sender.sendMessage(LEGACY.deserialize("§8[§r§bYou§7->§b" + displayName(target) + "§r§8]§r §7" + message));
            target.sendMessage(LEGACY.deserialize("§8[§r§b" + sourceName + "§7->§bYou§r§8]§r §7" + message));
            if (source != null) {
                directMessageService.recordConversation(source.getUuid(), target.getUuid());
            }
        }, playerArgument, messageArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.tell";
    }

    @Override
    public String summary() {
        return "send a private message";
    }

    @Override
    public String usage() {
        return "/tell <player> <message>";
    }

    private static boolean hasBlocked(xyz.fallnight.server.domain.user.UserProfile profile, String otherName) {
        Object raw = profile.getExtraData().get("blockedPlayers");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        String normalized = otherName.toLowerCase(Locale.ROOT);
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            if (String.valueOf(entry).trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAfk(xyz.fallnight.server.domain.user.UserProfile profile) {
        Object value = profile.getExtraData().get("afk");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return false;
    }

    private String displayName(Player player) {
        Object nickname = profileService.getOrCreate(player).getExtraData().get("nickname");
        if (nickname instanceof String value && !value.isBlank()) {
            return LegacyTextFormatter.normalize(value);
        }
        return player.getUsername();
    }
}
