package xyz.fallnight.server.gameplay.pvp;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.gameplay.player.PlayerSizing;
import xyz.fallnight.server.persistence.pvp.YamlPvpZoneRepository;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PvpZoneService;
import xyz.fallnight.server.service.SpawnService;
import java.util.List;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerStartSneakingEvent;
import net.minestom.server.event.player.PlayerStopSneakingEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public final class PvpGameplayModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final long DEFAULT_TAG_TIMEOUT_MILLIS = 10_000L;
    private static final Set<String> BLOCKED_COMBAT_COMMANDS = Set.of(
            "fly",
            "kill",
            "size",
            "shop",
            "mine",
            "mines",
            "spawn",
            "world",
            "worlds",
            "plots",
            "plot",
            "auction",
            "ah",
            "vault",
            "warp"
    );
    private static final Set<String> IMMEDIATE_PVP_BLOCKED_COMMANDS = Set.of("fly", "kill", "mines", "size", "plot", "shop");
    private static final Set<String> DELAYED_PVP_COMMANDS = Set.of("fly", "kill", "mines", "size", "plot", "shop", "mine", "teleport", "world", "spawn", "plots", "crates", "auction", "vault", "warp");

    private final PvpZoneService pvpZoneService;
    private final PlayerProfileService profileService;
    private final GangService gangService;
    private final CommandManager commandManager;
    private final SpawnService spawnWorldService;
    private final SpawnService pvpMineWorldService;
    private final LegacyCustomItemService itemService;
    private final CombatTagService combatTagService;
    private final CombatEnchantRuntime combatEnchantRuntime;
    private final AchievementService achievementService;
    private final EventNode<Event> eventNode;
    private final java.util.concurrent.ConcurrentMap<UUID, PendingCommand> pendingCommands = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<UUID, String> executingDelayedCommands = new java.util.concurrent.ConcurrentHashMap<>();
    private Task combatTagSweepTask;

    public PvpGameplayModule(
            PvpZoneService pvpZoneService,
            PlayerProfileService profileService,
            GangService gangService,
            PermissionService permissionService,
            CommandManager commandManager,
            SpawnService spawnWorldService,
            SpawnService pvpMineWorldService
    ) {
        this(pvpZoneService, profileService, gangService, permissionService, commandManager, spawnWorldService, pvpMineWorldService, DEFAULT_TAG_TIMEOUT_MILLIS);
    }

    public PvpGameplayModule(
            PvpZoneService pvpZoneService,
            PlayerProfileService profileService,
            GangService gangService,
            PermissionService permissionService,
            CommandManager commandManager,
            SpawnService spawnWorldService,
            SpawnService pvpMineWorldService,
            long combatTagTimeoutMillis
    ) {
        this.pvpZoneService = pvpZoneService;
        this.profileService = profileService;
        this.gangService = gangService;
        this.commandManager = commandManager;
        this.spawnWorldService = spawnWorldService;
        this.pvpMineWorldService = pvpMineWorldService;
        this.itemService = new LegacyCustomItemService();
        this.combatTagService = new CombatTagService(combatTagTimeoutMillis);
        this.combatEnchantRuntime = new CombatEnchantRuntime(itemService);
        this.achievementService = new AchievementService(profileService);
        this.eventNode = EventNode.all("pvp-gameplay");
    }

    public static PvpGameplayModule createDefaults(
            Path dataRoot,
            PlayerProfileService profileService,
            GangService gangService,
            PermissionService permissionService,
            CommandManager commandManager,
            SpawnService spawnWorldService,
            SpawnService pvpMineWorldService
    ) {
        PvpZoneService zoneService = new PvpZoneService(new YamlPvpZoneRepository(dataRoot.resolve("pvpzones.yml"), spawnWorldService.worldName()));
        return new PvpGameplayModule(zoneService, profileService, gangService, permissionService, commandManager, spawnWorldService, pvpMineWorldService);
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

    private static void clearCombatLoggerInventory(Player player) {
        player.getInventory().clear();
        for (net.minestom.server.entity.EquipmentSlot slot : net.minestom.server.entity.EquipmentSlot.armors()) {
            player.getInventory().setEquipment(slot, player.getHeldSlot(), ItemStack.AIR);
        }
        player.setLevel(0);
        player.setExp(0f);
    }

    private static boolean changedPosition(Player player, PlayerMoveEvent event) {
        return Double.compare(player.getPosition().x(), event.getNewPosition().x()) != 0
                || Double.compare(player.getPosition().y(), event.getNewPosition().y()) != 0
                || Double.compare(player.getPosition().z(), event.getNewPosition().z()) != 0;
    }

    private static String commandRoot(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return "";
        }
        int firstSpace = normalized.indexOf(' ');
        String root = firstSpace == -1 ? normalized : normalized.substring(0, firstSpace);
        int namespaceSep = root.indexOf(':');
        if (namespaceSep != -1 && namespaceSep + 1 < root.length()) {
            root = root.substring(namespaceSep + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public void register() {
        try {
            pvpZoneService.loadAll();
            pvpZoneService.renameWorldLabel("spawn", spawnWorldService.worldName());
            pvpZoneService.renameWorldLabel("PvPMine", pvpMineWorldService.worldName());
            pvpZoneService.renameWorldLabel("pvpmine", pvpMineWorldService.worldName());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        eventNode.addListener(EntityDamageEvent.class, this::onEntityDamage);
        eventNode.addListener(EntityTeleportEvent.class, this::onEntityTeleport);
        eventNode.addListener(PlayerCommandEvent.class, this::onPlayerCommand);
        eventNode.addListener(PlayerMoveEvent.class, this::onPlayerMove);
        eventNode.addListener(PlayerDeathEvent.class, this::onPlayerDeath);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        eventNode.addListener(PlayerStartSneakingEvent.class, event -> cancelDelayed(event.getPlayer(), true));
        eventNode.addListener(PlayerStopSneakingEvent.class, event -> cancelDelayed(event.getPlayer(), true));

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        combatTagSweepTask = MinecraftServer.getSchedulerManager().buildTask(this::tickCombatTags).repeat(TaskSchedule.tick(20)).schedule();
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        if (combatTagSweepTask != null) {
            combatTagSweepTask.cancel();
            combatTagSweepTask = null;
        }
        combatTagService.clearAll();
    }

    public PvpZoneService pvpZoneService() {
        return pvpZoneService;
    }

    private void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        Damage damage = event.getDamage();
        if (damage == null) {
            return;
        }

        if (damage.getAttacker() instanceof Player attackerForCancel) {
            cancelDelayed(attackerForCancel, true);
        }
        cancelDelayed(victim, true);

        if (damage.getType().name().equalsIgnoreCase("fall")) {
            achievementService.onFallDamage(victim, profileService.getOrCreate(victim), damage.getAmount());
        }

        combatEnchantRuntime.onVictimDamaged(victim, damage);

        Entity attackerEntity = damage.getAttacker();
        if (!(attackerEntity instanceof Player attacker) || attacker.equals(victim)) {
            return;
        }

        if (friendlyFireBlocked(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage(CommandMessages.error("Friendly fire is disabled."));
            return;
        }

        boolean attackerAdmin = AdminModeService.isEnabled(profileService.getOrCreate(attacker));

        if (!attackerAdmin && !isInPvpZone(attacker)) {
            event.setCancelled(true);
            attacker.sendActionBar(LEGACY.deserialize("§8[§bFN§8]§r\n§r§7You can't PvP here."));
            return;
        }
        if (!attackerAdmin && pvpZoneService.isSafe(victim.getPosition().blockX(), victim.getPosition().blockY(), victim.getPosition().blockZ(), zoneWorldName(victim))) {
            event.setCancelled(true);
            attacker.sendActionBar(LEGACY.deserialize("§8[§bFN§8]§r\n§r§7You can't PvP here."));
            return;
        }
        if (!attackerAdmin && !isInPvpZone(victim)) {
            event.setCancelled(true);
            attacker.sendActionBar(LEGACY.deserialize("§8[§bFN§8]§r\n§r§7You can't PvP here."));
            return;
        }

        if (!handleAttackerDurability(attacker, event)) {
            return;
        }
        handleVictimArmorDurability(attacker, victim);
        handleVictimHeldItemAutoRepair(victim);
        combatEnchantRuntime.onAttack(attacker, victim, damage, attacker.getItemInMainHand());

        boolean attackerWasTagged = combatTagService.isTagged(attacker.getUuid());
        boolean victimWasTagged = combatTagService.isTagged(victim.getUuid());
        combatTagService.tagPlayers(attacker.getUuid(), victim.getUuid(), attacker.getUsername());

        if (!attackerWasTagged) {
            attacker.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§b§l>§r§7 You are now in combat. Please don't log out, or you will be killed."));
        }
        if (!victimWasTagged) {
            victim.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§b§l>§r§7 You are now in combat. Please don't log out, or you will be killed."));
        }
    }

    private void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        if (isExecutingDelayedCommand(player, event.getCommand())) {
            return;
        }
        if (isInPvpZone(player)) {
            String root = commandRoot(event.getCommand());
            if (IMMEDIATE_PVP_BLOCKED_COMMANDS.contains(root)) {
                event.setCancelled(true);
                player.sendMessage(CommandMessages.error("You can't execute this command in a PvP area!"));
                return;
            }
            if (DELAYED_PVP_COMMANDS.contains(root)) {
                if (combatTagService.remainingMillis(player.getUuid()) > 0L) {
                    event.setCancelled(true);
                    long remainingSeconds = Math.max(1L, (combatTagService.remainingMillis(player.getUuid()) + 999L) / 1_000L);
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§r§c§l>§r§7 You can't execute this command while in combat. Please wait §c" + remainingSeconds + "§r§7 more seconds."));
                    return;
                }
                event.setCancelled(true);
                scheduleDelayed(player, event.getCommand());
                return;
            }
        }
    }

    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        enforcePvpMovementRules(player, event.getNewPosition());
        if (changedPosition(player, event)) {
            cancelDelayed(player, true);
        }
    }

    private boolean handleAttackerDurability(Player attacker, EntityDamageEvent event) {
        ItemStack weapon = attacker.getItemInMainHand();
        if (weapon == null || weapon.isAir() || !itemService.isDurabilityItem(weapon)) {
            return true;
        }
        if (itemService.maxDamage(weapon) <= itemService.currentDamage(weapon) + 1) {
            event.setCancelled(true);
            attacker.sendActionBar(LEGACY.deserialize("§r§8[§bFN§8]\n§r§7Your weapon has no durability left.\n§r§7Repair it using §b/forge§r§7."));
            return false;
        }
        weapon = applyCombatAutoRepair(attacker, weapon);
        if (shouldUseDurability(weapon)) {
            attacker.setItemInMainHand(itemService.applyDamage(weapon, 1));
        } else {
            attacker.setItemInMainHand(weapon);
        }
        return true;
    }

    private void handleVictimArmorDurability(Player attacker, Player victim) {
        boolean attackerItemDurable = itemService.isDurabilityItem(attacker.getItemInMainHand());
        for (EquipmentSlot slot : EquipmentSlot.armors()) {
            ItemStack armor = victim.getInventory().getEquipment(slot, victim.getHeldSlot());
            if (armor == null || armor.isAir() || !itemService.isDurabilityItem(armor)) {
                continue;
            }
            if (attackerItemDurable && itemService.maxDamage(armor) <= itemService.currentDamage(armor) + 1) {
                victim.sendActionBar(LEGACY.deserialize("§r§8[§bFN§8]\n§r§7Your armor has no durability left.\n§r§7Repair it using §b/forge§r§7."));
                victim.getInventory().addItemStack(armor);
                victim.getInventory().setEquipment(slot, victim.getHeldSlot(), ItemStack.AIR);
                continue;
            }
            armor = applyCombatAutoRepair(victim, armor);
            if (attackerItemDurable && shouldUseDurability(armor)) {
                victim.getInventory().setEquipment(slot, victim.getHeldSlot(), itemService.applyDamage(armor, 1));
            } else {
                victim.getInventory().setEquipment(slot, victim.getHeldSlot(), armor);
            }
        }
    }

    private void handleVictimHeldItemAutoRepair(Player victim) {
        ItemStack held = victim.getItemInMainHand();
        if (held == null || held.isAir() || !itemService.isDurabilityItem(held)) {
            return;
        }
        victim.setItemInMainHand(applyCombatAutoRepair(victim, held));
    }

    private ItemStack applyCombatAutoRepair(Player player, ItemStack item) {
        if (item == null || item.isAir() || !itemService.isDurabilityItem(item)) {
            return item;
        }
        if (!itemService.customEnchants(item).containsKey(FallnightCustomEnchantRegistry.AUTOREPAIR)) {
            return item;
        }
        if (itemService.maxDamage(item) + itemService.currentDamage(item) <= 0) {
            return item;
        }
        if (!consumeRepairShard(player)) {
            return item;
        }
        return itemService.applyDamage(item, -400);
    }

    private boolean consumeRepairShard(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (itemService.customItemId(stack) != 3) {
                continue;
            }
            int next = stack.amount() - 1;
            player.getInventory().setItemStack(slot, next <= 0 ? ItemStack.AIR : stack.withAmount(next));
            return true;
        }
        return false;
    }

    private boolean shouldUseDurability(ItemStack item) {
        Integer unbreaking = itemService.customEnchants(item).get(FallnightCustomEnchantRegistry.UNBREAKING_CUSTOM);
        if (unbreaking == null || unbreaking <= 0) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(101) < unbreaking * 10;
    }

    private void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player player) {
            enforcePvpMovementRules(player, event.getNewPosition());
        }
    }

    private void enforcePvpMovementRules(Player player, net.minestom.server.coordinate.Pos destination) {
        UserProfile profile = profileService.getOrCreate(player);
        if (AdminModeService.isEnabled(profile)) {
            return;
        }
        String worldName = zoneWorldName(player);
        boolean inPvpMine = isInPvpMine(player);
        boolean toPvp = inPvpMine || (destination != null && pvpZoneService.isInPvpZone(destination.blockX(), destination.blockY(), destination.blockZ(), worldName));
        boolean fromPvp = inPvpMine || pvpZoneService.isInPvpZone(player.getPosition().blockX(), player.getPosition().blockY(), player.getPosition().blockZ(), worldName);
        if (toPvp) {
            if (player.isAllowFlying() || player.isFlying()) {
                player.setAllowFlying(false);
                player.setFlying(false);
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§8[§bFN§8]\n§r§7Flying is not enabled in PvP"));
            }
            Object size = profile.getExtraData().get("playerSize");
            if (size instanceof Number number && number.intValue() != 100) {
                PlayerSizing.apply(player, 100);
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§8[§bFN§8]\n§r§7/size is not enabled in PvP"));
            }
            return;
        }
        if (fromPvp) {
            if (readBoolean(profile, "fly")) {
                player.setAllowFlying(true);
            }
            Object size = profile.getExtraData().get("playerSize");
            if (size instanceof Number number) {
                PlayerSizing.apply(player, number.intValue());
            }
        }
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        UserProfile victimProfile = profileService.getOrCreate(victim);
        victimProfile.addDeath();

        UUID killerId = combatTagService.lastHitter(victim.getUuid());
        if (killerId == null) {
            Damage lastDamage = victim.getLastDamageSource();
            if (lastDamage != null && lastDamage.getAttacker() instanceof Player playerAttacker) {
                killerId = playerAttacker.getUuid();
            }
        }

        if (killerId != null && !killerId.equals(victim.getUuid())) {
            Player killer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(killerId);
            if (killer == null) {
                Damage lastDamage = victim.getLastDamageSource();
                if (lastDamage != null && lastDamage.getAttacker() instanceof Player playerAttacker && playerAttacker.getUuid().equals(killerId)) {
                    killer = playerAttacker;
                }
            }
            if (killer != null) {
                UserProfile killerProfile = profileService.getOrCreate(killer);
                killerProfile.addKill();
                if (isInPvpMine(killer)) {
                    killerProfile.addPrestigePoints(100);
                    killer.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                            "§r§b§l> §r§7You earned §b100 §opp§r§7 for killing §b" + victim.getUsername() + "§r§7."
                    ));
                }
                profileService.save(killerProfile);
                combatEnchantRuntime.onKill(killer, killer.getItemInMainHand());
            }
        }

        combatEnchantRuntime.onDeath(victim);
        broadcastTaggedDeath(victim, killerId);

        if (!isInPvpMine(victim)) {
            profileService.save(victim);
            victimProfile.getExtraData().put("restoreInventoryAfterDeath", true);
            victimProfile.getExtraData().put("restoreLevelAfterDeath", victim.getLevel());
            victimProfile.getExtraData().put("restoreExpAfterDeath", victim.getExp());
            event.setDeathText(null);
            event.setChatMessage(null);
        }
        combatTagService.clear(victim.getUuid());
    }

    private void broadcastTaggedDeath(Player victim, UUID killerId) {
        if (killerId == null || killerId.equals(victim.getUuid())) {
            return;
        }
        Damage lastDamage = victim.getLastDamageSource();
        String killerName = combatTagService.lastHitterName(victim.getUuid());
        Player killer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(killerId);
        if (killerName == null && killer != null) {
            killerName = killer.getUsername();
        }
        if (killerName == null && lastDamage != null && lastDamage.getAttacker() instanceof Player playerAttacker) {
            killerName = playerAttacker.getUsername();
        }
        if (killerName == null || killerName.isBlank()) {
            return;
        }

        String message;
        if (lastDamage != null && DamageType.MOB_PROJECTILE.equals(lastDamage.getType())) {
            Double distance = null;
            if (lastDamage.getSourcePosition() != null) {
                double dx = victim.getPosition().x() - lastDamage.getSourcePosition().x();
                double dy = victim.getPosition().y() - lastDamage.getSourcePosition().y();
                double dz = victim.getPosition().z() - lastDamage.getSourcePosition().z();
                distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0) / 10.0;
            }
            String suffix = distance == null ? "" : "§8(" + distance + "m)§r";
            message = random(List.of(
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was sniped by §b" + killerName + "§r§7." + suffix,
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was shot to death by §b" + killerName + "§r§7." + suffix,
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was shot by §b" + killerName + "§r§7." + suffix
            ));
        } else if (lastDamage != null && DamageType.FALL.equals(lastDamage.getType())) {
            message = random(List.of(
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 fell to death after fighting §b" + killerName + "§r§7.",
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 fell to death while trying to escape §b" + killerName + "§r§7.",
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 got crushed while fighting §b" + killerName + "§r§7."
            ));
        } else if (lastDamage != null && (DamageType.IN_FIRE.equals(lastDamage.getType()) || DamageType.LAVA.equals(lastDamage.getType()) || DamageType.LIGHTNING_BOLT.equals(lastDamage.getType()))) {
            message = random(List.of(
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 burned to death after fighting §b" + killerName + "§r§7.",
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was fried by §b" + killerName + "§r§7.",
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was fried after escaping §b" + killerName + "§r§7.",
                "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 went up in flames after fighting §b" + killerName + "§r§7."
            ));
        } else if (lastDamage != null && (DamageType.PLAYER_ATTACK.equals(lastDamage.getType()) || DamageType.MOB_ATTACK.equals(lastDamage.getType()))) {
            String weapon = killer == null ? "" : weaponName(killer.getItemInMainHand());
            List<String> base = weapon.isBlank()
                ? List.of(
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was destroyed by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was mauled by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was killed by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was pacified by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was obliterated by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 got their feelings hurt by §b" + killerName + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was neutralized by §b" + killerName + "§r§7."
                )
                : List.of(
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was destroyed by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was mauled by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was killed by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was pacified by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was obliterated by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 got their feelings hurt by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7.",
                    "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 was neutralized by §b" + killerName + "§r§7 with their §r§f" + weapon + "§r§7."
                );
            message = random(base);
        } else {
            message = "§8[§bFN§8] §r§7§b" + victim.getUsername() + "§r§7 died while trying to escape from §b" + killerName + "§r§7.";
        }

        var component = LEGACY.deserialize(message);
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(viewer -> viewer.sendMessage(component));
    }

    private static String random(List<String> messages) {
        return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }

    private static String weaponName(ItemStack item) {
        if (item == null || item.isAir()) {
            return "";
        }
        var name = item.get(net.minestom.server.component.DataComponents.CUSTOM_NAME);
        if (name != null) {
            return LEGACY.serialize(name);
        }
        return item.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Player player = event.getPlayer();
        cancelDelayed(player, false);
        UUID playerId = player.getUuid();
        if (!combatTagService.isTagged(playerId)) {
            combatTagService.clear(playerId);
            return;
        }

        UserProfile profile = profileService.getOrCreate(player);
        profile.addDeath();
        clearCombatLoggerInventory(player);

        UUID killerId = combatTagService.lastHitter(playerId);
        if (killerId != null && !killerId.equals(playerId)) {
            Player killer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(killerId);
            if (killer != null) {
                UserProfile killerProfile = profileService.getOrCreate(killer);
                killerProfile.addKill();
                if (isInPvpMine(killer)) {
                    killerProfile.addPrestigePoints(100);
                    killer.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                            "§r§b§l> §r§7You earned §b100 §opp§r§7 for killing §b" + player.getUsername() + "§r§7."
                    ));
                }
                profileService.save(killerProfile);
                combatEnchantRuntime.onKill(killer, killer.getItemInMainHand());
            }
        }

        profileService.save(profile);

        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(viewer ->
                viewer.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        "§r§8[§bFN§8] §r§7§b" + player.getUsername() + "§r§7 has been killed for combat logging."
                ))
        );
        combatTagService.clear(playerId);
    }

    private boolean friendlyFireBlocked(Player attacker, Player victim) {
        Gang a = gangService.findGangForUser(attacker.getUsername()).orElse(null);
        Gang b = gangService.findGangForUser(victim.getUsername()).orElse(null);
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.isAlliedWith(b);
    }

    private void scheduleDelayed(Player player, String command) {
        cancelDelayed(player, false);
        player.sendMessage(LEGACY.deserialize("§r§b§l>§r§7 The command will be executed in §b5 seconds§7. Please hold still while waiting for the command to execute."));
        Task task = MinecraftServer.getSchedulerManager().buildTask(() -> {
            PendingCommand pending = pendingCommands.remove(player.getUuid());
            if (pending == null) {
                return;
            }
            String normalizedCommand = normalizeCommand(pending.command());
            executingDelayedCommands.put(player.getUuid(), normalizedCommand);
            try {
                MinecraftServer.getCommandManager().execute(player, pending.command());
            } finally {
                executingDelayedCommands.remove(player.getUuid(), normalizedCommand);
            }
        }).delay(TaskSchedule.seconds(5)).schedule();
        pendingCommands.put(player.getUuid(), new PendingCommand(command, task));
    }

    private boolean isExecutingDelayedCommand(Player player, String command) {
        String executingCommand = executingDelayedCommands.get(player.getUuid());
        if (executingCommand == null) {
            return false;
        }
        return executingCommand.equals(normalizeCommand(command));
    }

    private void cancelDelayed(Player player, boolean notify) {
        PendingCommand pending = pendingCommands.remove(player.getUuid());
        if (pending == null) {
            return;
        }
        pending.task().cancel();
        if (notify) {
            player.sendMessage(CommandMessages.error("You have cancelled the command!"));
        }
    }

    private boolean isInPvpZone(Player player) {
        if (player.getInstance() == null) {
            return false;
        }
        if (isInPvpMine(player)) {
            return true;
        }
        return pvpZoneService.isInPvpZone(
                player.getPosition().blockX(),
                player.getPosition().blockY(),
                player.getPosition().blockZ(),
                zoneWorldName(player)
        );
    }

    private boolean isInPvpMine(Player player) {
        return pvpMineWorldService != null && player.getInstance() == pvpMineWorldService.instance();
    }

    private String zoneWorldName(Player player) {
        if (player == null || player.getInstance() == null) {
            return spawnWorldService.worldName();
        }
        if (player.getInstance() == spawnWorldService.instance()) {
            return spawnWorldService.worldName();
        }
        if (pvpMineWorldService != null && player.getInstance() == pvpMineWorldService.instance()) {
            return pvpMineWorldService.worldName();
        }
        return spawnWorldService.worldName();
    }

    private void tickCombatTags() {
        for (UUID playerId : combatTagService.expireTags()) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
            if (player == null) {
                continue;
            }
            player.sendMessage(LEGACY.deserialize("§r§b§l> §r§7You are no longer in combat. You can now logout safely."));
        }
    }

    private record PendingCommand(String command, Task task) {
    }
}
