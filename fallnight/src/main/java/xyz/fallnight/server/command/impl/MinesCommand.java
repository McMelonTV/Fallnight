package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.MinesMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.entity.Player;

public final class MinesCommand extends FallnightCommand {
    private final MineService mineService;
    private final PlayerProfileService profileService;
    private final MinesMenuService minesMenuService;

    public MinesCommand(PermissionService permissionService, MineService mineService, PlayerProfileService profileService, MinesMenuService minesMenuService) {
        super("mines", permissionService);
        this.mineService = mineService;
        this.profileService = profileService;
        this.minesMenuService = minesMenuService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            minesMenuService.open((Player) sender);
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.mines";
    }

    @Override
    public String summary() {
        return "choose a mine";
    }

    @Override
    public String usage() {
        return "/mines";
    }
}
