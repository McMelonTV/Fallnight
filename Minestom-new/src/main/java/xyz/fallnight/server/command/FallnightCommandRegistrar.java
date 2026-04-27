package xyz.fallnight.server.command;

import xyz.fallnight.server.WorldAccessService;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.command.impl.AddRankCommand;
import xyz.fallnight.server.command.impl.AuctionCommand;
import xyz.fallnight.server.command.impl.BalanceCommand;
import xyz.fallnight.server.command.impl.BanCommand;
import xyz.fallnight.server.command.impl.BanlistCommand;
import xyz.fallnight.server.command.impl.BroadcastCommand;
import xyz.fallnight.server.command.impl.ClearWarnCommand;
import xyz.fallnight.server.command.impl.CrateItemsCommand;
import xyz.fallnight.server.command.impl.CratesCommand;
import xyz.fallnight.server.command.impl.EnchantmentForgeCommand;
import xyz.fallnight.server.command.impl.GangCommand;
import xyz.fallnight.server.command.impl.GiveTagCommand;
import xyz.fallnight.server.command.impl.GlobalMuteCommand;
import xyz.fallnight.server.command.impl.GuideCommand;
import xyz.fallnight.server.command.impl.HelpCommand;
import xyz.fallnight.server.command.impl.KeyAllCommand;
import xyz.fallnight.server.command.impl.KeyCommand;
import xyz.fallnight.server.command.impl.KitCommand;
import xyz.fallnight.server.command.impl.KothCommand;
import xyz.fallnight.server.command.impl.ListCommand;
import xyz.fallnight.server.command.impl.ListRanksCommand;
import xyz.fallnight.server.command.impl.LotteryCommand;
import xyz.fallnight.server.command.impl.MineCommand;
import xyz.fallnight.server.command.impl.MinesCommand;
import xyz.fallnight.server.command.impl.MuteCommand;
import xyz.fallnight.server.command.impl.MyCoordsCommand;
import xyz.fallnight.server.command.impl.MyWarnsCommand;
import xyz.fallnight.server.command.impl.NearCommand;
import xyz.fallnight.server.command.impl.NewsCommand;
import xyz.fallnight.server.command.impl.PayCommand;
import xyz.fallnight.server.command.impl.PingCommand;
import xyz.fallnight.server.command.impl.PlayerRanksCommand;
import xyz.fallnight.server.command.impl.PrestigeCommand;
import xyz.fallnight.server.command.impl.RankInfoCommand;
import xyz.fallnight.server.command.impl.RanksCommand;
import xyz.fallnight.server.command.impl.RankupCommand;
import xyz.fallnight.server.command.impl.RemoveRankCommand;
import xyz.fallnight.server.command.impl.ReplyCommand;
import xyz.fallnight.server.command.impl.RulesCommand;
import xyz.fallnight.server.command.impl.SayCommand;
import xyz.fallnight.server.command.impl.SetBalanceCommand;
import xyz.fallnight.server.command.impl.SetMineCommand;
import xyz.fallnight.server.command.impl.SetPrestigeCommand;
import xyz.fallnight.server.command.impl.ShopCommand;
import xyz.fallnight.server.command.impl.SpawnCommand;
import xyz.fallnight.server.command.impl.StatsCommand;
import xyz.fallnight.server.command.impl.TagsCommand;
import xyz.fallnight.server.command.impl.TellCommand;
import xyz.fallnight.server.command.impl.TempbanCommand;
import xyz.fallnight.server.command.impl.UnbanCommand;
import xyz.fallnight.server.command.impl.VaultCommand;
import xyz.fallnight.server.command.impl.VoteCommand;
import xyz.fallnight.server.command.impl.WarnCommand;
import xyz.fallnight.server.command.impl.WarningsCommand;
import xyz.fallnight.server.gameplay.koth.KothGameplayModule;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.service.AuctionMenuService;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.BroadcastService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.DirectMessageService;
import xyz.fallnight.server.service.EnchantmentForgeMenuService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.KitMenuService;
import xyz.fallnight.server.service.KitService;
import xyz.fallnight.server.service.KothService;
import xyz.fallnight.server.service.LotteryMenuService;
import xyz.fallnight.server.service.LotteryService;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.MinesMenuService;
import xyz.fallnight.server.service.ModerationSanctionsService;
import xyz.fallnight.server.service.PayMenuService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.PvpZoneService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.service.ShopMenuService;
import xyz.fallnight.server.service.ShopService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.service.TagService;
import xyz.fallnight.server.service.TagsMenuService;
import xyz.fallnight.server.service.VaultService;
import xyz.fallnight.server.service.VotePartyService;
import xyz.fallnight.server.service.WarningService;
import xyz.fallnight.server.service.WorldLabelService;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FallnightCommandRegistrar {
    private final CommandManager commandManager;
    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final MineRankService mineRankService;
    private final MinesMenuService minesMenuService;
    private final PayMenuService payMenuService;
    private final RankService rankService;
    private final GangService gangService;
    private final VaultService vaultService;
    private final BroadcastService broadcastService;
    private final AuctionService auctionService;
    private final AuctionMenuService auctionMenuService;
    private final ShopService shopService;
    private final ShopMenuService shopMenuService;
    private final TagService tagService;
    private final TagsMenuService tagsMenuService;
    private final KitService kitService;
    private final KitMenuService kitMenuService;
    private final CrateService crateService;
    private final ItemDeliveryService itemDeliveryService;
    private final LotteryService lotteryService;
    private final LotteryMenuService lotteryMenuService;
    private final VotePartyService votePartyService;
    private final KothService kothService;
    private final KothGameplayModule kothGameplayModule;
    private final ModerationSanctionsService moderationSanctionsService;
    private final WarningService warningService;
    private final InfoPagesService infoPagesService;
    private final DirectMessageService directMessageService;
    private final DefaultWorldService defaultWorldService;
    private final SpawnService spawnService;
    private final WorldAccessService worldAccessService;
    private final PvpZoneService pvpZoneService;
    private final SpawnService plotWorldService;
    private final SpawnService pvpMineWorldService;
    private final PlotService plotService;
    private final PlotRuntimeModule plotRuntimeModule;
    private final WorldLabelService worldLabelService;
    private final Path dataRoot;
    private final boolean devServer;

    public FallnightCommandRegistrar(
            CommandManager commandManager,
            PermissionService permissionService,
            PlayerProfileService profileService,
            MineService mineService,
            MineRankService mineRankService,
            MinesMenuService minesMenuService,
            PayMenuService payMenuService,
            RankService rankService,
            GangService gangService,
            VaultService vaultService,
            BroadcastService broadcastService,
            AuctionService auctionService,
            AuctionMenuService auctionMenuService,
            ShopService shopService,
            ShopMenuService shopMenuService,
            TagService tagService,
            TagsMenuService tagsMenuService,
            KitService kitService,
            KitMenuService kitMenuService,
            CrateService crateService,
            ItemDeliveryService itemDeliveryService,
            LotteryService lotteryService,
            LotteryMenuService lotteryMenuService,
            VotePartyService votePartyService,
            KothService kothService,
            KothGameplayModule kothGameplayModule,
            ModerationSanctionsService moderationSanctionsService,
            WarningService warningService,
            InfoPagesService infoPagesService,
            DirectMessageService directMessageService,
            DefaultWorldService defaultWorldService,
            SpawnService spawnService,
            WorldAccessService worldAccessService,
            PvpZoneService pvpZoneService,
            SpawnService plotWorldService,
            SpawnService pvpMineWorldService,
            PlotService plotService,
            PlotRuntimeModule plotRuntimeModule,
            WorldLabelService worldLabelService,
            Path dataRoot,
            boolean devServer
    ) {
        this.commandManager = commandManager;
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.mineService = mineService;
        this.mineRankService = mineRankService;
        this.minesMenuService = minesMenuService;
        this.payMenuService = payMenuService;
        this.rankService = rankService;
        this.gangService = gangService;
        this.vaultService = vaultService;
        this.broadcastService = broadcastService;
        this.auctionService = auctionService;
        this.auctionMenuService = auctionMenuService;
        this.shopService = shopService;
        this.shopMenuService = shopMenuService;
        this.tagService = tagService;
        this.tagsMenuService = tagsMenuService;
        this.kitService = kitService;
        this.kitMenuService = kitMenuService;
        this.crateService = crateService;
        this.itemDeliveryService = itemDeliveryService;
        this.lotteryService = lotteryService;
        this.lotteryMenuService = lotteryMenuService;
        this.votePartyService = votePartyService;
        this.kothService = kothService;
        this.kothGameplayModule = kothGameplayModule;
        this.moderationSanctionsService = moderationSanctionsService;
        this.warningService = warningService;
        this.infoPagesService = infoPagesService;
        this.directMessageService = directMessageService;
        this.defaultWorldService = defaultWorldService;
        this.spawnService = spawnService;
        this.worldAccessService = worldAccessService;
        this.pvpZoneService = pvpZoneService;
        this.plotWorldService = plotWorldService;
        this.pvpMineWorldService = pvpMineWorldService;
        this.plotService = plotService;
        this.plotRuntimeModule = plotRuntimeModule;
        this.worldLabelService = worldLabelService;
        this.dataRoot = dataRoot;
        this.devServer = devServer;
    }

    public CommandRegistrationResult registerAll() {
        List<FallnightCommand> implemented = new ArrayList<>();
        implemented.add(new BalanceCommand(permissionService, profileService));
        implemented.add(new MineCommand(permissionService, profileService, mineService, spawnService));
        implemented.add(new MinesCommand(permissionService, mineService, profileService, minesMenuService));
        implemented.add(new RanksCommand(permissionService, profileService, mineRankService));
        implemented.add(new PayCommand(permissionService, profileService, payMenuService));
        implemented.add(new PrestigeCommand(permissionService, profileService, mineRankService));
        implemented.add(new RankupCommand(permissionService, profileService, mineRankService));
        implemented.add(new AddRankCommand(permissionService, profileService, rankService));
        implemented.add(new RemoveRankCommand(permissionService, profileService, rankService));
        implemented.add(new ListRanksCommand(permissionService, rankService));
        implemented.add(new RankInfoCommand(permissionService, rankService));
        implemented.add(new PlayerRanksCommand(permissionService, profileService, rankService));
        implemented.add(new SetMineCommand(permissionService, profileService, mineService));
        implemented.add(new SetBalanceCommand(permissionService, profileService));
        implemented.add(new SetPrestigeCommand(permissionService, profileService));
        implemented.add(new SpawnCommand(permissionService, defaultWorldService));
        implemented.add(new StatsCommand(permissionService, profileService, mineService, rankService));
        implemented.add(new RulesCommand(permissionService, infoPagesService, profileService));
        implemented.add(new NewsCommand(permissionService, infoPagesService, profileService));
        implemented.add(new GuideCommand(permissionService, infoPagesService));
        implemented.add(new PingCommand(permissionService));
        implemented.add(new MyCoordsCommand(permissionService));
        implemented.add(new ListCommand(permissionService, profileService, rankService));
        implemented.add(new NearCommand(permissionService, profileService));
        implemented.add(new GangCommand(permissionService, gangService, profileService));
        implemented.add(new VaultCommand(permissionService, profileService, vaultService));
        implemented.add(new BroadcastCommand(permissionService, broadcastService));
        implemented.add(new TellCommand(permissionService, directMessageService, profileService, moderationSanctionsService));
        implemented.add(new ReplyCommand(permissionService, directMessageService, profileService, moderationSanctionsService));
        implemented.add(new ShopCommand(permissionService, profileService, shopService, shopMenuService));
        implemented.add(new TagsCommand(permissionService, profileService, tagService, tagsMenuService));
        implemented.add(new GiveTagCommand(permissionService, profileService, tagService));
        implemented.add(new KitCommand(permissionService, profileService, kitService, kitMenuService));
        implemented.add(new CratesCommand(permissionService, crateService, profileService, defaultWorldService));
        implemented.add(new CrateItemsCommand(permissionService, crateService));
        implemented.add(new KeyCommand(permissionService, crateService, profileService, itemDeliveryService));
        implemented.add(new KeyAllCommand(permissionService, crateService, profileService, itemDeliveryService));
        implemented.add(new EnchantmentForgeCommand(permissionService, profileService, new EnchantmentForgeMenuService()));
        implemented.add(new AuctionCommand(permissionService, auctionService, auctionMenuService, itemDeliveryService, profileService));
        implemented.add(new LotteryCommand(permissionService, lotteryService, lotteryMenuService));
        implemented.add(new VoteCommand(permissionService, votePartyService));
        implemented.add(new KothCommand(permissionService, kothService, kothGameplayModule));
        implemented.add(new SayCommand(permissionService));
        implemented.add(new BanCommand(permissionService, moderationSanctionsService, profileService));
        implemented.add(new TempbanCommand(permissionService, moderationSanctionsService));
        implemented.add(new UnbanCommand(permissionService, moderationSanctionsService));
        implemented.add(new BanlistCommand(permissionService, moderationSanctionsService));
        implemented.add(new MuteCommand(permissionService, moderationSanctionsService, profileService));
        implemented.add(new GlobalMuteCommand(permissionService, moderationSanctionsService));
        implemented.add(new WarnCommand(permissionService, warningService));
        implemented.add(new WarningsCommand(permissionService, warningService));
        implemented.add(new MyWarnsCommand(permissionService, warningService));
        implemented.add(new ClearWarnCommand(permissionService, warningService));
        implemented.add(new HelpCommand(permissionService, commandManager));

        implemented.forEach(commandManager::register);

        Set<String> implementedNames = implemented.stream()
                .flatMap(command -> command.allNames().stream())
                .collect(Collectors.toSet());

        List<Command> compatCommands = LegacyCompatCommands.createAll(
                permissionService,
                profileService,
                moderationSanctionsService,
                rankService,
                gangService,
                vaultService,
                itemDeliveryService,
                auctionService,
                crateService,
                mineService,
                defaultWorldService,
                spawnService,
                worldAccessService,
                pvpZoneService,
                kothService,
                plotWorldService,
                pvpMineWorldService,
                plotService,
                plotRuntimeModule,
                worldLabelService,
                dataRoot,
                devServer,
                implementedNames
        );
        compatCommands.forEach(commandManager::register);
        compatCommands.stream()
                .flatMap(command -> java.util.Arrays.stream(command.getNames()))
                .forEach(implementedNames::add);

        return new CommandRegistrationResult(implemented.size());
    }
}
