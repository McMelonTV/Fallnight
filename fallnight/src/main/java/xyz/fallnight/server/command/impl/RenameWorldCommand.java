package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.AppConfigLoader;
import xyz.fallnight.server.AppConfigWriter;
import xyz.fallnight.server.ServerConfig;
import xyz.fallnight.server.WorldAccessService;
import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.KothService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PvpZoneService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.service.WorldLabelService;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class RenameWorldCommand extends FallnightCommand {
    private final SpawnService spawnService;
    private final WorldAccessService worldAccessService;
    private final WorldLabelService worldLabelService;
    private final MineService mineService;
    private final PvpZoneService pvpZoneService;
    private final KothService kothService;

    public RenameWorldCommand(PermissionService permissionService, SpawnService spawnService, SpawnService plotWorldService, SpawnService pvpMineWorldService, WorldLabelService worldLabelService, WorldAccessService worldAccessService, MineService mineService, PvpZoneService pvpZoneService, KothService kothService) {
        super("renameworld", permissionService);
        this.spawnService = spawnService;
        this.worldLabelService = worldLabelService;
        this.worldAccessService = worldAccessService;
        this.mineService = mineService;
        this.pvpZoneService = pvpZoneService;
        this.kothService = kothService;

        var nameArg = ArgumentType.StringArray("name");

        setDefaultExecutor((sender, context) ->
                sender.sendMessage(CommandMessages.info("Current world display name: " + currentWorldService(sender).worldName()))
        );

        addSyntax((sender, context) -> {
            String worldName = String.join(" ", context.get(nameArg)).trim();
            if (worldName.isBlank()) {
                sender.sendMessage(CommandMessages.error("Please enter a new name for the world you're in."));
                return;
            }
            SpawnService service = currentWorldService(sender);
            String previousName = service.worldName();
            service.setWorldName(worldName);
            mineService.renameWorldLabel(previousName, worldName);
            pvpZoneService.renameWorldLabel(previousName, worldName);
            kothService.renameWorldLabelInMemory(previousName, worldName);
            worldAccessService.persistLevelName(service, worldName);
            persistWorldLabels(service, previousName, worldName);
            sender.sendMessage(CommandMessages.success("Changed the current level's name to §b" + service.worldName() + "§r§7."));
        }, nameArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.renameworld";
    }

    @Override
    public String summary() {
        return "rename the current world";
    }

    @Override
    public String usage() {
        return "/renameworld <new name...>";
    }

    private SpawnService currentWorldService(net.minestom.server.command.CommandSender sender) {
        if (sender instanceof Player player) {
            return worldAccessService.resolveCurrent(player);
        }
        return spawnService;
    }

    private void persistWorldLabels(SpawnService changedService, String previousName, String worldName) {
        worldLabelService.save(new WorldLabelService.WorldLabels(
                changedService == spawnService ? worldName : spawnService.worldName(),
                worldAccessService.resolve("plots").map(service -> changedService == service ? worldName : service.worldName()).orElse("plots"),
                worldAccessService.resolve("pvpmine").map(service -> changedService == service ? worldName : service.worldName()).orElse("PvPMine")
        ));
        try {
            ServerConfig current = AppConfigLoader.load(org.slf4j.LoggerFactory.getLogger(RenameWorldCommand.class));
            if (changedService == spawnService || current.spawnWorld().equalsIgnoreCase(previousName)) {
                AppConfigWriter.save(AppConfigWriter.withSpawn(current, worldName, changedService.spawn()), AppConfigLoader.writableConfigPath());
            }
        } catch (Exception ignored) {
            // Runtime label was updated; config persistence is best-effort.
        }
    }
}
