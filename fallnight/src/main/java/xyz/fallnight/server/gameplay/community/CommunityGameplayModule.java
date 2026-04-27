package xyz.fallnight.server.gameplay.community;

import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.command.impl.LotteryCommand;
import xyz.fallnight.server.command.impl.VoteCommand;
import xyz.fallnight.server.gameplay.lottery.LotteryDrawModule;
import xyz.fallnight.server.service.LotteryMenuService;
import xyz.fallnight.server.service.LotteryService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.service.VotePartyService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minestom.server.command.CommandManager;

public final class CommunityGameplayModule {
    private final CommandManager commandManager;
    private final LotteryService lotteryService;
    private final LotteryMenuService lotteryMenuService;
    private final VotePartyService votePartyService;
    private final LotteryDrawModule lotteryDrawModule;
    private final LotteryCommand lotteryCommand;
    private final VoteCommand voteCommand;

    public static CommunityGameplayModule createDefaults(
        Path dataRoot,
        PlayerProfileService profileService,
        RankService rankService
    ) {
        PermissionService permissionService = new PermissionService(profileService, rankService);
        LotteryService lotteryService = LotteryService.fromDataRoot(dataRoot, profileService);
        VotePartyService votePartyService = VotePartyService.fromDataRoot(dataRoot, profileService);
        return new CommunityGameplayModule(
            net.minestom.server.MinecraftServer.getCommandManager(),
            lotteryService,
            votePartyService,
            permissionService,
            profileService
        );
    }

    public CommunityGameplayModule(
        CommandManager commandManager,
        LotteryService lotteryService,
        VotePartyService votePartyService,
        PermissionService permissionService,
        PlayerProfileService profileService
    ) {
        this.commandManager = commandManager;
        this.lotteryService = lotteryService;
        this.lotteryMenuService = new LotteryMenuService(lotteryService, profileService);
        this.votePartyService = votePartyService;
        this.lotteryDrawModule = new LotteryDrawModule(lotteryService);
        this.lotteryCommand = new LotteryCommand(permissionService, lotteryService, lotteryMenuService);
        this.voteCommand = new VoteCommand(permissionService, votePartyService);
    }

    public void register() {
        try {
            lotteryService.load();
            votePartyService.load();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        lotteryDrawModule.register();
    }

    public void unregister() {
        lotteryDrawModule.unregister();
    }

    public LotteryService lotteryService() {
        return lotteryService;
    }

    public VotePartyService votePartyService() {
        return votePartyService;
    }
}
