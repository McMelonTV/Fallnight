package xyz.fallnight.server.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ModerationTimeFormatter {
    private ModerationTimeFormatter() {
    }

    public static String remaining(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "0 seconds";
        }

        long seconds = duration.toSeconds();
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        List<String> parts = new ArrayList<>(4);
        if (days > 0L) {
            parts.add(days + " days");
        }
        if (hours > 0L) {
            parts.add(hours + " hours");
        }
        if (minutes > 0L) {
            parts.add(minutes + " minutes");
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts.add(seconds + " seconds");
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            if (index == 0) {
                builder.append(parts.get(index));
            } else if (index == parts.size() - 1) {
                builder.append(" and ").append(parts.get(index));
            } else {
                builder.append(", ").append(parts.get(index));
            }
        }
        return builder.toString();
    }
}
