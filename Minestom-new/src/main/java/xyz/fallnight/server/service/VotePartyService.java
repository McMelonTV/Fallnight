package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;
import xyz.fallnight.server.util.LegacyTextFormatter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

public final class VotePartyService {
    public static final int DEFAULT_THRESHOLD = 50;
    public static final RewardType DEFAULT_REWARD_TYPE = RewardType.VOTE_KEYS;
    public static final double DEFAULT_MONEY_REWARD = 0d;
    public static final long DEFAULT_PRESTIGE_REWARD = 0L;

    private final Path votePartyFile;
    private final ObjectMapper mapper;
    private final PlayerProfileService profileService;
    private final MineRankService mineRankService;
    private final TagService tagService;
    private final LotteryService lotteryService;
    private final int threshold;
    private final RewardType rewardType;
    private final double moneyReward;
    private final long prestigeReward;
    private final LegacyCustomItemService customItemService;
    private final ItemDeliveryService itemDeliveryService;
    private int votes;

    public static VotePartyService fromDataRoot(Path dataRoot, PlayerProfileService profileService, MineRankService mineRankService, TagService tagService, LotteryService lotteryService, ItemDeliveryService itemDeliveryService) {
        return new VotePartyService(
            dataRoot.resolve("vote_party.json"),
            profileService,
            mineRankService,
            tagService,
            lotteryService,
            DEFAULT_THRESHOLD,
            DEFAULT_REWARD_TYPE,
            DEFAULT_MONEY_REWARD,
            DEFAULT_PRESTIGE_REWARD,
            itemDeliveryService
        );
    }

    public static VotePartyService fromDataRoot(Path dataRoot, PlayerProfileService profileService, ItemDeliveryService itemDeliveryService) {
        return fromDataRoot(dataRoot, profileService, null, null, null, itemDeliveryService);
    }

    public static VotePartyService fromDataRoot(Path dataRoot, PlayerProfileService profileService) {
        return fromDataRoot(dataRoot, profileService, null, null, null, null);
    }

    public VotePartyService(
        Path votePartyFile,
        PlayerProfileService profileService,
        MineRankService mineRankService,
        TagService tagService,
        LotteryService lotteryService,
        int threshold,
        RewardType rewardType,
        double moneyReward,
        long prestigeReward,
        ItemDeliveryService itemDeliveryService
    ) {
        this.votePartyFile = Objects.requireNonNull(votePartyFile, "votePartyFile");
        this.mapper = JacksonMappers.jsonMapper();
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.mineRankService = mineRankService;
        this.tagService = tagService;
        this.lotteryService = lotteryService;
        this.threshold = Math.max(1, threshold);
        this.rewardType = Objects.requireNonNull(rewardType, "rewardType");
        this.moneyReward = Math.max(0d, moneyReward);
        this.prestigeReward = Math.max(0L, prestigeReward);
        this.customItemService = new LegacyCustomItemService();
        this.itemDeliveryService = itemDeliveryService;
        this.votes = 0;
    }

    public synchronized void load() throws IOException {
        Path parent = votePartyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(votePartyFile)) {
            save();
            return;
        }

