package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.domain.vault.PlayerVault;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.VaultService;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class VaultCommand extends FallnightCommand {
    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final VaultService vaultService;

    public VaultCommand(PermissionService permissionService, PlayerProfileService profileService, VaultService vaultService) {
        super("vault", permissionService, "pv");
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.vaultService = vaultService;

        var pageArgument = ArgumentType.Integer("page");
        var targetArgument = ArgumentType.Word("player");

        setDefaultExecutor((sender, context) -> openVault(sender, 1));
        addSyntax((sender, context) -> openVault(sender, context.get(pageArgument)), pageArgument);
        addSyntax((sender, context) -> openVault(sender, 1, context.get(targetArgument)), targetArgument);
        addSyntax((sender, context) -> openVault(sender, context.get(pageArgument), context.get(targetArgument)), pageArgument, targetArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.vault";
    }

    @Override
    public String summary() {
        return "open your vault";
    }

    @Override
    public String usage() {
        return "/vault [number]";
    }

    private void openVault(net.minestom.server.command.CommandSender sender, int requestedPage) {
        openVault(sender, requestedPage, null);
    }

    private void openVault(net.minestom.server.command.CommandSender sender, int requestedPage, String targetName) {
        if (!ensurePlayer(sender)) {
            return;
        }

        Player player = (Player) sender;
        if (targetName != null && !permissionService.hasPermission(sender, "fallnight.command.vault.others")) {
            sender.sendMessage(CommandMessages.error("You do not have permission to view other players' vaults."));
            return;
        }
        UserProfile profile = targetName == null ? profileService.getOrCreate(player) : profileService.findByUsername(targetName).orElse(null);
        if (profile == null) {
            sender.sendMessage(CommandMessages.error("That player was not found."));
            return;
        }
        PlayerVault vault = vaultService.getOrCreate(profile.getUsername());

        int allowedPages = vaultService.resolveAccessiblePageCount(profile, vault);
        if (requestedPage < 1 || requestedPage > allowedPages) {
            sender.sendMessage(CommandMessages.error("You do not have this amount of vault pages."));
            return;
        }

        if (!vaultService.openVaultPage(player, vault, requestedPage, allowedPages)) {
            sender.sendMessage(CommandMessages.error("Someone is already looking in that vault."));
            return;
        }
        if (targetName != null) {
            sender.sendMessage(CommandMessages.info("Opening §b" + profile.getUsername() + "§r§7's vault page §b" + requestedPage + "§r§7."));
        } else if (requestedPage > 1) {
            sender.sendMessage(CommandMessages.info("Opening vault page §b" + requestedPage + "§r§7."));
        } else {
            sender.sendMessage(CommandMessages.info("Opening your vault."));
        }
    }
}
