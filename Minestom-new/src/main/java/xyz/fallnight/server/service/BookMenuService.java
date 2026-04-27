package xyz.fallnight.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.entity.Player;

public final class BookMenuService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int MAX_LINES_PER_PAGE = 13;
    private static final int MAX_LINE_WIDTH = 113;
    private static final int MAX_PAGES = 100;
    private static final String LINE_SEPARATOR = "\n";
    private final String author;

    public BookMenuService() {
        this("Fallnight");
    }

    public BookMenuService(String author) {
        this.author = author;
    }

    private static List<String> wrapEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> wrapped = new ArrayList<>();
        boolean firstEntry = true;
        for (String entry : entries) {
            if (!firstEntry) {
                wrapped.add("");
            }

            String value = entry == null ? "" : entry;
            String[] segments = value.split("\\n", -1);
            for (String segment : segments) {
                wrapped.addAll(wrapLine(segment));
            }
            firstEntry = false;
        }
        return wrapped;
    }

    private static List<String> wrapLine(String line) {
        if (line == null || line.isEmpty()) {
            return List.of("");
        }

        List<String> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String continuationPrefix = "";

        String[] words = line.split(" ", -1);
        for (String word : words) {
            String candidate = current.length() == 0 ? continuationPrefix + word : current + " " + word;
            if (current.length() > 0 && visibleWidth(candidate) > MAX_LINE_WIDTH) {
                String finished = current.toString();
                wrapped.add(finished);
                continuationPrefix = activeFormattingCodes(finished);
                current.setLength(0);
                candidate = continuationPrefix + word;
            }

            if (current.length() == 0 && visibleWidth(candidate) > MAX_LINE_WIDTH) {
                List<String> splitWord = splitOversizedToken(candidate);
                if (splitWord.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < splitWord.size() - 1; i++) {
                    wrapped.add(splitWord.get(i));
                }
                String tail = splitWord.get(splitWord.size() - 1);
                current.append(tail);
                continuationPrefix = activeFormattingCodes(tail);
                continue;
            }

            if (current.length() == 0 && !continuationPrefix.isEmpty()) {
                current.append(continuationPrefix);
            } else if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }

        if (current.length() > 0) {
            wrapped.add(current.toString());
        }
        return wrapped.isEmpty() ? List.of("") : wrapped;
    }

    private static List<String> splitOversizedToken(String token) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int width = 0;
        boolean bold = false;

        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character == '§' && index + 1 < token.length()) {
                char code = Character.toLowerCase(token.charAt(index + 1));
                current.append(character);
                current.append(token.charAt(index + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || isColorCode(code)) {
                    bold = false;
                }
                index++;
                continue;
            }

            int characterWidth = glyphWidth(character, bold);
            if (width + characterWidth > MAX_LINE_WIDTH && current.length() > 0) {
                String finished = current.toString();
                lines.add(finished);
                String continuationPrefix = activeFormattingCodes(finished);
                current.setLength(0);
                current.append(continuationPrefix);
                width = 0;
                bold = endsBold(continuationPrefix);
            }
            current.append(character);
            width += characterWidth;
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int visibleWidth(String text) {
        return visibleWidth(text, false);
    }

    private static int visibleWidth(String text, boolean initialBold) {
        int width = 0;
        boolean bold = initialBold;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || isColorCode(code)) {
                    bold = false;
                }
                index++;
                continue;
            }
            width += glyphWidth(character, bold);
        }
        return width;
    }

    private static boolean endsBold(String text) {
        boolean bold = false;
        for (int index = 0; index < text.length() - 1; index++) {
            if (text.charAt(index) != '§') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == 'l') {
                bold = true;
            } else if (code == 'r' || isColorCode(code)) {
                bold = false;
            }
            index++;
        }
        return bold;
    }

    private static String activeFormattingCodes(String text) {
        String color = "";
        LinkedHashSet<Character> formats = new LinkedHashSet<>();
        for (int index = 0; index < text.length() - 1; index++) {
            if (text.charAt(index) != '§') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == 'r') {
                color = "";
                formats.clear();
            } else if (isColorCode(code)) {
                color = "§" + code;
                formats.clear();
            } else if (isFormatCode(code)) {
                formats.add(code);
            }
            index++;
        }

        StringBuilder builder = new StringBuilder(color);
        for (char format : formats) {
            builder.append('§').append(format);
        }
        return builder.toString();
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isFormatCode(char code) {
        return code >= 'k' && code <= 'o';
    }

    private static int glyphWidth(char character, boolean bold) {
        int width = switch (character) {
            case 'i', '!', '.', ',', ':', ';', '|', '' -> 2;
            case ' ', 'l', 'I', '[', ']', 't', '(', ')', '{', '}', '', '' -> 4;
            case 'f', 'k', '<', '>', '"', '*' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
        if (bold && character != ' ') {
            width++;
        }
        return width;
    }

    public void open(Player player, String title, List<String> lines) {
        player.openBook(book(title, lines));
    }

    public Book book(String title, List<String> lines) {
        List<Component> pages = pages(lines);

        if (pages.isEmpty()) {
            pages = List.of(Component.empty());
        }
        return Book.book(LEGACY.deserialize(title == null ? "" : title), LEGACY.deserialize(author == null ? "" : author), pages);
    }

    public List<Component> pages(List<String> lines) {
        List<String> renderedLines = wrapEntries(lines);
        List<Component> pages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int lineCount = 0;

        for (String line : renderedLines) {
            if (lineCount == MAX_LINES_PER_PAGE && builder.length() > 0) {
                pages.add(LEGACY.deserialize(builder.toString()));
                if (pages.size() == MAX_PAGES) {
                    return List.copyOf(pages);
                }
                builder.setLength(0);
                lineCount = 0;
            }

            if (builder.length() > 0) {
                builder.append(LINE_SEPARATOR);
            }
            builder.append(line);
            lineCount++;
        }

        if (builder.length() > 0) {
            pages.add(LEGACY.deserialize(builder.toString()));
        }
        return List.copyOf(pages);
    }
}
