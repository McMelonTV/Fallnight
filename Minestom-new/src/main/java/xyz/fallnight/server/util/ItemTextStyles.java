package xyz.fallnight.server.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class ItemTextStyles {
    private ItemTextStyles() {
    }

    public static Component itemText(Component component) {
        if (component == null) {
            return Component.empty()
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return component.style(component.style()
                .colorIfAbsent(NamedTextColor.WHITE)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    public static List<Component> itemLore(List<Component> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                .map(ItemTextStyles::itemText)
                .toList();
    }
}
