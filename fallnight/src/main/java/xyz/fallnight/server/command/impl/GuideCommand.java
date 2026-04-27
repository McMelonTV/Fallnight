package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class GuideCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final LegacyCustomItemService customItemService;
    private final InfoPagesService infoPagesService;

    public GuideCommand(PermissionService permissionService, InfoPagesService infoPagesService) {
        super("guide", permissionService, "guidebook");
        this.customItemService = new LegacyCustomItemService();
        this.infoPagesService = infoPagesService;

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            player.getInventory().addItemStack(customItemService.guideBook(this.infoPagesService.guidePage()));
            sender.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You have been given a guide book."));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.guide";
    }

    @Override
    public String summary() {
        return "get the guide book";
    }

    @Override
    public String usage() {
        return "/guide";
    }
}
