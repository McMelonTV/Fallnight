package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class GlobalMuteCommand extends FallnightCommand {
    private final ModerationSanctionsService sanctionsService;

    public GlobalMuteCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService) {
        super("globalmute", permissionService);
        this.sanctionsService = sanctionsService;

        var modeArgument = ArgumentType.Word("mode");

        setDefaultExecutor((sender, context) -> {
            boolean enabled = sanctionsService.toggleGlobalMute();
            net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                    (enabled ? "\n".repeat(200) : "") + "§r§8-----------------------------\n§r§7 Global mute has been turned " + (enabled ? "on" : "off") + ".\n§r§8-----------------------------"
                ))
            );
        });

        addSyntax((sender, context) -> {
            String mode = context.get(modeArgument);
            if (mode.equalsIgnoreCase("toggle")) {
                boolean enabled = sanctionsService.toggleGlobalMute();
                net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        (enabled ? "\n".repeat(200) : "") + "§r§8-----------------------------\n§r§7 Global mute has been turned " + (enabled ? "on" : "off") + ".\n§r§8-----------------------------"
                    ))
                );
                return;
            }

            if (mode.equalsIgnoreCase("on")) {
                sanctionsService.setGlobalMute(true);
                net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        "\n".repeat(200) + "§r§8-----------------------------\n§r§7 Global mute has been turned on.\n§r§8-----------------------------"
                    ))
                );
                return;
            }

            if (mode.equalsIgnoreCase("off")) {
                sanctionsService.setGlobalMute(false);
                net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player ->
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        "§r§8-----------------------------\n§r§7 Global mute has been turned off.\n§r§8-----------------------------"
                    ))
                );
                return;
            }

            sender.sendMessage(CommandMessages.error("Invalid mode. Use on, off, or toggle."));
        }, modeArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.globalmute";
    }

    @Override
    public String summary() {
        return "toggle global mute";
    }

    @Override
    public String usage() {
        return "/globalmute [on|off|toggle]";
    }
}
