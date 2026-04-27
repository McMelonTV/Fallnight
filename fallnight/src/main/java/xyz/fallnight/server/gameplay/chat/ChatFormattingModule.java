package xyz.fallnight.server.gameplay.chat;

import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.gang.GangMemberRole;
import xyz.fallnight.server.domain.mine.MineRank;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.GangService;
import xyz.fallnight.server.service.MineRankService;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.RankService;
import xyz.fallnight.server.util.LegacyTextFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;

public final class ChatFormattingModule {
    private final PermissionService permissionService;
    private final PlayerProfileService profileService;
    private final GangService gangService;
    private final MineRankService mineRankService;
    private final RankService rankService;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

    public ChatFormattingModule(
        PermissionService permissionService,
        PlayerProfileService profileService,
        GangService gangService,
        MineRankService mineRankService,
        RankService rankService
    ) {
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.gangService = gangService;
        this.mineRankService = mineRankService;
        this.rankService = rankService;
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerChatEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            UserProfile profile = profileService.getOrCreate(event.getPlayer());
            if (!readBoolean(profile, "seenRules", false)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(serializer.deserialize("§c§l>§r§7 You cannot chat without having read the rules! Do §c/rules §r§7to read them and to gain access to chat."));
                return;
            }

            String prefix = rankService.displayPrefix(profile);
            String displayName = event.getPlayer().getUsername();
            Object nickname = profile.getExtraData().get("nickname");
            if (nickname instanceof String value && !value.isBlank()) {
                displayName = LegacyTextFormatter.normalize(value);
            }

            String tag = "";
            Object appliedTag = profile.getExtraData().get("appliedTag");
            if (appliedTag instanceof String value && !value.isBlank()) {
                Object tagColor = profile.getExtraData().get("tagColor");
                tag = (tagColor instanceof String color && !color.isBlank())
                    ? "§" + color + stripLegacyFormatting(value)
                    : LegacyTextFormatter.normalize(value);
            }

            List<Player> filtered = new ArrayList<>(event.getRecipients());
            filtered.removeIf(recipient -> shouldHideMessage(event.getPlayer().getUsername(), profileService.getOrCreate(recipient)));
            event.getRecipients().clear();
            event.getRecipients().addAll(filtered);

            String legacy = buildFormat(event.getPlayer(), profile, prefix, displayName, tag) + buildMessage(event.getPlayer(), event.getRawMessage());
            event.setFormattedMessage(serializer.deserialize(legacy));
        });
    }

    private String buildFormat(Player player, UserProfile profile, String prefix, String displayName, String tag) {
        StringBuilder format = new StringBuilder("• ");
        gangService.findGangForUser(player.getUsername()).ifPresent(gang ->
            format.append("§r§8[§c")
                .append(gangRoleSymbol(gang.roleOf(player.getUsername())))
                .append("§7")
                .append(gang.getName())
                .append("§r§8] ")
        );

        String mineTag = LegacyTextFormatter.normalize(mineRankService.find(profile.getMineRank()).map(MineRank::getTag).orElse("A"));
        format.append("§r§7")
            .append(toRoman(profile.getPrestige()))
            .append("§r§8§l-§r§7")
            .append(mineTag)
            .append(" §r§8|");

        if (!tag.isBlank()) {
            format.append(" §r").append(tag).append("§r§8 |");
        }

        format.append(" §r")
            .append(LegacyTextFormatter.normalize(prefix))
            .append(" §r§7")
            .append(displayName)
            .append("§r§8:§r §f");
        return format.toString();
    }

    private String buildMessage(Player player, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage;
        if (permissionService.hasPermission(player, "fallnight.chat.colored")) {
            return LegacyTextFormatter.normalize(message) + "§r⛏";
        }
        return message + "§r⛏";
    }

    private static String gangRoleSymbol(GangMemberRole role) {
        if (role == null) {
            return "-";
        }
        return switch (role) {
            case LEADER -> "**";
            case OFFICER -> "*";
            case RECRUIT -> "-";
            case MEMBER -> "";
        };
    }

    private static boolean shouldHideMessage(String senderUsername, UserProfile recipientProfile) {
        if (readBoolean(recipientProfile, "ignoreAll", false)) {
            return true;
        }
        return blockedNames(recipientProfile).contains(senderUsername.toLowerCase(Locale.ROOT));
    }

    private static boolean readBoolean(UserProfile profile, String key, boolean fallback) {
        Object value = profile.getExtraData().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on") || text.equalsIgnoreCase("yes")) {
                return true;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("off") || text.equalsIgnoreCase("no")) {
                return false;
            }
        }
        return fallback;
    }

    private static LinkedHashSet<String> blockedNames(UserProfile profile) {
        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        Object raw = profile.getExtraData().get("blockedPlayers");
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    String value = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
                    if (!value.isBlank()) {
                        blocked.add(value);
                    }
                }
            }
        }
        return blocked;
    }

    private static String stripLegacyFormatting(String input) {
        StringBuilder cleaned = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current == '§' || current == '&') && index + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(index + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || (code >= 'k' && code <= 'o') || code == 'r' || code == 'x') {
                    index++;
                    continue;
                }
            }
            cleaned.append(current);
        }
        return cleaned.toString();
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
}
