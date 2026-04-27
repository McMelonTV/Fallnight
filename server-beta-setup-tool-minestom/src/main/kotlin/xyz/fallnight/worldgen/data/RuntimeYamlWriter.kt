package xyz.fallnight.worldgen.data

import java.nio.file.Files
import java.nio.file.Path

object RuntimeYamlWriter {
    fun write(path: Path, lines: List<String>) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, buildString {
            append(lines.joinToString(separator = "\n"))
            append("\n")
        })
    }

    fun quoted(value: String): String = buildString {
        append('"')
        append(value.replace("\\", "\\\\").replace("\"", "\\\""))
        append('"')
    }

    fun minecraftMaterial(material: String): String {
        val normalized = material.trim().lowercase().replace(' ', '_')
        return if (normalized.startsWith("minecraft:")) normalized else "minecraft:$normalized"
    }
}
