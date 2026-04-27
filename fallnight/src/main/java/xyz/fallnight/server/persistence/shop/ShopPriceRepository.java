package xyz.fallnight.server.persistence.shop;

import java.util.Map;
import net.minestom.server.item.Material;

public interface ShopPriceRepository {
    Map<Material, Double> loadPrices();
}
