package xyz.fallnight.server.persistence.moderation;

import java.io.IOException;

public interface ModerationSanctionsRepository {
    ModerationSanctionsState load() throws IOException;

    void save(ModerationSanctionsState state) throws IOException;
}
