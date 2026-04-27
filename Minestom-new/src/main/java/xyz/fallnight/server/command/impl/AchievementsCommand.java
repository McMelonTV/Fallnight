package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.achievement.AchievementCategory;
import xyz.fallnight.server.domain.achievement.AchievementDefinition;
import xyz.fallnight.server.domain.achievement.AchievementStatus;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.AchievementsMenuService;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.util.LegacyTextFormatter;
import xyz.fallnight.server.util.NumberFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class AchievementsCommand extends FallnightCommand {
    private static final int DEFAULT_PAGE_SIZE = 5;

    private final PlayerProfileService profileService;
    private final AchievementService achievementService;
    private final AchievementsMenuService achievementsMenuService;
    private final PagedTextMenuService pagedTextMenuService = new PagedTextMenuService();

    public AchievementsCommand(
        PermissionService permissionService,
        PlayerProfileService profileService,
        AchievementService achievementService,
        AchievementsMenuService achievementsMenuService
    ) {
        super("achievements", permissionService);
        this.profileService = profileService;
        this.achievementService = achievementService;
        this.achievementsMenuService = achievementsMenuService;

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            achievementsMenuService.open(player);
            player.sendMessage(LegacyTextFormatter.deserialize("§b§l> §r§7Showing your achievements..."));
        });

    }

    @Override
    public String permission() {
        return "fallnight.command.achievements";
    }

    @Override
    public String summary() {
        return "view your achievements";
    }

    @Override
    public String usage() {
        return "/achievements";
    }

    private void sendOverview(Player player, UserProfile profile) {
        List<AchievementStatus> statuses = achievementService.statuses(profile);
        int total = statuses.size();
        int unlocked = 0;
        int claimed = 0;
        List<AchievementStatus> locked = new ArrayList<>();

        for (AchievementStatus status : statuses) {
            if (status.unlocked()) {
                unlocked++;
            }
            if (status.claimed()) {
                claimed++;
            }
            if (!status.unlocked()) {
                locked.add(status);
            }
        }

        player.sendMessage(CommandMessages.info(
            "Achievements: " + unlocked + "/" + total + " unlocked, " + claimed + " completed."
        ));
        sendSection(player, "Unlocked (page 1)", statuses.stream().filter(AchievementStatus::unlocked).toList(), DEFAULT_PAGE_SIZE);
        sendSection(player, "Locked (page 1)", locked, DEFAULT_PAGE_SIZE);
    }

    private void sendList(Player player, UserProfile profile, AchievementCategory category) {
        List<AchievementStatus> statuses = achievementService.statuses(profile);
        String title = category == null ? "all" : category.id();
        List<String> lines = new ArrayList<>();
        for (AchievementStatus status : statuses) {
            if (category != null && status.definition().category() != category) {
                continue;
            }
            lines.add("§b" + status.definition().title());
            lines.add("§7" + formatStatusLine(status));
        }

        if (lines.isEmpty()) {
            player.sendMessage(CommandMessages.info("No achievements in that category."));
            return;
        }
        pagedTextMenuService.open(player, "Achievements (" + title + ")", lines);
    }

    private static void sendSection(Player player, String title, List<AchievementStatus> statuses, int maxItems) {
        if (statuses.isEmpty()) {
            player.sendMessage(CommandMessages.info(title + ": none."));
            return;
        }
        player.sendMessage(CommandMessages.info(title + ":"));
        int shown = 0;
        for (AchievementStatus status : statuses) {
            player.sendMessage(CommandMessages.info(formatStatusLine(status)));
            shown++;
            if (shown >= maxItems) {
                break;
            }
        }
        if (statuses.size() > shown) {
            player.sendMessage(CommandMessages.info("Showing " + shown + " of " + statuses.size() + "."));
        }
    }

    private static String formatStatusLine(AchievementStatus status) {
        AchievementDefinition definition = status.definition();
        String state;
        if (status.claimed()) {
            state = "CLAIMED";
        } else if (status.unlocked()) {
            state = "UNLOCKED";
        } else {
            state = "LOCKED";
        }
        return "[" + state + "] "
            + definition.id()
            + " ("
            + definition.category().id()
            + ") - "
            + definition.title()
            + " | "
            + NumberFormatter.shortNumber(status.progress())
            + "/"
            + NumberFormatter.shortNumber(definition.requiredProgress())
            + rewardText(definition.moneyReward(), definition.prestigePointsReward());
    }

    private static String rewardText(double money, long prestige) {
        if (money > 0D && prestige > 0L) {
            return " | reward " + NumberFormatter.currency(money) + " + " + prestige + " PP";
        }
        if (money > 0D) {
            return " | reward " + NumberFormatter.currency(money);
        }
        if (prestige > 0L) {
            return " | reward " + prestige + " PP";
        }
        return "";
    }

    private static String categoriesText() {
        return java.util.Arrays.stream(AchievementCategory.values())
            .map(AchievementCategory::id)
            .collect(Collectors.joining(", "));
    }
}
