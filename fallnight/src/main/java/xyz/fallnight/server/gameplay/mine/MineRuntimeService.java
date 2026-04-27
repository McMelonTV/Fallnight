package xyz.fallnight.server.gameplay.mine;

import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.domain.mine.MineRegion;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.AchievementService;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.util.NumberFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MineRuntimeService {
    private static final List<String> REQUIRED_PRESTIGE_KEYS = List.of(
            "required_prestige",
            "requiredPrestige",
            "fallnight:required_prestige"
    );

    private final MineService mineService;
    private final PlayerProfileService profileService;
    private final Instance managedInstance;
    private final SpawnService managedWorldService;
    private final Map<String, Double> localPrices;
    private final LegacyCustomItemService customItemService;
    private final AchievementService achievementService;
    private final ConcurrentMap<Integer, MineRuntimeState> mineStates;
    private final Random random;
    private final double regenThreshold;
    private final long timedRegenIntervalMillis;
    private long timedRegenRemainingMillis;
    private long lastTimedRegenCheckAtMillis;
    private int autoRegenCursor;

    public MineRuntimeService(
            MineService mineService,
            PlayerProfileService profileService,
            Instance managedInstance,
            SpawnService managedWorldService,
            Map<String, Double> localPrices
    ) {
        this.mineService = mineService;
        this.profileService = profileService;
        this.managedInstance = managedInstance;
        this.managedWorldService = managedWorldService;
        this.localPrices = localPrices;
        this.customItemService = new LegacyCustomItemService();
        this.achievementService = new AchievementService(profileService);
        this.mineStates = new ConcurrentHashMap<>();
        this.random = new Random();
        this.regenThreshold = 0.85d;
        this.timedRegenIntervalMillis = 1_200_000L;
        this.timedRegenRemainingMillis = timedRegenIntervalMillis;
        this.lastTimedRegenCheckAtMillis = 0L;
        this.autoRegenCursor = 0;
    }

    private static String normalizeNamespace(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static long readLong(Object value) {
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

    private static double blockHardness(Block block) {
        if (block == null || block == Block.AIR) {
            return 1.5D;
        }
        String key = block.key().asString();
        if ("minecraft:diamond_block".equals(key)) {
            return 4.25D;
        }
        if ("minecraft:iron_block".equals(key)) {
            return 3.5D;
        }
        if ("minecraft:redstone_block".equals(key)) {
            return 3.0D;
        }
        return block.registry().hardness();
    }

    public Optional<MineDefinition> findMineAt(Point point, Instance instance) {
        if (point == null || instance == null || instance != managedInstance) {
            return Optional.empty();
        }

        Optional<MineDefinition> direct = mineService.findByCoordinates(
                point.blockX(),
                point.blockY(),
                point.blockZ(),
                managedWorldService.worldName()
        );
        if (direct.isPresent()) {
            return direct;
        }

        return mineService.allMines().stream()
                .filter(mine -> !mine.isDisabled())
                .filter(mine -> mine.getWorld() != null && mine.getWorld().equalsIgnoreCase(managedWorldService.worldName()))
                .filter(mine -> mine.getRegion() != null)
                .filter(mine -> mine.getRegion().contains(point.blockX(), point.blockY(), point.blockZ()))
                .findFirst();
    }

    public AccessResult checkAccess(Player player, MineDefinition mine) {
        UserProfile profile = profileService.getOrCreate(player);
        if (AdminModeService.isEnabled(profile)) {
            return AccessResult.allow();
        }
        int requiredRank = Math.max(1, mine.getId() + 1);
        int requiredPrestige = requiredPrestige(mine);

        if (profile.getMineRank() < requiredRank - 1) {
            return AccessResult.deny("Need rank " + requiredRank + " for mine " + mine.getName() + ".");
        }
        if (profile.getPrestige() < requiredPrestige) {
            return AccessResult.deny("Need prestige " + requiredPrestige + " for mine " + mine.getName() + ".");
        }
        return AccessResult.allow();
    }

    public double handleBlockBreak(Player player, MineDefinition mine, Block brokenBlock, Point point) {
        if (brokenBlock == null || brokenBlock == Block.AIR || point == null) {
            return 0d;
        }

        return handleBlockBreaks(player, mine, java.util.List.of(point));
    }

    public double handleBlockBreaks(Player player, MineDefinition mine, java.util.List<Point> points) {
        if (points == null || points.isEmpty()) {
            return 0d;
        }

        ItemStack tool = player.getItemInMainHand();
        if (customItemService.isDurabilityItem(tool) && customItemService.isBroken(tool)) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§r§8[§bFN§8]\n§r§7Your pickaxe has no durability left.\n§r§7Repair it using §b/forge§r§7."));
            return 0D;
        }
        MineEnchantRuntime.MiningModifiers modifiers = MineEnchantRuntime.evaluate(tool, customItemService);
        double baseCredit = 0D;
        MineRuntimeState state = stateFor(mine);
        int broken = 0;
        String triggerBlock = null;
        double resourceHardness = 1.5D;
        for (Point point : points) {
            if (point == null) {
                continue;
            }
            Block brokenBlock = managedInstance.getBlock(point.blockX(), point.blockY(), point.blockZ());
            if (brokenBlock == null || brokenBlock == Block.AIR) {
                continue;
            }
            if (triggerBlock == null) {
                resourceHardness = blockHardness(brokenBlock);
            }
            managedInstance.setBlock(point.blockX(), point.blockY(), point.blockZ(), Block.AIR);
            state.recordDepletion(point);
            String blockNamespace = brokenBlock.key().asString().toLowerCase(Locale.ROOT);
            if (triggerBlock == null) {
                triggerBlock = blockNamespace;
            }
            boolean fused = MineEnchantRuntime.shouldFuse(modifiers.fusionLevel());
            String pricedNamespace = fused ? fusedNamespace(blockNamespace) : blockNamespace;
            double multiplier = broken == 0 ? 1D : 0.1D;
            double resolvedPrice = resolvePrice(mine, pricedNamespace);
            if (resolvedPrice > 0D) {
                baseCredit += resolvedPrice * multiplier;
            } else {
                createMineDrop(pricedNamespace, brokenBlock).ifPresent(drop -> giveMineDrop(player, point, drop));
            }
            broken++;
        }
        if (broken <= 0) {
            return 0D;
        }

        UserProfile profile = profileService.getOrCreate(player);
        double credit = Math.ceil(baseCredit * modifiers.priceModifier() * profile.getPrestigeBoost());
        profile.deposit(credit);
        profile.addMinedBlocks(broken);
        achievementService.registerEarnedMoney(profile, credit);
        achievementService.onBlockBreak(player, profile, triggerBlock);
        achievementService.onMineRank(player, profile);
        awardMiningXp(player, modifiers.xpBoost());
        maybeGrantResource(player, modifiers.resourceBoost(), modifiers.mineStardust(), resourceHardness);
        maybeGrantRelic(player, profile);
        handleAutoRepair(player, tool, modifiers.autoRepair());
        handleToolDurability(player, tool, broken, modifiers.unbreakingLevel(), modifiers.obsidianBreakerLevel(), points);

        if (state.shouldRegenerate(regenThreshold)) {
            regenerateMine(mine, "threshold");
        }
        return credit;
    }

    public LegacyCustomItemService customItemService() {
        return customItemService;
    }

    public boolean maybeGrantRelic(Player player, UserProfile profile) {
        long now = System.currentTimeMillis() / 1000L;
        long lastRelicAt = readLong(profile.getExtraData().get("lastRelicAt"));
        long modifier = Math.max(Math.min(120 - (now - lastRelicAt), 120), 1);
        int bound = (int) (400 * Math.pow(modifier, 1.2D));
        if (random.nextInt(Math.max(2, bound) + 1) > 1) {
            return false;
        }
        String crateId = weightedRelicCrate();
        int keyVariant = switch (crateId) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            default -> 99;
        };
        customItemService.createById(20, 1, keyVariant).ifPresent(player.getInventory()::addItemStack);
        profile.getExtraData().put("lastRelicAt", now);
        return true;
    }

    public void tickRegeneration() {
        tickThresholdRegeneration();
        tickTimedRegeneration();
    }

    public void tickThresholdRegeneration() {
        List<MineDefinition> mines = mineService.allMines();
        if (mines.isEmpty()) {
            autoRegenCursor = 0;
            return;
        }
        if (autoRegenCursor >= mines.size()) {
            autoRegenCursor = 0;
        }
        MineDefinition mine = mines.get(autoRegenCursor);
        autoRegenCursor = (autoRegenCursor + 1) % mines.size();
        if (mine.isDisabled()) {
            return;
        }
        MineRuntimeState state = stateFor(mine);
        if (state.shouldRegenerate(regenThreshold)) {
            regenerateMine(mine, "threshold");
        }
    }

    public void tickTimedRegeneration() {
        long now = System.currentTimeMillis();
        if (lastTimedRegenCheckAtMillis == 0L) {
            lastTimedRegenCheckAtMillis = now - 1_000L;
        }
        long diff = Math.max(0L, now - lastTimedRegenCheckAtMillis);
        lastTimedRegenCheckAtMillis = now;
        timedRegenRemainingMillis -= diff;
        if (timedRegenRemainingMillis > 0L) {
            return;
        }
        timedRegenRemainingMillis = timedRegenIntervalMillis;
        int regenerated = 0;
        for (MineDefinition mine : mineService.allMines()) {
            if (mine.isDisabled()) {
                continue;
            }
            regenerateMine(mine, "timer");
            regenerated++;
        }
        if (regenerated > 0) {
            broadcastMineRegeneration();
        }
    }

    private void broadcastMineRegeneration() {
        var message = LegacyComponentSerializer.legacySection().deserialize("§8[§bFN§8] §r§7All mines are now regenerating...");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    public void tickActionBar() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            Instance instance = player.getInstance();
            if (instance == null || instance != managedInstance) {
                continue;
            }

            Optional<MineDefinition> mine = findMineAt(player.getPosition(), instance);
            if (mine.isEmpty()) {
                continue;
            }

            UserProfile profile = profileService.getOrCreate(player);
            MineRuntimeState state = stateFor(mine.get());
            String message = mine.get().getName()
                    + " | "
                    + NumberFormatter.currency(profile.getBalance())
                    + " | Blocks "
                    + NumberFormatter.shortNumber(profile.getMinedBlocks())
                    + " | Depleted "
                    + state.depletionPercent();
            player.sendActionBar(Component.text(message, NamedTextColor.GRAY));
        }
    }

    public boolean isMineResourceBlock(MineDefinition mine, Block block) {
        if (block == null || block == Block.AIR) {
            return false;
        }
        return toBlockPool(mine).stream().anyMatch(resource -> resource.compare(block));
    }

    public void regenerateMine(MineDefinition mine, String reason) {
        MineRegion region = mine.getRegion();
        if (region == null) {
            return;
        }

        List<Block> pool = toBlockPool(mine);
        if (pool.isEmpty()) {
            pool = List.of(Block.STONE);
        }

        MineRuntimeState state = stateFor(mine);
        for (MinedBlock point : state.depletedPositions()) {
            if (!region.contains(point.x(), point.y(), point.z())) {
                continue;
            }
            Block next = pool.get(random.nextInt(pool.size()));
            managedInstance.setBlock(point.x(), point.y(), point.z(), next);
        }

        int teleportY = mine.topTeleportY();
        var resetMessage = LegacyComponentSerializer.legacySection().deserialize("§8[§bFN§8] §r§7Mine §b" + mine.getName() + "§r§7 has been reset.");
        for (Player player : managedInstance.getPlayers()) {
            Pos playerPos = player.getPosition();
            if (playerPos.blockX() < region.minX() || playerPos.blockX() > region.maxX()
                || playerPos.blockY() < region.minY() || playerPos.blockY() > region.maxY()
                || playerPos.blockZ() < region.minZ() || playerPos.blockZ() > region.maxZ()) {
                continue;
            }
            player.teleport(new Pos(playerPos.x(), teleportY, playerPos.z(), playerPos.yaw(), playerPos.pitch()));
            player.sendMessage(resetMessage);
        }

        state.markRegenerated(reason);
    }

    public void clearMine(MineDefinition mine) {
        MineRegion region = mine.getRegion();
        if (region == null) {
            return;
        }
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    if (!isMineResourceBlock(mine, managedInstance.getBlock(x, y, z))) {
                        continue;
                    }
                    managedInstance.setBlock(x, y, z, Block.AIR);
                    stateFor(mine).recordDepletion(x, y, z);
                }
            }
        }
    }

    public long depletedBlocksForMine(int mineId) {
        return mineStates.getOrDefault(mineId, MineRuntimeState.empty()).depletedBlocks();
    }

    private List<Block> toBlockPool(MineDefinition mine) {
        List<Block> blocks = new java.util.ArrayList<>();
        for (String namespace : mine.getBlocks()) {
            Block block = Block.fromKey(namespace);
            if (block != null) {
                blocks.add(block);
            }
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        for (String namespace : mine.getPrices().keySet()) {
            String normalized = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
            if (REQUIRED_PRESTIGE_KEYS.contains(normalized)) {
                continue;
            }
            Block block = Block.fromKey(normalized);
            if (block != null) {
                blocks.add(block);
            }
        }

        return blocks;
    }

    private int requiredPrestige(MineDefinition mine) {
        for (Map.Entry<String, Double> entry : mine.getPrices().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (REQUIRED_PRESTIGE_KEYS.contains(key) && entry.getValue() != null) {
                return Math.max(0, entry.getValue().intValue());
            }
        }
        return 0;
    }

    private double resolvePrice(MineDefinition mine, String blockNamespace) {
        if (blockNamespace == null) {
            return 0d;
        }
        Double minePrice = mine.getPrices().get(blockNamespace);
        if (minePrice != null) {
            return Math.max(0d, minePrice);
        }
        Double localPrice = localPrices.get(blockNamespace);
        if (localPrice != null) {
            return Math.max(0d, localPrice);
        }
        return 0d;
    }

    private MineRuntimeState stateFor(MineDefinition mine) {
        return mineStates.computeIfAbsent(
                mine.getId(),
                ignored -> new MineRuntimeState(mine.getRegion() == null ? 0L : mine.getRegion().volume())
        );
    }

    private void giveMineDrop(Player player, Point point, ItemStack drop) {
        if (player.getInventory().addItemStack(drop)) {
            return;
        }
        ItemEntity entity = new ItemEntity(drop);
        entity.setPickupDelay(Duration.ZERO);
        entity.setInstance(managedInstance, new Pos(point.x() + 0.5d, point.y() + 0.5d, point.z() + 0.5d));
    }

    private void awardMiningXp(Player player, double xpBoost) {
        int gained = 1;
        if (random.nextDouble() < 0.33D) {
            gained += Math.max(0, (int) Math.floor(xpBoost));
        }
        player.setLevel(player.getLevel() + gained);
    }

    private void maybeGrantResource(Player player, double resourceBoost, boolean mineStardust, double hardness) {
        double clampedHardness = Math.max(0.5D, Math.min(hardness, 5D));
        int extra = (int) Math.round(400D * (3D - clampedHardness));
        int threshold = 950 + extra;
        int maxRoll = Math.max(1, (int) Math.round(1000D + resourceBoost + extra));
        int roll = random.nextInt(maxRoll + 1);
        if (roll <= threshold) {
            return;
        }
        int itemId = randomResourceId(mineStardust);
        if (itemId <= 0) {
            return;
        }
        customItemService.createById(itemId, 1, null).ifPresent(player.getInventory()::addItemStack);
    }

    private void handleAutoRepair(Player player, ItemStack tool, boolean autoRepair) {
        if (!autoRepair || tool == null || tool.isAir()) {
            return;
        }
        if (!customItemService.isDurabilityItem(tool)) {
            return;
        }
        int maxDamage = customItemService.maxDamage(tool);
        int currentDamage = customItemService.currentDamage(tool);
        if (maxDamage <= 0 || currentDamage <= (maxDamage / 2)) {
            return;
        }
        if (!consumeRepairShard(player)) {
            return;
        }
        player.setItemInMainHand(customItemService.applyDamage(tool, -400));
    }

    private void handleToolDurability(Player player, ItemStack tool, int minedBlocks, int unbreakingLevel, int obsidianBreakerLevel, List<Point> points) {
        if (tool == null || tool.isAir()) {
            return;
        }
        if (!customItemService.isDurabilityItem(tool)) {
            return;
        }
        int durabilityLoss = Math.max(1, minedBlocks);
        if (unbreakingLevel > 0 && random.nextInt(101) >= unbreakingLevel * 10) {
            // Preserve PMMP bug: durability is used only when the Unbreaking roll succeeds.
            durabilityLoss = 0;
        }
        ItemStack updated = customItemService.applyDamage(tool, durabilityLoss);
        player.setItemInMainHand(updated);
    }

    private boolean consumeRepairShard(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItemStack(slot);
            if (customItemService.customItemId(stack) != 3) {
                continue;
            }
            int next = stack.amount() - 1;
            player.getInventory().setItemStack(slot, next <= 0 ? ItemStack.AIR : stack.withAmount(next));
            return true;
        }
        return false;
    }

    private int randomResourceId(boolean mineStardust) {
        double f = random.nextDouble();
        if (mineStardust && f < 0.07D) {
            return 1;
        }
        if (f < 0.26D) {
            return 2;
        }
        if (f < 0.38D) {
            return 3;
        }
        return mineStardust ? 0 : 4;
    }

    private String fusedNamespace(String namespace) {
        return switch (namespace) {
            case "minecraft:coal", "minecraft:coal_ore", "minecraft:deepslate_coal_ore" -> "minecraft:iron_ingot";
            case "minecraft:raw_iron", "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "minecraft:gold_ingot";
            case "minecraft:raw_gold", "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> "minecraft:redstone";
            case "minecraft:redstone", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore" ->
                    "minecraft:lapis_lazuli";
            case "minecraft:lapis_lazuli", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore" ->
                    "minecraft:diamond";
            case "minecraft:diamond", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore" -> "minecraft:emerald";
            case "minecraft:coal_block" -> "minecraft:iron_block";
            case "minecraft:iron_block" -> "minecraft:gold_block";
            case "minecraft:gold_block" -> "minecraft:redstone_block";
            case "minecraft:redstone_block" -> "minecraft:lapis_block";
            case "minecraft:lapis_block" -> "minecraft:diamond_block";
            case "minecraft:diamond_block" -> "minecraft:emerald_block";
            default -> namespace;
        };
    }

    private String weightedRelicCrate() {
        int roll = random.nextInt(10_821);
        if (roll < 10_000) {
            return "iron";
        }
        if (roll < 10_750) {
            return "gold";
        }
        if (roll < 10_800) {
            return "diamond";
        }
        if (roll < 10_820) {
            return "emerald";
        }
        return "netherrite";
    }

    private Optional<ItemStack> createMineDrop(String namespace, Block originalBlock) {
        String normalized = normalizeNamespace(namespace);
        if (normalized != null) {
            net.minestom.server.item.Material material = net.minestom.server.item.Material.fromKey(normalized);
            if (material != null && material != net.minestom.server.item.Material.AIR) {
                return Optional.of(ItemStack.of(material, 1));
            }
            Block fusedBlock = Block.fromKey(normalized);
            if (fusedBlock != null && fusedBlock != Block.AIR) {
                net.minestom.server.item.Material blockMaterial = fusedBlock.registry().material();
                if (blockMaterial != null && blockMaterial != net.minestom.server.item.Material.AIR) {
                    return Optional.of(ItemStack.of(blockMaterial, 1));
                }
            }
        }
        if (originalBlock == null || originalBlock == Block.AIR) {
            return Optional.empty();
        }
        net.minestom.server.item.Material originalMaterial = originalBlock.registry().material();
        if (originalMaterial == null || originalMaterial == net.minestom.server.item.Material.AIR) {
            return Optional.empty();
        }
        return Optional.of(ItemStack.of(originalMaterial, 1));
    }

    public record AccessResult(boolean allowed, String message) {
        public static AccessResult allow() {
            return new AccessResult(true, "");
        }

        public static AccessResult deny(String message) {
            return new AccessResult(false, message);
        }
    }

    private record MinedBlock(int x, int y, int z) {
    }

    private static final class MineRuntimeState {
        private final long capacity;
        private final Set<MinedBlock> depletedPositions;
        private long depletedBlocks;

        private MineRuntimeState(long capacity) {
            this.capacity = Math.max(1L, capacity);
            this.depletedPositions = new java.util.HashSet<>();
            this.depletedBlocks = 0L;
        }

        private static MineRuntimeState empty() {
            return new MineRuntimeState(1L);
        }

        private synchronized void recordDepletion(Point point) {
            recordDepletion(point.blockX(), point.blockY(), point.blockZ());
        }

        private synchronized void recordDepletion(int x, int y, int z) {
            depletedPositions.add(new MinedBlock(x, y, z));
            depletedBlocks = depletedPositions.size();
        }

        private synchronized boolean shouldRegenerate(double threshold) {
            long thresholdBlocks = Math.max(1L, (long) Math.floor(capacity * threshold));
            return depletedBlocks >= thresholdBlocks;
        }

        private synchronized void markRegenerated(String ignoredReason) {
            depletedPositions.clear();
            depletedBlocks = 0L;
        }

        private synchronized Set<MinedBlock> depletedPositions() {
            return Set.copyOf(depletedPositions);
        }

        private synchronized long depletedBlocks() {
            return depletedBlocks;
        }

        private synchronized String depletionPercent() {
            int percent = (int) Math.min(100d, (depletedBlocks * 100d) / capacity);
            return percent + "%";
        }
    }
}
