package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.tag.TagDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.tag.TagDefinitionRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TagService {
    private final TagDefinitionRepository repository;
    private final ConcurrentMap<String, TagDefinition> tagsById;

    public TagService(TagDefinitionRepository repository) {
        this.repository = repository;
        this.tagsById = new ConcurrentHashMap<>();
    }

    private static String rarityColor(int rarity) {
        return switch (rarity) {
            case 2 -> "§a";
            case 3 -> "§9";
            case 4 -> "§5";
            case 5 -> "§b";
            default -> "§7";
        };
    }

    private static String rarityName(int rarity) {
        return switch (rarity) {
            case 2 -> "uncommon";
            case 3 -> "rare";
            case 4 -> "very rare";
            case 5 -> "legendary";
            default -> "common";
        };
    }

    private static LinkedHashSet<String> unlockedIdSet(UserProfile profile) {
        Object raw = profile.getExtraData().get("unlockedTags");
        if (raw == null) {
            raw = profile.getExtraData().get("ownedTags");
        }
        if (raw == null) {
            raw = profile.getExtraData().get("tags");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                String parsed = stringValue(entry);
                if (parsed != null) {
                    ids.add(normalize(parsed));
                }
            }
            return ids;
        }

        String single = stringValue(raw);
        if (single != null) {
            ids.add(normalize(single));
        }
        return ids;
    }

    private static void storeUnlockedIds(UserProfile profile, LinkedHashSet<String> ids) {
        List<String> stored = List.copyOf(ids);
        profile.getExtraData().put("unlockedTags", stored);
        profile.getExtraData().put("ownedTags", stored);
    }

    private static String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public void loadDefinitions() throws IOException {
        Map<String, TagDefinition> loaded = repository.loadAll();
        tagsById.clear();
        tagsById.putAll(loaded);
    }

    public List<TagDefinition> listDefinitions() {
        return tagsById.values().stream()
                .sorted(Comparator.comparing(TagDefinition::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<TagDefinition> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tagsById.get(normalize(id)));
    }

    public UnlockResult unlockTag(UserProfile profile, String id) {
        Optional<TagDefinition> definition = findById(id);
        if (definition.isEmpty()) {
            return UnlockResult.TAG_NOT_FOUND;
        }

        LinkedHashSet<String> unlockedIds = unlockedIdSet(profile);
        String resolvedId = normalize(definition.get().id());
        if (unlockedIds.contains(resolvedId)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }

        unlockedIds.add(resolvedId);
        storeUnlockedIds(profile, unlockedIds);
        return UnlockResult.SUCCESS;
    }

    public ApplyResult setAppliedTag(UserProfile profile, String id) {
        Optional<TagDefinition> definition = findById(id);
        if (definition.isEmpty()) {
            return ApplyResult.TAG_NOT_FOUND;
        }

        if (definition.get().publicTag()) {
            profile.getExtraData().put("appliedTag", definition.get().tag());
            return ApplyResult.SUCCESS;
        }

        LinkedHashSet<String> unlockedIds = unlockedIdSet(profile);
        String resolvedId = normalize(definition.get().id());
        if (!unlockedIds.contains(resolvedId)) {
            return ApplyResult.NOT_UNLOCKED;
        }

        profile.getExtraData().put("appliedTag", definition.get().tag());
        return ApplyResult.SUCCESS;
    }

    public boolean clearAppliedTag(UserProfile profile) {
        return profile.getExtraData().remove("appliedTag") != null;
    }

    public List<String> listUnlockedTagIds(UserProfile profile) {
        return List.copyOf(unlockedIdSet(profile));
    }

    public List<TagDefinition> listUnlockedTags(UserProfile profile) {
        List<TagDefinition> tags = new ArrayList<>();
        for (TagDefinition definition : listDefinitions()) {
            if (definition.publicTag()) {
                tags.add(definition);
            }
        }
        for (String id : unlockedIdSet(profile)) {
            TagDefinition definition = tagsById.get(id);
            if (definition != null) {
                tags.add(definition);
            }
        }
        return List.copyOf(tags);
    }

    public void grantJoinTags(UserProfile profile) {
        for (TagDefinition definition : listDefinitions()) {
            if (definition.receiveOnJoin()) {
                unlockTag(profile, definition.id());
            }
        }
    }

    public Optional<TagDefinition> grantRandomCrateDropTag(UserProfile profile) {
        List<TagDefinition> weighted = new ArrayList<>();
        for (TagDefinition definition : listDefinitions()) {
            if (!definition.crateDrop()) {
                continue;
            }
            int weight = Math.max(1, 6 - definition.rarity());
            for (int i = 0; i < weight * 10; i++) {
                weighted.add(definition);
            }
        }
        if (weighted.isEmpty()) {
            return Optional.empty();
        }
        TagDefinition selected = weighted.get(ThreadLocalRandom.current().nextInt(weighted.size()));
        grantCrateRewardTag(profile, selected);
        return Optional.of(selected);
    }

    public Optional<TagDefinition> grantSpecificCrateTag(UserProfile profile, String id) {
        Optional<TagDefinition> definition = findById(id);
        definition.ifPresent(tag -> grantCrateRewardTag(profile, tag));
        return definition;
    }

    private void grantCrateRewardTag(UserProfile profile, TagDefinition definition) {
        LinkedHashSet<String> unlockedIds = unlockedIdSet(profile);
        String resolvedId = normalize(definition.id());
        if (unlockedIds.contains(resolvedId)) {
            long prestige = Math.max(0, definition.rarity()) * 50L;
            profile.addPrestigePoints(prestige);
            profile.getExtraData().put("lastTagRewardMessage", "§r§b§l> §r§7You have received a duplicate §r " + definition.tag() + "§7 and received§b " + prestige + " §7prestige points.");
            return;
        }
        unlockedIds.add(resolvedId);
        storeUnlockedIds(profile, unlockedIds);
        profile.getExtraData().put("lastTagRewardMessage", "§r§b§l> §r§7You have received the §r" + definition.tag() + "§r§7 tag with " + rarityColor(definition.rarity()) + rarityName(definition.rarity()) + "§r§7 rarity.");
    }

    public String consumeLastRewardMessage(UserProfile profile) {
        Object raw = profile.getExtraData().remove("lastTagRewardMessage");
        return raw == null ? null : String.valueOf(raw);
    }

    public enum UnlockResult {
        SUCCESS,
        TAG_NOT_FOUND,
        ALREADY_UNLOCKED
    }

    public enum ApplyResult {
        SUCCESS,
        TAG_NOT_FOUND,
        NOT_UNLOCKED
    }
}
