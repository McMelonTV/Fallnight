package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.EnchantmentForgeMenuService;
import xyz.fallnight.server.service.ForgeService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

public final class EnchantmentForgeCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final ForgeService forgeService;
    private final EnchantmentForgeMenuService menuService;

    public EnchantmentForgeCommand(
        PermissionService permissionService,
        PlayerProfileService profileService,
        EnchantmentForgeMenuService menuService
    ) {
        super("enchantmentforge", permissionService, "eforge", "ceshop", "enchantshop");
        this.profileService = profileService;
        this.forgeService = new ForgeService(new LegacyCustomItemService(), profileService);
        this.menuService = menuService;

        setDefaultExecutor((sender, context) -> open(sender));
    }

    private void open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
            return;
        }
        UserProfile profile = profileService.getOrCreate(player);
        menuService.open(player, profile, forgeService);
    }

    @Override
    public String permission() {
        return "fallnight.command.enchantmentforge";
    }

    @Override
    public String summary() {
        return "open the enchantmentforge";
    }

    @Override
    public String usage() {
        return "/enchantmentforge";
    }
}
