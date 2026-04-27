package xyz.fallnight.server.domain.rank;

public final class RankInstance {
    private final String rankId;
    private final long expire;
    private final boolean persistent;

    public RankInstance(String rankId, long expire, boolean persistent) {
        this.rankId = rankId;
        this.expire = expire;
        this.persistent = persistent;
    }

    public String rankId() {
        return rankId;
    }

    public long expire() {
        return expire;
    }

    public boolean persistent() {
        return persistent;
    }

    public boolean permanent() {
        return expire < 0L;
    }
}
