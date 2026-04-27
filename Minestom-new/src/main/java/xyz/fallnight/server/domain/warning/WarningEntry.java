package xyz.fallnight.server.domain.warning;

import java.time.Instant;
import java.util.Objects;

public final class WarningEntry {
    public static final long WARN_EXPIRE_SECONDS = 604800L;
    private long id;
    private String targetUsername;
    private String actor;
    private String reason;
    private Instant createdAt;

    public WarningEntry() {
        this.targetUsername = "unknown";
        this.actor = "unknown";
        this.reason = "No reason provided.";
        this.createdAt = Instant.now();
    }

    public WarningEntry(long id, String targetUsername, String actor, String reason, Instant createdAt) {
        this.id = Math.max(1L, id);
        this.targetUsername = normalizeName(targetUsername, "unknown");
        this.actor = normalizeName(actor, "unknown");
        this.reason = normalizeReason(reason);
        this.createdAt = Objects.requireNonNullElse(createdAt, Instant.now());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = Math.max(1L, id);
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = normalizeName(targetUsername, "unknown");
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = normalizeName(actor, "unknown");
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = normalizeReason(reason);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = Objects.requireNonNullElse(createdAt, Instant.now());
    }

    public boolean isExpired(Instant now) {
        Instant compare = now == null ? Instant.now() : now;
        return createdAt.plusSeconds(WARN_EXPIRE_SECONDS).isBefore(compare);
    }

    private static String normalizeName(String value, String fallback) {
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
}
