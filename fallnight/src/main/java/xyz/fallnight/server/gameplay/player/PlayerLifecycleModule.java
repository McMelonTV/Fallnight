package xyz.fallnight.server.gameplay.player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.ClientSettings;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.AuctionService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.InfoPagesService;
import xyz.fallnight.server.service.KitService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.service.TagService;

public final class PlayerLifecycleModule {
    private final PlayerProfileService profileService;
    private final DefaultWorldService defaultWorldService;
    private final SpawnService spawnService;
    private final KitService kitService;
    private final LegacyCustomItemService customItemService;
    private final TagService tagService;
    private final AuctionService auctionService;
    private final InfoPagesService infoPagesService;
    private final MineService mineService;
    private final SpawnService plotWorldService;
    private final SpawnService pvpMineWorldService;
    private Task autosaveTask;

    public PlayerLifecycleModule(
            PlayerProfileService profileService,
            DefaultWorldService defaultWorldService,
            SpawnService spawnService,
            KitService kitService,
            TagService tagService,
            AuctionService auctionService,
            InfoPagesService infoPagesService,
            MineService mineService,
            SpawnService plotWorldService,
            SpawnService pvpMineWorldService
    ) {
        this.profileService = profileService;
        this.defaultWorldService = defaultWorldService;
        this.spawnService = spawnService;
        this.kitService = kitService;
        this.customItemService = new LegacyCustomItemService();
        this.tagService = tagService;
        this.auctionService = auctionService;
        this.infoPagesService = infoPagesService;
        this.mineService = mineService;
        this.plotWorldService = plotWorldService;
        this.pvpMineWorldService = pvpMineWorldService;
    }

    private static boolean readBoolean(UserProfile profile, String key) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes");
        }
        return false;
    }

    private static long readLong(UserProfile profile, String key) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            UserProfile profile = profileService.playerJoin(event.getPlayer());
            long now = System.currentTimeMillis() / 1000L;
            if (!profile.getExtraData().containsKey("joinDate")) {
                profile.getExtraData().put("joinDate", now);
                profile.getExtraData().put("firstJoinPendingMessage", true);
            }
            profile.getExtraData().put("sessionStartedAt", now);
            profile.getExtraData().put("online", true);
            profile.getExtraData().put("lastActivityAt", now);
            profile.getExtraData().put("afk", false);
            SpawnService defaultWorld = defaultWorldService.currentWorld();
            event.setSpawningInstance(defaultWorld.instance());
            event.getPlayer().setRespawnPoint(SpawnService.normalizedSpawn(defaultWorld.spawn()));
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            applyWorldViewDistance(event.getPlayer(), event.getSpawnInstance());
            if (!event.isFirstSpawn()) {
                restorePostDeathState(event.getPlayer());
                return;
            }
            applyProfileState(event.getPlayer());
            grantFirstJoinItems(event.getPlayer());
            broadcastJoin(event.getPlayer());
            sendJoinExperience(event.getPlayer());
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            UserProfile profile = profileService.getOrCreate(event.getPlayer());
            long now = System.currentTimeMillis() / 1000L;
            profile.getExtraData().put("lastSeen", now);
            profile.getExtraData().put("online", false);
            Object started = profile.getExtraData().get("sessionStartedAt");
            long sessionStart = started instanceof Number number ? number.longValue() : now;
            long existing = readLong(profile, "totalOnlineTime");
            profile.getExtraData().put("totalOnlineTime", Math.max(0L, existing + Math.max(0L, now - sessionStart)));
            profile.getExtraData().remove("sessionStartedAt");
            broadcastQuit(event.getPlayer(), profile);
            profileService.save(profile);
            AdminModeService.clear(profile);
            profileService.playerQuit(event.getPlayer());
        });

        autosaveTask = MinecraftServer.getSchedulerManager()
                .buildTask(profileService::saveAllOnline)
                .repeat(TaskSchedule.seconds(30))
                .schedule();
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        profileService.saveAllOnline();
    }

    private void applyProfileState(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        boolean fly = readBoolean(profile, "fly");
        player.setAllowFlying(fly);
        if (!fly && player.isFlying()) {
            player.setFlying(false);
        }

        boolean vanish = readBoolean(profile, "vanish");
        player.setInvisible(vanish);
        player.setAutoViewable(!vanish);

        Object size = profile.getExtraData().get("playerSize");
        if (size instanceof Number number) {
            PlayerSizing.apply(player, number.intValue());
        }

        restoreSnapshots(player, profile);
    }

    private void grantFirstJoinItems(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        tagService.grantJoinTags(profile);
        if (readBoolean(profile, "receivedStartItems") || readBoolean(profile, "hasReceivedStartItems")) {
            profileService.save(profile);
            return;
        }
        KitService.ClaimResult kitResult = kitService.claimKit(player, profile, "starter");
        boolean guideDelivered = player.getInventory().addItemStack(customItemService.guideBook(infoPagesService.guidePage()));
        if (kitResult.status() != KitService.ClaimStatus.SUCCESS || !guideDelivered) {
            profileService.save(profile);
            return;
        }
        profile.getExtraData().put("receivedStartItems", true);
        profile.getExtraData().put("hasReceivedStartItems", true);
        profileService.save(profile);
    }

    private void sendJoinExperience(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        String currentNewsVersion = infoPagesService.newsReleaseVersion();
        Object seen = profile.getExtraData().getOrDefault("lastPatchNotesVersion", profile.getExtraData().get("lastPatchNotes"));
        if (!(seen instanceof String text) || !currentNewsVersion.equals(text)) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§7There was a new patch while you were offline. Do §b/news §r§7to view the patchnotes."));
        }
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                "§8§l<--§bFN§8--> "
                        + "\n§r§7§b Fallnight§7 useful links§r"
