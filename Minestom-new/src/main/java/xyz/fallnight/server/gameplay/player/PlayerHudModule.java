package xyz.fallnight.server.gameplay.player;

import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.MineService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.PvpZoneService;
import xyz.fallnight.server.service.SpawnService;
import xyz.fallnight.server.util.LegacyTextFormatter;
import xyz.fallnight.server.util.NumberFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerHudModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final PlayerProfileService profileService;
    private final MineService mineService;
    private final MineRankService mineRankService;
    private final PvpZoneService pvpZoneService;
    private final SpawnService spawnWorldService;
    private final ConcurrentMap<UUID, Double> lastBalances = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> balanceDeltaUntil = new ConcurrentHashMap<>();
    private Task task;

    public PlayerHudModule(PlayerProfileService profileService, MineService mineService, MineRankService mineRankService, PvpZoneService pvpZoneService, SpawnService spawnWorldService) {
        this.profileService = profileService;
        this.mineService = mineService;
        this.mineRankService = mineRankService;
        this.pvpZoneService = pvpZoneService;
        this.spawnWorldService = spawnWorldService;
    }

    private static Component renderBalanceDelta(double previous, double current, double delta) {
        String prefix = NumberFormatter.currency(previous);
        String amount = NumberFormatter.currency(Math.abs(delta));
        if (delta > 0d) {
            return LEGACY.deserialize("§b" + prefix + " §7+ §a" + amount);
        }
        if (delta < 0d) {
            return LEGACY.deserialize("§b" + prefix + " §7- §c" + amount);
        }
        return LEGACY.deserialize("§b" + NumberFormatter.currency(current));
    }

    private static String toRoman(int value) {
        int number = Math.max(1, value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                builder.append(numerals[i]);
                number -= values[i];
            }
        }
        return builder.toString();
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(60))
                .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        TeamManager teamManager = MinecraftServer.getTeamManager();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            Team team = hudTeam(teamManager, player.getUsername());
            team.updateSuffix(renderSuffix(player));
            if (!team.getMembers().contains(player.getUsername())) {
                team.addMember(player.getUsername());
            }
            tickBalanceHud(player);
        }
    }

    private void tickBalanceHud(Player player) {
        if (player == null) {
            return;
        }
        UserProfile profile = profileService.getOrCreate(player);
        UUID uuid = player.getUuid();
        double current = profile.getBalance();
        Double previous = lastBalances.put(uuid, current);
        long now = System.currentTimeMillis();
        if (previous == null) {
            player.sendActionBar(LEGACY.deserialize("§b" + NumberFormatter.currency(current)));
            return;
        }
        double delta = current - previous;
        if (Math.abs(delta) >= 0.01d) {
            balanceDeltaUntil.put(uuid, now + 2000L);
            player.sendActionBar(renderBalanceDelta(previous, current, delta));
            return;
        }
        if (balanceDeltaUntil.getOrDefault(uuid, 0L) > now) {
            return;
        }
        player.sendActionBar(LEGACY.deserialize("§b" + NumberFormatter.currency(current)));
    }

    private Team hudTeam(TeamManager teamManager, String username) {
        String normalized = username == null ? "unknown" : username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String teamName = ("fnhud_" + normalized);
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        Team existing = teamManager.getTeam(teamName);
        if (existing != null) {
            return existing;
        }
        Team created = teamManager.createTeam(teamName);
        created.setNameTagVisibility(TeamsPacket.NameTagVisibility.ALWAYS);
        created.setTeamColor(NamedTextColor.GRAY);
        created.setPrefix(Component.empty());
        created.setSuffix(Component.empty());
        return created;
    }

    private Component renderSuffix(Player player) {
        if (player.getInstance() != null && pvpZoneService.isInPvpZone(
                player.getPosition().blockX(),
                player.getPosition().blockY(),
                player.getPosition().blockZ(),
                spawnWorldService.worldName()
        )) {
            int health = (int) Math.ceil(player.getHealth());
            return Component.text(" " + health + "❤", NamedTextColor.RED);
        }
        UserProfile profile = profileService.getOrCreate(player);
        String mineTag = LegacyTextFormatter.normalize(mineRankService.find(profile.getMineRank()).map(MineRank::getTag).orElse("A"));
        return LEGACY.deserialize("§r§7" + toRoman(profile.getPrestige()) + "⛏§r§8|⛏§r" + mineTag);
    }
}
