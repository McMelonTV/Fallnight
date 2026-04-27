package xyz.fallnight.server.gameplay.moderation;

import xyz.fallnight.server.service.PlayerProfileService;
import java.nio.file.Path;
import java.util.Optional;

public final class ModerationGameplayIntegration {
    private static volatile ModerationGameplayModule installedModule;

    private ModerationGameplayIntegration() {
    }

    public static ModerationGameplayModule install(
        Path dataRoot,
        PlayerProfileService profileService
    ) {
        ModerationGameplayModule module = ModerationGameplayModule.createDefaults(dataRoot, profileService);
        module.register();
        installedModule = module;
        return module;
    }

    public static Optional<ModerationGameplayModule> installedModule() {
        return Optional.ofNullable(installedModule);
    }
}
