package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.tag.TagDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.TagService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class GiveTagCommand extends FallnightCommand {
    private final PlayerProfileService profileService;
    private final TagService tagService;

    public GiveTagCommand(PermissionService permissionService, PlayerProfileService profileService, TagService tagService) {
        super("givetag", permissionService);
        this.profileService = profileService;
        this.tagService = tagService;

        var playerArgument = ArgumentType.Word("player");
        var idArgument = ArgumentType.Word("id");

        setDefaultExecutor((sender, context) -> sender.sendMessage(CommandMessages.error("Please enter a target to give a tag to.")));

        addSyntax((sender, context) -> sender.sendMessage(CommandMessages.error("Please enter a tag to give.")), playerArgument);

        addSyntax((sender, context) -> {
            String targetName = context.get(playerArgument);
            String id = context.get(idArgument);

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            if (target == null) {
                sender.sendMessage(CommandMessages.error("That player could not be found."));
                return;
            }

            TagDefinition definition = tagService.findById(id).orElse(null);
            if (definition == null) {
                sender.sendMessage(CommandMessages.error("That tag could not be found."));
                return;
            }

            UserProfile profile = profileService.getOrCreate(target);
            TagService.UnlockResult result = tagService.unlockTag(profile, definition.id());
            switch (result) {
                case SUCCESS -> {
                    profileService.save(profile);
                    sender.sendMessage(CommandMessages.success("You have given §b" + target.getUsername() + "§r§7 the §r" + definition.tag() + "§r§7 tag."));
                    target.sendMessage(CommandMessages.success("You have received the §r" + definition.tag() + "§r§7 tag with " + rarityColor(definition.rarity()) + rarityName(definition.rarity()) + "§r§7 rarity."));
                }
                case ALREADY_UNLOCKED -> {
                    long prestige = definition.rarity() * 50L;
                    profile.addPrestigePoints(prestige);
                    profileService.save(profile);
                    sender.sendMessage(CommandMessages.success("You have given §b" + target.getUsername() + "§r§7 the §r" + definition.tag() + "§r§7 tag."));
                    target.sendMessage(CommandMessages.success("You have received a duplicate §r §r" + definition.tag() + "§7 and received§b " + prestige + " §7prestige points."));
                }
                case TAG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("That tag could not be found."));
            }
        }, playerArgument, idArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.givetag";
    }

    @Override
    public String summary() {
        return "give someone a tag";
    }

    @Override
    public String usage() {
        return "/givetag <player> <tag>";
    }

    private static String rarityColor(int rarity) {
        return switch (rarity) {
            case 0, 1 -> "§7";
            case 2 -> "§a";
            case 3 -> "§9";
            case 4 -> "§5";
            case 5 -> "§b";
            default -> "§7";
        };
    }

    private static String rarityName(int rarity) {
        return switch (rarity) {
            case 0, 1 -> "common";
            case 2 -> "uncommon";
            case 3 -> "rare";
            case 4 -> "very rare";
            case 5 -> "legendary";
            default -> "common";
        };
    }
}
