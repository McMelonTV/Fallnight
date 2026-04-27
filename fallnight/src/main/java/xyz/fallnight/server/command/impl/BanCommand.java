package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class BanCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ModerationSanctionsService sanctionsService;
    private final PlayerProfileService profileService;

    public BanCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService, PlayerProfileService profileService) {
        super("ban", permissionService);
        this.sanctionsService = sanctionsService;
        this.profileService = profileService;

        var playerArgument = ArgumentType.Word("player");
        var reasonArgument = ArgumentType.StringArray("reason");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7Please enter a player to ban.")));
        addSyntax((sender, context) -> ban(sender, context.get(playerArgument), new String[0]), playerArgument);
        addSyntax((sender, context) -> ban(sender, context.get(playerArgument), context.get(reasonArgument)), playerArgument, reasonArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.ban";
    }

    @Override
    public String summary() {
        return "ban a player from the server";
    }

    @Override
    public String usage() {
        return "/ban <player> [reason]";
    }

    private void ban(net.minestom.server.command.CommandSender sender, String targetName, String[] reasonParts) {
        boolean silent = false;
        boolean superBan = false;
        String[] args = new String[reasonParts.length + 1];
        args[0] = targetName;
        System.arraycopy(reasonParts, 0, args, 1, reasonParts.length);
        int offset = 0;
        if (offset < args.length && args[offset].equalsIgnoreCase("silent")) {
            silent = true;
            offset++;
        }
        if (offset < args.length && args[offset].equalsIgnoreCase("super")) {
            superBan = true;
            offset++;
        }
        if (offset >= args.length) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7Please enter a player to ban."));
            return;
        }
        String actualTarget = args[offset++];
        String[] tail = java.util.Arrays.copyOfRange(args, offset, args.length);
        String reason = tail.length == 0 ? "you have been banned" : String.join(" ", tail).trim();
        if (profileService.findByUsername(actualTarget).isEmpty() && ModerationCommandSupport.findOnlinePlayerIgnoreCase(actualTarget) == null) {
            sender.sendMessage(LEGACY.deserialize("§r§c§l>§r §7That player has never connected."));
            return;
        }
        PlayerBan ban = sanctionsService.ban(actualTarget, ModerationCommandSupport.actorName(sender), reason, superBan);
        if (silent) {
            sender.sendMessage(CommandMessages.success("You permanently banned §b" + ban.username() + "§r§7 with reason: §b" + reason + "§r§7."));
        } else {
            String actor = superBan && !(sender instanceof Player) ? "ur mom" : ModerationCommandSupport.actorName(sender);
            String line = superBan
                ? "§r§8[§bFN§8] §r§b" + ban.username() + "§r§7 has been permanently §4S§cU§bP§eE§aR§2B§3A§bN§9N§dE§5D §r§7by §b" + actor + "§r§7 with reason: §b" + reason + "§r§7."
                : "§r§8[§bFN§8] §r§b" + ban.username() + "§r§7 has been permanently banned by §b" + actor + "§r§7 with reason: §b" + reason + "§r§7.";
            net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                player.sendMessage(LEGACY.deserialize(line))
            );
        }

        Player onlineTarget = ModerationCommandSupport.findOnlinePlayerIgnoreCase(ban.username());
        if (onlineTarget != null) {
            onlineTarget.kick(LEGACY.deserialize("§r§8[§bFN§8]\n§r§7You have been banned from the server by §b" + ban.actor() + "§r§7!\n§r§7Reason: §b" + ban.reason() + "\n§r§7Expiration date: §bnever"));
        }
    }
}
