package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class MineCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final SpawnService spawnService;

    public MineCommand(PermissionService permissionService, PlayerProfileService profileService, MineService mineService, SpawnService spawnService) {
        super("mine", permissionService);
        this.profileService = profileService;
        this.mineService = mineService;
        this.spawnService = spawnService;

        var mineArgument = ArgumentType.Word("mine");

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            MineDefinition mine = mineService.find(profile.getMineRank()).orElse(null);
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("The selected mine could not be found."));
                return;
            }

            if (!hasAccess(profile, mine)) {
                sender.sendMessage(CommandMessages.error("You don't have access to this mine."));
                return;
            }

            spawnService.teleportWithinSpawnInstance(player, mineSpawn(mine));
            sender.sendMessage(CommandMessages.success("You have been teleported to mine §b" + mine.getName() + "§r§7."));
        });

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            String requested = context.get(mineArgument);
            MineDefinition mine = mineService.findByName(requested);
            if (mine == null) {
                sender.sendMessage(CommandMessages.error("The selected mine could not be found."));
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            if (!hasAccess(profile, mine)) {
                sender.sendMessage(CommandMessages.error("You don't have access to this mine."));
                return;
            }

            spawnService.teleportWithinSpawnInstance(player, mineSpawn(mine));
            sender.sendMessage(CommandMessages.success("You have been teleported to mine §b" + mine.getName() + "§r§7."));
        }, mineArgument);
    }

    private static boolean hasAccess(UserProfile profile, MineDefinition mine) {
        if (AdminModeService.isEnabled(profile)) {
            return true;
        }
        return !mine.isDisabled() && profile.getMineRank() >= mine.getId();
    }

    private Pos mineSpawn(MineDefinition mine) {
        double x = mine.effectiveSpawnX() + 0.5d;
        double z = mine.effectiveSpawnZ() + 0.5d;
        double y = mine.effectiveSpawnY();
        return new Pos(x, y, z);
    }

    @Override
    public String permission() {
        return "fallnight.command.mine";
    }

    @Override
    public String summary() {
        return "teleport to a mine";
    }

    @Override
    public String usage() {
        return "/mine [mine]";
    }
}
