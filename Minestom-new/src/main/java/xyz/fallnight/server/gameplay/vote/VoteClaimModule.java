package xyz.fallnight.server.gameplay.vote;

import xyz.fallnight.server.service.VotePartyService;
import java.time.Duration;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class VoteClaimModule {
    private static final String SERVER_KEY = "fE6vi81D4FQVY7Qx4cnWnM0ZI0MrWGP95";

    private final VotePartyService votePartyService;
    private final HttpClient httpClient;
    private final Set<String> pendingClaims;
    private Task task;
    private long lastRunAtSeconds;

    public VoteClaimModule(VotePartyService votePartyService) {
        this.votePartyService = votePartyService;
        this.httpClient = HttpClient.newHttpClient();
        this.pendingClaims = ConcurrentHashMap.newKeySet();
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager().buildTask(this::tick).repeat(TaskSchedule.tick(8)).schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis() / 1000L;
        if (lastRunAtSeconds + 20 > now) {
            return;
        }
        lastRunAtSeconds = now;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            tryClaim(player);
        }
    }

    private void tryClaim(Player player) {
        String username = URLEncoder.encode(player.getUsername(), StandardCharsets.UTF_8);
        String pendingKey = player.getUsername().toLowerCase();
        if (!pendingClaims.add(pendingKey)) {
            return;
        }
        HttpRequest check = HttpRequest.newBuilder(URI.create("https://minecraftpocket-servers.com/api/?object=votes&element=claim&key=" + SERVER_KEY + "&username=" + username))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        httpClient.sendAsync(check, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (!"1".equals(response.body().trim())) {
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    HttpRequest confirm = HttpRequest.newBuilder(URI.create("https://minecraftpocket-servers.com/api/?action=post&object=votes&element=claim&key=" + SERVER_KEY + "&username=" + username))
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build();
                    return httpClient.sendAsync(confirm, HttpResponse.BodyHandlers.ofString());
                })
                .whenComplete((response, throwable) -> {
                    pendingClaims.remove(pendingKey);
                    if (throwable != null || response == null || !player.isOnline()) {
                        return;
                    }
                    MinecraftServer.getSchedulerManager().buildTask(() -> {
                        if (player.isOnline()) {
                            votePartyService.castVote(player);
                        }
                    }).delay(TaskSchedule.tick(1)).schedule();
                });
    }
}
