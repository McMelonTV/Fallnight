package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class SetPrestigeCommand extends FallnightCommand {
    private final PlayerProfileService profileService;

    public SetPrestigeCommand(PermissionService permissionService, PlayerProfileService profileService) {
        super("setprestige", permissionService);
        this.profileService = profileService;

        var playerArgument = ArgumentType.Word("player");
        var prestigeArgument = ArgumentType.Integer("prestige").min(0);

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);

            int prestige = context.get(prestigeArgument);
            UserProfile profile = profileService.getOrCreateByUsername(targetName);
            int appliedPrestige = Math.max(1, prestige);
            profile.setPrestige(appliedPrestige);
            profileService.save(profile);

            sender.sendMessage(CommandMessages.success("Set " + profile.getUsername() + " prestige to " + appliedPrestige + "."));
            if (target != null) {
                target.sendMessage(CommandMessages.info("Your prestige was set to " + appliedPrestige + "."));
            }
        }, playerArgument, prestigeArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.setprestige";
    }

    @Override
    public String summary() {
        return "set someones prestige";
    }

    @Override
    public String usage() {
        return "/setprestige <player> <prestige>";
    }
}
