package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.domain.moderation.PlayerMute;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.moderation.ModerationSanctionsRepository;
import xyz.fallnight.server.persistence.moderation.ModerationSanctionsState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModerationSanctionsService {
    private final ModerationSanctionsRepository repository;
    private final PlayerProfileService profileService;
    private final Clock clock;
    private final Map<String, PlayerBan> bans;
    private final Map<String, PlayerMute> mutes;
    private boolean globalMute;

    public ModerationSanctionsService(ModerationSanctionsRepository repository, PlayerProfileService profileService) {
        this(repository, profileService, Clock.systemUTC());
    }

    public ModerationSanctionsService(ModerationSanctionsRepository repository, PlayerProfileService profileService, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bans = new LinkedHashMap<>();
        this.mutes = new LinkedHashMap<>();
    }

    public synchronized void load() throws IOException {
        ModerationSanctionsState state = repository.load();
        bans.clear();
        mutes.clear();

        Instant now = now();
        for (PlayerBan ban : state.bans()) {
            if (ban == null || !ban.isActive(now)) {
                continue;
            }
            bans.put(normalize(ban.username()), ban);
        }
        for (PlayerMute mute : state.mutes()) {
            if (mute == null || !mute.isActive(now)) {
                continue;
            }
            mutes.put(normalize(mute.username()), mute);
        }
        globalMute = false;
        importLegacyMutes();
        persistUnchecked();
    }

    public synchronized void saveAll() throws IOException {
        purgeExpired();
        repository.save(snapshot());
    }

    public synchronized PlayerBan ban(String username, String actor, String reason) {
        return putBan(username, actor, reason, null);
    }

    public synchronized PlayerBan ban(String username, String actor, String reason, boolean superBan) {
        return putBan(username, actor, reason, null, superBan);
    }

    public synchronized PlayerBan tempBan(String username, String actor, String reason, Duration duration) {
        Duration applied = sanitizeDuration(duration);
        return putBan(username, actor, reason, now().plus(applied));
    }

    public synchronized PlayerBan tempBan(String username, String actor, String reason, Duration duration, boolean superBan) {
        Duration applied = sanitizeDuration(duration);
        return putBan(username, actor, reason, now().plus(applied), superBan);
    }

    public synchronized boolean unban(String username) {
        PlayerBan removed = bans.remove(normalize(username));
        if (removed == null) {
            return false;
        }
        persistUnchecked();
        return true;
    }

    public synchronized PlayerMute mute(String username, String actor, String reason) {
        return putMute(username, actor, reason, null);
    }

    public synchronized PlayerMute tempMute(String username, String actor, String reason, Duration duration) {
        Duration applied = sanitizeDuration(duration);
        return putMute(username, actor, reason, now().plus(applied));
    }

    public synchronized boolean unmute(String username) {
        PlayerMute removed = mutes.remove(normalize(username));
        if (removed == null) {
            return false;
        }
        persistUnchecked();
        return true;
    }

    public synchronized Optional<PlayerBan> activeBan(String username) {
        String key = normalize(username);
        PlayerBan ban = bans.get(key);
        if (ban == null) {
            return Optional.empty();
        }

        if (!ban.isActive(now())) {
            bans.remove(key);
            persistUnchecked();
            return Optional.empty();
        }
        return Optional.of(ban);
    }

    public synchronized Optional<PlayerMute> activeMute(String username) {
        String key = normalize(username);
        PlayerMute mute = mutes.get(key);
        if (mute == null) {
            return Optional.empty();
        }

        if (!mute.isActive(now())) {
            mutes.remove(key);
            persistUnchecked();
            return Optional.empty();
        }
        return Optional.of(mute);
    }

    public synchronized boolean isBanned(String username) {
        return activeBan(username).isPresent();
    }

    public synchronized boolean isMuted(String username) {
        return activeMute(username).isPresent();
    }

    public synchronized List<PlayerBan> listActiveBans() {
        purgeExpired();
        List<PlayerBan> active = new ArrayList<>(bans.values());
        active.sort(Comparator.comparing(PlayerBan::createdAt).reversed());
        return List.copyOf(active);
    }

    public synchronized boolean isGlobalMuteEnabled() {
        return globalMute;
    }

    public synchronized boolean setGlobalMute(boolean enabled) {
        if (globalMute == enabled) {
            return false;
        }
        globalMute = enabled;
        return true;
    }

    public synchronized boolean toggleGlobalMute() {
        globalMute = !globalMute;
        return globalMute;
    }

    private PlayerBan putBan(String username, String actor, String reason, Instant expiresAt) {
        return putBan(username, actor, reason, expiresAt, false);
    }

    private PlayerBan putBan(String username, String actor, String reason, Instant expiresAt, boolean superBan) {
        String resolvedUsername = resolveUsername(username);
        PlayerBan ban = new PlayerBan(resolvedUsername, reason, actor, now(), expiresAt, superBan);
        bans.put(normalize(resolvedUsername), ban);
        persistUnchecked();
        return ban;
    }

    private PlayerMute putMute(String username, String actor, String reason, Instant expiresAt) {
        String resolvedUsername = resolveUsername(username);
        PlayerMute mute = new PlayerMute(resolvedUsername, reason, actor, now(), expiresAt);
        mutes.put(normalize(resolvedUsername), mute);
        persistUnchecked();
        return mute;
    }

    private void purgeExpired() {
        Instant now = now();
        boolean changed = bans.values().removeIf(ban -> !ban.isActive(now));
        changed |= mutes.values().removeIf(mute -> !mute.isActive(now));
        if (changed) {
            persistUnchecked();
        }
    }

    private void importLegacyMutes() {
        Instant now = now();
        for (UserProfile profile : profileService.allProfiles()) {
            Object muted = profile.getExtraData().get("muted");
            boolean isMuted = muted instanceof Boolean bool && bool;
            if (!isMuted) {
                continue;
            }
            Instant expiresAt = null;
            Object rawExpire = profile.getExtraData().get("muteExpire");
            if (rawExpire instanceof Number number && number.longValue() > 0L) {
                expiresAt = Instant.ofEpochSecond(number.longValue());
                if (expiresAt.isBefore(now)) {
                    continue;
                }
            }
            String username = profile.getUsername();
            mutes.put(normalize(username), new PlayerMute(username, "Legacy mute", "Legacy", now, expiresAt));
        }
    }

    private ModerationSanctionsState snapshot() {
        return new ModerationSanctionsState(List.copyOf(bans.values()), List.copyOf(mutes.values()), false);
    }

    private String resolveUsername(String username) {
        String input = requireUsername(username);
        Optional<UserProfile> known = profileService.findByUsername(input);
        return known.map(UserProfile::getUsername).orElse(input);
    }

    private static Duration sanitizeDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return duration;
    }

    private static String requireUsername(String username) {
        Objects.requireNonNull(username, "username");
        String normalized = username.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        return normalized;
    }

    private void persistUnchecked() {
        try {
            repository.save(snapshot());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
