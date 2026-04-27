package xyz.fallnight.server.domain.gang;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Gang {
    private String name;
    private String id;
    private Instant creationDate;
    private final Set<String> members;
    private final Set<String> allies;
    private final Map<String, String> memberRoles;
    private String description;
    @JsonIgnore
    private final transient Set<String> invites;
    @JsonIgnore
    private final transient Set<String> allyRequests;

    public Gang() {
        this.id = UUID.randomUUID().toString();
        this.creationDate = Instant.now();
        this.members = new LinkedHashSet<>();
        this.allies = new LinkedHashSet<>();
        this.memberRoles = new LinkedHashMap<>();
        this.description = "A new gang!";
        this.invites = new LinkedHashSet<>();
        this.allyRequests = new LinkedHashSet<>();
    }

    public Gang(String name, String creatorUsername, String description) {
        this();
        setName(name);
        setDescription(description);
        addMember(creatorUsername, GangMemberRole.LEADER);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        this.id = id.trim();
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate == null ? Instant.now() : creationDate;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void setMembers(Set<String> members) {
        this.members.clear();
        if (members == null) {
            return;
        }
        for (String member : members) {
            addMember(member);
        }
    }

    public Set<String> getAllies() {
        return allies;
    }

    public void setAllies(Set<String> allies) {
        this.allies.clear();
        if (allies == null) {
            return;
        }
        for (String ally : allies) {
            if (ally != null && !ally.isBlank()) {
                this.allies.add(ally.trim().toLowerCase());
            }
        }
    }

    public Map<String, String> getMemberRoles() {
        return memberRoles;
    }

    public void setMemberRoles(Map<String, String> memberRoles) {
        this.memberRoles.clear();
        if (memberRoles == null) {
            return;
        }
        for (Map.Entry<String, String> entry : memberRoles.entrySet()) {
            String member = findMemberExact(entry.getKey());
            if (member == null) {
                continue;
            }
            GangMemberRole role = parseRole(entry.getValue());
            this.memberRoles.put(normalizeMember(member), role.name());
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null || description.isBlank() ? "A new gang!" : description.trim();
    }

    public boolean addMember(String username) {
        return addMember(username, GangMemberRole.RECRUIT);
    }

    public boolean addMember(String username, GangMemberRole role) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String clean = username.trim();
        if (findMemberExact(clean) != null) {
            return false;
        }
        members.add(clean);
        memberRoles.put(normalizeMember(clean), (role == null ? GangMemberRole.RECRUIT : role).name());
        return true;
    }

    public boolean removeMember(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String existing = findMemberExact(username);
        if (existing == null) {
            return false;
        }
        memberRoles.remove(normalizeMember(existing));
        return members.remove(existing);
    }

    public boolean hasMember(String username) {
        return findMemberExact(username) != null;
    }

    public String leader() {
        for (String member : members) {
            if (roleOf(member) == GangMemberRole.LEADER) {
                return member;
            }
        }
        return members.stream().findFirst().orElse(null);
    }

    public int memberCount() {
        return members.size();
    }

    public boolean isLeader(String username) {
        return roleOf(username) == GangMemberRole.LEADER;
    }

    public GangMemberRole roleOf(String username) {
        String existing = findMemberExact(username);
        if (existing == null) {
            return null;
        }
        return parseRole(memberRoles.get(normalizeMember(existing)));
    }

    public boolean setRole(String username, GangMemberRole role) {
        String existing = findMemberExact(username);
        if (existing == null || role == null) {
            return false;
        }
        memberRoles.put(normalizeMember(existing), role.name());
        return true;
    }

    public boolean hasInvite(String username) {
        return username != null && invites.contains(normalizeMember(username));
    }

    public void invitePlayer(String username) {
        if (username != null && !username.isBlank()) {
            invites.add(normalizeMember(username));
        }
    }

    public void revokeInvite(String username) {
        if (username != null && !username.isBlank()) {
            invites.remove(normalizeMember(username));
        }
    }

    public boolean askedToAllyWith(Gang gang) {
        return gang != null && allyRequests.contains(normalizeGang(gang.getName()));
    }

    public void askToAllyWith(Gang gang) {
        if (gang != null && gang.getName() != null) {
            allyRequests.add(normalizeGang(gang.getName()));
        }
    }

    public boolean isAlliedWith(Gang gang) {
        return gang != null && allies.contains(normalizeGang(gang.getName()));
    }

    public void allyWith(Gang gang) {
        if (gang == null || gang == this || gang.getName() == null) {
            return;
        }
        allyRequests.remove(normalizeGang(gang.getName()));
        if (allies.add(normalizeGang(gang.getName()))) {
            gang.allyWith(this);
        }
    }

    public void removeAlly(Gang gang) {
        if (gang == null || gang.getName() == null) {
            return;
        }
        if (allies.remove(normalizeGang(gang.getName()))) {
            gang.removeAlly(this);
        }
    }

    public static String normalizeName(String gangName) {
        return gangName == null ? "" : gangName.trim().toLowerCase();
    }

    private String findMemberExact(String username) {
        if (username == null) {
            return null;
        }
        return members.stream()
            .filter(member -> member.equalsIgnoreCase(username.trim()))
            .findFirst()
            .orElse(null);
    }

    private static String normalizeMember(String username) {
        return username.trim().toLowerCase();
    }

    private static String normalizeGang(String gangName) {
        return gangName.trim().toLowerCase();
    }

    private static GangMemberRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return GangMemberRole.RECRUIT;
        }
        try {
            return GangMemberRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return GangMemberRole.RECRUIT;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Gang gang)) {
            return false;
        }
        return Objects.equals(id, gang.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
