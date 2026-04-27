package xyz.fallnight.server.domain.shop;

import net.minestom.server.item.Material;

public record ShopPrice(Material material, double price) {
    public ShopPrice {
        if (material == null) {
            throw new IllegalArgumentException("material");
        }
        if (price < 0d) {
            throw new IllegalArgumentException("price");
        }
    }
}
