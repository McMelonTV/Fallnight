package xyz.fallnight.server;

import xyz.fallnight.server.bootstrap.DataDirectoryBootstrap;
import xyz.fallnight.server.bootstrap.DefaultContentSeeder;
import xyz.fallnight.server.command.CommandRegistrationResult;
import xyz.fallnight.server.command.FallnightCommandRegistrar;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.gameplay.auction.AuctionExpirationModule;
import xyz.fallnight.server.gameplay.broadcast.BroadcastRotationModule;
import xyz.fallnight.server.gameplay.chat.ChatFormattingModule;
import xyz.fallnight.server.gameplay.community.CommunityBehaviorModule;
import xyz.fallnight.server.gameplay.crate.CrateInteractionModule;
import xyz.fallnight.server.gameplay.gang.GangChatModule;
import xyz.fallnight.server.gameplay.item.ArmorEnchantPassiveModule;
import xyz.fallnight.server.gameplay.item.EnchantmentBookModule;
import xyz.fallnight.server.gameplay.koth.KothGameplayModule;
import xyz.fallnight.server.gameplay.leaderboard.LeaderboardRefreshModule;
import xyz.fallnight.server.gameplay.lottery.LotteryDrawModule;
import xyz.fallnight.server.gameplay.maintenance.MaintenanceModule;
import xyz.fallnight.server.gameplay.mine.MineGameplayIntegration;
import xyz.fallnight.server.gameplay.moderation.ModerationGameplayModule;
import xyz.fallnight.server.gameplay.player.ItemRestrictionModule;
import xyz.fallnight.server.gameplay.player.PlayerTickerModule;
import xyz.fallnight.server.gameplay.player.LoginGateModule;
import xyz.fallnight.server.gameplay.player.PlayerHudModule;
import xyz.fallnight.server.gameplay.player.PlayerLifecycleModule;
import xyz.fallnight.server.gameplay.player.ProtectionGameplayModule;
import xyz.fallnight.server.gameplay.player.WorldRuleModule;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.gameplay.plot.PlotWorldGenerator;
import xyz.fallnight.server.gameplay.pvp.PvpGameplayModule;
import xyz.fallnight.server.gameplay.rank.RankExpirationModule;
import xyz.fallnight.server.gameplay.vote.VoteClaimModule;
import xyz.fallnight.server.persistence.DataDirectories;
import xyz.fallnight.server.persistence.ServerDataStores;
import xyz.fallnight.server.persistence.auction.JsonAuctionRepository;
import xyz.fallnight.server.persistence.plot.JsonPlotRepository;
import xyz.fallnight.server.persistence.tag.YamlTagDefinitionRepository;
import xyz.fallnight.server.service.AuctionMenuService;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.BountyService;
import xyz.fallnight.server.service.BroadcastService;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.DirectMessageService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.HolotextService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.ItemDeliveryService;
import xyz.fallnight.server.service.KitMenuService;
import xyz.fallnight.server.service.KitService;
import xyz.fallnight.server.service.KothService;
import xyz.fallnight.server.service.LeaderboardService;
import xyz.fallnight.server.service.LegacyCustomItemService;
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
import xyz.fallnight.server.service.UserProfileService;
import xyz.fallnight.server.service.VaultService;
import xyz.fallnight.server.service.VotePartyService;
import xyz.fallnight.server.service.WarningService;
import xyz.fallnight.server.service.WorldLabelService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.CommandResult;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.generator.Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        try {
            ServerConfig config = AppConfigLoader.load(LOGGER);
            MinecraftServer minecraftServer = MinecraftServer.init();
            ConsoleCommandBridge consoleBridge = new ConsoleCommandBridge(
                    new InputStreamReader(System.in),
                    command -> {
                        LOGGER.info("[console] > {}", command);
                        CommandResult result = MinecraftServer.getCommandManager().execute(MinecraftServer.getCommandManager().getConsoleSender(), command);
                        if (result.getType() != CommandResult.Type.SUCCESS) {
                            LOGGER.warn("[console] command result: {} for '{}'", result.getType(), command);
                        }
                    },
                    LOGGER,
                    false
            );

            DataDirectories dataDirectories = new DataDirectories(Path.of(config.dataPath()));
            DataDirectoryBootstrap.prepareData(dataDirectories);
            ServerDataStores stores = ServerDataStores.fromDirectories(dataDirectories);
            Pos spawnPos = new Pos(config.spawnX(), config.spawnY(), config.spawnZ());

            InstanceContainer mainInstance = createMainInstance(dataDirectories.root().resolve(config.mainWorldDirectory()), spawnPos);
            InstanceContainer plotsInstance = createPlotsInstance(dataDirectories.root().resolve("plots"), new Pos(0.5, 65.0, 0.5));
            InstanceContainer pvpMineInstance = createMainInstance(dataDirectories.root().resolve("PvPMine"), new Pos(0.5, 72.0, 0.5));
            mainInstance.setTime(6000);
            mainInstance.setTimeRate(0);
            plotsInstance.setTime(6000);
            plotsInstance.setTimeRate(0);
            pvpMineInstance.setTime(6000);
            pvpMineInstance.setTimeRate(0);

            UserProfileService userProfileService = new UserProfileService(stores.userProfiles());
            userProfileService.loadAll();
            PlayerProfileService profileService = new PlayerProfileService(userProfileService);

            MineService mineService = new MineService(stores.mines());
            mineService.loadAll();

            MineRankService mineRankService = new MineRankService(stores.mineRanks());
            mineRankService.loadAll();

            DefaultContentSeeder.seedIfNeeded(config, mineRankService, mineService);

            RankService rankService = new RankService(dataDirectories.root().resolve("ranks.yml"));
            rankService.load();

            VaultService vaultService = new VaultService(stores.vaults(), rankService);
            vaultService.loadAll();
            ItemDeliveryService itemDeliveryService = new ItemDeliveryService(vaultService);

            GangService gangService = new GangService(stores.gangs(), userProfileService);
            gangService.loadAll();

            BountyService bountyService = BountyService.fromDataRoot(dataDirectories.root());
            bountyService.loadAll();

            HolotextService holotextService = HolotextService.fromDataRoot(dataDirectories.root());
            holotextService.loadAll();
            WorldLabelService worldLabelService = WorldLabelService.fromDataRoot(dataDirectories.root());
            WorldLabelService.WorldLabels worldLabels = worldLabelService.loadOrDefaults(config.spawnWorld(), "plots", "PvPMine");

            LegacyCustomItemService customItemService = new LegacyCustomItemService();
            LeaderboardService leaderboardService = new LeaderboardService(profileService, 30);
            SpawnWorldLightWorkaroundService spawnWorldLightWorkaroundService = new SpawnWorldLightWorkaroundService();
            spawnWorldLightWorkaroundService.register(mainInstance);

            SpawnService spawnService = new SpawnService(mainInstance, WorldAccessService.readSpawn(dataDirectories.root().resolve(config.mainWorldDirectory()), spawnPos), worldLabels.main());
            SpawnService plotWorldService = new SpawnService(plotsInstance, WorldAccessService.readSpawn(dataDirectories.root().resolve("plots"), new Pos(0.5, 65.0, 0.5)), worldLabels.plots());
            SpawnService pvpMineWorldService = new SpawnService(pvpMineInstance, WorldAccessService.readSpawn(dataDirectories.root().resolve("PvPMine"), new Pos(0.5, 72.0, 0.5)), worldLabels.pvpMine());
            DefaultWorldService defaultWorldService = new DefaultWorldService(resolveInitialDefaultWorld(config.spawnWorld(), spawnService, plotWorldService, pvpMineWorldService));
            WorldAccessService worldAccessService = new WorldAccessService(dataDirectories.root(), config.mainWorldDirectory(), spawnService, plotWorldService, pvpMineWorldService);
            holotextService.spawnAll(worldAccessService);
            defaultWorldService.setCurrentWorld(worldAccessService.resolve(config.spawnWorld()).orElse(defaultWorldService.currentWorld()));
            PlotService plotService = new PlotService(new JsonPlotRepository(dataDirectories.root().resolve("plots.json")), profileService, rankService);
            plotService.load();
            PlotRuntimeModule plotRuntimeModule = new PlotRuntimeModule(plotService, profileService, plotWorldService);
            plotRuntimeModule.register();
            mineService.renameWorldLabel("spawn", spawnService.worldName());
            mineService.renameWorldLabel("spawn-world", spawnService.worldName());
            mineService.renameWorldLabel("PvPMine", pvpMineWorldService.worldName());
            mineService.renameWorldLabel("pvpmine", pvpMineWorldService.worldName());
            ProtectionGameplayModule protectionGameplayModule = new ProtectionGameplayModule(profileService, mineService, plotRuntimeModule, spawnService);
            protectionGameplayModule.register();
            GangChatModule gangChatModule = new GangChatModule(gangService);
            gangChatModule.register();

            BroadcastService broadcastService = BroadcastService.fromDataRoot(dataDirectories.root());
            broadcastService.load();
            BroadcastRotationModule broadcastRotationModule = new BroadcastRotationModule(broadcastService);
            broadcastRotationModule.register();

            AuctionService auctionService = new AuctionService(
                    new JsonAuctionRepository(dataDirectories.root().resolve("auction.json")),
                    profileService
            );
            auctionService.loadAll();
            AuctionMenuService auctionMenuService = new AuctionMenuService(auctionService, profileService, itemDeliveryService);
            AuctionExpirationModule auctionExpirationModule = new AuctionExpirationModule(auctionService);
            auctionExpirationModule.register();

            PermissionService permissionService = new PermissionService(profileService, rankService);

            ShopService shopService = ShopService.fromDataRoot(dataDirectories.root(), rankService);
            shopService.loadPrices();
            ShopMenuService shopMenuService = new ShopMenuService(shopService, profileService);
            MinesMenuService minesMenuService = new MinesMenuService(profileService, mineService, mineRankService);
            PayMenuService payMenuService = new PayMenuService(profileService);

            TagService tagService = new TagService(new YamlTagDefinitionRepository(dataDirectories.root().resolve("tags.yml")));
            tagService.loadDefinitions();
            TagsMenuService tagsMenuService = new TagsMenuService(profileService, tagService, permissionService);

            KitService kitService = new KitService(profileService);
            KitMenuService kitMenuService = new KitMenuService(kitService, profileService, permissionService);
            CrateService crateService = CrateService.createDefaults(profileService, itemDeliveryService, tagService);
            DirectMessageService directMessageService = new DirectMessageService();
            MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event ->
                    directMessageService.clearPlayer(event.getPlayer().getUuid())
            );

            LotteryService lotteryService = LotteryService.fromDataRoot(dataDirectories.root(), profileService);
            lotteryService.load();
            LotteryMenuService lotteryMenuService = new LotteryMenuService(lotteryService, profileService);
            LotteryDrawModule lotteryDrawModule = new LotteryDrawModule(lotteryService);
            lotteryDrawModule.register();
            VotePartyService votePartyService = VotePartyService.fromDataRoot(dataDirectories.root(), profileService, mineRankService, tagService, lotteryService, itemDeliveryService);
            votePartyService.load();
            VoteClaimModule voteClaimModule = new VoteClaimModule(votePartyService);
            voteClaimModule.register();

            KothService kothService = KothService.fromDataRoot(dataDirectories.root(), profileService, itemDeliveryService, spawnService, pvpMineWorldService, spawnPos);
            kothService.load();
            KothGameplayModule kothGameplayModule = new KothGameplayModule(kothService, gangService);
            kothGameplayModule.register();

            WarningService warningService = WarningService.fromDataRoot(dataDirectories.root(), profileService);
            warningService.loadAll();

            InfoPagesService infoPagesService = InfoPagesService.fromDataRoot(dataDirectories.root());

            ModerationGameplayModule moderationGameplayModule = ModerationGameplayModule.createDefaults(
                    dataDirectories.root(),
                    profileService
            );
            moderationGameplayModule.register();
            ModerationSanctionsService moderationSanctionsService = moderationGameplayModule.sanctionsService();

            PvpGameplayModule pvpGameplayModule = PvpGameplayModule.createDefaults(
                    dataDirectories.root(),
                    profileService,
                    gangService,
                    permissionService,
                    MinecraftServer.getCommandManager(),
                    spawnService,
                    pvpMineWorldService
            );
            pvpGameplayModule.register();

            PvpZoneService sharedPvpZoneService = pvpGameplayModule.pvpZoneService();

            PlayerLifecycleModule lifecycleModule = new PlayerLifecycleModule(
                    profileService,
                    defaultWorldService,
                    spawnService,
                    kitService,
                    tagService,
                    auctionService,
                    infoPagesService,
                    mineService,
                    plotWorldService,
                    pvpMineWorldService
            );
            lifecycleModule.register();
            PlayerHudModule playerHudModule = new PlayerHudModule(profileService, mineService, mineRankService, sharedPvpZoneService, spawnService);
            playerHudModule.register();
            LoginGateModule loginGateModule = new LoginGateModule(config);
            loginGateModule.register();

            RankExpirationModule rankExpirationModule = new RankExpirationModule(profileService, rankService);
            rankExpirationModule.register();

            CommunityBehaviorModule communityBehaviorModule = new CommunityBehaviorModule(profileService, permissionService);
            communityBehaviorModule.register();

            EnchantmentBookModule enchantmentBookModule = new EnchantmentBookModule(customItemService);
            enchantmentBookModule.register();

            ArmorEnchantPassiveModule armorEnchantPassiveModule = new ArmorEnchantPassiveModule(customItemService);
            armorEnchantPassiveModule.register();

            LeaderboardRefreshModule leaderboardRefreshModule = new LeaderboardRefreshModule(leaderboardService);
            leaderboardRefreshModule.register();

            MaintenanceModule maintenanceModule = new MaintenanceModule();
            maintenanceModule.register();
            MaintenanceModule.configureRelaunch(System.getProperty("sun.java.command") == null ? null : "java -cp \"" + System.getProperty("java.class.path") + "\" xyz.fallnight.server.Main");
            MaintenanceModule.configureTransferTarget(resolveTransferHost(config.host()), config.port());

            ItemRestrictionModule itemRestrictionModule = new ItemRestrictionModule(profileService, plotWorldService);
            itemRestrictionModule.register();

            PlayerTickerModule playerTickerModule = new PlayerTickerModule(profileService, () -> {
                try {
                    profileService.saveAllOnline();
                    mineService.saveAll();
                    vaultService.saveAll();
                    gangService.saveAll();
                    auctionService.saveAll();
                    moderationGameplayModule.saveAll();
                    warningService.saveAll();
                    pvpGameplayModule.pvpZoneService().saveAll();
                    rankService.save();
                } catch (Exception exception) {
                    LOGGER.error("Periodic autosave failed", exception);
                }
            });
            playerTickerModule.register();

            WorldRuleModule worldRuleModule = new WorldRuleModule(defaultWorldService, spawnService, plotWorldService, pvpMineWorldService);
            worldRuleModule.register();

            CrateInteractionModule crateInteractionModule = new CrateInteractionModule(crateService, profileService, defaultWorldService);
            crateInteractionModule.register();

            MineGameplayIntegration.install(mineService, profileService, mainInstance, spawnService, dataDirectories.root());
            new ChatFormattingModule(permissionService, profileService, gangService, mineRankService, rankService).register();
            MinecraftServer.getGlobalEventHandler().addListener(net.minestom.server.event.player.PlayerChatEvent.class, event -> broadcastRotationModule.addMessage());

            registerCommands(
                    mainInstance,
                    profileService,
                    mineService,
                    mineRankService,
                    minesMenuService,
                    payMenuService,
                    rankService,
                    gangService,
                    vaultService,
                    broadcastService,
                    auctionService,
                    auctionMenuService,
                    shopService,
                    shopMenuService,
                    tagService,
                    tagsMenuService,
                    kitService,
                    kitMenuService,
                    crateService,
                    itemDeliveryService,
                    lotteryService,
                    lotteryMenuService,
                    votePartyService,
                    kothService,
                    kothGameplayModule,
                    moderationSanctionsService,
                    warningService,
                    infoPagesService,
                    directMessageService,
                    defaultWorldService,
                    permissionService,
                    worldAccessService,
                    sharedPvpZoneService,
                    spawnService,
                    plotWorldService,
                    pvpMineWorldService,
                    plotService,
                    plotRuntimeModule,
                    worldLabelService,
                    dataDirectories.root(),
                    config.devServer()
            );
            MaintenanceModule.clearItemEntities();

            LOGGER.info("Starting Fallnight server on {}:{}", config.host(), config.port());
            LOGGER.info("Configured MOTD: {}", config.motd());
            MinecraftServer.setBrandName(config.motd());
            LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
            MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent.class, event -> {
                var builder = net.minestom.server.ping.Status.builder(event.getStatus())
                        .description(legacySerializer.deserialize(config.motd()));
                if (config.devServer()) {
                    builder.playerInfo(-1, 0);
                } else {
                    builder.playerInfo(MinecraftServer.getConnectionManager().getOnlinePlayers().size(), config.maxPlayers());
                }
                event.setStatus(builder.build());
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown requested, stopping server.");
                consoleBridge.close();
                lifecycleModule.shutdown();
                playerHudModule.unregister();
                loginGateModule.unregister();
                broadcastRotationModule.unregister();
                auctionExpirationModule.unregister();
                lotteryDrawModule.unregister();
                voteClaimModule.unregister();
                kothGameplayModule.unregister();
                moderationGameplayModule.unregister();
                pvpGameplayModule.unregister();
                rankExpirationModule.unregister();
                communityBehaviorModule.unregister();
                enchantmentBookModule.unregister();
                armorEnchantPassiveModule.unregister();
                leaderboardRefreshModule.unregister();
                maintenanceModule.unregister();
                itemRestrictionModule.unregister();
                playerTickerModule.unregister();
                worldRuleModule.unregister();
                crateInteractionModule.unregister();
                plotRuntimeModule.unregister();
                protectionGameplayModule.unregister();
                gangChatModule.unregister();
                try {
                    mineService.saveAll();
                    mineRankService.saveAll();
                    vaultService.saveAll();
                    gangService.saveAll();
                    bountyService.saveAll();
                    holotextService.saveAll();
                    holotextService.despawnAll();
                    auctionService.saveAll();
                    lotteryService.refundAll();
                    lotteryService.saveAll();
                    votePartyService.saveAll();
                    kothService.saveAll();
                    spawnWorldLightWorkaroundService.clear(mainInstance);
                    pvpMineInstance.saveChunksToStorage().join();
                    plotsInstance.saveChunksToStorage().join();
                    worldAccessService.saveAll();
                    moderationGameplayModule.saveAll();
                    warningService.saveAll();
                    pvpGameplayModule.pvpZoneService().saveAll();
                    userProfileService.saveAll();
                    rankService.save();
                } catch (Exception exception) {
                    LOGGER.error("Failed to persist data during shutdown", exception);
                }
            }, "fallnight-shutdown"));

            consoleBridge.start();
            minecraftServer.start(config.host(), config.port());
        } catch (Throwable throwable) {
            LOGGER.error("Server startup failed.", throwable);
            System.exit(1);
        }
    }

    static InstanceContainer createMainInstance(Path path, Pos preloadCenter) {
        return createConfiguredInstance(path, null, preloadCenter);
    }

    static InstanceContainer createPlotsInstance(Path path, Pos preloadCenter) {
        return createConfiguredInstance(path, new PlotWorldGenerator(), preloadCenter);
    }

    private static InstanceContainer createConfiguredInstance(Path path, Generator generator, Pos preloadCenter) {
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        AnvilLoader loader = new AnvilLoader(path);
        instance.setChunkLoader(loader);
        loader.loadInstance(instance);
        instance.enableAutoChunkLoad(true);
        if (generator != null) {
            instance.setGenerator(generator);
        }
        instance.setExplosionSupplier((x, y, z, strength, tag) -> new xyz.fallnight.server.gameplay.player.NoopExplosion(x, y, z, strength));
        preloadChunks(instance, preloadCenter, 1);
        registerChunkLoadRelight(instance);
        return instance;
    }

    private static void preloadChunks(InstanceContainer instance, Pos center, int radius) {
        if (instance == null || center == null || radius < 0) {
            return;
        }
        int centerChunkX = center.chunkX();
        int centerChunkZ = center.chunkZ();
        List<Chunk> loadedChunks = new ArrayList<>();
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                loadedChunks.add(instance.loadChunk(chunkX, chunkZ).join());
            }
        }
        LightingChunk.relight(instance, loadedChunks);
    }

    private static void registerChunkLoadRelight(InstanceContainer instance) {
        instance.eventNode().addListener(InstanceChunkLoadEvent.class, event -> {
            List<Chunk> affectedChunks = loadedChunksAround(instance, event.getChunkX(), event.getChunkZ(), 1);
            if (affectedChunks.isEmpty()) {
                return;
            }

            List<Chunk> relitChunks = new ArrayList<>(LightingChunk.relight(instance, affectedChunks));
            if (relitChunks.isEmpty()) {
                return;
            }

            instance.scheduleNextTick(ignored -> sendLighting(relitChunks));
        });

        instance.eventNode().addListener(PlayerChunkLoadEvent.class, event -> {
            Chunk chunk = instance.getChunk(event.getChunkX(), event.getChunkZ());
            if (chunk instanceof LightingChunk lightingChunk) {
                instance.scheduleNextTick(ignored -> {
                    lightingChunk.sendLighting();
                    if (hasBlockLightData(lightingChunk)) {
                        event.getPlayer().sendPacket(chunk.getFullDataPacket());
                    }
                });
            }
        });
    }

    private static boolean hasBlockLightData(Chunk chunk) {
        for (Section section : chunk.getSections()) {
            if (section.blockLight().array().length != 0) {
                return true;
            }
        }
        return false;
    }

    private static List<Chunk> loadedChunksAround(InstanceContainer instance, int centerChunkX, int centerChunkZ, int radius) {
        List<Chunk> chunks = new ArrayList<>();
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                Chunk chunk = instance.getChunk(chunkX, chunkZ);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            }
        }
        return chunks;
    }

    private static void sendLighting(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (chunk instanceof LightingChunk lightingChunk) {
                lightingChunk.sendLighting();
            }
        }
    }

    private static void registerCommands(
            InstanceContainer mainInstance,
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
            PermissionService permissionService,
            WorldAccessService worldAccessService,
            PvpZoneService pvpZoneService,
            SpawnService spawnService,
            SpawnService plotWorldService,
            SpawnService pvpMineWorldService,
            PlotService plotService,
            PlotRuntimeModule plotRuntimeModule,
            WorldLabelService worldLabelService,
            Path dataRoot,
            boolean devServer
    ) {
        FallnightCommandRegistrar registrar = new FallnightCommandRegistrar(
                MinecraftServer.getCommandManager(),
                permissionService,
                profileService,
                mineService,
                mineRankService,
                minesMenuService,
                payMenuService,
                rankService,
                gangService,
                vaultService,
                broadcastService,
                auctionService,
                auctionMenuService,
                shopService,
                shopMenuService,
                tagService,
                tagsMenuService,
                kitService,
                kitMenuService,
                crateService,
                itemDeliveryService,
                lotteryService,
                lotteryMenuService,
                votePartyService,
                kothService,
                kothGameplayModule,
                moderationSanctionsService,
                warningService,
                infoPagesService,
                directMessageService,
                defaultWorldService,
                spawnService,
                worldAccessService,
                pvpZoneService,
                plotWorldService,
                pvpMineWorldService,
                plotService,
                plotRuntimeModule,
                worldLabelService,
                dataRoot,
                devServer
        );

        CommandRegistrationResult result = registrar.registerAll();
        LOGGER.info(
                "Registered {} implemented commands.",
                result.implementedCommandCount()
        );
    }

    private static SpawnService resolveInitialDefaultWorld(String configuredWorld, SpawnService spawnService, SpawnService plotWorldService, SpawnService pvpMineWorldService) {
        if (configuredWorld != null) {
            if (configuredWorld.equalsIgnoreCase(plotWorldService.worldName()) || configuredWorld.equalsIgnoreCase("plotworld") || configuredWorld.equalsIgnoreCase("plots")) {
                return plotWorldService;
            }
            if (configuredWorld.equalsIgnoreCase(pvpMineWorldService.worldName()) || configuredWorld.equalsIgnoreCase("pvpmine") || configuredWorld.equalsIgnoreCase("minepvp")) {
                return pvpMineWorldService;
            }
        }
        return spawnService;
    }

    private static String resolveTransferHost(String configuredHost) {
        if (configuredHost == null || configuredHost.isBlank() || "0.0.0.0".equals(configuredHost)) {
            try {
                var request = java.net.http.HttpRequest.newBuilder(URI.create("https://api.ipify.org")).GET().build();
                String body = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                if (body != null && !body.isBlank()) {
                    return body.trim();
                }
            } catch (Exception ignored) {
            }
            return "127.0.0.1";
        }
        return configuredHost;
    }
}
