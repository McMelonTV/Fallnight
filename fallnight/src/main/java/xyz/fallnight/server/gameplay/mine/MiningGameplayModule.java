package xyz.fallnight.server.gameplay.mine;

import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class MiningGameplayModule {
    private static final Random RANDOM = new Random();
    private final MineRuntimeService runtimeService;
    private Task thresholdRegenerationTask;
    private Task timedRegenerationTask;

    public MiningGameplayModule(
            MineService mineService,
            PlayerProfileService profileService,
            Instance managedInstance,
            SpawnService managedWorldService,
            Map<String, Double> localPrices
    ) {
        this.runtimeService = new MineRuntimeService(
                mineService,
                profileService,
                managedInstance,
                managedWorldService,
                localPrices
        );
    }

    public static MiningGameplayModule createDefaults(
            MineService mineService,
            PlayerProfileService profileService,
            Instance managedInstance,
            SpawnService managedWorldService,
            Path dataRoot
    ) {
        return new MiningGameplayModule(
                mineService,
                profileService,
                managedInstance,
                managedWorldService,
                MineLocalPriceRepository.loadDefaults(dataRoot)
        );
    }

    public MineRuntimeService runtimeService() {
        return runtimeService;
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerStartDiggingEvent.class, event -> {
            Block block = event.getBlock();
            if (block != Block.OBSIDIAN) {
                return;
            }
            Player player = event.getPlayer();
            var enchants = runtimeService.customItemService().customEnchants(player.getItemInMainHand());
            int obsidianBreakerLevel = 0;
            for (var entry : enchants.entrySet()) {
                if (entry.getKey().equals(FallnightCustomEnchantRegistry.OBSIDIAN_BREAKER)) {
                    obsidianBreakerLevel = Math.max(1, entry.getValue());
                    break;
                }
            }
            if (obsidianBreakerLevel <= 0) {
                return;
            }
            if (!MineEnchantRuntime.shouldBreakObsidian(obsidianBreakerLevel, RANDOM)) {
                return;
            }
            Instance instance = player.getInstance();
            if (instance == null) {
                return;
            }
            Point pos = event.getBlockPosition();
            Optional<MineDefinition> mine = runtimeService.findMineAt(pos, instance);
            if (mine.isPresent()) {
                MineRuntimeService.AccessResult access = runtimeService.checkAccess(player, mine.get());
                if (access.allowed()) {
                    instance.setBlock(pos, Block.AIR);
                    runtimeService.handleBlockBreak(player, mine.get(), Block.OBSIDIAN, pos);
                }
            }
            event.setCancelled(true);
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerBlockBreakEvent.class, event -> {
            Player player = event.getPlayer();
            Instance instance = player.getInstance();
            if (instance == null) {
                return;
            }

            Optional<MineDefinition> mine = runtimeService.findMineAt(event.getBlockPosition(), instance);
            if (mine.isEmpty()) {
                return;
            }

            MineRuntimeService.AccessResult access = runtimeService.checkAccess(player, mine.get());
            if (!access.allowed()) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(access.message(), NamedTextColor.RED));
                return;
            }

            Block brokenBlock = event.getBlock();
            if (brokenBlock == null || brokenBlock == Block.AIR) {
                event.setCancelled(true);
                return;
            }
            if (!runtimeService.isMineResourceBlock(mine.get(), brokenBlock)) {
                event.setCancelled(true);
                return;
            }

            event.setResultBlock(Block.AIR);
            List<Point> targets = resolveBreakTargets(player, mine.get(), event.getBlockPosition(), instance);
            runtimeService.handleBlockBreaks(player, mine.get(), targets);
        });

        thresholdRegenerationTask = MinecraftServer.getSchedulerManager()
                .buildTask(() -> runtimeService.tickThresholdRegeneration())
                .repeat(TaskSchedule.tick(11))
                .schedule();

        timedRegenerationTask = MinecraftServer.getSchedulerManager()
                .buildTask(() -> runtimeService.tickTimedRegeneration())
                .repeat(TaskSchedule.tick(10))
                .schedule();
    }

    private List<Point> resolveBreakTargets(Player player, MineDefinition mine, Point origin, Instance instance) {
        List<Point> targets = new ArrayList<>();
        targets.add(origin);
        MineEnchantRuntime.MiningModifiers modifiers = MineEnchantRuntime.evaluate(player.getItemInMainHand(), runtimeService.customItemService());
        if (!MineEnchantRuntime.shouldDrill(modifiers.drillerLevel())) {
            return targets;
        }
        for (int x = origin.blockX() - 1; x <= origin.blockX() + 1; x++) {
            for (int y = origin.blockY() - 1; y <= origin.blockY() + 1; y++) {
                for (int z = origin.blockZ() - 1; z <= origin.blockZ() + 1; z++) {
                    if (x == origin.blockX() && y == origin.blockY() && z == origin.blockZ()) {
                        continue;
                    }
                    Point point = new net.minestom.server.coordinate.Vec(x, y, z);
                    Optional<MineDefinition> at = runtimeService.findMineAt(point, instance);
                    if (at.isPresent()
                            && at.get().getId() == mine.getId()
                            && runtimeService.isMineResourceBlock(mine, instance.getBlock(x, y, z))) {
                        targets.add(point);
                    }
                }
            }
        }
        return targets;
    }

    public void unregister() {
        if (thresholdRegenerationTask != null) {
            thresholdRegenerationTask.cancel();
        }
        if (timedRegenerationTask != null) {
            timedRegenerationTask.cancel();
        }
    }
}
