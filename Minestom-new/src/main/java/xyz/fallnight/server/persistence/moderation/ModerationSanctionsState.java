package xyz.fallnight.server.persistence.moderation;

import xyz.fallnight.server.domain.moderation.PlayerBan;
import xyz.fallnight.server.domain.moderation.PlayerMute;
import java.util.List;

public record ModerationSanctionsState(List<PlayerBan> bans, List<PlayerMute> mutes, boolean globalMute) {
    public ModerationSanctionsState {
        bans = bans == null ? List.of() : List.copyOf(bans);
        mutes = mutes == null ? List.of() : List.copyOf(mutes);
    }
}
