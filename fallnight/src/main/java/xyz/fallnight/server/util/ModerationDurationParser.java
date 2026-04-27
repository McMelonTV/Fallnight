package xyz.fallnight.server.util;

import java.time.Duration;
import java.util.Optional;

public final class ModerationDurationParser {
    private ModerationDurationParser() {
    }

    public static Optional<Duration> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }

        String normalized = token.trim().toLowerCase();
        if (normalized.length() < 2) {
            return Optional.empty();
        }

        char suffix = normalized.charAt(normalized.length() - 1);
        String numberPart = normalized.substring(0, normalized.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numberPart);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        if (amount <= 0L) {
            return Optional.empty();
        }

        return switch (suffix) {
            case 's' -> Optional.of(Duration.ofSeconds(amount));
            case 'm' -> Optional.of(Duration.ofMinutes(amount));
            case 'h' -> Optional.of(Duration.ofHours(amount));
            case 'd' -> Optional.of(Duration.ofDays(amount));
            default -> Optional.empty();
        };
    }

    public static Optional<ParsedDuration> parseLeadingTokens(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return Optional.empty();
        }
        Duration total = Duration.ZERO;
        int consumed = 0;
        for (String token : tokens) {
            Optional<Duration> parsed = parse(token);
            if (parsed.isEmpty()) {
                break;
            }
            total = total.plus(parsed.get());
            consumed++;
            if (consumed >= 4) {
                break;
            }
        }
        if (consumed == 0) {
            return Optional.empty();
        }
        return Optional.of(new ParsedDuration(total, consumed));
    }

    public record ParsedDuration(Duration duration, int consumedTokens) {
    }
}
