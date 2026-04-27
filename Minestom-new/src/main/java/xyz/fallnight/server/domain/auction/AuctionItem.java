package xyz.fallnight.server.domain.auction;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

public final class AuctionItem {
    private String material;
    private int amount;
    private String nbt;

    public AuctionItem() {
        this.material = "minecraft:stone";
        this.amount = 1;
        this.nbt = null;
    }

    public AuctionItem(String material, int amount) {
        this(material, amount, null);
    }

    public AuctionItem(String material, int amount, String nbt) {
        this.material = normalizeMaterial(material);
        this.amount = Math.max(1, amount);
        this.nbt = normalizeNbt(nbt);
    }

    public static AuctionItem from(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return new AuctionItem(itemStack.material().key().asString(), itemStack.amount(), serializeNbt(itemStack));
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = normalizeMaterial(material);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public String getNbt() {
        return nbt;
    }

    public void setNbt(String nbt) {
        this.nbt = normalizeNbt(nbt);
    }

    public ItemStack toItemStack() {
        if (nbt != null && !nbt.isBlank()) {
            try {
                return ItemStack.fromItemNBT(TagStringIO.tagStringIO().asCompound(nbt)).withAmount(Math.max(1, amount));
            } catch (Exception ignored) {
            }
        }
        Material resolved = Material.fromKey(material);
        if (resolved == null) {
            resolved = Material.STONE;
        }
        return ItemStack.of(resolved, Math.max(1, amount));
    }

    private static String serializeNbt(ItemStack itemStack) {
        try {
            return TagStringIO.tagStringIO().asString(itemStack.toItemNBT());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeMaterial(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:stone";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private static String normalizeNbt(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
