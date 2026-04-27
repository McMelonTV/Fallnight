package xyz.fallnight.server.domain.plot;

import java.util.Objects;
import java.util.Optional;

public record PlotCoordinate(int x, int z) {
    public String key() {
        return x + ":" + z;
    }

    public static Optional<PlotCoordinate> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.trim().split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            return Optional.of(new PlotCoordinate(x, z));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static PlotCoordinate of(int x, int z) {
        return new PlotCoordinate(x, z);
    }

    public static PlotCoordinate require(PlotCoordinate value) {
        return Objects.requireNonNull(value, "value");
    }
}
