package xyz.fallnight.worldgen.data

import xyz.fallnight.worldgen.spec.PriceSpec
import java.nio.file.Path

object PriceDataWriter {
    fun write(path: Path, prices: PriceSpec) {
        RuntimeYamlWriter.write(
            path,
            buildList {
                add("---")
                prices.itemPrices.forEach { (material, value) ->
                    add("${RuntimeYamlWriter.quoted(RuntimeYamlWriter.minecraftMaterial(material))}: $value")
                }
            },
        )
    }
}
