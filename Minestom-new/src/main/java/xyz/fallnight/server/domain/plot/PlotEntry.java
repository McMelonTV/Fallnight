package xyz.fallnight.server.domain.plot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class PlotEntry {
    private String owner;
    private List<String> members;
    private List<String> blockedUsers;
    private String name;

    public PlotEntry() {
        this.members = new ArrayList<>();
        this.blockedUsers = new ArrayList<>();
        this.name = "";
    }

    public PlotEntry(String owner) {
        this();
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = sanitize(members);
    }

    public List<String> getBlockedUsers() {
        return blockedUsers;
    }

    public void setBlockedUsers(List<String> blockedUsers) {
        this.blockedUsers = sanitize(blockedUsers);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public PlotEntry copy() {
        PlotEntry copy = new PlotEntry(owner);
        copy.setMembers(members);
        copy.setBlockedUsers(blockedUsers);
        copy.setName(name);
        return copy;
    }

    private static List<String> sanitize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(normalized);
    }
}
