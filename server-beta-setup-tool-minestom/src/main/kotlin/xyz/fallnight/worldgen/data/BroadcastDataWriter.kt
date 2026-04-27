package xyz.fallnight.worldgen.data

import java.nio.file.Path

object BroadcastDataWriter {
    fun write(path: Path, broadcasts: List<String>) {
        RuntimeYamlWriter.write(
            path,
            buildList {
                add("---")
                add("intervalSeconds: 300")
                add("broadcasts:")
                broadcasts.forEach { message ->
                    add("- ${RuntimeYamlWriter.quoted("§8[§6FN§8] §7$message")}")
                }
            },
        )
    }
}
