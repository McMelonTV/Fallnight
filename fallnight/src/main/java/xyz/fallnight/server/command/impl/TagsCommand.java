package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.domain.tag.TagDefinition;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.PlayerProfileService;
import xyz.fallnight.server.service.TagService;
import xyz.fallnight.server.service.TagsMenuService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

public final class TagsCommand extends FallnightCommand {
    private static final Map<String, String> TAG_COLORS = createTagColors();

    private final PlayerProfileService profileService;
    private final PermissionService permissionService;
    private final TagService tagService;
    private final TagsMenuService tagsMenuService;

    public TagsCommand(PermissionService permissionService, PlayerProfileService profileService, TagService tagService, TagsMenuService tagsMenuService) {
        super("tags", permissionService, "tag", "nametag", "title");
        this.permissionService = permissionService;
        this.profileService = profileService;
        this.tagService = tagService;
        this.tagsMenuService = tagsMenuService;

        var applyLiteral = ArgumentType.Literal("apply");
        var clearLiteral = ArgumentType.Literal("clear");
        var colorLiteral = ArgumentType.Literal("color");
        var idArgument = ArgumentType.Word("id");
        var colorNameArgument = ArgumentType.StringArray("colorName");

        setDefaultExecutor((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            tagsMenuService.open((Player) sender);
        });

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            String id = context.get(idArgument);
            TagService.ApplyResult result = tagService.setAppliedTag(profile, id);
            switch (result) {
                case SUCCESS -> {
                    profile.getExtraData().put("tagColor", "");
                    profileService.save(profile);
                    TagDefinition definition = tagService.findById(id).orElse(null);
                    String resolvedTag = definition == null ? id : definition.tag();
                    sender.sendMessage(CommandMessages.success("You have changed your tag to §r" + resolvedTag + "§r§7."));
                }
                case TAG_NOT_FOUND -> sender.sendMessage(CommandMessages.error("Tag '" + id + "' was not found."));
                case NOT_UNLOCKED -> sender.sendMessage(CommandMessages.error("You have not unlocked tag '" + id + "'."));
            }
        }, applyLiteral, idArgument);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }

            Player player = (Player) sender;
            UserProfile profile = profileService.getOrCreate(player);
            boolean changed = tagService.clearAppliedTag(profile);
            profile.getExtraData().put("tagColor", "");
            profileService.save(profile);
            if (changed) {
                sender.sendMessage(CommandMessages.success("You have removed your tag."));
                return;
            }
            sender.sendMessage(CommandMessages.info("You do not have an applied tag."));
        }, clearLiteral);

        addSyntax((sender, context) -> {
            if (!ensurePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            if (!permissionService.hasPermission(player, "fallnight.tag.colored")) {
                sender.sendMessage(CommandMessages.error("You don't have permission to a custom color."));
                return;
            }
            UserProfile profile = profileService.getOrCreate(player);
            String raw = String.join(" ", context.get(colorNameArgument)).trim();
            if (raw.equalsIgnoreCase("clear")) {
                profile.getExtraData().put("tagColor", "");
                profileService.save(profile);
                sender.sendMessage(CommandMessages.success("You removed your tag color."));
                return;
            }
            String code = TAG_COLORS.get(raw.toLowerCase(Locale.ROOT));
            if (code == null) {
                sender.sendMessage(CommandMessages.error("You have selected an invalid color."));
                return;
            }
            profile.getExtraData().put("tagColor", code);
            profileService.save(profile);
            sender.sendMessage(CommandMessages.success("You changed your tag color to §r§" + code + raw + "§r§7."));
        }, colorLiteral, colorNameArgument);
    }

    @Override
    public String permission() {
        return "fallnight.command.tags";
    }

    @Override
    public String summary() {
        return "select a tag";
    }

    @Override
    public String usage() {
        return "/tags";
    }

    private void sendTagOverview(Player player, UserProfile profile) {
        Object applied = profile.getExtraData().get("appliedTag");
        String current = applied instanceof String value && !value.isBlank() ? value : "none";
        player.sendMessage(CommandMessages.info("Current tag: " + current));
        Object color = profile.getExtraData().get("tagColor");
        if (color instanceof String value && !value.isBlank()) {
            player.sendMessage(CommandMessages.info("Current tag color: " + colorName(value)));
        }

        List<TagDefinition> unlocked = tagService.listUnlockedTags(profile);
        if (unlocked.isEmpty()) {
            player.sendMessage(CommandMessages.info("You have no unlocked tags."));
            return;
        }

        player.sendMessage(CommandMessages.info("Unlocked tags (" + unlocked.size() + "):"));
        for (TagDefinition definition : unlocked) {
            player.sendMessage(CommandMessages.info("- " + definition.id() + " -> " + definition.tag()));
        }
    }

    private static Map<String, String> createTagColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("dark red", "4");
        colors.put("red", "c");
        colors.put("gold", "6");
        colors.put("yellow", "e");
        colors.put("dark green", "2");
        colors.put("green", "a");
        colors.put("aqua", "b");
        colors.put("dark aqua", "3");
        colors.put("dark blue", "1");
        colors.put("blue", "9");
        colors.put("light purple", "d");
        colors.put("dark purple", "5");
        colors.put("white", "f");
        colors.put("gray", "7");
        colors.put("dark gray", "8");
        colors.put("black", "0");
        return colors;
    }

    private static String colorName(String code) {
        for (Map.Entry<String, String> entry : TAG_COLORS.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(code)) {
                return "§" + code + entry.getKey() + "§r";
            }
        }
        return "§" + code + code + "§r";
    }
}
