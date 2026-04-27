package xyz.fallnight.server.gameplay.plot;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.service.AdminModeService;
import xyz.fallnight.server.service.PlotService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.block.Block;

public final class PlotRuntimeModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlotService plotService;
    private final PlayerProfileService profileService;
    private final SpawnService plotWorldService;
    private final EventNode<Event> eventNode;

    public PlotRuntimeModule(PlotService plotService, PlayerProfileService profileService, SpawnService plotWorldService) {
        this.plotService = plotService;
        this.profileService = profileService;
        this.plotWorldService = plotWorldService;
        this.eventNode = EventNode.all("plot-runtime");
    }

    public void register() {
        eventNode.addListener(PlayerMoveEvent.class, this::onMove);
        eventNode.addListener(PlayerBlockBreakEvent.class, this::onBreak);
        eventNode.addListener(PlayerBlockPlaceEvent.class, this::onPlace);
        eventNode.addListener(PlayerBlockInteractEvent.class, this::onInteract);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    public void clearPlot(PlotCoordinate coordinate) {
        if (coordinate == null) {
            return;
        }
        int minX = -51 + (coordinate.x() * PlotService.GRID_SPACING);
        int maxX = -4 + (coordinate.x() * PlotService.GRID_SPACING);
        int minZ = -51 + (coordinate.z() * PlotService.GRID_SPACING);
        int maxZ = -4 + (coordinate.z() * PlotService.GRID_SPACING);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = 0; y < 256; y++) {
                    if (y == 0) {
                        plotWorldService.instance().setBlock(x, y, z, Block.BEDROCK);
                    } else if (y < 64) {
                        plotWorldService.instance().setBlock(x, y, z, Block.DIRT);
                    } else if (y == 64) {
                        plotWorldService.instance().setBlock(x, y, z, Block.GRASS_BLOCK);
                    } else {
                        plotWorldService.instance().setBlock(x, y, z, Block.AIR);
                    }
                }
            }
        }
    }

    public boolean canBuildAt(net.minestom.server.entity.Player player, Pos position) {
        if (!isInPlotWorld(player, position)) {
            return false;
        }
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return true;
        }
        Optional<PlotService.PlotEntryView> plot = currentPlot(position);
        return plot.isPresent() && isAccessible(plot.get(), player.getUsername());
    }

    public boolean canInteractAt(net.minestom.server.entity.Player player, Pos position, Block block) {
        if (AdminModeService.isEnabled(profileService.getOrCreate(player))) {
            return true;
        }
        String key = block == null ? "" : block.key().asString();
        boolean restricted = key.contains("chest") || key.contains("door") || key.contains("trapdoor") || key.contains("furnace") || key.contains("smoker") || key.contains("blast_furnace");
        if (!restricted) {
            return true;
        }
        if (!isInPlotWorld(player, position)) {
            return true;
        }
        Optional<PlotService.PlotEntryView> plot = currentPlot(position);
        return plot.isPresent() && isAccessible(plot.get(), player.getUsername());
    }

    private void onMove(PlayerMoveEvent event) {
        if (!isInPlotWorld(event.getPlayer(), event.getNewPosition())) {
            return;
        }
        PlotCoordinate from = plotService.resolveCoordinateForPosition(plotWorldService.spawn(), event.getPlayer().getPosition());
        PlotCoordinate to = plotService.resolveCoordinateForPosition(plotWorldService.spawn(), event.getNewPosition());
        if (from.equals(to)) {
            return;
        }
        Optional<PlotService.PlotEntryView> target = plotService.getPlot(to);
        if (target.isPresent() && target.get().entry().getBlockedUsers().stream().anyMatch(name -> name.equalsIgnoreCase(event.getPlayer().getUsername()))
            && !AdminModeService.isEnabled(profileService.getOrCreate(event.getPlayer()))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(LEGACY.deserialize("§8[§bFN§8]\n§r§7You are blocked from this plot"));
            return;
        }
        String name = target.map(view -> view.entry().getName()).orElse("");
        String owner = target.map(view -> view.entry().getOwner().isBlank() ? "unclaimed" : "by " + view.entry().getOwner()).orElse("unclaimed");
        Component subtitle = LEGACY.deserialize((name != null && !name.isBlank() ? "§r§b" + name + "\n§7" : "") + "§r§7" + owner);
        event.getPlayer().showTitle(Title.title(
            LEGACY.deserialize("§8[§b" + to.key().replace(":", "§8:§b") + "§8]"),
            subtitle,
            Title.DEFAULT_TIMES
        ));
    }

    private void onBreak(PlayerBlockBreakEvent event) {
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (!isInPlotWorld(event.getPlayer(), pos)) {
            return;
        }
        if (canBuildAt(event.getPlayer(), pos)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(CommandMessages.error("You can't build here!"));
    }

    private void onPlace(PlayerBlockPlaceEvent event) {
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (!isInPlotWorld(event.getPlayer(), pos)) {
            return;
        }
        if (canBuildAt(event.getPlayer(), pos)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(CommandMessages.error("You can't build here!"));
    }

    private void onInteract(PlayerBlockInteractEvent event) {
        Pos pos = new Pos(event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (!isInPlotWorld(event.getPlayer(), pos)) {
            return;
        }
        if (canInteractAt(event.getPlayer(), pos, event.getBlock())) {
            return;
        }
        event.setBlockingItemUse(true);
        event.setCancelled(true);
        event.getPlayer().sendMessage(CommandMessages.error("You can't use that here!"));
    }

    public boolean isInManagedPlotWorld(net.minestom.server.entity.Player player, Pos position) {
        return isInPlotWorld(player, position);
    }

    private Optional<PlotService.PlotEntryView> currentPlot(Pos position) {
        PlotCoordinate coordinate = plotService.resolveCoordinateForPosition(plotWorldService.spawn(), position);
        return plotService.getPlot(coordinate);
    }

    private boolean isAccessible(PlotService.PlotEntryView plot, String username) {
        String normalized = username == null ? "" : username.toLowerCase();
        return plot.entry().getOwner().equalsIgnoreCase(normalized)
            || plot.entry().getMembers().stream().anyMatch(member -> member.equalsIgnoreCase(normalized));
    }

    private boolean isInPlotWorld(net.minestom.server.entity.Player player, Pos position) {
        return player.getInstance() == plotWorldService.instance()
            && position != null
            && plotService.plotAtPosition(plotWorldService.spawn(), position).isPresent();
    }
}
