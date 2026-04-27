package xyz.fallnight.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.persistence.JacksonMappers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class LotteryService {
    public static final double DEFAULT_TICKET_PRICE = 100_000d;
    public static final double DEFAULT_JACKPOT_CONTRIBUTION = 75_000d;
    public static final long DEFAULT_DRAW_TIME_SECONDS = 3_600L;
    public static final int MAX_PURCHASE_TICKETS = 100;

    private final Path lotteryFile;
    private final ObjectMapper mapper;
    private final PlayerProfileService profileService;
    private final double ticketPrice;
    private final double jackpotContribution;
    private final Map<String, Integer> ticketsByUsername;
    private double jackpotPool;
    private long remainingDrawSeconds;

    public static LotteryService fromDataRoot(Path dataRoot, PlayerProfileService profileService) {
        return new LotteryService(
            dataRoot.resolve("lottery.json"),
            profileService,
            DEFAULT_TICKET_PRICE,
            DEFAULT_JACKPOT_CONTRIBUTION
        );
    }

    public LotteryService(
        Path lotteryFile,
        PlayerProfileService profileService,
        double ticketPrice,
        double jackpotContribution
    ) {
        this.lotteryFile = Objects.requireNonNull(lotteryFile, "lotteryFile");
        this.mapper = JacksonMappers.jsonMapper();
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.ticketPrice = Math.max(1d, ticketPrice);
        this.jackpotContribution = Math.max(0d, jackpotContribution);
        this.ticketsByUsername = new LinkedHashMap<>();
        this.jackpotPool = 0d;
        this.remainingDrawSeconds = DEFAULT_DRAW_TIME_SECONDS;
    }

    public synchronized void load() throws IOException {
        Path parent = lotteryFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(lotteryFile)) {
            save();
            return;
        }

        JsonNode root = mapper.readTree(lotteryFile.toFile());
        ticketsByUsername.clear();
        JsonNode ticketsNode = root.get("tickets");
        if (ticketsNode != null && ticketsNode.isObject()) {
            ticketsNode.properties().forEach(entry -> {
                int count = positiveInt(entry.getValue(), 0);
                if (count > 0) {
                    ticketsByUsername.put(entry.getKey(), count);
                }
            });
        }

        jackpotPool = Math.max(0d, positiveDouble(root.get("jackpotPool"), 0d));
        remainingDrawSeconds = Math.max(0L, positiveLong(root.get("remainingDrawSeconds"), DEFAULT_DRAW_TIME_SECONDS));
    }

    public synchronized LotteryStatus status() {
        return new LotteryStatus(
            Map.copyOf(ticketsByUsername),
            jackpotPool,
            ticketPrice,
            jackpotContribution,
            totalTickets(),
            remainingDrawSeconds
        );
    }

    public synchronized DrawResult tickSecond() {
        remainingDrawSeconds = Math.max(0L, remainingDrawSeconds - 1L);
        if (remainingDrawSeconds > 0L) {
            persist();
            return DrawResult.pending(remainingDrawSeconds);
        }
        DrawResult result = drawWinnerIfPossible();
        if (result.status() != DrawStatus.SUCCESS) {
            reset();
        }
        return result;
    }

    public synchronized PurchaseResult buyTickets(String username, int amount) {
        if (username == null || username.isBlank()) {
            return PurchaseResult.invalidPlayer();
        }
        if (amount <= 0) {
            return PurchaseResult.invalidAmount();
        }
        if (amount > MAX_PURCHASE_TICKETS) {
            return PurchaseResult.invalidAmount();
        }

        UserProfile profile = profileService.getOrCreateByUsername(username);
        double totalCost = amount * ticketPrice;
        if (!profile.withdraw(totalCost)) {
            return PurchaseResult.insufficientBalance(totalCost);
        }

        profileService.save(profile);
        ticketsByUsername.merge(profile.getUsername(), amount, Integer::sum);
        jackpotPool += amount * jackpotContribution;
        persist();
        return PurchaseResult.success(amount, totalCost, jackpotPool, ticketsByUsername.get(profile.getUsername()));
    }

    public synchronized void grantTickets(String username, int amount) {
        if (username == null || username.isBlank() || amount <= 0) {
            return;
        }
        UserProfile profile = profileService.getOrCreateByUsername(username);
        ticketsByUsername.merge(profile.getUsername(), amount, Integer::sum);
        jackpotPool += amount * jackpotContribution;
        persist();
    }

    public synchronized DrawResult drawWinnerIfPossible() {
        int totalTickets = totalTickets();
        if (totalTickets <= 0) {
            return DrawResult.noTickets();
        }

        int winningTicket = ThreadLocalRandom.current().nextInt(totalTickets) + 1;
        int cursor = 0;
        String winner = null;

        for (Map.Entry<String, Integer> entry : ticketsByUsername.entrySet()) {
            cursor += Math.max(0, entry.getValue());
            if (winningTicket <= cursor) {
                winner = entry.getKey();
                break;
            }
        }

        if (winner == null || winner.isBlank()) {
            return DrawResult.noTickets();
        }

        double reward = jackpotPool;
        UserProfile winnerProfile = profileService.getOrCreateByUsername(winner);
        winnerProfile.deposit(reward);
        profileService.save(winnerProfile);

        ticketsByUsername.clear();
        jackpotPool = 0d;
        remainingDrawSeconds = DEFAULT_DRAW_TIME_SECONDS;
        persist();
        return DrawResult.success(winnerProfile.getUsername(), reward, totalTickets);
    }

    public synchronized void reset() {
        ticketsByUsername.clear();
        jackpotPool = 0d;
        remainingDrawSeconds = DEFAULT_DRAW_TIME_SECONDS;
        persist();
    }

    public synchronized void refundAll() {
        for (Map.Entry<String, Integer> entry : ticketsByUsername.entrySet()) {
            int count = Math.max(0, entry.getValue());
            if (count <= 0) {
                continue;
            }
            UserProfile profile = profileService.getOrCreateByUsername(entry.getKey());
            profile.deposit(ticketPrice * count);
            profileService.save(profile);
        }
        ticketsByUsername.clear();
        jackpotPool = 0d;
        remainingDrawSeconds = DEFAULT_DRAW_TIME_SECONDS;
        persist();
    }

    public synchronized void saveAll() {
        persist();
    }

    private int totalTickets() {
        int total = 0;
        for (Integer count : ticketsByUsername.values()) {
            if (count == null || count <= 0) {
                continue;
            }
            total += count;
        }
        return total;
    }

    private void persist() {
        try {
            save();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void save() throws IOException {
        Path parent = lotteryFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("ticketPrice", ticketPrice);
        root.put("jackpotContribution", jackpotContribution);
        root.put("jackpotPool", Math.max(0d, jackpotPool));
        root.put("remainingDrawSeconds", Math.max(0L, remainingDrawSeconds));

        ObjectNode ticketsNode = root.putObject("tickets");
        for (Map.Entry<String, Integer> entry : ticketsByUsername.entrySet()) {
            int count = Math.max(0, entry.getValue());
            if (count > 0) {
                ticketsNode.put(entry.getKey(), count);
            }
        }

        mapper.writeValue(lotteryFile.toFile(), root);
    }

    private static int positiveInt(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return Math.max(0, node.intValue());
        }
        if (node.isTextual()) {
            try {
                return Math.max(0, Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double positiveDouble(JsonNode node, double fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return Math.max(0d, node.doubleValue());
        }
        if (node.isTextual()) {
            try {
                return Math.max(0d, Double.parseDouble(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long positiveLong(JsonNode node, long fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return Math.max(0L, node.longValue());
        }
        if (node.isTextual()) {
            try {
                return Math.max(0L, Long.parseLong(node.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public enum PurchaseStatus {
        SUCCESS,
        INVALID_PLAYER,
        INVALID_AMOUNT,
        INSUFFICIENT_BALANCE
    }

    public enum DrawStatus {
        SUCCESS,
        NO_TICKETS,
        PENDING
    }

    public record LotteryStatus(
        Map<String, Integer> ticketsByUsername,
        double jackpotPool,
        double ticketPrice,
        double jackpotContribution,
        int totalTickets,
        long remainingDrawSeconds
    ) {
        public int uniqueEntrants() {
            return ticketsByUsername.size();
        }
    }

    public record PurchaseResult(
        PurchaseStatus status,
        int purchasedTickets,
        double totalCost,
        double jackpotPool,
        int playerTicketCount
    ) {
        public static PurchaseResult success(int purchasedTickets, double totalCost, double jackpotPool, int playerTicketCount) {
            return new PurchaseResult(PurchaseStatus.SUCCESS, purchasedTickets, totalCost, jackpotPool, playerTicketCount);
        }

        public static PurchaseResult invalidPlayer() {
            return new PurchaseResult(PurchaseStatus.INVALID_PLAYER, 0, 0d, 0d, 0);
        }

        public static PurchaseResult invalidAmount() {
            return new PurchaseResult(PurchaseStatus.INVALID_AMOUNT, 0, 0d, 0d, 0);
        }

        public static PurchaseResult insufficientBalance(double totalCost) {
            return new PurchaseResult(PurchaseStatus.INSUFFICIENT_BALANCE, 0, totalCost, 0d, 0);
        }
    }

    public record DrawResult(DrawStatus status, String winnerUsername, double payout, int ticketCount) {
        public static DrawResult success(String winnerUsername, double payout, int ticketCount) {
            return new DrawResult(DrawStatus.SUCCESS, winnerUsername, payout, ticketCount);
        }

        public static DrawResult noTickets() {
            return new DrawResult(DrawStatus.NO_TICKETS, null, 0d, 0);
        }

        public static DrawResult pending(long remainingDrawSeconds) {
            return new DrawResult(DrawStatus.PENDING, null, remainingDrawSeconds, 0);
        }
    }
}
