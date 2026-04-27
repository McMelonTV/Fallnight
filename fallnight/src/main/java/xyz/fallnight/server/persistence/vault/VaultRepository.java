package xyz.fallnight.server.persistence.vault;

import xyz.fallnight.server.domain.vault.PlayerVault;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface VaultRepository {
    Map<String, PlayerVault> loadAll() throws IOException;

    Optional<PlayerVault> findByOwner(String owner);

    void save(PlayerVault vault) throws IOException;

    void saveAll() throws IOException;
}
