package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

public final class PlayerProfileService {
    private final UserProfileService userProfileService;
    private final ConcurrentMap<UUID, UserProfile> onlineProfiles;
    private final ConcurrentMap<String, UUID> onlineByUsername;
    private final ConcurrentMap<UUID, Player> onlinePlayers;

    public PlayerProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
        this.onlineProfiles = new ConcurrentHashMap<>();
        this.onlineByUsername = new ConcurrentHashMap<>();
        this.onlinePlayers = new ConcurrentHashMap<>();
    }

    public UserProfile playerJoin(Player player) {
        return load(player);
    }

    public void playerQuit(Player player) {
        save(player);
        onlineProfiles.remove(player.getUuid());
        onlineByUsername.remove(normalize(player.getUsername()));
        onlinePlayers.remove(player.getUuid());
    }

    public UserProfile load(Player player) {
        UserProfile profile = userProfileService.getOrCreate(player.getUsername());
        profile.setUsername(player.getUsername());
        normalizeLegacyKeys(profile);
        rememberConnectionIdentifiers(player, profile);
        onlineProfiles.put(player.getUuid(), profile);
        onlineByUsername.put(normalize(player.getUsername()), player.getUuid());
        onlinePlayers.put(player.getUuid(), player);
        return profile;
    }

    public void save(Player player) {
        UserProfile online = onlineProfiles.get(player.getUuid());
        if (online != null) {
            online.setUsername(player.getUsername());
            snapshotInventory(player, online);
            userProfileService.save(online);
            return;
        }
        userProfileService.find(player.getUsername()).ifPresent(userProfileService::save);
    }

    public void save(UserProfile profile) {
        userProfileService.save(profile);
    }

    public void saveAllOnline() {
        for (Player player : onlinePlayers.values()) {
            save(player);
        }
    }

    public Optional<UserProfile> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        UUID onlineUuid = onlineByUsername.get(normalize(username));
        if (onlineUuid != null) {
            UserProfile online = onlineProfiles.get(onlineUuid);
            if (online != null) {
                return Optional.of(online);
            }
        }
        return userProfileService.find(username);
    }

    public Optional<UserProfile> findOfflineByUsername(String username) {
        return userProfileService.find(username);
    }

    public UserProfile getOrCreateByUsername(String username) {
        return findByUsername(username).orElseGet(() -> userProfileService.getOrCreate(username));
    }

    public UserProfile getOrCreate(Player player) {
        UserProfile existing = onlineProfiles.get(player.getUuid());
        if (existing != null) {
            existing.setUsername(player.getUsername());
            return existing;
        }
        return playerJoin(player);
    }

    public List<UserProfile> allProfiles() {
        return userProfileService.allProfiles();
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static void rememberConnectionIdentifiers(Player player, UserProfile profile) {
        SocketAddress remote = player.getPlayerConnection().getRemoteAddress();
        if (remote == null) {
            return;
        }
        List<String> ips = stringList(profile.getExtraData().get("iplist"));
        String ip = normalizeIp(remote);
        if (!ips.contains(ip)) {
            ips.add(ip);
            profile.getExtraData().put("iplist", ips);
        }
    }

    private static String normalizeIp(SocketAddress remote) {
        if (remote instanceof InetSocketAddress inet) {
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress().toLowerCase(Locale.ROOT);
            }
            return inet.getHostString().toLowerCase(Locale.ROOT);
        }
        return remote.toString().replaceFirst("^/", "").split(":", 2)[0].toLowerCase(Locale.ROOT);
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            iterable.forEach(item -> values.add(String.valueOf(item)));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static void normalizeLegacyKeys(UserProfile profile) {
        Map<String, Object> data = profile.getExtraData();
        moveIfMissing(data, "tags", "unlockedTags");
        moveIfMissing(data, "ownedTags", "unlockedTags");
        moveIfMissing(data, "blockedUsers", "blockedPlayers");
        moveIfMissing(data, "nick", "nickname");
        moveIfMissing(data, "size", "playerSize");
        moveIfMissing(data, "vanished", "vanish");
        moveIfMissing(data, "hasSeenRules", "seenRules");
        moveIfMissing(data, "totalearned", "totalEarnedMoney");
        moveIfMissing(data, "maxAucItems", "maxAuctionListings");
        moveIfMissing(data, "hasRecievedGuide", "receivedStartItems");
        moveIfMissing(data, "hasRecievedGuide", "hasReceivedStartItems");
        data.remove("adminMode");
        Object achievements = data.get("achievements");
        if (achievements instanceof Map<?, ?> map) {
            List<String> achieved = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue()) && entry.getKey() != null) {
                    String mapped = mapLegacyAchievementId(String.valueOf(entry.getKey()));
                    if (!mapped.isBlank()) {
                        achieved.add(mapped);
                    }
                }
            }
            data.put("achievements", achieved);
            data.put("claimedAchievements", achieved);
        }
        Object cooldowns = data.get("kitCooldowns");
        if (cooldowns instanceof Map<?, ?> rawMap) {
            Map<String, Long> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                String mapped = switch (key) {
                    case "10" -> "starter";
                    default -> key;
                };
                if (mapped.isBlank()) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    normalized.put(mapped, number.longValue());
                }
            }
            data.put("kitCooldowns", normalized);
        }
        Object clientIds = data.get("clientIdList");
        if (clientIds instanceof Iterable<?> iterable) {
            List<String> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(String.valueOf(item)));
            data.put("clientIdList", normalized);
        }
    }

    private static void moveIfMissing(Map<String, Object> data, String from, String to) {
        if (!data.containsKey(from) || data.containsKey(to)) {
            return;
        }
        data.put(to, data.get(from));
    }

    private static String mapLegacyAchievementId(String rawId) {
        if (rawId == null) {
            return "";
        }
        return switch (rawId.trim()) {
            case "0" -> AchievementService.TIME_TO_MINE;
            case "1" -> AchievementService.GETTING_AN_UPGRADE;
            case "2" -> AchievementService.BIG_BUCKS_1;
            case "3" -> AchievementService.BIG_BUCKS_2;
            case "4" -> AchievementService.BIG_BUCKS_3;
            case "5" -> AchievementService.BIG_BUCKS_4;
            case "6" -> AchievementService.BIG_BUCKS_5;
            case "7" -> AchievementService.GRINDER_1;
            case "8" -> AchievementService.GRINDER_2;
            case "9" -> AchievementService.GRINDER_3;
            case "10" -> AchievementService.GRINDER_4;
            case "11" -> AchievementService.GRINDER_5;
            case "12" -> AchievementService.QUALITY_MARKSMANSHIP;
            case "13" -> AchievementService.DAREDEVIL;
            case "14" -> AchievementService.PRESTIGIOUS;
            case "15" -> AchievementService.WHAT_DID_IT_COST;
            case "16" -> "";
            case "17" -> AchievementService.TEAM_UP;
            case "18" -> AchievementService.KOTH;
            default -> rawId.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static void snapshotInventory(Player player, UserProfile profile) {
        List<Map<String, Object>> inventory = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot >= 41 && slot <= 44) {
                continue;
            }
            ItemStack stack = player.getInventory().getItemStack(slot);
            Map<String, Object> serialized = serializeStack(slot, stack, null);
            if (serialized != null) {
                inventory.add(serialized);
            }
        }
        List<Map<String, Object>> armor = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.armors()) {
            ItemStack stack = player.getInventory().getEquipment(slot, player.getHeldSlot());
            Map<String, Object> serialized = serializeStack(-1, stack, slot.name().toLowerCase(Locale.ROOT));
            if (serialized != null) {
                armor.add(serialized);
            }
        }
        profile.getExtraData().put("inventorySnapshot", inventory);
        profile.getExtraData().put("armorSnapshot", armor);
    }

    private static Map<String, Object> serializeStack(int slot, ItemStack stack, String armorSlot) {
        if (stack == null || stack.isAir() || stack.amount() <= 0) {
            return null;
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        if (slot >= 0) {
            serialized.put("slot", slot);
        }
        if (armorSlot != null) {
            serialized.put("armorSlot", armorSlot);
        }
        serialized.put("material", stack.material().name().toLowerCase(Locale.ROOT));
        serialized.put("amount", stack.amount());
        try {
            serialized.put("nbt", TagStringIO.get().asString(stack.toItemNBT()));
        } catch (Exception ignored) {
        }
        var customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            serialized.put("name", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName));
        }
        return serialized;
    }

    public static ItemStack deserializeSnapshotItem(Map<?, ?> serialized) {
        Object nbt = serialized.get("nbt");
        if (nbt instanceof String text && !text.isBlank()) {
            try {
                return ItemStack.fromItemNBT(TagStringIO.get().asCompound(text));
            } catch (Exception ignored) {
            }
        }
        Object material = serialized.get("material");
        if (material == null) {
            return ItemStack.AIR;
        }
        net.minestom.server.item.Material resolved = net.minestom.server.item.Material.fromKey(String.valueOf(material));
        if (resolved == null) {
            return ItemStack.AIR;
        }
        Object amount = serialized.get("amount");
        int count = amount instanceof Number number ? number.intValue() : 1;
        return ItemStack.of(resolved, Math.max(1, count));
    }
}
