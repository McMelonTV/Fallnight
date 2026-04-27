package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.BetaBaselineSpec
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationConfigWriterTest {
    @Test
    fun `application config writes beta spawn world and coordinates`() {
        val temp = Files.createTempDirectory("worldgen-config-test")
        val spec = BetaBaselineSpec.default()

        ApplicationConfigWriter.write(temp.resolve("application.yml"), spec)

        val written = Files.readString(temp.resolve("application.yml"))
        assertTrue(written.contains("world: \"spawn-world\""))
        assertTrue(written.contains("devServer: false"))
        assertTrue(written.contains("mainWorldDirectory: \"../../spawn-world\""))
        assertTrue(written.contains("x: 0.5"))
        assertTrue(written.contains("y: 51.0"))
        assertTrue(written.contains("z: 0.5"))
    }

    @Test
    fun `application config omits legacy import settings`() {
        val temp = Files.createTempDirectory("worldgen-config-legacy-test")

        ApplicationConfigWriter.write(temp.resolve("application.yml"), BetaBaselineSpec.default())

        val written = Files.readString(temp.resolve("application.yml"))
        assertTrue(!written.contains("autoImportLegacy"))
        assertTrue(!written.contains("legacyDataPath"))
    }
}