//                        + "\n§r §b§l> §r§7Vote: §bvote.fallnight.xyz §8(get rewards like a free rankup)"
//                        + "\n§r §b§l> §r§7Shop: §bshop.fallnight.xyz"
//                        + "\n§r §b§l> §r§7Discord: §bdiscord.fallnight.xyz"
                        + "\n§r §b§l> §r§7i cba to set up any rn"
                        + "\n§r§8§l<--++-->⛏"
        ));
        player.showTitle(Title.title(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§b<§o§8§lFallnight§r§b>"),
                Component.empty(),
                Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(1500))
        ));
        player.playSound(Sound.sound(Key.key("minecraft:item.totem.use"), Sound.Source.MASTER, 1f, 1f));
        int expiredClaims = auctionService.listClaims(player.getUsername()).size();
        if (expiredClaims > 0) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                    "§r§b§l> §r§7You have §b" + expiredClaims + "§7 expired auction item(s) that you can reclaim."
            ));
        }
    }

    private void broadcastJoin(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        if (readBoolean(profile, "vanish")) {
            profile.getExtraData().remove("firstJoinPendingMessage");
            return;
        }
        boolean firstJoin = readBoolean(profile, "firstJoinPendingMessage");
        String message = firstJoin
                ? "§r§8§l[§b+§8]§r§7 Please welcome §b" + player.getUsername() + "§r§7 to the server!"
                : "§r§8§l[§b+§8]§r §7" + player.getUsername();
        broadcastVisible(message);
        profile.getExtraData().remove("firstJoinPendingMessage");
    }

    private void broadcastQuit(Player player, UserProfile profile) {
        if (readBoolean(profile, "vanish")) {
            return;
        }
        broadcastVisible("§r§8§l[§b-§8]§r §7" + player.getUsername());
    }

    private void broadcastVisible(String legacyMessage) {
        var component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacyMessage);
        for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            UserProfile targetProfile = profileService.getOrCreate(online);
            if (readBoolean(targetProfile, "vanish")) {
                continue;
            }
            online.sendMessage(component);
        }
    }

    private void applyWorldViewDistance(Player player, Instance instance) {
        if (player == null || instance == null) {
            return;
        }
        ClientSettings settings = player.getSettings();
        if (settings == null) {
            return;
        }
        byte target = targetViewDistance(player, instance);
        if (settings.viewDistance() == target) {
            return;
        }
        player.refreshSettings(new ClientSettings(
                settings.locale(),
                target,
                settings.chatMessageType(),
                settings.chatColors(),
                settings.displayedSkinParts(),
                settings.mainHand(),
                settings.enableTextFiltering(),
                settings.allowServerListings(),
                settings.particleSetting()
        ));
    }

    private byte targetViewDistance(Player player, Instance instance) {
        if (instance == plotWorldService.instance() || instance == pvpMineWorldService.instance()) {
            return 8;
        }
        MineDefinition mine = mineService.findByCoordinates(
                player.getPosition().blockX(),
                player.getPosition().blockY(),
                player.getPosition().blockZ(),
                spawnService.worldName()
        ).orElse(null);
        if (mine != null) {
            return 4;
        }
        return 6;
    }

    private void restorePostDeathState(Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        if (!readBoolean(profile, "restoreInventoryAfterDeath")) {
            return;
        }
        restoreSnapshots(player, profile);
        Object level = profile.getExtraData().get("restoreLevelAfterDeath");
        if (level instanceof Number number) {
            player.setLevel(number.intValue());
        }
        Object exp = profile.getExtraData().get("restoreExpAfterDeath");
        if (exp instanceof Number number) {
            player.setExp(number.floatValue());
        }
        profile.getExtraData().remove("restoreInventoryAfterDeath");
        profile.getExtraData().remove("restoreLevelAfterDeath");
        profile.getExtraData().remove("restoreExpAfterDeath");
        profileService.save(profile);
    }

    @SuppressWarnings("unchecked")
    private void restoreSnapshots(Player player, UserProfile profile) {
        Object rawInventory = profile.getExtraData().get("inventorySnapshot");
        if (rawInventory instanceof Iterable<?> iterable) {
            player.getInventory().clear();
            for (Object entry : iterable) {
                if (!(entry instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                Object slotValue = map.get("slot");
                if (!(slotValue instanceof Number number)) {
                    continue;
                }
                player.getInventory().setItemStack(number.intValue(), PlayerProfileService.deserializeSnapshotItem(map));
            }
        }
        Object rawArmor = profile.getExtraData().get("armorSnapshot");
        if (rawArmor instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (!(entry instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                Object armorSlot = map.get("armorSlot");
                if (!(armorSlot instanceof String slotName)) {
                    continue;
                }
                switch (slotName.toLowerCase()) {
                    case "helmet" ->
                            player.getInventory().setEquipment(net.minestom.server.entity.EquipmentSlot.HELMET, player.getHeldSlot(), PlayerProfileService.deserializeSnapshotItem(map));
                    case "chestplate" ->
                            player.getInventory().setEquipment(net.minestom.server.entity.EquipmentSlot.CHESTPLATE, player.getHeldSlot(), PlayerProfileService.deserializeSnapshotItem(map));
                    case "leggings" ->
                            player.getInventory().setEquipment(net.minestom.server.entity.EquipmentSlot.LEGGINGS, player.getHeldSlot(), PlayerProfileService.deserializeSnapshotItem(map));
                    case "boots" ->
                            player.getInventory().setEquipment(net.minestom.server.entity.EquipmentSlot.BOOTS, player.getHeldSlot(), PlayerProfileService.deserializeSnapshotItem(map));
                }
            }
        }
    }
}
