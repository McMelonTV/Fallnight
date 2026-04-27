package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.gang.Gang;
import xyz.fallnight.server.domain.gang.GangMemberRole;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.gang.GangRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

public final class GangService {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9]{3,15}$");
    public static final int MAX_MEMBERS = 15;

    private final GangRepository repository;
    private final UserProfileService userProfileService;
    private final ConcurrentMap<String, Gang> gangsById;
    private final ConcurrentMap<String, String> gangIdByName;

    public GangService(GangRepository repository, UserProfileService userProfileService) {
        this.repository = repository;
        this.userProfileService = userProfileService;
        this.gangsById = new ConcurrentHashMap<>();
        this.gangIdByName = new ConcurrentHashMap<>();
    }

    public void loadAll() throws IOException {
        Map<String, Gang> loaded = repository.loadAll();
        gangsById.clear();
        gangIdByName.clear();
        for (Gang gang : loaded.values()) {
            String id = normalizeId(gang.getId());
            if (id == null) {
                continue;
            }
            if (gang.getName() == null || gang.getName().isBlank()) {
                gang.setName(id);
            }
            if (gang.getCreationDate() == null) {
                gang.setCreationDate(Instant.now());
            }
            ensureMemberRoles(gang);
            gangsById.put(id, gang);
            gangIdByName.put(Gang.normalizeName(gang.getName()), id);
        }
        syncMembershipFromProfiles();
    }

    public List<Gang> allGangs() {
        return gangsById.values().stream()
            .sorted(Comparator.comparing(gang -> gang.getName() == null ? "" : gang.getName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public Optional<Gang> findById(String gangId) {
        String normalizedId = normalizeId(gangId);
        if (normalizedId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(gangsById.get(normalizedId));
    }

    public Optional<Gang> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String gangId = gangIdByName.get(Gang.normalizeName(name));
        if (gangId == null) {
            return Optional.empty();
        }
        return findById(gangId);
    }

    public Optional<Gang> findGangForUser(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<UserProfile> profile = userProfileService.find(username);
        if (profile.isPresent()) {
            String gangId = profile.get().getGangId();
            if (gangId != null) {
                Gang gang = gangsById.get(gangId);
                if (gang != null) {
                    if (!gang.hasMember(profile.get().getUsername())) {
                        gang.addMember(profile.get().getUsername(), GangMemberRole.RECRUIT);
                        persistGang(gang);
                    }
                    return Optional.of(gang);
                }
            }
        }

        for (Gang gang : gangsById.values()) {
            if (gang.hasMember(username)) {
                return Optional.of(gang);
            }
        }
        return Optional.empty();
    }

    public GangOperationResult createGang(String name, String creatorUsername) {
        if (!isValidGangName(name)) {
            return new GangOperationResult(GangOperationStatus.INVALID_NAME, null, null);
        }
        if (creatorUsername == null || creatorUsername.isBlank()) {
            return new GangOperationResult(GangOperationStatus.INVALID_PLAYER, null, null);
        }
        if (findGangForUser(creatorUsername).isPresent()) {
            return new GangOperationResult(GangOperationStatus.ALREADY_IN_GANG, null, null);
        }
        if (findByName(name).isPresent()) {
            return new GangOperationResult(GangOperationStatus.GANG_ALREADY_EXISTS, null, null);
        }

        Gang gang = new Gang(name.trim(), creatorUsername.trim(), "A new gang!");
        gang.setId(UUID.randomUUID().toString());
        gang.setCreationDate(Instant.now());
        persistGang(gang);
        updateProfileGangId(creatorUsername, gang.getId());
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, creatorUsername.trim());
    }

    public GangOperationResult leaveGang(String username) {
        Optional<Gang> gang = findGangForUser(username);
        if (gang.isEmpty()) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }

        Gang current = gang.get();
        if (current.isLeader(username)) {
            return new GangOperationResult(GangOperationStatus.LEADER_CANNOT_LEAVE_WITH_MEMBERS, current, username);
        }

        current.removeMember(username);
        persistGang(current);
        updateProfileGangId(username, null);
        return new GangOperationResult(GangOperationStatus.SUCCESS, current, username);
    }

    public GangOperationResult disbandGang(String username) {
        return disbandGang(username, null, false);
    }

    public GangOperationResult disbandGang(String username, String targetGangName, boolean adminMode) {
        if (adminMode && targetGangName != null && !targetGangName.isBlank()) {
            Gang targetGang = findByName(targetGangName).orElse(null);
            if (targetGang == null) {
                return new GangOperationResult(GangOperationStatus.GANG_NOT_FOUND, null, null);
            }
            disbandInternal(targetGang);
            return new GangOperationResult(GangOperationStatus.SUCCESS, null, username);
        }

        Optional<Gang> gang = findGangForUser(username);
        if (gang.isEmpty()) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }

        Gang current = gang.get();
        if (!current.isLeader(username)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, current, username);
        }

        disbandInternal(current);
        return new GangOperationResult(GangOperationStatus.SUCCESS, null, username);
    }

    public GangOperationResult invitePlayer(String actorUsername, String targetUsername) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        GangMemberRole actorRole = gang.roleOf(actorUsername);
        if (actorRole == null || !actorRole.officerOrAbove()) {
            return new GangOperationResult(GangOperationStatus.NOT_OFFICER, gang, null);
        }
        if (targetUsername == null || targetUsername.isBlank()) {
            return new GangOperationResult(GangOperationStatus.INVALID_PLAYER, gang, null);
        }
        if (gang.hasMember(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.TARGET_ALREADY_IN_GANG, gang, targetUsername);
        }
        if (gang.hasInvite(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.INVITE_ALREADY_EXISTS, gang, targetUsername);
        }
        gang.invitePlayer(targetUsername);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetUsername);
    }

    public GangOperationResult acceptInvite(String username, String gangName) {
        if (findGangForUser(username).isPresent()) {
            return new GangOperationResult(GangOperationStatus.ALREADY_IN_GANG, null, username);
        }
        Gang gang = findByName(gangName).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.GANG_NOT_FOUND, null, null);
        }
        if (!gang.hasInvite(username)) {
            return new GangOperationResult(GangOperationStatus.NOT_INVITED, gang, username);
        }
        if (gang.memberCount() >= MAX_MEMBERS + 1) {
            return new GangOperationResult(GangOperationStatus.GANG_FULL, gang, username);
        }
        gang.revokeInvite(username);
        gang.addMember(username, GangMemberRole.RECRUIT);
        persistGang(gang);
        updateProfileGangId(username, gang.getId());
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, username);
    }

    public GangOperationResult kickMember(String actorUsername, String targetUsername) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        GangMemberRole actorRole = gang.roleOf(actorUsername);
        if (actorRole == null || !actorRole.officerOrAbove()) {
            return new GangOperationResult(GangOperationStatus.NOT_OFFICER, gang, null);
        }
        if (!gang.hasMember(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.TARGET_NOT_IN_GANG, gang, targetUsername);
        }
        GangMemberRole targetRole = gang.roleOf(targetUsername);
        if (targetRole == GangMemberRole.LEADER) {
            return new GangOperationResult(GangOperationStatus.TARGET_IS_LEADER, gang, targetUsername);
        }
        if (targetRole == GangMemberRole.OFFICER && actorRole != GangMemberRole.LEADER) {
            return new GangOperationResult(GangOperationStatus.ONLY_LEADER_CAN_KICK_OFFICER, gang, targetUsername);
        }
        gang.removeMember(targetUsername);
        persistGang(gang);
        updateProfileGangId(targetUsername, null);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetUsername);
    }

    public GangOperationResult forceKick(String gangName, String targetUsername) {
        Gang gang = findByName(gangName).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.GANG_NOT_FOUND, null, null);
        }
        if (targetUsername == null || targetUsername.isBlank()) {
            return new GangOperationResult(GangOperationStatus.INVALID_PLAYER, gang, null);
        }
        if (onlinePlayer(targetUsername) == null && userProfileService.find(targetUsername).isEmpty()) {
            return new GangOperationResult(GangOperationStatus.INVALID_PLAYER, gang, targetUsername);
        }
        if (!gang.hasMember(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.TARGET_NOT_IN_GANG, gang, targetUsername);
        }
        if (gang.roleOf(targetUsername) == GangMemberRole.LEADER) {
            return new GangOperationResult(GangOperationStatus.TARGET_IS_LEADER, gang, targetUsername);
        }
        gang.removeMember(targetUsername);
        persistGang(gang);
        updateProfileGangId(targetUsername, null);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetUsername);
    }

    public GangOperationResult promoteMember(String actorUsername, String targetUsername) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        if (!gang.isLeader(actorUsername)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, gang, null);
        }
        if (!gang.hasMember(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.TARGET_NOT_IN_GANG, gang, targetUsername);
        }
        GangMemberRole role = gang.roleOf(targetUsername);
        if (role == GangMemberRole.LEADER || role == GangMemberRole.OFFICER) {
            return new GangOperationResult(GangOperationStatus.CANNOT_PROMOTE, gang, targetUsername);
        }
        gang.setRole(targetUsername, role.promote());
        persistGang(gang);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetUsername);
    }

    public GangOperationResult demoteMember(String actorUsername, String targetUsername) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        if (!gang.isLeader(actorUsername)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, gang, null);
        }
        if (!gang.hasMember(targetUsername)) {
            return new GangOperationResult(GangOperationStatus.TARGET_NOT_IN_GANG, gang, targetUsername);
        }
        GangMemberRole role = gang.roleOf(targetUsername);
        if (role == GangMemberRole.LEADER) {
            return new GangOperationResult(GangOperationStatus.CANNOT_DEMOTE, gang, targetUsername);
        }
        if (role == GangMemberRole.RECRUIT) {
            return new GangOperationResult(GangOperationStatus.ALREADY_LOWEST_RANK, gang, targetUsername);
        }
        gang.setRole(targetUsername, role.demote());
        persistGang(gang);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetUsername);
    }

    public GangOperationResult setDescription(String actorUsername, String description) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        if (!gang.isLeader(actorUsername)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, gang, null);
        }
        gang.setDescription(description);
        persistGang(gang);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, actorUsername);
    }

    public GangOperationResult ally(String actorUsername, String targetGangName) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        if (!gang.isLeader(actorUsername)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, gang, null);
        }
        Gang targetGang = findByName(targetGangName).orElse(null);
        if (targetGang == null) {
            return new GangOperationResult(GangOperationStatus.GANG_NOT_FOUND, gang, null);
        }
        if (gang.equals(targetGang)) {
            return new GangOperationResult(GangOperationStatus.CANNOT_TARGET_SELF, gang, null);
        }
        if (gang.isAlliedWith(targetGang)) {
            return new GangOperationResult(GangOperationStatus.ALREADY_ALLIED, gang, targetGang.leader());
        }
        if (targetGang.askedToAllyWith(gang)) {
            targetGang.allyWith(gang);
            persistGang(gang);
            persistGang(targetGang);
            return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetGang.leader());
        }
        Player targetLeader = onlinePlayer(targetGang.leader());
        if (targetLeader == null) {
            return new GangOperationResult(GangOperationStatus.TARGET_LEADER_OFFLINE, gang, targetGang.leader());
        }
        gang.askToAllyWith(targetGang);
        return new GangOperationResult(GangOperationStatus.ALLY_REQUEST_SENT, gang, targetLeader.getUsername());
    }

    public GangOperationResult enemy(String actorUsername, String targetGangName) {
        Gang gang = findGangForUser(actorUsername).orElse(null);
        if (gang == null) {
            return new GangOperationResult(GangOperationStatus.NOT_IN_GANG, null, null);
        }
        if (!gang.isLeader(actorUsername)) {
            return new GangOperationResult(GangOperationStatus.NOT_GANG_LEADER, gang, null);
        }
        Gang targetGang = findByName(targetGangName).orElse(null);
        if (targetGang == null) {
            return new GangOperationResult(GangOperationStatus.GANG_NOT_FOUND, gang, null);
        }
        if (!gang.isAlliedWith(targetGang)) {
            return new GangOperationResult(GangOperationStatus.ALREADY_ENEMIES, gang, targetGang.leader());
        }
        gang.removeAlly(targetGang);
        persistGang(gang);
        persistGang(targetGang);
        return new GangOperationResult(GangOperationStatus.SUCCESS, gang, targetGang.leader());
    }

    public boolean isValidGangName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return NAME_PATTERN.matcher(name.trim()).matches();
    }

    public void saveAll() throws IOException {
        repository.saveAll();
    }

    public void resetAll() {
        for (Gang gang : new ArrayList<>(gangsById.values())) {
            disbandInternal(gang);
        }
    }

    private void syncMembershipFromProfiles() {
        for (UserProfile profile : userProfileService.allProfiles()) {
            String gangId = profile.getGangId();
            if (gangId == null) {
                continue;
            }
            Gang gang = gangsById.get(gangId);
            if (gang == null) {
                profile.setGangId(null);
                userProfileService.save(profile);
                continue;
            }
            if (gang.addMember(profile.getUsername(), GangMemberRole.RECRUIT)) {
                persistGang(gang);
            }
            ensureMemberRoles(gang);
        }
    }

    private void ensureMemberRoles(Gang gang) {
        String leader = gang.leader();
        if (leader == null && !gang.getMembers().isEmpty()) {
            leader = gang.getMembers().iterator().next();
        }
        for (String member : gang.getMembers()) {
            if (gang.roleOf(member) == null) {
                gang.setRole(member, member.equalsIgnoreCase(leader) ? GangMemberRole.LEADER : GangMemberRole.RECRUIT);
            }
        }
        if (leader != null) {
            gang.setRole(leader, GangMemberRole.LEADER);
        }
    }

    private void disbandInternal(Gang gang) {
        List<String> members = new ArrayList<>(gang.getMembers());
        for (String allyName : new ArrayList<>(gang.getAllies())) {
            findByName(allyName).ifPresent(ally -> {
                ally.removeAlly(gang);
                persistGang(ally);
            });
        }
        gangIdByName.remove(Gang.normalizeName(gang.getName()));
        gangsById.remove(gang.getId());
        try {
            repository.delete(gang.getId());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        for (String member : members) {
            updateProfileGangId(member, null);
        }
    }

    private void persistGang(Gang gang) {
        String id = normalizeId(gang.getId());
        if (id == null) {
            throw new IllegalStateException("Gang must have a valid id before persisting.");
        }
        gangsById.put(id, gang);
        gangIdByName.put(Gang.normalizeName(gang.getName()), id);
        try {
            repository.save(gang);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void updateProfileGangId(String username, String gangId) {
        if (username == null || username.isBlank()) {
            return;
        }
        Optional<UserProfile> existing = userProfileService.find(username);
        if (existing.isPresent()) {
            UserProfile profile = existing.get();
            profile.setGangId(gangId);
            userProfileService.save(profile);
        }
    }

    private static Player onlinePlayer(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        final net.minestom.server.network.ConnectionManager connectionManager;
        try {
            connectionManager = MinecraftServer.getConnectionManager();
        } catch (NullPointerException ignored) {
            return null;
        }
        if (connectionManager == null) {
            return null;
        }
        Player exact = connectionManager.getOnlinePlayerByUsername(username);
        if (exact != null) {
            return exact;
        }
        for (Player player : connectionManager.getOnlinePlayers()) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }

    public enum GangOperationStatus {
        SUCCESS,
        INVALID_NAME,
        INVALID_PLAYER,
        ALREADY_IN_GANG,
        GANG_ALREADY_EXISTS,
        GANG_NOT_FOUND,
        NOT_IN_GANG,
        NOT_GANG_LEADER,
        NOT_OFFICER,
        LEADER_CANNOT_LEAVE_WITH_MEMBERS,
        TARGET_ALREADY_IN_GANG,
        INVITE_ALREADY_EXISTS,
        NOT_INVITED,
        GANG_FULL,
        TARGET_NOT_IN_GANG,
        TARGET_IS_LEADER,
        ONLY_LEADER_CAN_KICK_OFFICER,
        CANNOT_PROMOTE,
        CANNOT_DEMOTE,
        ALREADY_LOWEST_RANK,
        CANNOT_TARGET_SELF,
        ALREADY_ALLIED,
        ALLY_REQUEST_SENT,
        TARGET_LEADER_OFFLINE,
        ALREADY_ENEMIES
    }

    public record GangOperationResult(GangOperationStatus status, Gang gang, String subject) {
    }
}
