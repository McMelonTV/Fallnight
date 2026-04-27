package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.CrateMenuService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class CratesCommand extends FallnightCommand {
    private static final Pos CRATE_ROOM = new Pos(-55.5, 50, 0.5);

    private final CrateService crateService;
    private final PlayerProfileService profileService;
    private final CrateMenuService crateMenuService;
    private final DefaultWorldService defaultWorldService;

    public CratesCommand(PermissionService permissionService, CrateService crateService, PlayerProfileService profileService, DefaultWorldService defaultWorldService) {
        super("crates", permissionService);
        this.crateService = crateService;
        this.profileService = profileService;
        this.defaultWorldService = defaultWorldService;
        this.crateMenuService = new CrateMenuService(crateService, profileService, new PagedTextMenuService());

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            var targetInstance = defaultWorldService.currentWorld().instance();
            if (player.getInstance() == targetInstance) {
                player.teleport(CRATE_ROOM);
            } else {
                player.setInstance(targetInstance, CRATE_ROOM).join();
            }
            sender.sendMessage(CommandMessages.info("Teleported to the crate room."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.crates";
    }

    @Override
    public String summary() {
        return "teleport to the crate room";
    }

    @Override
    public String usage() {
        return "/crates";
    }

}
