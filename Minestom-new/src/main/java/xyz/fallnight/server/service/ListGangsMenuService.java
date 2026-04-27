package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.gang.Gang;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.entity.Player;

public final class ListGangsMenuService {
    private final GangService gangService;
    private final PagedTextMenuService pagedTextMenuService;

    public ListGangsMenuService(GangService gangService, PagedTextMenuService pagedTextMenuService) {
        this.gangService = gangService;
        this.pagedTextMenuService = pagedTextMenuService;
    }

    public void open(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("§r§fHere is a list of all gangs on the server.");
        lines.add("");

        for (Gang gang : gangService.allGangs()) {
            lines.add("§b > §7" + gang.getName());
        }

        pagedTextMenuService.open(player, "§bGang list", lines);
    }
}
