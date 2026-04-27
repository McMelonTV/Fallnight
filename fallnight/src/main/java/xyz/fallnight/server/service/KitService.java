package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.kit.KitDefinition;
import xyz.fallnight.server.domain.kit.KitReward;
import xyz.fallnight.server.domain.user.UserProfile;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class KitService {
    private static final String COOLDOWNS_EXTRA_DATA_KEY = "kitCooldowns";

    private final PlayerProfileService profileService;
    private final LegacyCustomItemService customItemService;
    private final Map<String, KitDefinition> kitsById;
    private final List<KitDefinition> kits;

    public KitService(PlayerProfileService profileService) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.customItemService = new LegacyCustomItemService();

        this.kits = List.of(
            new KitDefinition("starter", "fallnight.kit.starter", 3_600L, List.of(
                new KitReward("13:1", "inventory"),
                new KitReward("6:1", "inventory"),
                new KitReward("12:1", "boots"),
                new KitReward("11:1", "leggings"),
                new KitReward("10:1", "chestplate"),
                new KitReward("9:1", "helmet")
            )),
            new KitDefinition("mercenary", "fallnight.kit.mercenary", 21_600L, List.of(
                new KitReward("13:2", "inventory"),
                new KitReward("6:2", "inventory"),
                new KitReward("12:2", "boots"),
                new KitReward("11:2", "leggings"),
                new KitReward("10:2", "chestplate"),
                new KitReward("9:2", "helmet")
            )),
            new KitDefinition("warrior", "fallnight.kit.warrior", 21_600L, List.of(
                new KitReward("13:2", "inventory"),
                new KitReward("6:3", "inventory"),
                new KitReward("12:2", "boots"),
                new KitReward("11:2", "leggings"),
                new KitReward("10:2", "chestplate"),
                new KitReward("9:2", "helmet")
            )),
            new KitDefinition("knight", "fallnight.kit.knight", 21_600L, List.of(
                new KitReward("13:3", "inventory"),
                new KitReward("6:4", "inventory"),
                new KitReward("12:3", "boots"),
                new KitReward("11:3", "leggings"),
                new KitReward("10:3", "chestplate"),
                new KitReward("9:3", "helmet")
            )),
            new KitDefinition("lord", "fallnight.kit.lord", 21_600L, List.of(
                new KitReward("13:3", "inventory"),
                new KitReward("6:5", "inventory"),
                new KitReward("12:3", "boots"),
                new KitReward("11:3", "leggings"),
                new KitReward("10:3", "chestplate"),
                new KitReward("9:3", "helmet")
            )),
            new KitDefinition("titan", "fallnight.kit.titan", 21_600L, List.of(
                new KitReward("13:4", "inventory"),
                new KitReward("6:5", "inventory"),
                new KitReward("12:4", "boots"),
                new KitReward("11:4", "leggings"),
                new KitReward("10:4", "chestplate"),
                new KitReward("9:4", "helmet")
            ))
        );

        Map<String, KitDefinition> byId = new LinkedHashMap<>();
        for (KitDefinition kit : kits) {
            byId.put(kit.id(), kit);
        }
        this.kitsById = Map.copyOf(byId);
    }

    public List<KitDefinition> listKits() {
        return kits;
    }

    public long remainingCooldownSeconds(UserProfile profile, String kitId) {
        Objects.requireNonNull(profile, "profile");
        String normalizedKitId = normalizeKitId(kitId);
        if (normalizedKitId.isEmpty()) {
            return 0L;
        }

        KitDefinition kit = kitsById.get(normalizedKitId);
        if (kit == null) {
            return 0L;
        }

        long now = Instant.now().getEpochSecond();
        long availableAt = readCooldown(profile).getOrDefault(normalizedKitId, 0L);
        return Math.max(0L, availableAt - now);
    }

    public ClaimResult claimKit(Player player, UserProfile profile, String kitId) {
        return claimKit(player, profile, kitId, null);
    }

    public ClaimResult claimKit(Player player, UserProfile profile, String kitId, xyz.fallnight.server.command.framework.PermissionService permissionService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");

        String normalizedKitId = normalizeKitId(kitId);
        KitDefinition kit = kitsById.get(normalizedKitId);
        if (kit == null) {
            return ClaimResult.invalidKit();
        }
        if (permissionService != null && !permissionService.hasPermission(player, kit.permission())) {
            return ClaimResult.noPermission(kit);
        }
        if (!canAdd(player, kit)) {
            return ClaimResult.inventoryFull(kit);
        }

        long now = Instant.now().getEpochSecond();
        Map<String, Long> cooldowns = readCooldown(profile);
        long availableAt = cooldowns.getOrDefault(normalizedKitId, 0L);
        long remaining = Math.max(0L, availableAt - now);
        if (remaining > 0L) {
            return ClaimResult.onCooldown(kit, remaining);
        }

        for (KitReward reward : kit.rewards()) {
            ItemStack item = customItemService.createFromCustomId(reward.customItemId()).orElse(ItemStack.AIR);
            if (item.isAir()) {
                continue;
            }
            if (reward.armorSlot()) {
                EquipmentSlot slot = armorSlot(reward.slot());
                if (slot != null && player.getInventory().getEquipment(slot, player.getHeldSlot()).isAir()) {
                    player.getInventory().setEquipment(slot, player.getHeldSlot(), item);
                    continue;
                }
            }
            player.getInventory().addItemStack(item);
        }

        cooldowns.put(normalizedKitId, now + kit.cooldownSeconds());
        profile.getExtraData().put(COOLDOWNS_EXTRA_DATA_KEY, cooldowns);
        profileService.save(profile);

        return ClaimResult.success(kit);
    }

    private boolean canAdd(Player player, KitDefinition kit) {
        int freeSlots = 0;
        for (int slot = 0; slot < player.getInventory().getInnerSize(); slot++) {
            ItemStack existing = player.getInventory().getItemStack(slot);
            if (existing == null || existing.isAir()) {
                freeSlots++;
            }
        }
        int neededInventorySlots = 0;
        for (KitReward reward : kit.rewards()) {
            if (reward.armorSlot()) {
                EquipmentSlot slot = armorSlot(reward.slot());
                if (slot != null && player.getInventory().getEquipment(slot, player.getHeldSlot()).isAir()) {
                    continue;
                }
            }
            neededInventorySlots++;
        }
        return freeSlots >= neededInventorySlots;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> readCooldown(UserProfile profile) {
        Object raw = profile.getExtraData().get(COOLDOWNS_EXTRA_DATA_KEY);
        if (!(raw instanceof Map<?, ?> rawMap)) {
            Map<String, Long> created = new LinkedHashMap<>();
            profile.getExtraData().put(COOLDOWNS_EXTRA_DATA_KEY, created);
            return created;
        }

        if (isStringLongMap(rawMap)) {
            return (Map<String, Long>) rawMap;
        }

        Map<String, Long> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = normalizeKitId(entry.getKey() == null ? "" : String.valueOf(entry.getKey()));
            if (key.isEmpty()) {
                continue;
            }
            long availableAt = parseEpochSecond(entry.getValue());
            if (availableAt <= 0L) {
                continue;
            }
            normalized.put(key, availableAt);
        }

        profile.getExtraData().put(COOLDOWNS_EXTRA_DATA_KEY, normalized);
        return normalized;
    }

    private static boolean isStringLongMap(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Long)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeKitId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static long parseEpochSecond(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static EquipmentSlot armorSlot(String slot) {
        return switch (slot) {
            case "helmet" -> EquipmentSlot.HELMET;
            case "chestplate" -> EquipmentSlot.CHESTPLATE;
            case "leggings" -> EquipmentSlot.LEGGINGS;
            case "boots" -> EquipmentSlot.BOOTS;
            default -> null;
        };
    }

    public enum ClaimStatus {
        SUCCESS,
        INVALID_KIT,
        ON_COOLDOWN,
        NO_PERMISSION,
        INVENTORY_FULL
    }

    public record ClaimResult(ClaimStatus status, KitDefinition kit, long remainingCooldownSeconds) {
        static ClaimResult success(KitDefinition kit) {
            return new ClaimResult(ClaimStatus.SUCCESS, kit, 0L);
        }

        static ClaimResult invalidKit() {
            return new ClaimResult(ClaimStatus.INVALID_KIT, null, 0L);
        }

        static ClaimResult onCooldown(KitDefinition kit, long remainingCooldownSeconds) {
            return new ClaimResult(ClaimStatus.ON_COOLDOWN, kit, Math.max(1L, remainingCooldownSeconds));
        }

        static ClaimResult noPermission(KitDefinition kit) {
            return new ClaimResult(ClaimStatus.NO_PERMISSION, kit, 0L);
        }

        static ClaimResult inventoryFull(KitDefinition kit) {
            return new ClaimResult(ClaimStatus.INVENTORY_FULL, kit, 0L);
        }
    }
}
