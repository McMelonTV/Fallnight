package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class AdminCommand extends FallnightCommand {
    private final PlayerProfileService profileService;

    public AdminCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("admin", permissionService, "adminmode");
        this.profileService = profileService;

        var targetArg = ArgumentType.Word("target");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            toggleFor(sender, player, player);
        });

        addSyntax((sender, context) -> {
            Player target = findOnlinePlayerIgnoreCase(context.get(targetArg));
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player was not found."));
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            toggleFor(sender, player, target);
        }, targetArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.admin";
    }

    @Override
    public String summary() {
        return "toggle admin mode";
    }

    @Override
    public String usage() {
        return "/admin";
    }

    private void toggleFor(net.minestom.server.command.CommandSender sender, Player actor, Player target) {
        UserProfile profile = profileService.getOrCreate(target);
        profile.getExtraData().remove("adminMode");
        boolean enabled = AdminModeService.toggle(profile);
        if (!actor.getUuid().equals(target.getUuid())) {
            sender.sendMessage(CommandMessages.success("You have turned §b" + (enabled ? "on" : "off") + "§r§7 Admin mode for §b" + target.getUsername() + "§r§7."));
            target.sendMessage(CommandMessages.success("Admin mode is now turned §b" + (enabled ? "on" : "off") + "§r§7."));
        } else {
            sender.sendMessage(CommandMessages.success("You have turned §b" + (enabled ? "on" : "off") + "§r§7 Admin mode."));
        }
    }

    private static Player findOnlinePlayerIgnoreCase(String username) {
        Player exact = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username);
        if (exact != null) {
            return exact;
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }
}
