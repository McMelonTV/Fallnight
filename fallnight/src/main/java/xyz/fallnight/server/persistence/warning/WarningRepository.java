package xyz.fallnight.server.persistence.warning;

import xyz.fallnight.server.domain.warning.WarningEntry;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface WarningRepository {
    WarningState load() throws IOException;

    void save(WarningState state) throws IOException;

    record WarningState(Map<String, List<WarningEntry>> warningsByTarget, long nextId) {
    }
}
