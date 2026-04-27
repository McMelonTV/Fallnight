package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.crate.CrateDefinition;
import xyz.fallnight.server.domain.crate.CrateReward;
import xyz.fallnight.server.domain.crate.WeightedCrateReward;
import xyz.fallnight.server.service.CrateMenuService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.PagedTextMenuService;
import xyz.fallnight.server.util.NumberFormatter;
import java.text.DecimalFormat;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class CrateItemsCommand extends FallnightCommand {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##");
    private final CrateService crateService;
    private final CrateMenuService crateMenuService;

    public CrateItemsCommand(PermissionService permissionService, CrateService crateService) {
        super("crateitems", permissionService, "viewcrate");
        this.crateService = crateService;
        this.crateMenuService = new CrateMenuService(crateService, new PagedTextMenuService());

        var crateIdArgument = ArgumentType.Word("crateId");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
                return;
            }
            crateMenuService.openBrowser(player);
        });
        addSyntax((sender, context) -> {
            String crateId = context.get(crateIdArgument);
            CrateDefinition crate = crateService.findCrate(crateId).orElse(null);
            if (crate == null) {
                sender.sendMessage(CommandMessages.error("Unknown crate '" + crateId + "'."));
                return;
            }

             if (sender instanceof net.minestom.server.entity.Player player) {
                crateMenuService.openRewards(player, crate);
                return;
            }

            int totalWeight = 0;
            for (WeightedCrateReward weightedReward : crate.rewards()) {
                totalWeight += weightedReward.weight();
            }

            sender.sendMessage(CommandMessages.info("Rewards for " + crate.displayName() + " (" + crate.id() + "):"));
            for (WeightedCrateReward weightedReward : crate.rewards()) {
                double chance = (weightedReward.weight() * 100D) / totalWeight;
                sender.sendMessage(CommandMessages.info(
                    "- " + formatReward(weightedReward.reward()) + " [weight=" + weightedReward.weight() + ", chance=" + PERCENT_FORMAT.format(chance) + "%]"
                ));
            }
        }, crateIdArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.crateitems";
    }

    @Override
    public String summary() {
        return "check what items you can find in crates";
    }

    @Override
    public String usage() {
        return "/crateitems";
    }

    private static String formatReward(CrateReward reward) {
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            return reward.description();
        }
        if (reward.forgedBookCount() > 0) {
            return reward.description();
        }
        if (reward.randomTagCount() > 0 || (reward.grantedTag() != null && !reward.grantedTag().isBlank())) {
            return reward.description();
        }
        if (reward.money() > 0D && reward.prestigePoints() > 0L) {
            return NumberFormatter.currency(reward.money()) + " and " + reward.prestigePoints() + " PP";
        }
        if (reward.money() > 0D) {
            return NumberFormatter.currency(reward.money());
        }
        return reward.prestigePoints() + " PP";
    }
}
