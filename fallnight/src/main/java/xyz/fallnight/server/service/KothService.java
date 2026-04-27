package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.fallnight.server.domain.koth.KothHill;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class KothService {
    private final Path file;
    private final ObjectMapper yaml;
    private final PlayerProfileService profileService;
    private final LegacyCustomItemService customItemService;
    private final ItemDeliveryService itemDeliveryService;
    private final SpawnService spawnWorldService;
    private final SpawnService pvpMineWorldService;
    private final Map<String, KothHill> hills;

    private String nextHillId;
    private String activeHillId;
    private String capturerUsername;
    private int captureProgressSeconds;
    private long nextStartEpochSecond;
    private int spawnNext;

    public KothService(Path file, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService, SpawnService spawnWorldService, SpawnService pvpMineWorldService, Pos spawnPos) {
        this.file = Objects.requireNonNull(file, "file");
        this.yaml = JacksonMappers.yamlMapper();
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.customItemService = new LegacyCustomItemService();
        this.itemDeliveryService = itemDeliveryService;
        this.spawnWorldService = spawnWorldService;
        this.pvpMineWorldService = pvpMineWorldService;
        this.hills = new LinkedHashMap<>();
        this.nextHillId = null;
        this.activeHillId = null;
        this.capturerUsername = null;
        this.captureProgressSeconds = 0;
        this.nextStartEpochSecond = Instant.now().getEpochSecond() + (6 * 3600L);
        this.spawnNext = 0;
    }

    public static KothService fromDataRoot(Path dataRoot, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService, SpawnService spawnWorldService, SpawnService pvpMineWorldService, Pos spawnPos) {
        return new KothService(dataRoot.resolve("koth.yml"), profileService, itemDeliveryService, spawnWorldService, pvpMineWorldService, spawnPos);
    }

    private static boolean isInsideHill(Player player, KothHill hill) {
        return hill.contains(player.getPosition().x(), player.getPosition().y(), player.getPosition().z());
    }

    private static Object getIgnoreCase(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (String.valueOf(entry.getKey()).equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.replace(' ', '_');
    }

    private static String normalizeNullableId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public synchronized void load() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(file)) {
            seedDefaultHill();
            persist();
            return;
        }

        Object loaded = yaml.readValue(file.toFile(), Object.class);
        loadFromObject(loaded);

        boolean changed = false;
        if (hills.isEmpty()) {
            seedDefaultHill();
            changed = true;
        } else {
            changed |= ensureDefaultHills();
        }
        changed |= renameWorldLabel("spawn", spawnWorldService.worldName());
        changed |= renameWorldLabel("pvpmine", pvpMineWorldService.worldName());
        changed |= renameWorldLabel("PvPMine", pvpMineWorldService.worldName());

        if (nextHillId == null || !hills.containsKey(nextHillId)) {
            nextHillId = hills.keySet().iterator().next();
            changed = true;
        }

        if (activeHillId != null && !hills.containsKey(activeHillId)) {
            activeHillId = null;
            capturerUsername = null;
            captureProgressSeconds = 0;
            changed = true;
        }

        if (nextStartEpochSecond <= 0L) {
            nextStartEpochSecond = Instant.now().getEpochSecond() + (6 * 3600L);
            changed = true;
        }

        if (changed) {
            persist();
        }
    }

    public synchronized List<KothHill> hills() {
        return hills.values().stream()
                .map(KothHill::normalizedCopy)
                .sorted(Comparator.comparing(KothHill::getId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized void saveAll() throws IOException {
        persist();
    }

    public synchronized void renameWorldLabelInMemory(String from, String to) {
        if (renameWorldLabel(from, to)) {
            persistUnchecked();
        }
    }

    public synchronized Optional<KothState> activeHillState() {
        if (activeHillId == null) {
            return Optional.empty();
        }
        KothHill hill = hills.get(activeHillId);
        if (hill == null) {
            return Optional.empty();
        }
        return Optional.of(new KothState(hill.normalizedCopy(), capturerUsername, captureProgressSeconds, hill.getCaptureSeconds(), nextHillId));
    }

    public synchronized Optional<KothHill> nextHill() {
        if (nextHillId == null) {
            return Optional.empty();
        }
        KothHill hill = hills.get(nextHillId);
        return hill == null ? Optional.empty() : Optional.of(hill.normalizedCopy());
    }

    public synchronized long nextStartEpochSecond() {
        return nextStartEpochSecond;
    }

    public synchronized int spawnNext() {
        return spawnNext;
    }

    public synchronized String resolveScheduledHillId() {
        if (spawnNext == 0) {
            List<String> phpMineHills = new ArrayList<>();
            if (hills.containsKey("cave")) {
                phpMineHills.add("cave");
            }
            if (hills.containsKey("forest")) {
                phpMineHills.add("forest");
            }
            if (!phpMineHills.isEmpty()) {
                return phpMineHills.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(phpMineHills.size()));
            }

            List<String> mineHills = hills.values().stream()
                    .filter(h -> isPvpMineWorldName(h.getWorld()))
                    .map(KothHill::getId)
                    .toList();
            if (mineHills.isEmpty()) {
                return null;
            }
            return mineHills.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(mineHills.size()));
        }
        if (hills.containsKey("spawn")) {
            return "spawn";
        }
        return hills.values().stream()
                .filter(h -> isSpawnWorldName(h.getWorld()))
                .findFirst()
                .map(KothHill::getId)
                .orElse(null);
    }

    public synchronized void advanceScheduleAfterWin() {
        if (++spawnNext > 3) {
            spawnNext = 0;
        }
        nextStartEpochSecond = java.time.Instant.now().getEpochSecond() + (6 * 3600L);
        persistUnchecked();
    }

    public synchronized StartResult startEvent(String hillId) {
        if (activeHillId != null) {
            return StartResult.alreadyActive(hills.get(activeHillId));
        }

        KothHill hill = resolveHillToStart(hillId);
        if (hill == null) {
            return StartResult.notFound();
        }

        activeHillId = hill.getId();
        capturerUsername = null;
        captureProgressSeconds = 0;
        persistUnchecked();
        return StartResult.success(hill.normalizedCopy());
    }

    public synchronized StopResult stopEvent() {
        if (activeHillId == null) {
            return StopResult.notActive();
        }

        KothHill stopped = hills.get(activeHillId);
        activeHillId = null;
        capturerUsername = null;
        captureProgressSeconds = 0;
        persistUnchecked();
        return StopResult.success(stopped == null ? null : stopped.normalizedCopy());
    }

    public synchronized boolean setNextHill(String hillId) {
        if (hillId == null || hillId.isBlank()) {
            return false;
        }
        String normalized = normalizeId(hillId);
        if (!hills.containsKey(normalized)) {
            return false;
        }
        nextHillId = normalized;
        persistUnchecked();
        return true;
    }

    public synchronized TickResult tickCapture(Collection<Player> players) {
        if (activeHillId == null) {
            return TickResult.inactive();
        }

        KothHill hill = hills.get(activeHillId);
        if (hill == null) {
            activeHillId = null;
            capturerUsername = null;
            captureProgressSeconds = 0;
            persistUnchecked();
            return TickResult.inactive();
        }

        List<Player> contenders = new ArrayList<>();
        for (Player player : players) {
            if (player.getInstance() == null) {
                continue;
            }
            if (!matchesWorld(player, hill)) {
                continue;
            }
            if (!isInsideHill(player, hill)) {
                continue;
            }
            UserProfile profile = profileService.getOrCreate(player);
            if (profile.getGangId() == null || profile.getGangId().isBlank()) {
                player.sendActionBar(Component.text("You must be in a gang to be the king of the hill"));
                continue;
            }
            contenders.add(player);
        }

        java.util.Collections.shuffle(contenders);

        if (contenders.isEmpty()) {
            if (capturerUsername != null) {
                capturerUsername = null;
                captureProgressSeconds = 0;
                persistUnchecked();
            }
            return TickResult.idle(hill.normalizedCopy(), captureProgressSeconds);
        }

        Player capturer = findCurrentCapturer(contenders);
        if (capturer == null) {
            capturer = contenders.get(0);
        }
        String username = capturer.getUsername();
        boolean changed = false;
        boolean changedCapturer = false;
        if (!username.equalsIgnoreCase(capturerUsername)) {
            capturerUsername = username;
            captureProgressSeconds = 0;
            changed = true;
            changedCapturer = true;
        }

        if (!changedCapturer) {
            captureProgressSeconds = Math.min(hill.getCaptureSeconds(), captureProgressSeconds + 1);
            changed = true;
        }

        if (captureProgressSeconds >= hill.getCaptureSeconds()) {
            RewardResult reward = claimWinnerReward(capturerUsername, hill);
            activeHillId = null;
            capturerUsername = null;
            captureProgressSeconds = 0;
            rotateNextHill(hill.getId());
            persistUnchecked();
            return TickResult.winner(hill.normalizedCopy(), reward);
        }

        if (changed) {
            persistUnchecked();
        }
        return TickResult.capturing(hill.normalizedCopy(), capturerUsername, captureProgressSeconds);
    }

    private Player findCurrentCapturer(List<Player> contenders) {
        if (capturerUsername == null || capturerUsername.isBlank()) {
            return null;
        }
        for (Player contender : contenders) {
            if (contender.getUsername().equalsIgnoreCase(capturerUsername)) {
                return contender;
            }
        }
        return null;
    }

    public synchronized RewardResult claimWinnerReward(String username, KothHill hill) {
        if (username == null || username.isBlank() || hill == null) {
            return new RewardResult(null, 0d, 0L);
        }

        UserProfile profile = profileService.getOrCreateByUsername(username);
        int keys = isSpawnWorldName(hill.getWorld()) ? 1 : 3 + (int) (Math.random() * 2);
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(profile.getUsername());
        if (online != null) {
            customItemService.createById(20, keys, 120).ifPresent(item -> itemDeliveryService.deliver(online, profile, item));
        }
        new AchievementService(profileService).onKothWin(online, profile);
        profileService.save(profile);
        return new RewardResult(profile.getUsername(), 0D, keys);
    }

    private void loadFromObject(Object loaded) {
        hills.clear();
        nextHillId = null;
        activeHillId = null;
        capturerUsername = null;
        captureProgressSeconds = 0;

        if (!(loaded instanceof Map<?, ?> root)) {
            return;
        }

        readHills(root);
        readState(root);
    }

    private void readHills(Map<?, ?> root) {
        Object hillsNode = getIgnoreCase(root, "hills");
        if (hillsNode instanceof Collection<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                KothHill hill = yaml.convertValue(map, KothHill.class).normalizedCopy();
                if (hill.getId().isBlank()) {
                    continue;
                }
                hills.put(hill.getId(), hill);
            }
            return;
        }

        if (hillsNode instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> rawHill)) {
                    continue;
                }
                KothHill hill = yaml.convertValue(rawHill, KothHill.class);
                if (hill.getId().isBlank()) {
                    hill.setId(String.valueOf(entry.getKey()));
                }
                hill = hill.normalizedCopy();
                if (!hill.getId().isBlank()) {
                    hills.put(hill.getId(), hill);
                }
            }
        }
    }

    private void readState(Map<?, ?> root) {
        Object stateNode = getIgnoreCase(root, "state");
        if (stateNode instanceof Map<?, ?> state) {
            nextHillId = normalizeNullableId(stringValue(getIgnoreCase(state, "nextHillId")));
            nextStartEpochSecond = Math.max(0L, longValue(getIgnoreCase(state, "nextStartEpochSecond"), 0L));
            spawnNext = Math.max(0, intValue(getIgnoreCase(state, "spawnNext"), 0));
            Object activeNode = getIgnoreCase(state, "active");
            if (activeNode instanceof Map<?, ?> active) {
                activeHillId = normalizeNullableId(stringValue(getIgnoreCase(active, "hillId")));
                capturerUsername = nonBlank(stringValue(getIgnoreCase(active, "capturer")));
                captureProgressSeconds = Math.max(0, intValue(getIgnoreCase(active, "progressSeconds"), 0));
            }
        }

        Object legacySpawnNext = getIgnoreCase(root, "spawn-next");
        if (legacySpawnNext != null) {
            spawnNext = Math.max(0, intValue(legacySpawnNext, spawnNext));
        }
        Object legacyNextKoth = getIgnoreCase(root, "next-koth");
        if (legacyNextKoth instanceof Number number && number.longValue() > 10_000L) {
            nextStartEpochSecond = number.longValue();
        }

        if (nextHillId == null) {
            Object legacy = getIgnoreCase(root, "next-koth");
            if (legacy == null) {
                legacy = getIgnoreCase(root, "next_koth");
            }
            if (legacy != null) {
                nextHillId = resolveLegacyNextValue(legacy);
            }
        }
    }

    private String resolveLegacyNextValue(Object raw) {
        if (hills.isEmpty()) {
            return null;
        }

        if (raw instanceof Number number) {
            int index = Math.max(0, number.intValue());
            List<String> ids = new ArrayList<>(hills.keySet());
            if (index >= ids.size()) {
                return ids.get(0);
            }
            return ids.get(index);
        }

        String candidate = normalizeNullableId(stringValue(raw));
        if (candidate != null && hills.containsKey(candidate)) {
            return candidate;
        }
        return null;
    }

    private void seedDefaultHill() {
        hills.clear();
        hills.put("cave", new KothHill("cave", "Cave", pvpMineWorldService.worldName(), -64, 28, -30, 6, 1800, 0d, 0L, -67, 24, -32, -62, 32, -27, "red"));
        hills.put("forest", new KothHill("forest", "Forest", pvpMineWorldService.worldName(), -70, 65, 80, 6, 1800, 0d, 0L, -73, 62, 77, -66, 69, 84, "blue"));
        hills.put("spawn", new KothHill("spawn", "Spawn", spawnWorldService.worldName(), 1578, 14, 699, 6, 1200, 0d, 0L, 1575, 11, 696, 1580, 17, 701, null));
        nextHillId = "cave";
        activeHillId = null;
        capturerUsername = null;
        captureProgressSeconds = 0;
        nextStartEpochSecond = Instant.now().getEpochSecond() + (6 * 3600L);
        spawnNext = 0;
    }

    private boolean ensureDefaultHills() {
        boolean changed = false;
        changed |= ensureHillDefaults(new KothHill("cave", "Cave", pvpMineWorldService.worldName(), -64, 28, -30, 6, 1800, 0d, 0L, -67, 24, -32, -62, 32, -27, "red"));
        changed |= ensureHillDefaults(new KothHill("forest", "Forest", pvpMineWorldService.worldName(), -70, 65, 80, 6, 1800, 0d, 0L, -73, 62, 77, -66, 69, 84, "blue"));
        changed |= ensureHillDefaults(new KothHill("spawn", "Spawn", spawnWorldService.worldName(), 1578, 14, 699, 6, 1200, 0d, 0L, 1575, 11, 696, 1580, 17, 701, null));
        return changed;
    }

    public String spawnWorldName() {
        return spawnWorldService.worldName();
    }

    public String pvpMineWorldName() {
        return pvpMineWorldService.worldName();
    }

    private boolean ensureHillDefaults(KothHill template) {
        KothHill existing = hills.get(template.getId());
        if (existing == null) {
            hills.put(template.getId(), template);
            return true;
        }

        boolean changed = false;
        if (!existing.usesBounds() && template.usesBounds()) {
            existing.setX1(template.getX1());
            existing.setY1(template.getY1());
            existing.setZ1(template.getZ1());
            existing.setX2(template.getX2());
            existing.setY2(template.getY2());
            existing.setZ2(template.getZ2());
            changed = true;
        }
        if ((existing.getPathHint() == null || existing.getPathHint().isBlank()) && template.getPathHint() != null) {
            existing.setPathHint(template.getPathHint());
            changed = true;
        }
        return changed;
    }

    private boolean renameWorldLabel(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank() || from.equalsIgnoreCase(to)) {
            return false;
        }
        boolean changed = false;
        for (KothHill hill : hills.values()) {
            if (hill.getWorld() != null && hill.getWorld().equalsIgnoreCase(from)) {
                hill.setWorld(to);
                changed = true;
            }
        }
        return changed;
    }

    private void rotateNextHill(String capturedId) {
        if (hills.isEmpty()) {
            nextHillId = null;
            return;
        }

        List<String> ids = new ArrayList<>(hills.keySet());
        if (capturedId == null) {
            nextHillId = ids.get(0);
            return;
        }

        int current = ids.indexOf(capturedId);
        if (current < 0) {
            nextHillId = ids.get(0);
            return;
        }
        nextHillId = ids.get((current + 1) % ids.size());
    }

    private KothHill resolveHillToStart(String hillId) {
        if (hills.isEmpty()) {
            return null;
        }

        if (hillId == null || hillId.isBlank()) {
            if (nextHillId != null && hills.containsKey(nextHillId)) {
                return hills.get(nextHillId);
            }
            return hills.values().iterator().next();
        }

        return hills.get(normalizeId(hillId));
    }

    private boolean matchesWorld(Player player, KothHill hill) {
        if (hill.getWorld() == null || hill.getWorld().isBlank()) {
            return true;
        }
        if (isSpawnWorldName(hill.getWorld())) {
            return player.getInstance() == spawnWorldService.instance();
        }
        if (isPvpMineWorldName(hill.getWorld())) {
            return player.getInstance() == pvpMineWorldService.instance();
        }
        return false;
    }

    private boolean isSpawnWorldName(String worldName) {
        return worldName != null && (worldName.equalsIgnoreCase(spawnWorldService.worldName()) || worldName.equalsIgnoreCase("spawn"));
    }

    private boolean isPvpMineWorldName(String worldName) {
        return worldName != null && (worldName.equalsIgnoreCase(pvpMineWorldService.worldName()) || worldName.equalsIgnoreCase("pvpmine") || worldName.equalsIgnoreCase("PvPMine"));
    }

    private void persistUnchecked() {
        try {
            persist();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("hills", hills.values().stream().map(KothHill::normalizedCopy).toList());

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("nextHillId", nextHillId);
        state.put("nextStartEpochSecond", nextStartEpochSecond);
        state.put("spawnNext", spawnNext);
        if (activeHillId != null) {
            Map<String, Object> active = new LinkedHashMap<>();
            active.put("hillId", activeHillId);
            active.put("capturer", capturerUsername);
            active.put("progressSeconds", captureProgressSeconds);
            state.put("active", active);
        }
        root.put("state", state);
        root.put("next-koth", nextStartEpochSecond);
        root.put("spawn-next", spawnNext);

        yaml.writeValue(file.toFile(), root);
    }

    public enum StartStatus {
        SUCCESS,
        ALREADY_ACTIVE,
        HILL_NOT_FOUND
    }

    public enum StopStatus {
        SUCCESS,
        NOT_ACTIVE
    }

    public enum TickState {
        INACTIVE,
        IDLE,
        CONTESTED,
        CAPTURING,
        WINNER
    }

    public record KothState(
            KothHill hill,
            String capturer,
            int progressSeconds,
            int captureSeconds,
            String nextHillId
    ) {
    }

    public record StartResult(StartStatus status, KothHill hill) {
        static StartResult success(KothHill hill) {
            return new StartResult(StartStatus.SUCCESS, hill);
        }

        static StartResult alreadyActive(KothHill hill) {
            return new StartResult(StartStatus.ALREADY_ACTIVE, hill == null ? null : hill.normalizedCopy());
        }

        static StartResult notFound() {
            return new StartResult(StartStatus.HILL_NOT_FOUND, null);
        }
    }

    public record StopResult(StopStatus status, KothHill stoppedHill) {
        static StopResult success(KothHill hill) {
            return new StopResult(StopStatus.SUCCESS, hill);
        }

        static StopResult notActive() {
            return new StopResult(StopStatus.NOT_ACTIVE, null);
        }
    }

    public record RewardResult(String winnerUsername, double rewardMoney, long rewardPrestige) {
    }

    public record TickResult(
            TickState state,
            KothHill hill,
            String capturer,
            int progressSeconds,
            int contenderCount,
            RewardResult reward
    ) {
        static TickResult inactive() {
            return new TickResult(TickState.INACTIVE, null, null, 0, 0, null);
        }

        static TickResult idle(KothHill hill, int progressSeconds) {
            return new TickResult(TickState.IDLE, hill, null, progressSeconds, 0, null);
        }

        static TickResult contested(KothHill hill, int contenderCount, int progressSeconds) {
            return new TickResult(TickState.CONTESTED, hill, null, progressSeconds, contenderCount, null);
        }

        static TickResult capturing(KothHill hill, String capturer, int progressSeconds) {
            return new TickResult(TickState.CAPTURING, hill, capturer, progressSeconds, 1, null);
        }

        static TickResult winner(KothHill hill, RewardResult reward) {
            return new TickResult(TickState.WINNER, hill, reward.winnerUsername(), hill.getCaptureSeconds(), 1, reward);
        }
    }
}
