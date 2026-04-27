package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class KillTopMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LeaderboardService leaderboardService;
    private final PagedTextMenuService pagedTextMenuService;

    public KillTopMenuService(LeaderboardService leaderboardService, PagedTextMenuService pagedTextMenuService) {
        this.leaderboardService = leaderboardService;
        this.pagedTextMenuService = pagedTextMenuService;
    }

    public void open(Player player) {
        leaderboardService.regenerateAll();
        List<UserProfile> top = leaderboardService.top(LeaderboardService.Type.KILLS);

        if (top.isEmpty()) {
            player.sendMessage(LEGACY.deserialize("§r§c§l> §r§7The leaderboards are still regenerating, please try again later!"));
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("§r§fA list of the players who currently have the most kills.");
        lines.add("");

        for (int i = 0; i < top.size(); i++) {
            UserProfile user = top.get(i);
            lines.add(" §b" + (i + 1) + "§r§8>§r§7 " + user.getUsername() + "§r§8 [§b" + user.getKills() + " kills§8]§r");
        }

        pagedTextMenuService.open(player, "§bTop: kills", lines);
    }
}
