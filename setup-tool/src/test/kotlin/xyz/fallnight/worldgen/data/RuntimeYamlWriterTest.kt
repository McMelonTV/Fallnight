package xyz.fallnight.worldgen.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeYamlWriterTest {
    @Test
    fun `minecraft material normalizes already namespaced uppercase keys`() {
        assertEquals("minecraft:stone", RuntimeYamlWriter.minecraftMaterial("MINECRAFT:STONE"))
        assertEquals("minecraft:diamond_ore", RuntimeYamlWriter.minecraftMaterial("minecraft:DIAMOND_ORE"))
    }
}