        JsonNode root = mapper.readTree(votePartyFile.toFile());
        votes = parseVotes(root.get("votes"));
    }

    public synchronized VoteProgress progress() {
        return new VoteProgress(votes, threshold, Math.max(0, threshold - votes));
    }

    public synchronized VoteUpdate addVote(String source) {
        votes++;
        if (votes >= threshold) {
            int rewardedPlayers = rewardOnlinePlayers();
            String rewardDescription = rewardDescription();
            broadcastThresholdReached(rewardedPlayers, rewardDescription);
            votes = 0;
            persist();
            return VoteUpdate.thresholdReached(source, threshold, rewardedPlayers, rewardDescription);
        }
        broadcastProgress(source, threshold - votes);
        persist();
        return VoteUpdate.progress(source, votes, threshold);
    }

    public synchronized VoteUpdate castVote(net.minestom.server.entity.Player player) {
        UserProfile profile = profileService.getOrCreate(player);
        profile.getExtraData().put("lastVote", System.currentTimeMillis() / 1000L);
        profile.getExtraData().put("votes", readLong(profile.getExtraData().get("votes")) + 1L);
        profile.addPrestigePoints(250);
        profile.getExtraData().put("randomTagCredits", readLong(profile.getExtraData().get("randomTagCredits")) + 1L);
        customItemService.createById(20, 2, 99).ifPresent(item -> deliverItem(player, profile, item));
        if (tagService != null) {
            tagService.grantRandomCrateDropTag(profile);
        }
        if (lotteryService != null) {
            lotteryService.grantTickets(profile.getUsername(), 1);
        }
        freeRankup(player, profile);
        profileService.save(profile);
        VoteUpdate update = addVote(player.getUsername());
        broadcastVoteClaim(player.getUsername());
        player.sendMessage(LegacyTextFormatter.deserialize("§r§b§l> §r§7You have been given §b2 vote keys§7, §b250 prestige points§7, §b1 lottery ticket§r§7 and a §brandom tag§r§7 for voting for the server!"));
        return update;
    }

    public synchronized void resetVotes() {
        votes = 0;
        persist();
    }

    public synchronized void saveAll() {
        persist();
    }

    public int threshold() {
        return threshold;
    }

    public RewardType rewardType() {
        return rewardType;
    }

    private int rewardOnlinePlayers() {
        int rewarded = 0;
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            UserProfile profile = profileService.getOrCreate(player);
            switch (rewardType) {
                case MONEY -> profile.deposit(moneyReward);
                case PRESTIGE_POINTS -> profile.addPrestigePoints(prestigeReward);
                case VOTE_KEYS -> customItemService.createById(20, 4, 99).ifPresent(item -> deliverItem(player, profile, item));
            }
            profileService.save(profile);
            rewarded++;
        }
        return rewarded;
    }

    private String rewardDescription() {
        return switch (rewardType) {
            case MONEY -> "$" + (long) moneyReward;
            case PRESTIGE_POINTS -> prestigeReward + " prestige points";
            case VOTE_KEYS -> "4 vote keys";
        };
    }

    private void deliverItem(Player player, UserProfile profile, net.minestom.server.item.ItemStack item) {
        if (itemDeliveryService != null) {
            itemDeliveryService.deliver(player, profile, item);
            return;
        }
        player.getInventory().addItemStack(item);
    }

    private void broadcastThresholdReached(int rewardedPlayers, String rewardDescription) {
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(LegacyTextFormatter.deserialize("§r§8[§bFN§8] §r§7It's vote party! Everyone gets a §bvote key§r§7!"));
        }
    }

    private void broadcastProgress(String voter, int remainingVotes) {
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(LegacyTextFormatter.deserialize("§r§8[§bFN§8] §r§7Vote party will start in §b" + remainingVotes + " votes§r§7."));
        }
    }

    private void broadcastVoteClaim(String voter) {
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(LegacyTextFormatter.deserialize("§r§8[§bFN§8] §r§b" + voter + " §r§7voted for the server with §b/vote §r§7and received special rewards."));
        }
    }

    private void freeRankup(Player player, UserProfile profile) {
        if (mineRankService == null) {
            return;
        }
        var nextRank = mineRankService.nextRank(profile.getMineRank()).orElse(null);
        if (nextRank == null) {
            return;
        }
        profile.setMineRank(nextRank.getId());
        player.sendMessage(LegacyTextFormatter.deserialize(
            "§b§l> §r§7You have been ranked up to §b" + nextRank.getTag() + "§r§7 for §bfree§r§7!"
        ));
    }

    private void persist() {
        try {
            save();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void save() throws IOException {
        Path parent = votePartyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("votes", Math.max(0, votes));
        mapper.writeValue(votePartyFile.toFile(), root);
    }

    private static int parseVotes(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return Math.max(0, node.intValue());
        }
        if (node.isTextual()) {
            try {
                return Math.max(0, Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public enum RewardType {
        MONEY,
        PRESTIGE_POINTS,
        VOTE_KEYS
    }

    private static long readLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public record VoteProgress(int votes, int threshold, int remaining) {
    }

    public record VoteUpdate(
        String source,
        int votes,
        int threshold,
        boolean thresholdReached,
        int rewardedPlayers,
        String rewardDescription
    ) {
        public static VoteUpdate progress(String source, int votes, int threshold) {
            return new VoteUpdate(source, votes, threshold, false, 0, null);
        }

        public static VoteUpdate thresholdReached(String source, int threshold, int rewardedPlayers, String rewardDescription) {
            return new VoteUpdate(source, 0, threshold, true, rewardedPlayers, rewardDescription);
        }
    }
}
