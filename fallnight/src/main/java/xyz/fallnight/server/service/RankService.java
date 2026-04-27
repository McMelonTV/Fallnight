package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.rank.RankDefinition;
import xyz.fallnight.server.domain.rank.RankInstance;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RankService {
    private static final String RANK_COMPONENT_FIELD = "rankComponent";
    private static final String RANKS_FIELD = "ranks";
    private static final List<String> ADMIN_COMMAND_PERMISSIONS = List.of("fallnight.command.*", "ranksystem.command.*");
    private static final Set<String> LEGACY_RANK_COMMANDS = Set.of(
            "addrank",
            "removerank",
            "listranks",
            "playerranks",
            "rankinfo"
    );

    private final Path rankFile;
    private final ObjectMapper yaml;
    private final Map<String, RankDefinition> ranks = new ConcurrentHashMap<>();

    public RankService(Path rankFile) {
        this.rankFile = rankFile;
        this.yaml = JacksonMappers.yamlMapper();
    }

    private static List<RankDefinition> defaultRankDefinitions() {
        RankDefinition member = new RankDefinition();
        member.setId("member");
        member.setName("Member");
        member.setPrefix("§fMember");
        member.setDefaultRank(true);
        member.setStaff(false);
        member.setDonator(false);
        member.setPriority(0);
        member.setVaults(0);
        member.setPlots(0);
        member.setPermissions(List.of());
        member.setInherit(List.of());

        RankDefinition admin = new RankDefinition();
        admin.setId("admin");
        admin.setName("Admin");
        admin.setPrefix("§4Admin");
        admin.setDefaultRank(false);
        admin.setStaff(true);
        admin.setDonator(false);
        admin.setPriority(90);
        admin.setVaults(0);
        admin.setPlots(0);
        admin.setPermissions(List.of("fallnight.command.*", "ranksystem.command.*"));
        admin.setInherit(List.of());

        RankDefinition owner = new RankDefinition();
        owner.setId("owner");
        owner.setName("Owner");
        owner.setPrefix("§4Owner");
        owner.setDefaultRank(false);
        owner.setStaff(true);
        owner.setDonator(false);
        owner.setPriority(100);
        owner.setVaults(0);
        owner.setPlots(0);
        owner.setPermissions(List.of());
        owner.setInherit(List.of("admin"));

        return List.of(member, admin, owner);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rankMap(UserProfile profile, boolean create) {
        Object rawComponent = profile.getExtraData().get(RANK_COMPONENT_FIELD);
        Map<String, Object> component;
        if (rawComponent instanceof Map<?, ?> rawMap) {
            component = (Map<String, Object>) rawMap;
        } else if (create) {
            component = new LinkedHashMap<>();
            profile.getExtraData().put(RANK_COMPONENT_FIELD, component);
        } else {
            return new LinkedHashMap<>();
        }

        Object rawRanks = component.get(RANKS_FIELD);
        if (rawRanks instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        if (!create) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> ranks = new LinkedHashMap<>();
        component.put(RANKS_FIELD, ranks);
        return ranks;
    }

    private static void cleanupRankComponent(UserProfile profile, Map<String, Object> ranksMap) {
        if (!ranksMap.isEmpty()) {
            return;
        }
        Object rawComponent = profile.getExtraData().get(RANK_COMPONENT_FIELD);
        if (!(rawComponent instanceof Map<?, ?> component)) {
            return;
        }
        ((Map<?, ?>) component).remove(RANKS_FIELD);
        if (((Map<?, ?>) component).isEmpty()) {
            profile.getExtraData().remove(RANK_COMPONENT_FIELD);
        }
    }

    private static RankInstance parseRankInstance(String key, Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> data)) {
            if (key == null || key.isBlank()) {
                return null;
            }
            return new RankInstance(key.trim().toLowerCase(Locale.ROOT), -1L, true);
        }
        String rankId = stringValue(data.get("rankId"), key == null ? "" : key);
        if (rankId.isBlank()) {
            return null;
        }
        long expire = longValue(data.get("expire"), -1L);
        boolean persistent = booleanValue(data.get("isPersistent"), booleanValue(data.get("isPersistant"), true));
        return new RankInstance(rankId.toLowerCase(Locale.ROOT), expire, persistent);
    }

    private static Map<String, Object> toData(RankInstance instance) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rankId", instance.rankId().toLowerCase(Locale.ROOT));
        data.put("isPersistant", instance.persistent());
        data.put("isPersistent", instance.persistent());
        data.put("expire", instance.expire());
        return data;
    }

    private static boolean matchesPermission(String granted, String requested) {
        if (granted.equals("*") || granted.equals(requested)) {
            return true;
        }
        if (granted.endsWith(".*")) {
            String prefix = granted.substring(0, granted.length() - 1);
            return requested.startsWith(prefix);
        }
        return false;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback == null ? "" : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("on")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no") || text.equalsIgnoreCase("off")) {
                return false;
            }
        }
        return fallback;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item == null) {
                continue;
            }
            String entry = String.valueOf(item).trim();
            if (!entry.isBlank()) {
                result.add(entry);
            }
        }
        return result;
    }

    public void save() {
        try {
            Files.createDirectories(rankFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> rankNode = new LinkedHashMap<>();
            for (RankDefinition rank : allRanks()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", rank.id());
                data.put("name", rank.name());
                data.put("prefix", rank.prefix());
                data.put("isDefault", rank.defaultRank());
                data.put("isStaff", rank.staff());
                data.put("isDonator", rank.donator());
                data.put("priority", rank.priority());
                data.put("vaults", rank.vaults());
                data.put("plots", rank.plots());
                data.put("permissions", rank.permissions());
                data.put("inherit", rank.inherit());
                rankNode.put(rank.id(), data);
            }
            root.put("ranks", rankNode);
            yaml.writeValue(rankFile.toFile(), root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public List<RankDefinition> allRanks() {
        return ranks.values().stream().sorted(Comparator.comparingInt(RankDefinition::priority)).toList();
    }

    public Optional<RankDefinition> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ranks.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<RankInstance> assignedRanks(UserProfile profile) {
        if (profile == null) {
            return List.of();
        }
        Map<String, Object> ranksMap = rankMap(profile, false);
        if (ranksMap.isEmpty()) {
            return List.of();
        }
        List<RankInstance> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ranksMap.entrySet()) {
            RankInstance instance = parseRankInstance(entry.getKey(), entry.getValue());
            if (instance != null) {
                result.add(instance);
            }
        }
        result.sort(Comparator.comparing(RankInstance::rankId));
        return result;
    }

    public List<RankInstance> effectiveRanks(UserProfile profile) {
        return effectiveRanks(profile, Instant.now());
    }

    public List<RankInstance> effectiveRanks(UserProfile profile, Instant now) {
        LinkedHashMap<String, RankInstance> effective = new LinkedHashMap<>();
        for (RankDefinition rank : defaultRanks()) {
            effective.put(rank.id().toLowerCase(Locale.ROOT), new RankInstance(rank.id(), -1L, true));
        }
        for (RankInstance instance : assignedRanks(profile)) {
            if (!instance.permanent() && instance.expire() <= now.getEpochSecond()) {
                continue;
            }
            effective.put(instance.rankId().toLowerCase(Locale.ROOT), instance);
        }
        return effective.values().stream().sorted(Comparator.comparing(RankInstance::rankId)).toList();
    }

    public List<RankInstance> removeExpiredRanks(UserProfile profile, Instant now) {
        if (profile == null) {
            return List.of();
        }
        Map<String, Object> ranksMap = rankMap(profile, false);
        if (ranksMap.isEmpty()) {
            return List.of();
        }
        List<RankInstance> expired = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ranksMap.entrySet()) {
            RankInstance instance = parseRankInstance(entry.getKey(), entry.getValue());
            if (instance == null || instance.permanent() || instance.expire() > now.getEpochSecond()) {
                continue;
            }
            expired.add(instance);
            toRemove.add(entry.getKey());
        }
        toRemove.forEach(ranksMap::remove);
        cleanupRankComponent(profile, ranksMap);
        expired.sort(Comparator.comparing(RankInstance::rankId));
        return expired;
    }

    public boolean assignRank(UserProfile profile, String rankId, long expire, boolean persistent) {
        RankDefinition rank = findById(rankId).orElse(null);
        if (profile == null || rank == null) {
            return false;
        }
        Map<String, Object> ranksMap = rankMap(profile, true);
        ranksMap.put(rank.id().toLowerCase(Locale.ROOT), toData(new RankInstance(rank.id(), expire, persistent)));
        return true;
    }

    public boolean removeRank(UserProfile profile, String rankId) {
        if (profile == null || rankId == null || rankId.isBlank()) {
            return false;
        }
        Map<String, Object> ranksMap = rankMap(profile, false);
        Object removed = ranksMap.remove(rankId.toLowerCase(Locale.ROOT));
        cleanupRankComponent(profile, ranksMap);
        return removed != null;
    }

    public void resetSeasonRanks(UserProfile profile) {
        if (profile == null) {
            return;
        }
        Optional<RankDefinition> donor = donatorRank(profile);
        Optional<RankDefinition> staff = staffRank(profile);
        Map<String, Object> ranksMap = rankMap(profile, false);
        ranksMap.clear();
        cleanupRankComponent(profile, ranksMap);
        if (staff.isPresent()) {
            assignRank(profile, staff.get().id(), -1L, false);
            return;
        }
        donor.ifPresent(rank -> assignRank(profile, rank.id(), -1L, false));
    }

    public String displayPrefix(UserProfile profile) {
        return mainRank(profile).map(RankDefinition::prefix).orElse("§fMember");
    }

    public Optional<RankDefinition> mainRank(UserProfile profile) {
        return effectiveRankDefinitions(profile).stream().max(Comparator.comparingInt(RankDefinition::priority));
    }

    public Optional<RankDefinition> donatorRank(UserProfile profile) {
        return effectiveRankDefinitions(profile).stream()
                .filter(RankDefinition::donator)
                .max(Comparator.comparingInt(RankDefinition::priority));
    }

    public Optional<RankDefinition> staffRank(UserProfile profile) {
        return effectiveRankDefinitions(profile).stream()
                .filter(RankDefinition::staff)
                .max(Comparator.comparingInt(RankDefinition::priority));
    }

    public boolean isDonator(UserProfile profile) {
        return donatorRank(profile).isPresent();
    }

    public boolean isStaff(UserProfile profile) {
        return staffRank(profile).isPresent();
    }

    public int vaultPageLimit(UserProfile profile) {
        int max = 0;
        for (RankDefinition rank : effectiveRankDefinitions(profile)) {
            max = Math.max(max, rank.vaults());
        }
        return max;
    }

    public int plotLimit(UserProfile profile) {
        int max = 0;
        for (RankDefinition rank : effectiveRankDefinitions(profile)) {
            max = Math.max(max, rank.plots());
        }
        return max;
    }

    public boolean hasPermission(UserProfile profile, String permission) {
        if (profile == null || permission == null || permission.isBlank()) {
            return false;
        }
        String normalized = permission.toLowerCase(Locale.ROOT);
        Boolean decision = permissionDecision(profile, normalized);
        if (decision == null) {
            String legacyAlias = permissionAlias(normalized);
            if (legacyAlias != null) {
                decision = permissionDecision(profile, legacyAlias);
            }
        }
        return Boolean.TRUE.equals(decision);
    }

    private Boolean permissionDecision(UserProfile profile, String normalizedPermission) {
        Boolean decision = null;
        for (String entry : permissionsFor(profile)) {
            String trimmed = entry == null ? "" : entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean grant = true;
            if (trimmed.startsWith("-")) {
                grant = false;
                trimmed = trimmed.substring(1);
            }
            if (matchesPermission(trimmed.toLowerCase(Locale.ROOT), normalizedPermission)) {
                decision = grant;
            }
        }
        return decision;
    }

    private static String permissionAlias(String permission) {
        if (permission.startsWith("fallnight.command.")) {
            String suffix = permission.substring("fallnight.command.".length());
            if (LEGACY_RANK_COMMANDS.contains(suffix)) {
                return "ranksystem.command." + suffix;
            }
            return null;
        }
        if (permission.startsWith("ranksystem.command.")) {
            String suffix = permission.substring("ranksystem.command.".length());
            if (LEGACY_RANK_COMMANDS.contains(suffix)) {
                return "fallnight.command." + suffix;
            }
        }
        return null;
    }

    public List<RankDefinition> defaultRanks() {
        return ranks.values().stream().filter(RankDefinition::defaultRank).toList();
    }

    public List<String> permissionsFor(UserProfile profile) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        for (RankDefinition rank : effectiveRankDefinitions(profile)) {
            collectPermissions(rank, permissions, new LinkedHashSet<>());
        }
        return List.copyOf(permissions);
    }

    public List<RankDefinition> effectiveRankDefinitions(UserProfile profile) {
        List<RankDefinition> definitions = new ArrayList<>();
        for (RankInstance instance : effectiveRanks(profile)) {
            RankDefinition rank = findById(instance.rankId()).orElse(null);
            if (rank != null) {
                definitions.add(rank);
            }
        }
        definitions.sort(Comparator.comparingInt(RankDefinition::priority));
        return definitions;
    }

    private void collectPermissions(RankDefinition rank, Set<String> collector, Set<String> visited) {
        String id = rank.id().toLowerCase(Locale.ROOT);
        if (!visited.add(id)) {
            return;
        }
        for (String inheritId : rank.inherit()) {
            RankDefinition inherited = ranks.get(inheritId.toLowerCase(Locale.ROOT));
            if (inherited != null) {
                collectPermissions(inherited, collector, visited);
            }
        }
        collector.addAll(rank.permissions());
    }

    public void load() throws IOException {
        if (!Files.exists(rankFile)) {
            ranks.clear();
            defaultRankDefinitions().forEach(rank -> ranks.put(rank.id().toLowerCase(Locale.ROOT), rank));
            save();
            return;
        }

        Object data = yaml.readValue(rankFile.toFile(), Object.class);
        if (!(data instanceof Map<?, ?> root)) {
            return;
        }
        Object ranksNode = root.get("ranks");
        if (!(ranksNode instanceof Map<?, ?> rankMap)) {
            return;
        }

        ranks.clear();
        for (Map.Entry<?, ?> entry : rankMap.entrySet()) {
            String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            if (!(entry.getValue() instanceof Map<?, ?> value)) {
                continue;
            }

            RankDefinition rank = new RankDefinition();
            rank.setId(stringValue(value.get("id"), key));
            rank.setName(stringValue(value.get("name"), key));
            rank.setPrefix(stringValue(value.get("prefix"), rank.name()));
            rank.setDefaultRank(booleanValue(value.get("isDefault"), false));
            rank.setStaff(booleanValue(value.get("isStaff"), false));
            rank.setDonator(booleanValue(value.get("isDonator"), false));
            rank.setPriority(intValue(value.get("priority"), 0));
            rank.setVaults(intValue(value.get("vaults"), 0));
            rank.setPlots(intValue(value.get("plots"), 0));
            rank.setPermissions(stringList(value.get("permissions")));
            rank.setInherit(stringList(value.get("inherit")));
            ranks.put(rank.id().toLowerCase(Locale.ROOT), rank);
        }
        if (normalizeBuiltinRanks()) {
            save();
        }
    }

    private boolean normalizeBuiltinRanks() {
        boolean changed = false;

        RankDefinition admin = ranks.get("admin");
        if (admin != null) {
            LinkedHashSet<String> permissions = new LinkedHashSet<>(admin.permissions());
            if (permissions.addAll(ADMIN_COMMAND_PERMISSIONS)) {
                admin.setPermissions(List.copyOf(permissions));
                changed = true;
            }
        }

        RankDefinition owner = ranks.get("owner");
        if (owner != null) {
            LinkedHashSet<String> inherit = new LinkedHashSet<>(owner.inherit());
            if (inherit.add("admin")) {
                owner.setInherit(List.copyOf(inherit));
                changed = true;
            }
        }

        return changed;
    }
}
