package xyz.fallnight.server.persistence.tag;

import xyz.fallnight.server.domain.tag.TagDefinition;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface TagDefinitionRepository {
    Map<String, TagDefinition> loadAll() throws IOException;

    Optional<TagDefinition> findById(String id);
}
