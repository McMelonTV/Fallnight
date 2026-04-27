package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.TagSpec
import java.nio.file.Path

object TagDataWriter {
    fun write(path: Path, tags: List<TagSpec>) {
        RuntimeYamlWriter.write(
            path,
            buildList {
                add("---")
                add("tags:")
                tags.forEachIndexed { index, tag ->
                    add("  - id: ${tag.id}")
                    add("    tag: ${RuntimeYamlWriter.quoted(tag.displayName)}")
                    add("    rarity: ${index + 1}")
                    add("    isCrateDrop: false")
                }
            },
        )
    }
}
