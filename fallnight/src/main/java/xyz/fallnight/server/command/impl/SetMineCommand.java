package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class SetMineCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final AchievementService achievementService;

    public SetMineCommand(PermissionService permissionService, PlayerProfileService profileService, MineService mineService) {
        super("setmine", permissionService);
        this.profileService = profileService;
        this.mineService = mineService;
        this.achievementService = new AchievementService(profileService);

        var playerArgument = ArgumentType.Word("player");
        var mineArgument = ArgumentType.Word("mine");

        setDefaultExecutor((sender, context) -> sendUsage(sender));
        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            String mineName = context.get(mineArgument);
            MineDefinition mine = mineService.findByName(mineName);
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("Mine '" + mineName + "' does not exist."));
                return;
            }

            UserProfile profile = profileService.getOrCreateByUsername(targetName);
            profile.setMineRank(mine.getId());
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            achievementService.onMineRank(target, profile);
            profileService.save(profile);

            sender.sendMessage(CommandMessages.success("Assigned mine " + mine.getName() + " to " + profile.getUsername() + "."));
            if (target != null) {
                target.sendMessage(CommandMessages.info("Your mine is now " + mine.getName() + "."));
            }
        }, playerArgument, mineArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.setmine";
    }

    @Override
    public String summary() {
        return "set someones mine";
    }

    @Override
    public String usage() {
        return "/setrank <player> <mine>";
    }
}
