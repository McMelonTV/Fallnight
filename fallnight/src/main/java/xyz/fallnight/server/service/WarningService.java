package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.domain.warning.WarningEntry;
import xyz.fallnight.server.persistence.warning.JsonWarningRepository;
import xyz.fallnight.server.persistence.warning.WarningRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class WarningService {
    private final WarningRepository warningRepository;
    private final PlayerProfileService profileService;
    private final Map<String, List<WarningEntry>> warningsByTarget;
    private long nextId;

    public static WarningService fromDataRoot(Path dataRoot, PlayerProfileService profileService) {
        return new WarningService(new JsonWarningRepository(dataRoot.resolve("warnings.json")), profileService);
    }

    public WarningService(WarningRepository warningRepository, PlayerProfileService profileService) {
        this.warningRepository = Objects.requireNonNull(warningRepository, "warningRepository");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.warningsByTarget = new LinkedHashMap<>();
        this.nextId = 1L;
    }

    public synchronized void loadAll() throws IOException {
        WarningRepository.WarningState state = warningRepository.load();
        warningsByTarget.clear();

        long maxId = 0L;
        Instant now = Instant.now();
        for (Map.Entry<String, List<WarningEntry>> entry : state.warningsByTarget().entrySet()) {
            String key = normalize(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }

            List<WarningEntry> warnings = new ArrayList<>();
            for (WarningEntry warning : entry.getValue()) {
                WarningEntry sanitized = sanitizeWarning(warning, key);
                if (sanitized.isExpired(now)) {
                    continue;
                }
                maxId = Math.max(maxId, sanitized.getId());
                warnings.add(sanitized);
            }

            warnings.sort(Comparator.comparing(WarningEntry::getCreatedAt).thenComparingLong(WarningEntry::getId));
            warningsByTarget.put(key, warnings);
        }

        long requestedNextId = Math.max(1L, state.nextId());
        nextId = Math.max(requestedNextId, maxId + 1L);
        importLegacyWarnings(now);
    }

    public synchronized WarningEntry addWarning(String targetUsername, String actor, String reason) {
        String key = requireTargetKey(targetUsername);

        WarningEntry warning = new WarningEntry(
            nextId++,
            normalizeDisplayName(targetUsername, "unknown"),
            normalizeDisplayName(actor, "console"),
            normalizeReason(reason),
            Instant.now()
        );
        warningsByTarget.computeIfAbsent(key, ignored -> new ArrayList<>()).add(warning);
        persist();
        return warning;
    }

    public synchronized List<WarningEntry> listWarnings(String targetUsername) {
        String key = normalize(targetUsername);
        if (key.isEmpty()) {
            return List.of();
        }

        List<WarningEntry> warnings = warningsByTarget.get(key);
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }

        warnings.removeIf(warning -> warning.isExpired(Instant.now()));
        if (warnings.isEmpty()) {
            warningsByTarget.remove(key);
            persist();
            return List.of();
        }

        return List.copyOf(warnings);
    }

    public synchronized boolean clearWarning(String targetUsername, long id) {
        if (id <= 0L) {
            return false;
        }

        String key = normalize(targetUsername);
        if (key.isEmpty()) {
            return false;
        }

        List<WarningEntry> warnings = warningsByTarget.get(key);
        if (warnings == null || warnings.isEmpty()) {
            return false;
        }

        boolean removed = warnings.removeIf(entry -> entry.getId() == id);
        if (!removed) {
            return false;
        }

        if (warnings.isEmpty()) {
            warningsByTarget.remove(key);
        }
        persist();
        return true;
    }

    public synchronized int clearAllForTarget(String targetUsername) {
        String key = normalize(targetUsername);
        if (key.isEmpty()) {
            return 0;
        }

        List<WarningEntry> removed = warningsByTarget.remove(key);
        if (removed == null || removed.isEmpty()) {
            return 0;
        }

        persist();
        return removed.size();
    }

    public synchronized void saveAll() {
        persist();
    }

    private WarningEntry sanitizeWarning(WarningEntry warning, String key) {
        WarningEntry safe = warning == null ? new WarningEntry() : warning;
        safe.setId(Math.max(1L, safe.getId()));

        String target = safe.getTargetUsername();
        if (target == null || target.isBlank()) {
            safe.setTargetUsername(key);
        } else {
            safe.setTargetUsername(target);
        }

        safe.setActor(normalizeDisplayName(safe.getActor(), "unknown"));
        safe.setReason(normalizeReason(safe.getReason()));
        safe.setCreatedAt(Objects.requireNonNullElse(safe.getCreatedAt(), Instant.now()));
        return safe;
    }

    private void persist() {
        try {
            warningRepository.save(snapshot());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private WarningRepository.WarningState snapshot() {
        Map<String, List<WarningEntry>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<WarningEntry>> entry : warningsByTarget.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new WarningRepository.WarningState(Map.copyOf(copy), Math.max(1L, nextId));
    }

    private static String requireTargetKey(String username) {
        String key = normalize(username);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("targetUsername must not be blank");
        }
        return key;
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "No reason provided.";
        }
        return value.trim();
    }

    private void importLegacyWarnings(Instant now) {
        for (UserProfile profile : profileService.allProfiles()) {
            Object raw = profile.getExtraData().get("warnings");
            if (!(raw instanceof Iterable<?> iterable)) {
                continue;
            }
            String key = normalize(profile.getUsername());
            List<WarningEntry> warnings = warningsByTarget.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object staff = map.get("staff");
                Object reason = map.get("reason");
                WarningEntry warning = new WarningEntry(
                    nextId++,
                    profile.getUsername(),
                    staff == null ? "unknown" : String.valueOf(staff),
                    reason == null ? "No reason provided." : String.valueOf(reason),
                    Instant.ofEpochSecond(readLong(map.get("time"), now.getEpochSecond()))
                );
                if (!warning.isExpired(now)) {
                    warnings.add(warning);
                }
            }
            warnings.sort(Comparator.comparing(WarningEntry::getCreatedAt).thenComparingLong(WarningEntry::getId));
        }
    }

    private static long readLong(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
