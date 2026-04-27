package xyz.fallnight.server.domain.bounty;

public final class BountyEntry {
    private String player;
    private long bounty;

    public BountyEntry() {
    }

    public BountyEntry(String player, long bounty) {
        this.player = player;
        this.bounty = bounty;
    }

    public String player() {
        return player;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public long bounty() {
        return bounty;
    }

    public long getBounty() {
        return bounty;
    }

    public void setBounty(long bounty) {
        this.bounty = Math.max(0L, bounty);
    }
}
