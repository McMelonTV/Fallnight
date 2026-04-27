package xyz.fallnight.server.util;

import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyTextFormatter {
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Set<Character> FORMAT_CODES = Set.of(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
        'k', 'l', 'm', 'n', 'o', 'r', 'x'
    );

    private LegacyTextFormatter() {
    }

    public static Component deserialize(String message) {
        return SECTION_SERIALIZER.deserialize(normalize(message));
    }

    public static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        StringBuilder result = new StringBuilder(message.length());
        for (int index = 0; index < message.length(); index++) {
            char current = message.charAt(index);
            if (current == '&' && index + 1 < message.length()) {
                char next = Character.toLowerCase(message.charAt(index + 1));
                if (FORMAT_CODES.contains(next)) {
                    result.append('§');
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }
}
