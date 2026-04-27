package xyz.fallnight.server.gameplay.player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerEditSignEvent;
import net.minestom.server.event.player.PlayerPickBlockEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import xyz.fallnight.server.domain.mine.MineDefinition;
import xyz.fallnight.server.gameplay.mine.MineGameplayIntegration;
import xyz.fallnight.server.gameplay.plot.PlotRuntimeModule;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;

import java.util.Optional;

public final class ProtectionGameplayModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final PlotRuntimeModule plotRuntimeModule;
    private final SpawnService spawnWorldService;
    private final EventNode<Event> eventNode;

    public ProtectionGameplayModule(
            PlayerProfileService profileService,
            MineService mineService,
            PlotRuntimeModule plotRuntimeModule,
            SpawnService spawnWorldService
    ) {
        this.profileService = profileService;
        this.mineService = mineService;
        this.plotRuntimeModule = plotRuntimeModule;
        this.spawnWorldService = spawnWorldService;
        this.eventNode = EventNode.all("protection-gameplay");
    }

    private static void sendTip(Player player, String message) {
//        player.sendActionBar(LEGACY.deserialize("§r§8[§bFN§8]§r\n§r§7" + message));
        player.sendActionBar(LEGACY.deserialize("§r§8[§bFN§8]§r §r§7" + message));
    }

    public void register() {
        eventNode.addListener(PlayerBlockBreakEvent.class, this::onBreak);
        eventNode.addListener(PlayerBlockPlaceEvent.class, this::onPlace);
        eventNode.addListener(PlayerBlockInteractEvent.class, this::onInteract);
        eventNode.addListener(PlayerEditSignEvent.class, this::onEditSign);
        eventNode.addListener(PlayerPickBlockEvent.class, this::onPickBlock);
        eventNode.addListener(PlayerUseItemEvent.class, this::onUseItem);
        eventNode.addListener(PlayerUseItemOnBlockEvent.class, this::onUseItemOnBlock);
        eventNode.addListener(EntityDamageEvent.class, this::onDamage);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    private void onBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        Instance instance = player.getInstance();
        var runtime = MineGameplayIntegration.runtimeService();
        if (runtime.isPresent()) {
            Optional<MineDefinition> mine = runtime.get().findMineAt(pos, instance);
            if (mine.isPresent() && runtime.get().isMineResourceBlock(mine.get(), event.getBlock())) {
                return;
            }
        }
        if (plotRuntimeModule.canBuildAt(player, pos)) {
            return;
        }
        event.setCancelled(true);
        sendTip(player, "You can't build here!");
    }

    private void onPlace(PlayerBlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (plotRuntimeModule.canBuildAt(player, pos)) {
            return;
        }
        event.setCancelled(true);
        sendTip(player, "You can't build here!");
    }

    private void onInteract(PlayerBlockInteractEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (plotRuntimeModule.canInteractAt(player, pos, event.getBlock())) {
            if (!plotRuntimeModule.isInManagedPlotWorld(player, pos) || plotRuntimeModule.canBuildAt(player, pos)) {
                return;
            }
            event.setBlockingItemUse(true);
            event.setCancelled(true);
            sendTip(player, "You can't build here!");
            return;
        }
        event.setBlockingItemUse(true);
        event.setCancelled(true);
        sendTip(player, "You can't use that here!");
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Damage damage = event.getDamage();
        if (damage != null && damage.getAttacker() instanceof Player) {
            return;
        }
        if (damage == null) {
            return;
        }
        String cause = damage.getType().name().toLowerCase();
        if (cause.contains("fall") || cause.contains("suffocation") || cause.contains("void")
                || cause.contains("explosion") || cause.contains("starvation") || cause.contains("drown")
                || cause.contains("in_wall") || cause.contains("cramming") || cause.contains("magma")
                || cause.contains("hot_floor") || cause.contains("campfire") || cause.contains("cactus")) {
            event.setCancelled(true);
        }
    }

    private void onUseItemOnBlock(PlayerUseItemOnBlockEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Pos pos = new Pos(event.getPosition().x(), event.getPosition().y(), event.getPosition().z());
        if (!plotRuntimeModule.isInManagedPlotWorld(player, pos) || plotRuntimeModule.canBuildAt(player, pos)) {
            return;
        }
        sendTip(event.getPlayer(), "You can't build here!");
    }

    private void onEditSign(PlayerEditSignEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (plotRuntimeModule.canBuildAt(player, pos)) {
            return;
        }
        sendTip(player, "You can't build here!");
        player.closeInventory();
    }

    private void onPickBlock(PlayerPickBlockEvent event) {
        sendTip(event.getPlayer(), "You can't use that here!");
        event.getPlayer().setItemInMainHand(event.getPlayer().getItemInMainHand());
    }

    private void onUseItem(PlayerUseItemEvent event) {
        Player player = event.getPlayer();
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return;
        }
        Material material = event.getItemStack().material();
        if (material != Material.WATER_BUCKET && material != Material.LAVA_BUCKET && material != Material.BUCKET) {
            return;
        }
        Pos pos = player.getPosition();
        if (plotRuntimeModule.canBuildAt(player, pos)) {
            return;
        }
        event.setCancelled(true);
        sendTip(player, "You can't build here!");
    }
}
