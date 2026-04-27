package xyz.fallnight.server.command;

import java.util.List;
import java.util.Set;

public final class LegacyCommandParityRegistry {
    private static final Set<String> LEGACY_COMMANDS = Set.copyOf(List.of(
            "setprestige", "setmine", "balance", "ranks", "spawn", "setbalance", "pay", "rankup", "nick", "fly",
            "baltop", "leaderboard", "customenchant", "vault", "size", "stats", "feed", "commandspy", "afk",
            "checkip", "plugins", "help", "world", "setspawn", "regenerate",
            "regenerateall", "admin", "achievements", "me", "mute", "sudo", "convertworld", "id", "renameworld", "mine",
            "mines", "disablemine", "enablemine", "clearmine", "teleport", "ban",
            "unban", "tempban", "plot", "block", "unblock", "blocklist", "globalmute", "plots", "fnitem",
            "enchant", "gang", "forge", "prestige", "fix", "performance", "enchantmentforge", "setmaxplots",
            "setmaxauc", "seasonreset", "enchantmentlist", "trash",
            "disenchant", "ignoreall", "banlist", "setblock", "spectator", "survival",
            "inventorysee", "clearlag", "nicklist", "alias", "broadcast", "softrestart", "note", "vanish",
            "superlist", "koth", "eval", "renameitem", "setmaxvaults", "addrank", "giverank", "removerank", "takerank", "listranks",
            "rankinfo", "playerranks", "pranks"
    ));

    private LegacyCommandParityRegistry() {
    }

    public static Set<String> allLegacyCommandNames() {
        return LEGACY_COMMANDS;
    }
}
