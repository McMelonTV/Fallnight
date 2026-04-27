package xyz.fallnight.server.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfoPagesService {
    private static final Pattern SEMVER_PATTERN = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\b");
    private static final List<String> DEFAULT_RULES = List.of(
            "§r§81 - §r§l§bBe respectful and kind\n§r§7Everyone should be respected, other players and staff members alike. Toxicity in chat is not acceptable and will be punished appropriately.",
            "§r§82 - §r§l§bListen to the staff\n§r§7The staff team is here to help. Feel free to ask them anything and they will offer their assistance! If you are instructed to stop doing something, listen to them. If you refuse it will result in a ban/kick.",
            "§r§83 - §r§l§bNo exploiting, or hacking\n§r§7Hacks, exploiting bug or glitches are prohibited on the server. If you come across someone hacking or abusing something that can be exploited, please let the staff know by doing /tell and their ign! \n§r§8(ANY KIND OF CLIENTS, INCLUDING NON-HACKED, ARE NOT ALLOWED! exploits could include: block glitching, item duping, very small or large custom skins in pvp, money boosting, ect.)",
            "§r§84 - §r§l§bNo griefing\n§r§7Destroying or stealing from someone else’s property is prohibited and will be punished.",
            "§r§85 - §r§l§bNo advertising\n§r§7Do not advertise on the server, this is not appreciated and can result in punishment. If you would like to apply for the YouTuber rank, make a ticket on the Fallnight Discord with the details of your channel!",
            "§r§86 - §r§l§bDo not beg for unbans/ban evade\n§r§7Do not ask the staff to unban a player (a friend etc.) or ban evade using an alt account. If you’re banned, you can however create a ticket on the Fallnight Discord. In the ticket, explain why you were banned and the staff team can assist you.",
            "§r§87 - §r§l§bDon't cause drama\n§r§7If you are to have an issue between you and someone else please keep it out of the server or report your issue to a staff member. You can report it in a ticket on the discord or DM a staff in game. That way it's a pleasant experience for all of us!",
            "§r§88 - §r§l§bNo scamming or stealing\n§r§7Trading items is allowed on the server, but do not scam or steal items. If you do, it will result in a punishment.",
            "§r§89 - §r§l§bProvide proof\n§r§7If another player has been hacking or wronged you in any way (scammed etc.), please create a ticket and provide proof. This way our staff team can take a look at it and give proper punishment.",
            "§r§810 - §r§l§bNo inappropriate comments, usernames or skins\n§r§7Inappropriate comments, usernames or skins are prohibited. If we deem one of these things inappropriate we will ask you to change or stop it. If you don't, it will result in punishment.",
            "§r§811 - §r§l§bMoney Boosting is prohibited\n§r§7Donating money to players or an alt account in anyway is not allowed. If you are caught taking part in this, you will be punished appropriately. This includes prestige boosting (also known as self boosting)."
    );
    private static final List<String> DEFAULT_NEWS = List.of(
            "§r§bFallnight Season 3 §8- §7Release 3.1.1",
            "§r§73.1.1",
            "§r§8- §r§7Shortened Spawn KOTH timer to 10 minutes.",
            "§r§8- §r§7Fixed `/p clear` replacing bedrock to dirt.",
            "§r§73.1.0",
            "§r§8- §r§7Increased all mine sizes (except for A) by a lot.",
            "§r§8- §r§7Added a spawn koth.",
            "§r§8- §r§7Nerfed dust rates for iron, redstone, and diamond blocks.",
            "§r§8- §r§7Added total online time to /stats.",
            "§r§8- §r§7Fixed a dupe with enchants.",
            "§r§8- §r§7Fixed a crash.",
            "§r§8- §r§7Fixed a typo in the rename item form.",
            "§r§73.0.3",
            "§r§8- §r§7Extraction is now mythic.",
            "§r§8- §r§7Auto repair is now very rare.",
            "§r§73.0.2",
            "§r§8- §r§7Added user's gang in /stats.",
            "§r§8- §r§7Added back the auction house confirm purchase form.",
            "§r§8- §r§7Fixed block lag when mining.",
            "§r§8- §r§7Fixed players not being able to run /ah in pvp.",
            "§r§8- §r§7Fixed XP dropping from blocks in plots.",
            "§r§8- §r§7Fixed two crashes.",
            "§r§73.0.1",
            "§r§8- §r§7Fixed missing permissions for warrior (/renameitem).",
            "§r§8- §r§7Made some performance improvements.",
            "§r§8- §r§7Attempted to fix the multiple votes per vote bug.",
            "§r§8- §r§7Mostly fixed chunk rendering.",
            "§r§8- §r§7Disabled sub command auto completion temporarily.",
            "§r§8------",
            "§r§8It seems like there is no logs of updates before this!"
    );
    private static final List<String> DEFAULT_GUIDE = List.of(
            "§r§0Welcome to the §bFallnight server§0!",
            "§r§0This book will help you learn the basics of the server and get started with mining. If you would still like more information, you can ask our staff and they will help you out. Please also report any issues or hackers to them.",
            "Guide contents:",
            "\n§0Page 1 > §bIntroduction\n§0Page 2 > §bContents\n§0Page 3 > §bThe basics\n§0Page 5 > §bRanking\n§0Page 6 > §bPrestige Pts\n§0Page 8 > §bThe forge\n§0Page 9 > §bEnchanting",
            "§r§l§bThe basics\n§r§0Fallnight is an OP prison server. The main goal of a prison server is to mine materials, get to higher mines and become the richest inmate. Let's start with some of the basic commands. The get your first loadout, type §b/kit§0 and select the starter kit. The starter kit is not that good,",
            "but it will get you started. If you have purchased a donator rank, you can get yourself a better kit that will get you started faster. To start mining, do §b/mine§0. This will teleport you to your current mine. You will need §b$5000§0 to rank up to mine B. Once you got this, do §b/rankup§0. Now repeat!",
            "§r§l§bRanking\n§r§0The server has a total of 26 mines (not including mine pvp), each one being better than the last. Once you've hit the last mine, Z you can still continue playing. You can §b/prestige§0: it will take away all your money and reset you to mine A but you'll get some prestige points.",
            "§r§l§bPrestige Points\n§r§0Prestige points, or in short §opp§r§0, can be obtained in several different ways. The main way is by prestiging. This gives you by far the most §opp§r§0 but is also the most time consuming way. You can also get it by unlocking achievements or killing players.",
            "Prestige points are mainly used for buying player upgrades. You can unlock more plots, vaults and auction slots in §b/shop§0. You are also able to buy donator ranks with your prestige points.",
            "§r§l§bThe forge\n§r§0The forge is the main way of acquiring new items in the server. You can use your resources (steeldust/stardust) to create new items. You can also repair your tools. Obsidian shards are the best resources for repairing. Repairing works like anvils.",
            "§r§l§bEnchanting\n§r§0You can create enchantments in §b/eforge§0. Select a rarity and you will get a random enchantment with that rarity. Forging enchants requires magicdust. You can combine enchants with the same level to increase the level by 1.",
            "You can do this on tools/armor/weapons too. To equip enchants on compatible items, drag the book onto it in your inventory. If you have an enchantment you don't like, you could auction it off."
    );

    private final Path dataRoot;

    public InfoPagesService(Path dataRoot) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
    }

    public static InfoPagesService fromDataRoot(Path dataRoot) {
        return new InfoPagesService(dataRoot);
    }

    public static List<String> defaultGuidePage() {
        return DEFAULT_GUIDE;
    }

    private static String versionFor(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public List<String> rulesPage() {
        return loadPage("rules.txt", DEFAULT_RULES);
    }

    public List<String> newsPage() {
        return loadPage("news.txt", DEFAULT_NEWS);
    }

    public String newsVersion() {
        return versionFor(newsPage());
    }

    public List<String> guidePage() {
        return loadPage("guide.txt", DEFAULT_GUIDE);
    }

    public String newsReleaseVersion() {
        List<String> lines = newsPage();
        if (lines.size() > 1) {
            String candidate = lines.get(1).trim();
            if (SEMVER_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher matcher = SEMVER_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "unknown";
    }

    private List<String> loadPage(String fileName, List<String> fallback) {
        Path file = dataRoot.resolve(fileName);
        if (!Files.exists(file)) {
            return fallback;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }

            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        if (normalized.isEmpty()) {
            return fallback;
        }
        return List.copyOf(normalized);
    }
}
