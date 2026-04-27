package xyz.fallnight.server.service;

import xyz.fallnight.server.domain.plot.PlotCoordinate;
import xyz.fallnight.server.domain.plot.PlotEntry;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.plot.PlotRepository;
import net.minestom.server.coordinate.Pos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class PlotService {
    public static final int PLOT_SIZE = 48;
    public static final int ROAD_SIZE = 7;
    public static final int GRID_SPACING = PLOT_SIZE + ROAD_SIZE;

    private final PlotRepository repository;
    private final PlayerProfileService profileService;
    private final RankService rankService;
    private final ConcurrentMap<PlotCoordinate, PlotEntry> plots;

    public PlotService(PlotRepository repository, PlayerProfileService profileService, RankService rankService) {
        this.repository = repository;
        this.profileService = profileService;
        this.rankService = rankService;
        this.plots = new ConcurrentHashMap<>();
    }

    private static Comparator<PlotCoordinate> plotComparator() {
        return Comparator.comparingInt(PlotCoordinate::x).thenComparingInt(PlotCoordinate::z);
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public void load() throws IOException {
        Map<PlotCoordinate, PlotEntry> loaded = repository.loadAll();
        plots.clear();
        loaded.forEach((coordinate, entry) -> plots.put(coordinate, entry.copy()));
    }

    public void save() throws IOException {
        repository.saveAll(snapshot());
    }

    public List<PlotCoordinate> resetAll() {
        List<PlotCoordinate> coordinates = new ArrayList<>(plots.keySet());
        plots.clear();
        persistSafely();
        return coordinates;
    }

    public ClaimResult claimPlot(String username) {
        String owner = normalize(username);
        if (owner == null) {
            return new ClaimResult(ClaimStatus.INVALID_USERNAME, null);
        }

        List<PlotCoordinate> owned = listOwnedPlotCoordinates(owner);
        int cap = maxPlotsFor(owner);
        if (owned.size() >= cap) {
            return new ClaimResult(ClaimStatus.AT_PLOT_CAP, null);
        }

        PlotCoordinate next = resolveNextFreePlotCoordinate();
        PlotEntry entry = new PlotEntry(owner);
        plots.put(next, entry);
        persistSafely();
        return new ClaimResult(ClaimStatus.SUCCESS, next);
    }

    public ClaimResult claimPlotAt(String username, PlotCoordinate coordinate) {
        String owner = normalize(username);
        if (owner == null || coordinate == null) {
            return new ClaimResult(ClaimStatus.INVALID_USERNAME, null);
        }
        List<PlotCoordinate> owned = listOwnedPlotCoordinates(owner);
        int cap = maxPlotsFor(owner);
        if (owned.size() >= cap) {
            return new ClaimResult(ClaimStatus.AT_PLOT_CAP, null);
        }
        if (plots.containsKey(coordinate)) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, null);
        }
        plots.put(coordinate, new PlotEntry(owner));
        persistSafely();
        return new ClaimResult(ClaimStatus.SUCCESS, coordinate);
    }

    public List<PlotCoordinate> listOwnedPlotCoordinates(String username) {
        String owner = normalize(username);
        if (owner == null) {
            return List.of();
        }
        return plots.entrySet().stream()
                .filter(entry -> owner.equals(normalize(entry.getValue().getOwner())))
                .map(Map.Entry::getKey)
                .sorted(plotComparator())
                .toList();
    }

    public List<PlotEntryView> listOwnedPlots(String username) {
        String owner = normalize(username);
        if (owner == null) {
            return List.of();
        }
        return plots.entrySet().stream()
                .filter(entry -> owner.equals(normalize(entry.getValue().getOwner())))
                .sorted(Map.Entry.comparingByKey(plotComparator()))
                .map(entry -> new PlotEntryView(entry.getKey(), entry.getValue().copy()))
                .toList();
    }

    public List<PlotEntryView> listAccessiblePlots(String username) {
        String target = normalize(username);
        if (target == null) {
            return List.of();
        }
        return plots.entrySet().stream()
                .filter(entry -> {
                    PlotEntry plot = entry.getValue();
                    String owner = normalize(plot.getOwner());
                    if (target.equals(owner)) {
                        return true;
                    }
                    return plot.getMembers().stream().map(PlotService::normalize).anyMatch(target::equals);
                })
                .sorted(Map.Entry.comparingByKey(plotComparator()))
                .map(entry -> new PlotEntryView(entry.getKey(), entry.getValue().copy()))
                .toList();
    }

    public Optional<PlotEntryView> getHomePlot(String ownerUsername) {
        List<PlotEntryView> owned = listOwnedPlots(ownerUsername);
        return owned.isEmpty() ? Optional.empty() : Optional.of(owned.getFirst());
    }

    public Optional<PlotEntryView> getPlotByOwner(String ownerUsername) {
        return getHomePlot(ownerUsername);
    }

    public Optional<PlotEntryView> getPlot(PlotCoordinate coordinate) {
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new PlotEntryView(coordinate, entry.copy()));
    }

    public UpdateStatus setPlotName(String ownerUsername, String name) {
        PlotCoordinate home = homeCoordinate(ownerUsername).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return setPlotNameAt(ownerUsername, home, name);
    }

    public UpdateStatus setPlotNameAt(String ownerUsername, PlotCoordinate coordinate, String name) {
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        entry.setName(name);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus setPlotNameAnyAt(PlotCoordinate coordinate, String name) {
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        entry.setName(name);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus addMember(String ownerUsername, String memberUsername) {
        String member = normalize(memberUsername);
        String owner = normalize(ownerUsername);
        if (owner == null || member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (owner.equals(member)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }

        PlotCoordinate home = homeCoordinate(owner).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }

        return addMemberAt(ownerUsername, home, memberUsername);
    }

    public UpdateStatus addMemberAt(String ownerUsername, PlotCoordinate coordinate, String memberUsername) {
        String member = normalize(memberUsername);
        String owner = normalize(ownerUsername);
        if (owner == null || member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (owner.equals(member)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }

        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        if (entry.getBlockedUsers().contains(member)) {
            return UpdateStatus.USER_BLOCKED;
        }
        if (entry.getMembers().contains(member)) {
            return UpdateStatus.ALREADY_SET;
        }
        List<String> members = new ArrayList<>(entry.getMembers());
        members.add(member);
        entry.setMembers(members);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus addMemberAnyAt(PlotCoordinate coordinate, String memberUsername) {
        String member = normalize(memberUsername);
        if (member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        if (entry.getBlockedUsers().contains(member)) {
            return UpdateStatus.USER_BLOCKED;
        }
        if (entry.getMembers().contains(member)) {
            return UpdateStatus.ALREADY_SET;
        }
        List<String> members = new ArrayList<>(entry.getMembers());
        members.add(member);
        entry.setMembers(members);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus removeMember(String ownerUsername, String memberUsername) {
        String member = normalize(memberUsername);
        if (member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotCoordinate home = homeCoordinate(ownerUsername).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return removeMemberAt(ownerUsername, home, memberUsername);
    }

    public UpdateStatus removeMemberAt(String ownerUsername, PlotCoordinate coordinate, String memberUsername) {
        String member = normalize(memberUsername);
        if (member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        List<String> members = new ArrayList<>(entry.getMembers());
        if (!members.remove(member)) {
            return UpdateStatus.NOT_SET;
        }
        entry.setMembers(members);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus removeMemberAnyAt(PlotCoordinate coordinate, String memberUsername) {
        String member = normalize(memberUsername);
        if (member == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        List<String> members = new ArrayList<>(entry.getMembers());
        if (!members.remove(member)) {
            return UpdateStatus.NOT_SET;
        }
        entry.setMembers(members);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus blockUser(String ownerUsername, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        String owner = normalize(ownerUsername);
        if (blocked == null || owner == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (blocked.equals(owner)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }
        PlotCoordinate home = homeCoordinate(owner).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return blockUserAt(ownerUsername, home, blockedUsername);
    }

    public UpdateStatus blockUserAt(String ownerUsername, PlotCoordinate coordinate, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        String owner = normalize(ownerUsername);
        if (blocked == null || owner == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (blocked.equals(owner)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }

        if (entry.getBlockedUsers().contains(blocked)) {
            return UpdateStatus.ALREADY_SET;
        }

        List<String> blockedUsers = new ArrayList<>(entry.getBlockedUsers());
        blockedUsers.add(blocked);
        entry.setBlockedUsers(blockedUsers);

        List<String> members = entry.getMembers().stream()
                .filter(member -> !member.equals(blocked))
                .collect(Collectors.toList());
        entry.setMembers(members);

        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus blockUserAnyAt(PlotCoordinate coordinate, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        if (blocked == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        if (entry.getBlockedUsers().contains(blocked)) {
            return UpdateStatus.ALREADY_SET;
        }
        List<String> blockedUsers = new ArrayList<>(entry.getBlockedUsers());
        blockedUsers.add(blocked);
        entry.setBlockedUsers(blockedUsers);
        List<String> members = entry.getMembers().stream()
                .filter(member -> !member.equals(blocked))
                .collect(Collectors.toList());
        entry.setMembers(members);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus unblockUser(String ownerUsername, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        if (blocked == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotCoordinate home = homeCoordinate(ownerUsername).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return unblockUserAt(ownerUsername, home, blockedUsername);
    }

    public UpdateStatus unblockUserAt(String ownerUsername, PlotCoordinate coordinate, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        if (blocked == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        List<String> blockedUsers = new ArrayList<>(entry.getBlockedUsers());
        if (!blockedUsers.remove(blocked)) {
            return UpdateStatus.NOT_SET;
        }
        entry.setBlockedUsers(blockedUsers);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus unblockUserAnyAt(PlotCoordinate coordinate, String blockedUsername) {
        String blocked = normalize(blockedUsername);
        if (blocked == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        List<String> blockedUsers = new ArrayList<>(entry.getBlockedUsers());
        if (!blockedUsers.remove(blocked)) {
            return UpdateStatus.NOT_SET;
        }
        entry.setBlockedUsers(blockedUsers);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus unclaimHomePlot(String ownerUsername) {
        PlotCoordinate home = homeCoordinate(ownerUsername).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return unclaimPlotAt(ownerUsername, home);
    }

    public UpdateStatus unclaimPlotAt(String ownerUsername, PlotCoordinate coordinate) {
        if (ownedEntry(ownerUsername, coordinate).isEmpty()) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        PlotEntry removed = plots.remove(coordinate);
        if (removed == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus unclaimPlotAnyAt(PlotCoordinate coordinate) {
        PlotEntry removed = plots.remove(coordinate);
        if (removed == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus clearHomePlot(String ownerUsername) {
        PlotCoordinate home = homeCoordinate(ownerUsername).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return clearPlotAt(ownerUsername, home);
    }

    public UpdateStatus clearPlotAt(String ownerUsername, PlotCoordinate coordinate) {
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus clearPlotAnyAt(PlotCoordinate coordinate) {
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus transferHomePlot(String ownerUsername, String newOwnerUsername) {
        String owner = normalize(ownerUsername);
        String newOwner = normalize(newOwnerUsername);
        if (owner == null || newOwner == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (owner.equals(newOwner)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }

        PlotCoordinate home = homeCoordinate(owner).orElse(null);
        if (home == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        return transferPlotAt(ownerUsername, home, newOwnerUsername);
    }

    public UpdateStatus transferPlotAt(String ownerUsername, PlotCoordinate coordinate, String newOwnerUsername) {
        String owner = normalize(ownerUsername);
        String newOwner = normalize(newOwnerUsername);
        if (owner == null || newOwner == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        if (owner.equals(newOwner)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }
        PlotEntry entry = ownedEntry(ownerUsername, coordinate).orElse(null);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }

        int currentOwned = listOwnedPlotCoordinates(newOwner).size();
        if (currentOwned >= maxPlotsFor(newOwner)) {
            return UpdateStatus.AT_PLOT_CAP;
        }

        List<String> members = new ArrayList<>(entry.getMembers());
        members.remove(newOwner);
        if (!members.contains(owner)) {
            members.add(owner);
        }
        entry.setMembers(members);
        entry.setOwner(newOwner);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public UpdateStatus transferPlotAnyAt(PlotCoordinate coordinate, String newOwnerUsername) {
        String newOwner = normalize(newOwnerUsername);
        if (newOwner == null) {
            return UpdateStatus.INVALID_USERNAME;
        }
        PlotEntry entry = plots.get(coordinate);
        if (entry == null) {
            return UpdateStatus.NO_HOME_PLOT;
        }
        String owner = normalize(entry.getOwner());
        if (owner != null && owner.equals(newOwner)) {
            return UpdateStatus.CANNOT_TARGET_SELF;
        }
        int currentOwned = listOwnedPlotCoordinates(newOwner).size();
        if (currentOwned >= maxPlotsFor(newOwner)) {
            return UpdateStatus.AT_PLOT_CAP;
        }
        List<String> members = new ArrayList<>(entry.getMembers());
        members.remove(newOwner);
        if (owner != null && !owner.isBlank() && !members.contains(owner)) {
            members.add(owner);
        }
        entry.setMembers(members);
        entry.setOwner(newOwner);
        persistSafely();
        return UpdateStatus.SUCCESS;
    }

    public PlotCoordinate resolveNextFreePlotCoordinate() {
        int bound = 1000;
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        int max = Math.max(bound, bound);
        int maxIterations = max * max;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if (-bound / 2 <= x && x <= bound / 2 && -bound / 2 <= z && z <= bound / 2) {
                PlotCoordinate candidate = PlotCoordinate.of(x, z);
                if (!plots.containsKey(candidate)) {
                    return candidate;
                }
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int swap = dx;
                dx = -dz;
                dz = swap;
            }
            x += dx;
            z += dz;
        }
        throw new IllegalStateException("No free plot coordinate available in search space.");
    }

    public PlotCoordinate resolveCoordinateForPosition(Pos spawn, Pos position) {
        Optional<PlotCoordinate> coordinate = plotAtPosition(spawn, position);
        return coordinate.orElseGet(() -> PlotCoordinate.of(0, 0));
    }

    public Optional<PlotCoordinate> plotAtPosition(Pos spawn, Pos position) {
        int x = (int) Math.floor(position.x());
        int z = (int) Math.floor(position.z());
        if (!isPlotArea(x, z)) {
            return Optional.empty();
        }
        int plotX = x < 0
                ? (int) Math.floor((x - 4D) / GRID_SPACING) + 1
                : (int) Math.ceil((x - GRID_SPACING + 3D) / GRID_SPACING) + 1;
        int plotZ = z < 0
                ? (int) Math.floor((z - 4D) / GRID_SPACING) + 1
                : (int) Math.ceil((z - GRID_SPACING + 3D) / GRID_SPACING) + 1;
        return Optional.of(PlotCoordinate.of(plotX, plotZ));
    }

    public Pos plotHomePosition(PlotCoordinate coordinate, Pos spawn, Pos current) {
        double anchorX = 0.5D;
        double anchorZ = 0.5D;
        double y = 65D;
        double x = anchorX - 53D + (coordinate.x() * GRID_SPACING);
        double z = anchorZ - 53D + (coordinate.z() * GRID_SPACING);
        return new Pos(x, y, z, -45f, 0f);
    }

    public int maxPlotsFor(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            return 1;
        }
        Optional<UserProfile> profile = profileService.findOfflineByUsername(normalized);
        if (profile.isEmpty()) {
            return 1;
        }
        int rankBonus = Math.max(0, rankService.plotLimit(profile.get()));
        Object raw = profile.get().getExtraData().get("maxPlots");
        if (raw instanceof Number number) {
            return number.intValue() + rankBonus;
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim()) + rankBonus;
            } catch (NumberFormatException ignored) {
                return 1 + rankBonus;
            }
        }
        return 1 + rankBonus;
    }

    public Optional<PlotCoordinate> ownedCoordinateAt(String ownerUsername, Pos spawnPos, Pos position) {
        PlotCoordinate coordinate = plotAtPosition(spawnPos, position).orElse(null);
        return ownedEntry(ownerUsername, coordinate).isPresent() ? Optional.of(coordinate) : Optional.empty();
    }

    private boolean isPlotArea(int x, int z) {
        int localX = Math.floorMod(x, GRID_SPACING);
        int localZ = Math.floorMod(z, GRID_SPACING);
        return singleCoordIsPlot(localX) && singleCoordIsPlot(localZ);
    }

    private boolean singleCoordIsPlot(int local) {
        return local > 3 && local <= PLOT_SIZE + 3;
    }

    private Optional<PlotCoordinate> homeCoordinate(String ownerUsername) {
        List<PlotCoordinate> owned = listOwnedPlotCoordinates(ownerUsername);
        return owned.isEmpty() ? Optional.empty() : Optional.of(owned.getFirst());
    }

    private Optional<PlotEntry> ownedEntry(String ownerUsername, PlotCoordinate coordinate) {
        if (coordinate == null) {
            return Optional.empty();
        }
        PlotEntry entry = plots.get(coordinate);
        String owner = normalize(ownerUsername);
        if (entry == null || owner == null || !owner.equals(normalize(entry.getOwner()))) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private Map<PlotCoordinate, PlotEntry> snapshot() {
        return plots.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy(),
                        (first, second) -> first,
                        java.util.LinkedHashMap::new
                ));
    }

    private void persistSafely() {
        try {
            save();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public enum ClaimStatus {
        SUCCESS,
        INVALID_USERNAME,
        ALREADY_CLAIMED,
        AT_PLOT_CAP
    }

    public enum UpdateStatus {
        SUCCESS,
        INVALID_USERNAME,
        NO_HOME_PLOT,
        ALREADY_SET,
        NOT_SET,
        USER_BLOCKED,
        CANNOT_TARGET_SELF,
        AT_PLOT_CAP
    }

    public record ClaimResult(ClaimStatus status, PlotCoordinate coordinate) {
    }

    public record PlotEntryView(PlotCoordinate coordinate, PlotEntry entry) {
    }
}
