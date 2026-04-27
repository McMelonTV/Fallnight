package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

public final class NearCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat DISTANCE_FORMAT = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private final PlayerProfileService profileService;

    public NearCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("near", permissionService);
        this.profileService = profileService;

        setDefaultExecutor((sender, context) -> execute(sender));
    }

    private void execute(net.minestom.server.command.CommandSender sender) {
        if (!ensurePlayer(sender)) {
            return;
        }

        Player source = (Player) sender;
        if (source.getInstance() == null) {
            sender.sendMessage(CommandMessages.error("You are not in an instance."));
            return;
        }

        List<NearbyPlayer> nearby = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
            .filter(player -> !player.getUuid().equals(source.getUuid()))
            .filter(player -> player.getInstance() == source.getInstance())
            .filter(player -> !readBoolean(profileService.getOrCreate(player), "vanish"))
            .map(player -> new NearbyPlayer(player.getUsername(), distanceBetween(source, player)))
            .sorted(Comparator.comparingDouble(NearbyPlayer::distance))
            .toList();

        StringBuilder message = new StringBuilder("§8§l<--§bFN§8--> \n§r§7 Nearby players in world §r§b")
            .append(source.getInstance().getDimensionType().name());
        for (NearbyPlayer nearbyPlayer : nearby) {
            message.append("\n§b")
                .append(nearbyPlayer.username())
                .append("§r§8 (")
                .append(DISTANCE_FORMAT.format(nearbyPlayer.distance()))
                .append("m)");
        }
        message.append("\n§r§8§l<--++-->⛏");
        sender.sendMessage(LEGACY.deserialize(message.toString()));
    }

    private static double distanceBetween(Player first, Player second) {
        double dx = first.getPosition().x() - second.getPosition().x();
        double dy = first.getPosition().y() - second.getPosition().y();
        double dz = first.getPosition().z() - second.getPosition().z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String permission() {
        return "fallnight.command.near";
    }

    @Override
    public String summary() {
        return "check nearby players";
    }

    @Override
    public String usage() {
        return "/near";
    }

    private record NearbyPlayer(String username, double distance) {
    }

    private static boolean readBoolean(xyz.fallnight.server.domain.user.UserProfile profile, String key) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return false;
    }
}
