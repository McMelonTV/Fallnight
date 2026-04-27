package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.PlayerProfileService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class NewsCommand extends FallnightCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final InfoPagesService infoPagesService;
    private final PlayerProfileService profileService;

    public NewsCommand(PermissionService permissionService, InfoPagesService infoPagesService, PlayerProfileService profileService) {
        super("news", permissionService, "patchnotes");
        this.infoPagesService = infoPagesService;
        this.profileService = profileService;

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LEGACY.deserialize("§c§l> §r§7Sender needs to be a player."));
                return;
            }
            String releaseVersion = infoPagesService.newsReleaseVersion();
            var profile = profileService.getOrCreate(player);
            profile.getExtraData().put("lastPatchNotes", releaseVersion);
            profile.getExtraData().put("lastPatchNotesVersion", releaseVersion);
            profileService.save(profile);
            player.openBook(book("§bPatch notes - " + releaseVersion, this.infoPagesService.newsPage()));
        });
    }

    private static Book book(String title, List<String> lines) {
        List<Component> pages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (count == 8 && builder.length() > 0) {
                pages.add(LEGACY.deserialize(builder.toString()));
                builder.setLength(0);
                count = 0;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(line);
            count++;
        }
        if (builder.length() > 0) {
            pages.add(LEGACY.deserialize(builder.toString()));
        }
        if (pages.isEmpty()) {
            pages.add(Component.empty());
        }
        return Book.book(LEGACY.deserialize(title), LEGACY.deserialize("§bFallnight§r"), pages);
    }

    @Override
    public String permission() {
        return "fallnight.command.news";
    }

    @Override
    public String summary() {
        return "view the latest patch notes";
    }

    @Override
    public String usage() {
        return "/news";
    }
}
