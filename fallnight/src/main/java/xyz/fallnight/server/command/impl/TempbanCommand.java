package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.util.ModerationDurationParser;
import java.time.Duration;
import java.util.Optional;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class TempbanCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ModerationSanctionsService sanctionsService;

    public TempbanCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService) {
        super("tempban", permissionService);
        this.sanctionsService = sanctionsService;

        var playerArgument = ArgumentType.Word("player");
        var tailArgument = ArgumentType.StringArray("args");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7Please enter a player to ban.")));
        addSyntax((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7Please enter a ban duration.")), playerArgument);
        addSyntax((sender, context) -> tempban(sender, context.get(playerArgument), context.get(tailArgument)), playerArgument, tailArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.tempban";
    }

    @Override
    public String summary() {
        return "temporarily ban a player from the server";
    }

    @Override
    public String usage() {
        return "/tempban <player> <duration> [reason]";
    }

    private void tempban(net.minestom.server.command.CommandSender sender, String targetName, String[] args) {
        boolean silent = false;
        boolean superBan = false;
        String[] allArgs = new String[args.length + 1];
        allArgs[0] = targetName;
        System.arraycopy(args, 0, allArgs, 1, args.length);
        int offset = 0;
        if (offset < allArgs.length && allArgs[offset].equalsIgnoreCase("silent")) {
            silent = true;
            offset++;
        }
        if (offset < allArgs.length && allArgs[offset].equalsIgnoreCase("super")) {
            superBan = true;
            offset++;
        }
        if (offset >= allArgs.length) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7Please enter a player to ban."));
            return;
        }
        String actualTarget = allArgs[offset++];
        String[] actualArgs = java.util.Arrays.copyOfRange(allArgs, offset, allArgs.length);

        Optional<ModerationDurationParser.ParsedDuration> parsedDuration = ModerationDurationParser.parseLeadingTokens(actualArgs);
        if (parsedDuration.isEmpty()) {
            sender.sendMessage(CommandMessages.error("Please enter a correct time string."));
            return;
        }

        Duration duration = parsedDuration.get().duration();
        String[] actualReasonParts = java.util.Arrays.copyOfRange(actualArgs, parsedDuration.get().consumedTokens(), actualArgs.length);
        String reason = actualReasonParts.length == 0 ? "you have been banned" : String.join(" ", actualReasonParts).trim();
        PlayerBan ban = sanctionsService.tempBan(actualTarget, ModerationCommandSupport.actorName(sender), reason, duration, superBan);
        if (silent) {
            sender.sendMessage(CommandMessages.success("You banned §b" + ban.username() + "§r§7 for §b" + ModerationCommandSupport.renderDuration(duration) + "§r§7 with reason: §b" + reason + "§r§7."));
        } else {
            String actor = superBan && !(sender instanceof Player) ? "AEGIS v1" : ModerationCommandSupport.actorName(sender);
            String line = superBan
                ? "§r§8[§bAEGIS§8] §r§b" + ban.username() + "§r§7 has been §4S§cU§bP§eE§aR§2B§3A§bN§9N§dE§5D §r§7by §b" + actor + "§r§7 for §b" + ModerationCommandSupport.renderDuration(duration) + "§r§7 with reason: §b" + reason + "§r§7."
                : "§r§8[§bFN§8] §r§b" + ban.username() + "§r§7 has been banned by §b" + actor + "§r§7 for §b" + ModerationCommandSupport.renderDuration(duration) + "§r§7 with reason: §b" + reason + "§r§7.";
            net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                player.sendMessage(LEGACY.deserialize(line))
            );
        }

        Player onlineTarget = ModerationCommandSupport.findOnlinePlayerIgnoreCase(ban.username());
        if (onlineTarget != null) {
            String expires = ban.expiresAt() == null ? "never" : java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' hh:mm:ss").withZone(java.time.ZoneId.systemDefault()).format(ban.expiresAt());
            onlineTarget.kick(LEGACY.deserialize("§r§8[§bFN§8]\n§r§7You have been banned from the server by §b" + ban.actor() + "§r§7!\n§r§7Reason: §b" + ban.reason() + "\n§r§7Expiration date: §b" + expires));
        }
    }
}
