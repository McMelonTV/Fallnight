package xyz.fallnight.server.domain.rank;

import java.util.ArrayList;
import java.util.List;

public final class RankDefinition {
    private String id;
    private String name;
    private String prefix;
    private boolean defaultRank;
    private boolean staff;
    private boolean donator;
    private int priority;
    private int vaults;
    private int plots;
    private List<String> permissions = new ArrayList<>();
    private List<String> inherit = new ArrayList<>();

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String prefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean defaultRank() {
        return defaultRank;
    }

    public void setDefaultRank(boolean defaultRank) {
        this.defaultRank = defaultRank;
    }

    public boolean staff() {
        return staff;
    }

    public void setStaff(boolean staff) {
        this.staff = staff;
    }

    public boolean donator() {
        return donator;
    }

    public void setDonator(boolean donator) {
        this.donator = donator;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int vaults() {
        return vaults;
    }

    public void setVaults(int vaults) {
        this.vaults = Math.max(0, vaults);
    }

    public int plots() {
        return plots;
    }

    public void setPlots(int plots) {
        this.plots = Math.max(0, plots);
    }

    public List<String> permissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public List<String> inherit() {
        return inherit;
    }

    public void setInherit(List<String> inherit) {
        this.inherit = inherit == null ? new ArrayList<>() : new ArrayList<>(inherit);
    }
}
