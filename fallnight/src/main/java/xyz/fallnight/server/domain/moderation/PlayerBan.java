package xyz.fallnight.server.domain.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class PlayerBan {
    private final String username;
    private final String reason;
    private final String actor;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final boolean superBan;

    public PlayerBan(String username, String reason, String actor, Instant createdAt, Instant expiresAt) {
        this(username, reason, actor, createdAt, expiresAt, false);
    }

    public PlayerBan(String username, String reason, String actor, Instant createdAt, Instant expiresAt, boolean superBan) {
        this.username = requireText(username, "username");
        this.reason = sanitizeReason(reason);
        this.actor = sanitizeActor(actor);
        this.createdAt = Objects.requireNonNullElse(createdAt, Instant.now());
        this.expiresAt = expiresAt;
        this.superBan = superBan;
    }

    public String username() {
        return username;
    }

    public String reason() {
        return reason;
    }

    public String actor() {
        return actor;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean superBan() {
        return superBan;
    }

    public boolean isTemporary() {
        return expiresAt != null;
    }

    public boolean isActive(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Duration remaining(Instant now) {
        if (expiresAt == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }

    private static String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "No reason provided.";
        }
        return value.trim();
    }

    private static String sanitizeActor(String value) {
        if (value == null || value.isBlank()) {
            return "Console";
        }
        return value.trim();
    }
}
