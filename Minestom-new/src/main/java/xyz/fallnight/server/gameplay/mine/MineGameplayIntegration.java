package xyz.fallnight.server.gameplay.mine;

import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.SpawnService;
import net.minestom.server.instance.Instance;

import java.nio.file.Path;
import java.util.Optional;

public final class MineGameplayIntegration {
    private static volatile MiningGameplayModule installedModule;

    private MineGameplayIntegration() {
    }

    public static MineRuntimeService install(
            MineService mineService,
            PlayerProfileService profileService,
            Instance instance,
            SpawnService spawnWorldService,
            Path dataRoot
    ) {
        MiningGameplayModule module = MiningGameplayModule.createDefaults(
                mineService,
                profileService,
                instance,
                spawnWorldService,
                dataRoot
        );
        module.register();
        installedModule = module;
        return module.runtimeService();
    }

    public static Optional<MineRuntimeService> runtimeService() {
        MiningGameplayModule module = installedModule;
        if (module == null) {
            return Optional.empty();
        }
        return Optional.of(module.runtimeService());
    }
}
