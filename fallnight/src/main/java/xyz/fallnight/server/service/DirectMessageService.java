package xyz.fallnight.server.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DirectMessageService {
    private final Map<UUID, UUID> lastPartnerByPlayer = new ConcurrentHashMap<>();

    public void recordConversation(UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            return;
        }

        lastPartnerByPlayer.put(targetId, sourceId);
    }

    public UUID lastPartner(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        return lastPartnerByPlayer.get(playerId);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        lastPartnerByPlayer.remove(playerId);
        lastPartnerByPlayer.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
    }
}
