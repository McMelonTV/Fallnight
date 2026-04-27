package xyz.fallnight.server.persistence;

import java.nio.file.Path;
import java.util.Objects;

public final class DataDirectories {
    private final Path root;
    private final String usersDirectory;
    private final String mineRanksDirectory;
    private final String minesDirectory;
    private final String vaultsDirectory;
    private final String gangsDirectory;

    public DataDirectories(Path root) {
        this(root, "users", "mineranks", "mines", "vaults", "gangs");
    }

    public DataDirectories(Path root, String usersDirectory, String mineRanksDirectory, String minesDirectory, String vaultsDirectory) {
        this(root, usersDirectory, mineRanksDirectory, minesDirectory, vaultsDirectory, "gangs");
    }

    public DataDirectories(Path root, String usersDirectory, String mineRanksDirectory, String minesDirectory, String vaultsDirectory, String gangsDirectory) {
        this.root = Objects.requireNonNull(root, "root");
        this.usersDirectory = Objects.requireNonNull(usersDirectory, "usersDirectory");
        this.mineRanksDirectory = Objects.requireNonNull(mineRanksDirectory, "mineRanksDirectory");
        this.minesDirectory = Objects.requireNonNull(minesDirectory, "minesDirectory");
        this.vaultsDirectory = Objects.requireNonNull(vaultsDirectory, "vaultsDirectory");
        this.gangsDirectory = Objects.requireNonNull(gangsDirectory, "gangsDirectory");
    }

    public Path root() {
        return root;
    }

    public Path usersPath() {
        return root.resolve(usersDirectory);
    }

    public Path mineRanksPath() {
        return root.resolve(mineRanksDirectory);
    }

    public Path minesPath() {
        return root.resolve(minesDirectory);
    }

    public Path vaultsPath() {
        return root.resolve(vaultsDirectory);
    }

    public Path gangsPath() {
        return root.resolve(gangsDirectory);
    }
}
