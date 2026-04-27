package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.moderation.PlayerMute;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.ModerationDurationParser;
import java.time.Duration;
import java.util.Optional;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class MuteCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ModerationSanctionsService sanctionsService;
    private final PlayerProfileService profileService;

    public MuteCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService, PlayerProfileService profileService) {
        super("mute", permissionService);
        this.sanctionsService = sanctionsService;
        this.profileService = profileService;

        var playerArgument = ArgumentType.Word("player");
        var tailArgument = ArgumentType.StringArray("args");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7Please enter a player to mute.")));
        addSyntax((sender, context) -> mute(sender, context.get(playerArgument), new String[0]), playerArgument);
        addSyntax((sender, context) -> mute(sender, context.get(playerArgument), context.get(tailArgument)), playerArgument, tailArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.mute";
    }

    @Override
    public String summary() {
        return "mute someone";
    }

    @Override
    public String usage() {
        return "/mute <player> [time]";
    }

    private void mute(net.minestom.server.command.CommandSender sender, String targetName, String[] tail) {
        TargetResolution target = resolveTarget(targetName);
        if (target == null) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r§7 That player has never connected."));
            return;
        }

        if (sanctionsService.isMuted(target.username())) {
            sanctionsService.unmute(target.username());
            sender.sendMessage(LEGACY.deserialize("§b§l> §r§7You have unmuted §b" + target.username() + "§r§7."));
            if (target.onlinePlayer() != null && !sameActorAndTarget(sender, target.onlinePlayer())) {
                target.onlinePlayer().sendMessage(LEGACY.deserialize("§r§b§l> §r§7You have been unmuted."));
            }
            return;
        }

        if (tail.length == 0) {
            applyPermanentMute(sender, target);
            return;
        }

        Optional<ModerationDurationParser.ParsedDuration> parsed = ModerationDurationParser.parseLeadingTokens(tail);
        if (parsed.isPresent() && parsed.get().consumedTokens() == tail.length) {
            applyTemporaryMute(sender, target, parsed.get().duration());
            return;
        }

        sender.sendMessage(LEGACY.deserialize("§r§c§l> §r§7You have entered an incorrect time period."));
    }

    private void applyPermanentMute(net.minestom.server.command.CommandSender sender, TargetResolution target) {
        PlayerMute mute = sanctionsService.mute(target.username(), ModerationCommandSupport.actorName(sender), "No reason provided.");
        sender.sendMessage(LEGACY.deserialize("§b§l> §r§7You have muted §b" + mute.username() + "§r§7."));
        if (target.onlinePlayer() != null && !sameActorAndTarget(sender, target.onlinePlayer())) {
            target.onlinePlayer().sendMessage(LEGACY.deserialize("§b§l> §r§7You have been muted."));
        }
    }

    private void applyTemporaryMute(net.minestom.server.command.CommandSender sender, TargetResolution target, Duration duration) {
        PlayerMute mute = sanctionsService.tempMute(target.username(), ModerationCommandSupport.actorName(sender), "No reason provided.", duration);
        String rendered = ModerationCommandSupport.renderDuration(duration);
        String colored = rendered.replace(",", "§7,§b").replace("and", "§7and§b");
        sender.sendMessage(LEGACY.deserialize("§b§l> §r§7You have muted §b" + mute.username() + "§r§7 for §b" + colored + "§r§7."));
        if (target.onlinePlayer() != null && !sameActorAndTarget(sender, target.onlinePlayer())) {
            target.onlinePlayer().sendMessage(LEGACY.deserialize("§b§l> §r§7You have been muted for §b" + colored + "§r§7."));
        }
    }

    private TargetResolution resolveTarget(String targetName) {
        Player online = ModerationCommandSupport.findOnlinePlayerByPrefixIgnoreCase(targetName);
        if (online != null) {
            return new TargetResolution(online.getUsername(), online);
        }
        return profileService.findByUsername(targetName)
            .map(profile -> new TargetResolution(profile.getUsername(), ModerationCommandSupport.findOnlinePlayerIgnoreCase(profile.getUsername())))
            .orElse(null);
    }

    private static boolean sameActorAndTarget(net.minestom.server.command.CommandSender sender, Player target) {
        return sender instanceof Player player && player.getUuid().equals(target.getUuid());
    }

    private record TargetResolution(String username, Player onlinePlayer) {
    }
}
