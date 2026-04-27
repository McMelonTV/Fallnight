package xyz.fallnight.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import xyz.fallnight.server.domain.crate.CrateDefinition;
import xyz.fallnight.server.domain.crate.CrateReward;
import xyz.fallnight.server.domain.crate.WeightedCrateReward;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateService {
    private final Map<String, CrateDefinition> crates;
    private final LegacyCustomItemService customItemService;
    private final ForgeService forgeService;
    private final ItemDeliveryService itemDeliveryService;
    private final TagService tagService;

    public CrateService(List<CrateDefinition> crates, LegacyCustomItemService customItemService, ForgeService forgeService, ItemDeliveryService itemDeliveryService, TagService tagService) {
        Map<String, CrateDefinition> byId = new LinkedHashMap<>();
        for (CrateDefinition crate : Objects.requireNonNull(crates, "crates")) {
            byId.put(crate.id(), crate);
        }
        this.crates = Map.copyOf(byId);
        this.customItemService = customItemService;
        this.forgeService = forgeService;
        this.itemDeliveryService = itemDeliveryService;
        this.tagService = tagService;
    }

    public static CrateService createDefaults(PlayerProfileService profileService, ItemDeliveryService itemDeliveryService, TagService tagService) {
        LegacyCustomItemService items = new LegacyCustomItemService();
        ForgeService forge = new ForgeService(items, profileService, itemDeliveryService);
        List<CrateDefinition> defaults = loadDefaultsFromResource();
        return new CrateService(defaults, items, forge, itemDeliveryService, tagService);
    }

    private static List<CrateDefinition> loadDefaultsFromResource() {
        try (var stream = CrateService.class.getClassLoader().getResourceAsStream("crate-defaults.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing crate-defaults.json resource");
            }
            var mapper = JacksonMappers.jsonMapper();
            List<CrateResource> resources = mapper.readValue(stream, new TypeReference<List<CrateResource>>() {
            });
            List<CrateDefinition> defaults = new ArrayList<>();
            for (CrateResource resource : resources) {
                List<WeightedCrateReward> rewards = new ArrayList<>();
                for (RewardResource reward : resource.rewards) {
                    rewards.add(new WeightedCrateReward(
                            new CrateReward(
                                    reward.description,
                                    reward.money,
                                    reward.prestigePoints,
                                    reward.customItemId,
                                    reward.forgedBookCount,
                                    reward.highEndForge,
                                    reward.randomTagCount,
                                    reward.grantedTag
                            ),
                            reward.weight
                    ));
                }
                defaults.add(new CrateDefinition(resource.id, resource.displayName, rewards));
            }
            return defaults;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load crate defaults resource", exception);
        }
    }

    private static String normalize(String crateId) {
        if (crateId == null) {
            return "";
        }
        return crateId.trim().toLowerCase(Locale.ROOT);
    }

    private static long readLong(Object rawValue) {
        if (rawValue instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (rawValue instanceof String value) {
            try {
                return Math.max(0L, Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public List<CrateDefinition> allCrates() {
        return new ArrayList<>(crates.values());
    }

    public Optional<CrateDefinition> findCrate(String crateId) {
        return Optional.ofNullable(crates.get(normalize(crateId)));
    }

    public OpenResult openWithExternalKey(String crateId, Player player, UserProfile profile) {
        String normalized = normalize(crateId);
        CrateDefinition crate = crates.get(normalized);
        if (crate == null) {
            return OpenResult.unknownCrate();
        }
        CrateReward reward = chooseReward(crate);
        applyReward(player, profile, reward);
        return OpenResult.success(crate.id(), reward, 0L);
    }

    private void applyReward(Player player, UserProfile profile, CrateReward reward) {
        profile.deposit(reward.money());
        profile.addPrestigePoints(reward.prestigePoints());
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            customItemService.createFromCustomId(reward.customItemId()).ifPresent(item -> itemDeliveryService.deliver(player, profile, item));
        }
        if (reward.randomTagCount() > 0) {
            for (int i = 0; i < reward.randomTagCount(); i++) {
                tagService.grantRandomCrateDropTag(profile);
                sendTagRewardMessage(player, profile);
            }
        }
        if (reward.grantedTag() != null && !reward.grantedTag().isBlank()) {
            tagService.grantSpecificCrateTag(profile, reward.grantedTag());
            sendTagRewardMessage(player, profile);
        }
        if (reward.forgedBookCount() > 0) {
            for (int i = 0; i < reward.forgedBookCount(); i++) {
                ForgeService.EnchantForgeResult result = forgeService.forgeEnchantment(player, profile, reward.highEndForge());
                if (!result.success()) {
                    break;
                }
            }
        }
    }

    private CrateReward chooseReward(CrateDefinition crate) {
        int totalWeight = 0;
        for (WeightedCrateReward reward : crate.rewards()) {
            totalWeight += reward.weight();
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (WeightedCrateReward reward : crate.rewards()) {
            cumulative += reward.weight();
            if (roll < cumulative) {
                return reward.reward();
            }
        }
        return crate.rewards().get(crate.rewards().size() - 1).reward();
    }

    private void sendTagRewardMessage(Player player, UserProfile profile) {
        String message = tagService.consumeLastRewardMessage(profile);
        if (message != null && !message.isBlank()) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
        }
    }

    public enum OpenStatus {
        SUCCESS,
        UNKNOWN_CRATE,
        NO_KEYS
    }

    public record OpenResult(OpenStatus status, String crateId, CrateReward reward, long remainingKeys) {
        private static OpenResult success(String crateId, CrateReward reward, long remainingKeys) {
            return new OpenResult(OpenStatus.SUCCESS, crateId, reward, remainingKeys);
        }

        private static OpenResult unknownCrate() {
            return new OpenResult(OpenStatus.UNKNOWN_CRATE, null, null, 0L);
        }

        private static OpenResult noKeys(String crateId) {
            return new OpenResult(OpenStatus.NO_KEYS, crateId, null, 0L);
        }

        public boolean successful() {
            return status == OpenStatus.SUCCESS;
        }
    }

    private static final class CrateResource {
        public String id;
        public String displayName;
        public List<RewardResource> rewards = List.of();
    }

    private static final class RewardResource {
        public String description;
        public int weight;
        public double money;
        public long prestigePoints;
        public String customItemId;
        public int forgedBookCount;
        public boolean highEndForge;
        public int randomTagCount;
        public String grantedTag;
    }
}
