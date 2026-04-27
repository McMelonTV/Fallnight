package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.EnchantmentListMenuService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import java.util.Comparator;

public final class EnchantmentListCommand extends FallnightCommand {
    private final EnchantmentListMenuService menuService;

    public EnchantmentListCommand(PermissionService permissionService, EnchantmentListMenuService menuService) {
        super("enchantmentlist", permissionService, "celist", "customenchants");
        this.menuService = menuService;

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof net.minestom.server.entity.Player player) {
                menuService.open(player);
                return;
            }
            sender.sendMessage(CommandMessages.info("Available enchantments:"));
            FallnightCustomEnchantRegistry.all().stream()
                .sorted(Comparator.comparingInt(FallnightCustomEnchantRegistry.Definition::legacyId))
                .forEach(def -> sender.sendMessage(CommandMessages.info("- " + def.id() + " (legacy " + def.legacyId() + ")")));
            EnchantCommandSupport.knownIds().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(id -> sender.sendMessage(CommandMessages.info("- " + id)));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.enchantmentlist";
    }

    @Override
    public String summary() {
        return "see all the custom enchants";
    }

    @Override
    public String usage() {
        return "/enchantmentlist";
    }
}
