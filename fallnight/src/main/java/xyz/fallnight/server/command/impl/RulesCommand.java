package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.BookMenuService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class RulesCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final InfoPagesService infoPagesService;
    private final PlayerProfileService profileService;
    private final BookMenuService bookMenuService = new BookMenuService();

    public RulesCommand(PermissionService permissionService, InfoPagesService infoPagesService, PlayerProfileService profileService) {
        super("rules", permissionService);
        this.infoPagesService = infoPagesService;
        this.profileService = profileService;

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof net.minestom.server.entity.Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            var profile = profileService.getOrCreate(player);
            profile.getExtraData().put("seenRules", true);
            profileService.save(profile);
            bookMenuService.open(player, "§bServer rules", this.infoPagesService.rulesPage());
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.rules";
    }

    @Override
    public String summary() {
        return "view the rules";
    }

    @Override
    public String usage() {
        return "/rules";
    }
}
