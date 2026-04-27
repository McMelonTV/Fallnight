package xyz.fallnight.server.domain.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class UserProfile {
    private String username;
    private long minedBlocks;
    private double balance;
    private int mineRank;
    private int prestige;
    private long prestigePoints;
    private final Map<String, Object> extraData;

    public UserProfile() {
        this.prestige = 1;
        this.extraData = new LinkedHashMap<>();
    }

    public UserProfile(String username) {
        this();
        this.username = Objects.requireNonNull(username, "username");
    }

    public String getUsername() {
        return username;
    }

    @JsonAlias({"name", "player"})
    public void setUsername(String username) {
        this.username = username;
    }

    public long getMinedBlocks() {
        return minedBlocks;
    }

    public void setMinedBlocks(long minedBlocks) {
        this.minedBlocks = Math.max(0, minedBlocks);
    }

    public void addMinedBlocks(long delta) {
        if (delta <= 0) {
            return;
        }
        this.minedBlocks += delta;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void deposit(double amount) {
        if (amount <= 0d) {
            return;
        }
        this.balance += amount;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0d || balance < amount) {
            return false;
        }
        this.balance -= amount;
        return true;
    }

    public int getMineRank() {
        return mineRank;
    }

    @JsonAlias({"mine_rank", "rank"})
    public void setMineRank(int mineRank) {
        this.mineRank = Math.max(0, mineRank);
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = Math.max(1, prestige);
    }

    public long getPrestigePoints() {
        return prestigePoints;
    }

    @JsonIgnore
    public double getPrestigeBoost() {
        return 1D + Math.max(0, prestige - 1) * 0.1D;
    }

    @JsonAlias({"pp", "prestige_points"})
    public void setPrestigePoints(long prestigePoints) {
        this.prestigePoints = Math.max(0, prestigePoints);
    }

    public void addPrestigePoints(long points) {
        if (points <= 0) {
            return;
        }
        this.prestigePoints += points;
    }

    @JsonIgnore
    public long getKills() {
        Object value = extraData.get("kills");
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return 0L;
    }

    @JsonIgnore
    public long getDeaths() {
        Object value = extraData.get("deaths");
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return 0L;
    }

    public void addKill() {
        extraData.put("kills", getKills() + 1L);
    }

    public void addDeath() {
        extraData.put("deaths", getDeaths() + 1L);
    }

    @JsonIgnore
    public String getRankTag() {
        Object rankComponent = extraData.get("rankComponent");
        if (!(rankComponent instanceof Map<?, ?> component)) {
            return "Member";
        }
        Object ranksRaw = component.get("ranks");
        if (!(ranksRaw instanceof Map<?, ?> ranks) || ranks.isEmpty()) {
            return "Member";
        }
        Object firstKey = ranks.keySet().iterator().next();
        return firstKey == null ? "Member" : String.valueOf(firstKey);
    }

    @JsonIgnore
    public String getGangId() {
        Object value = extraData.get("gangId");
        if (value == null) {
            return null;
        }
        String gangId = String.valueOf(value).trim();
        return gangId.isEmpty() ? null : gangId;
    }

    public void setGangId(String gangId) {
        if (gangId == null || gangId.isBlank()) {
            extraData.remove("gangId");
            return;
        }
        extraData.put("gangId", gangId.trim());
    }

    @JsonAnySetter
    public void setExtraValue(String key, Object value) {
        if (key == null) {
            return;
        }
        if (isKnownField(key)) {
            return;
        }
        extraData.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraData() {
        return extraData;
    }

    private static boolean isKnownField(String key) {
        return switch (key) {
            case "username", "name", "player", "minedBlocks", "balance", "mineRank", "mine_rank", "rank", "prestige", "prestigePoints", "pp", "prestige_points", "gangId" -> true;
            default -> false;
        };
    }
}
