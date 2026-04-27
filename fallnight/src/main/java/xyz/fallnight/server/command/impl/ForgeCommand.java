package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.ForgeMenuService;
import xyz.fallnight.server.service.ForgeService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;

public final class ForgeCommand extends FallnightCommand {
    private final ForgeService forgeService;
    private final PlayerProfileService profileService;
    private final ForgeMenuService forgeMenuService;

    public ForgeCommand(PermissionService permissionService, PlayerProfileService profileService, ForgeMenuService forgeMenuService) {
        super("forge", permissionService);
        this.profileService = profileService;
        this.forgeService = new ForgeService(new LegacyCustomItemService(), profileService);
        this.forgeMenuService = forgeMenuService;

        var listLiteral = ArgumentType.Literal("list");
        var craftLiteral = ArgumentType.Literal("craft");
        var repairLiteral = ArgumentType.Literal("repair");
        var enchantLiteral = ArgumentType.Literal("enchant");
        var categoryArg = ArgumentType.Word("category");
        var recipeArg = ArgumentType.Word("recipe");
        var modeArg = ArgumentType.Word("mode");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                forgeMenuService.open(player);
                return;
            }
            sender.sendMessage(CommandMessages.info("Forge categories:"));
            forgeService.categories().forEach(category ->
                sender.sendMessage(CommandMessages.info("- " + category.displayName() + " (" + category.key() + ")"))
            );
            sender.sendMessage(CommandMessages.info("Use /forge list <category>, /forge craft <recipe>, /forge enchant <normal|high-end>, or /forge repair."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.forge";
    }

    @Override
    public String summary() {
        return "open the forge";
    }

    @Override
    public String usage() {
        return "/forge";
    }
}
