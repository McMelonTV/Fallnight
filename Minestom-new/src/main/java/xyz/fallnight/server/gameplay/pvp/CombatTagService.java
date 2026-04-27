package xyz.fallnight.server.gameplay.pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CombatTagService {
    private final long timeoutMillis;
    private final ConcurrentMap<UUID, CombatTagState> byPlayer;
    private final ConcurrentMap<UUID, LastHitState> lastHits;

    CombatTagService(long timeoutMillis) {
        this.timeoutMillis = Math.max(1_000L, timeoutMillis);
        this.byPlayer = new ConcurrentHashMap<>();
        this.lastHits = new ConcurrentHashMap<>();
    }

    boolean isTagged(UUID playerId) {
        return remainingMillis(playerId) > 0L;
    }

    long remainingMillis(UUID playerId) {
        CombatTagState state = byPlayer.get(playerId);
        if (state == null) {
            return 0L;
        }
        long remaining = state.expiresAtMillis() - System.currentTimeMillis();
        if (remaining <= 0L) {
            byPlayer.remove(playerId, state);
            return 0L;
        }
        return remaining;
    }

    void tagPlayers(UUID attackerId, UUID victimId) {
        tagPlayers(attackerId, victimId, null);
    }

    void tagPlayers(UUID attackerId, UUID victimId, String attackerName) {
        if (attackerId == null || victimId == null || attackerId.equals(victimId)) {
            return;
        }

        long expiresAt = System.currentTimeMillis() + timeoutMillis;
        byPlayer.put(attackerId, new CombatTagState(expiresAt, victimId));
        byPlayer.put(victimId, new CombatTagState(expiresAt, attackerId));
        lastHits.put(victimId, new LastHitState(expiresAt, attackerId, attackerName));
    }

    UUID lastHitter(UUID playerId) {
        LastHitState state = lastHits.get(playerId);
        if (state == null) {
            return null;
        }
        if (state.expiresAtMillis() <= System.currentTimeMillis()) {
            lastHits.remove(playerId, state);
            return null;
        }
        return state.lastHitter();
    }

    String lastHitterName(UUID playerId) {
        LastHitState state = lastHits.get(playerId);
        if (state == null) {
            return null;
        }
        if (state.expiresAtMillis() <= System.currentTimeMillis()) {
            lastHits.remove(playerId, state);
            return null;
        }
        return state.lastHitterName();
    }

    void clear(UUID playerId) {
        if (playerId != null) {
            byPlayer.remove(playerId);
            lastHits.remove(playerId);
        }
    }

    void clearAll() {
        byPlayer.clear();
        lastHits.clear();
    }

    List<UUID> expireTags() {
        long now = System.currentTimeMillis();
        List<UUID> expiredPlayers = new ArrayList<>();
        for (var entry : byPlayer.entrySet()) {
            CombatTagState state = entry.getValue();
            if (state == null || state.expiresAtMillis() > now) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (byPlayer.remove(playerId, state)) {
                lastHits.remove(playerId);
                expiredPlayers.add(playerId);
            }
        }
        return expiredPlayers;
    }

    long timeoutMillis() {
        return timeoutMillis;
    }

    private record CombatTagState(long expiresAtMillis, UUID lastHitter) {
    }

    private record LastHitState(long expiresAtMillis, UUID lastHitter, String lastHitterName) {
    }
}
