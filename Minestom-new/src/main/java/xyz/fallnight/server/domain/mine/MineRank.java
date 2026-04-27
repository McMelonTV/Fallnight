package xyz.fallnight.server.domain.mine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class MineRank {
    private int id;
    private String name;
    private String tag;
    private double price;
    private List<String> perms;

    public MineRank() {
        this.perms = new ArrayList<>();
    }

    public MineRank(int id, String name, String tag, double price) {
        this();
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.tag = tag == null ? name : tag;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public List<String> getPerms() {
        return Collections.unmodifiableList(perms);
    }

    @JsonAlias("permissions")
    public void setPerms(List<String> perms) {
        this.perms = perms == null ? new ArrayList<>() : new ArrayList<>(perms);
    }

    public double priceForPrestige(int prestige) {
        int safePrestige = Math.max(1, prestige);
        return price + (price * 0.6d * (safePrestige - 1));
    }
}
