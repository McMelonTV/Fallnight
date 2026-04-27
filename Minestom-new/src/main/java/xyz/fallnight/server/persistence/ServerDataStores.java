package xyz.fallnight.server.persistence;

import xyz.fallnight.server.persistence.mine.MineDefinitionRepository;
import xyz.fallnight.server.persistence.mine.MineRankRepository;
import xyz.fallnight.server.persistence.mine.YamlMineDefinitionRepository;
import xyz.fallnight.server.persistence.mine.YamlMineRankRepository;
import xyz.fallnight.server.persistence.gang.GangRepository;
import xyz.fallnight.server.persistence.gang.JsonGangRepository;
import xyz.fallnight.server.persistence.user.JsonUserProfileRepository;
import xyz.fallnight.server.persistence.user.UserProfileRepository;
import xyz.fallnight.server.persistence.vault.JsonVaultRepository;
import xyz.fallnight.server.persistence.vault.VaultRepository;

public final class ServerDataStores {
    private final UserProfileRepository userProfiles;
    private final MineRankRepository mineRanks;
    private final MineDefinitionRepository mines;
    private final VaultRepository vaults;
    private final GangRepository gangs;

    private ServerDataStores(
        UserProfileRepository userProfiles,
        MineRankRepository mineRanks,
        MineDefinitionRepository mines,
        VaultRepository vaults,
        GangRepository gangs
    ) {
        this.userProfiles = userProfiles;
        this.mineRanks = mineRanks;
        this.mines = mines;
        this.vaults = vaults;
        this.gangs = gangs;
    }

    public static ServerDataStores fromDirectories(DataDirectories directories) {
        return new ServerDataStores(
            new JsonUserProfileRepository(directories.usersPath()),
            new YamlMineRankRepository(directories.mineRanksPath()),
            new YamlMineDefinitionRepository(directories.minesPath()),
            new JsonVaultRepository(directories.vaultsPath()),
            new JsonGangRepository(directories.gangsPath())
        );
    }

    public UserProfileRepository userProfiles() {
        return userProfiles;
    }

    public MineRankRepository mineRanks() {
        return mineRanks;
    }

    public MineDefinitionRepository mines() {
        return mines;
    }

    public VaultRepository vaults() {
        return vaults;
    }

    public GangRepository gangs() {
        return gangs;
    }
}
