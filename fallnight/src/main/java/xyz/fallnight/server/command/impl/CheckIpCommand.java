package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class CheckIpCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;

    public CheckIpCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("checkip", permissionService, "iplist");
        this.profileService = profileService;

        var playerArg = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Please enter a target to check.")));
        addSyntax((sender, context) -> {
            String targetName = context.get(playerArg);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            var profile = profileService.findByUsername(targetName).orElse(null);
            if (profile == null) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Player with name §c" + targetName + "§r§7 was never connected."));
                return;
            }
            List<String> ips = new ArrayList<>();
            Object raw = profile.getExtraData().get("iplist");
            if (raw instanceof Iterable<?> iterable) {
                iterable.forEach(item -> ips.add(String.valueOf(item)));
            }
            if (ips.isEmpty()) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Player §c" + profile.getUsername() + "§r§7 has no IPs on record."));
                return;
            }
            StringBuilder string = new StringBuilder("§8§l<--§bFN§8--> \n§r§7§7 §b" + profile.getUsername() + "§r§7's IP list");
            if (target != null && target.getPlayerConnection().getRemoteAddress() instanceof InetSocketAddress address && address.getAddress() != null) {
                string.append("\n§r §8§l> §r§7Current ip: §b").append(address.getAddress().getHostAddress());
            }
            for (String ip : ips) {
                string.append("\n§r §8§l> §r§7").append(ip);
            }
            sender.sendMessage(LEGACY.deserialize(string + "\n§r§8§l<--++-->⛏"));
        }, playerArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.checkip";
    }

    @Override
    public String summary() {
        return "check someone's IP's";
    }

    @Override
    public String usage() {
        return "/checkip <player>";
    }
}
