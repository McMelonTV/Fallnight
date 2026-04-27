package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import net.minestom.server.entity.Player;

public final class RulesBookService {
    private final InfoPagesService infoPagesService;
    private final BookMenuService bookMenuService;
    private final PlayerProfileService playerProfileService;

    public RulesBookService(InfoPagesService infoPagesService, BookMenuService bookMenuService, PlayerProfileService playerProfileService) {
        this.infoPagesService = infoPagesService;
        this.bookMenuService = bookMenuService;
        this.playerProfileService = playerProfileService;
    }

    public void open(Player player) {
        UserProfile profile = playerProfileService.getOrCreate(player);
        profile.setExtraValue("seenRules", true);
        
        bookMenuService.open(player, "§bServer rules", infoPagesService.rulesPage());
    }
}
