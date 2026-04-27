package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.service.VaultService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class SeasonResetCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final RankService rankService;
    private final GangService gangService;
    private final PlotService plotService;
    private final PlotRuntimeModule plotRuntimeModule;
    private final AuctionService auctionService;
    private final VaultService vaultService;
    private final Path plotWorldDirectory;

    public SeasonResetCommand(
        PermissionService permissionService,
        PlayerProfileService profileService,
        RankService rankService,
        GangService gangService,
        PlotService plotService,
        PlotRuntimeModule plotRuntimeModule,
        AuctionService auctionService,
        VaultService vaultService,
        Path plotWorldDirectory
    ) {
        super("seasonreset", permissionService);
        this.profileService = profileService;
        this.rankService = rankService;
        this.gangService = gangService;
        this.plotService = plotService;
        this.plotRuntimeModule = plotRuntimeModule;
        this.auctionService = auctionService;
        this.vaultService = vaultService;
        this.plotWorldDirectory = plotWorldDirectory;

        var confirmArg = ArgumentType.Word("confirm");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(CommandMessages.info("Season reset preview: " + profileService.allProfiles().size() + " profile(s), gangs, plots, auctions, and vaults will be reset."));
            sender.sendMessage(CommandMessages.info("Use /seasonreset confirm from console to execute."));
        });

        addSyntax((sender, context) -> {
            if (sender instanceof Player) {
                sender.sendMessage(CommandMessages.error("Please execute this command from console."));
                return;
            }
            if (!"confirm".equalsIgnoreCase(context.get(confirmArg))) {
                sender.sendMessage(CommandMessages.error("Use /seasonreset confirm to execute."));
                return;
            }

            for (UserProfile profile : profileService.allProfiles()) {
                resetProfile(profile);
                rankService.resetSeasonRanks(profile);
                profileService.save(profile);
            }

            gangService.resetAll();
            List<PlotCoordinate> oldPlots = plotService.resetAll();
            oldPlots.forEach(plotRuntimeModule::clearPlot);
            auctionService.resetAll();
            vaultService.resetAll();
            clearItemEntities();
            resetPlotWorldStorage();

            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.getInventory().clear();
                player.setHealth(20f);
                player.setFood(20);
                player.setFoodSaturation(20f);
                player.setExp(0f);
                player.setLevel(0);
                player.setItemInMainHand(ItemStack.AIR);
            }

            sender.sendMessage(CommandMessages.success("Season reset complete. The server is shutting down."));
            MinecraftServer.stopCleanly();
        }, confirmArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.seasonreset";
    }

    @Override
    public String summary() {
        return "a very dangerous command";
    }

    @Override
    public String usage() {
        return "/seasonreset";
    }

    private static void resetProfile(UserProfile profile) {
        profile.setBalance(0D);
        profile.setMinedBlocks(0L);
        profile.setMineRank(0);
        profile.setPrestige(1);
        profile.setPrestigePoints(0L);
        profile.setGangId("");
        profile.getExtraData().put("kills", 0L);
        profile.getExtraData().put("deaths", 0L);
        profile.getExtraData().put("totalEarnedMoney", 0D);
        profile.getExtraData().put("receivedStartItems", false);
        profile.getExtraData().put("hasReceivedStartItems", false);
        profile.getExtraData().put("maxAuctionListings", 1);
        profile.getExtraData().put("maxPlots", 1);
        profile.getExtraData().put("maxVaults", 1);
        profile.getExtraData().put("fly", false);
        profile.getExtraData().put("achievements", List.of());
        profile.getExtraData().put("claimedAchievements", List.of());
        profile.getExtraData().put("shopPurchases", List.of());
        profile.getExtraData().put("kitCooldowns", java.util.Map.of());
        profile.getExtraData().put("inventorySnapshot", List.of());
        profile.getExtraData().put("armorSnapshot", List.of());
        profile.getExtraData().remove("randomTagCredits");
        profile.getExtraData().remove("crateKeys");
        profile.getExtraData().remove("auctionsSold");
        profile.getExtraData().remove("auctionSales");
        profile.getExtraData().remove("auctionSold");
        profile.getExtraData().remove("auction_sold");
        profile.getExtraData().remove("ahSales");
        profile.getExtraData().remove("expiredAuctionItems");
    }

    private void clearItemEntities() {
        for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (Entity entity : instance.getEntities()) {
                if (entity instanceof ItemEntity) {
                    entity.remove();
                }
            }
        }
    }

    private void resetPlotWorldStorage() {
        if (plotWorldDirectory == null || !Files.exists(plotWorldDirectory)) {
            return;
        }
        try (var walk = Files.walk(plotWorldDirectory)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
