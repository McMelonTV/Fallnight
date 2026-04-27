package xyz.fallnight.server.bootstrap;

import xyz.fallnight.server.persistence.DataDirectories;

import java.io.IOException;
import java.nio.file.Files;

public final class DataDirectoryBootstrap {
    private DataDirectoryBootstrap() {
    }

    public static void prepareData(DataDirectories directories) throws IOException {
        Files.createDirectories(directories.root());
        Files.createDirectories(directories.usersPath());
        Files.createDirectories(directories.mineRanksPath());
        Files.createDirectories(directories.minesPath());
        Files.createDirectories(directories.vaultsPath());
        Files.createDirectories(directories.gangsPath());
    }
}
