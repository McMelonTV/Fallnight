package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.service.ModerationSanctionsService;
import java.time.Instant;
import java.util.List;

public final class BanlistCommand extends FallnightCommand {
    private final ModerationSanctionsService sanctionsService;

    public BanlistCommand(PermissionService permissionService, ModerationSanctionsService sanctionsService) {
        super("banlist", permissionService);
        this.sanctionsService = sanctionsService;

        setDefaultExecutor((sender, context) -> {
            List<PlayerBan> bans = sanctionsService.listActiveBans();
            StringBuilder message = new StringBuilder("§8§l<--§bFN§8-->§r\n§b Fallnight§r§7 banlist§r");
            Instant now = Instant.now();
            for (PlayerBan ban : bans) {
                String remaining = ban.isTemporary() && ban.remaining(now) != null && !ban.remaining(now).isNegative()
                    ? ModerationCommandSupport.renderDuration(ban.remaining(now))
                    : "permanent";
                message.append("\n§b > §r§b").append(ban.username()).append(": §r§7").append(remaining);
            }
            message.append("\n§r§8§l<--++-->⛏");
            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message.toString()));
        });
    }

    @Override
    public String permission() {
        return "fallnight.command.banlist";
    }

    @Override
    public String summary() {
        return "view all banned players";
    }

    @Override
    public String usage() {
        return "/banlist";
    }
}
