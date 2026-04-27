package xyz.fallnight.server.persistence.plot;

import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.domain.plot.PlotEntry;
import java.io.IOException;
import java.util.Map;

public interface PlotRepository {
    Map<PlotCoordinate, PlotEntry> loadAll() throws IOException;

    void saveAll(Map<PlotCoordinate, PlotEntry> plots) throws IOException;
}
