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
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class ReplyCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final DirectMessageService directMessageService;
    private final PlayerProfileService profileService;
    private final ModerationSanctionsService sanctionsService;

    public ReplyCommand(
        PermissionService permissionService,
        DirectMessageService directMessageService,
        PlayerProfileService profileService,
        ModerationSanctionsService sanctionsService
    ) {
        super("reply", permissionService, "r");
        this.directMessageService = directMessageService;
        this.profileService = profileService;
        this.sanctionsService = sanctionsService;

        var messageArgument = ArgumentType.StringArray("message");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a message to send.")));
        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player source = (Player) sender;
            if (sanctionsService.isMuted(source.getUsername())) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You are muted!"));
                return;
            }
            UUID targetId = directMessageService.lastPartner(source.getUuid());
            if (targetId == null) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetId);
            if (target == null) {
                directMessageService.clearPlayer(source.getUuid());
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7The target player was not found."));
                return;
            }

            var sourceProfile = profileService.getOrCreate(source);
            var targetProfile = profileService.getOrCreate(target);
            if (isAfk(targetProfile)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7The target player is currently AFK."));
            }
            if (!AdminModeService.isEnabled(sourceProfile) && hasBlocked(targetProfile, source.getUsername())) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7You are unable to send messages to this player."));
                return;
            }

            String message = String.join(" ", context.get(messageArgument)).trim();
            if (message.isBlank()) {
                sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a message to send."));
                return;
            }

            source.sendMessage(LEGACY.deserialize("§8[§r§bYou§7->§b" + displayName(target) + "§r§8]§r §7" + message));
            target.sendMessage(LEGACY.deserialize("§8[§r§b" + displayName(source) + "§7->§bYou§r§8]§r §7" + message));
            directMessageService.recordConversation(source.getUuid(), target.getUuid());
        }, messageArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.tell";
    }

    @Override
    public String summary() {
        return "reply to a private message";
    }

    @Override
    public String usage() {
        return "/r <message>";
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
