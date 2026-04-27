package xyz.fallnight.server.domain.tag;

import java.util.Objects;

public record TagDefinition(
    String id,
    String tag,
    int rarity,
    boolean crateDrop,
    boolean publicTag,
    boolean receiveOnJoin
) {
    public TagDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tag, "tag");
    }
}
