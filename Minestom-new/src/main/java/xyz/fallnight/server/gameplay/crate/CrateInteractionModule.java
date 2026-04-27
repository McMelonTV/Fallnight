package xyz.fallnight.server.gameplay.crate;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.domain.crate.CrateDefinition;
import xyz.fallnight.server.domain.crate.CrateReward;
import xyz.fallnight.server.domain.crate.WeightedCrateReward;
import xyz.fallnight.server.domain.user.UserProfile;
import xyz.fallnight.server.service.CrateService;
import xyz.fallnight.server.service.DefaultWorldService;
import xyz.fallnight.server.service.InventoryOpeners;
import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.PlayerProfileService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

public final class CrateInteractionModule {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat CHANCE = new DecimalFormat("0.##");
    private static final List<CrateLocation> LOCATIONS = List.of(
            new CrateLocation(new Pos(1571, 25, 623), "iron"),
            new CrateLocation(new Pos(1572, 25, 620), "gold"),
            new CrateLocation(new Pos(1574, 25, 618), "diamond"),
            new CrateLocation(new Pos(1577, 25, 617), "emerald"),
            new CrateLocation(new Pos(1580, 25, 618), "netherrite"),
            new CrateLocation(new Pos(1582, 25, 620), "vote"),
            new CrateLocation(new Pos(1583, 25, 623), "koth")
    );

    private final CrateService crateService;
    private final PlayerProfileService profileService;
    private final DefaultWorldService defaultWorldService;
    private final LegacyCustomItemService customItemService = new LegacyCustomItemService();
    private final EventNode<Event> eventNode = EventNode.all("crate-interaction");

    public CrateInteractionModule(CrateService crateService, PlayerProfileService profileService, DefaultWorldService defaultWorldService) {
        this.crateService = crateService;
        this.profileService = profileService;
        this.defaultWorldService = defaultWorldService;
    }

    private static String formatReward(CrateReward reward) {
        if (reward == null) {
            return "nothing";
        }
        if (reward.description() != null && !reward.description().isBlank()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*) x(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(reward.description());
            if (matcher.matches()) {
                return "§r§b" + matcher.group(2) + "§r§bx " + matcher.group(1);
            }
            return reward.description();
        }
        if (reward.money() > 0D && reward.prestigePoints() > 0L) {
            return reward.money() + " and " + reward.prestigePoints() + " PP";
        }
        if (reward.money() > 0D) {
            return "$" + (Math.rint(reward.money()) == reward.money() ? Long.toString((long) reward.money()) : Double.toString(reward.money()));
        }
        return reward.prestigePoints() + " PP";
    }

    private static Material fallbackMaterial(CrateReward reward) {
        if (reward.money() > 0D) {
            return Material.GOLD_INGOT;
        }
        if (reward.prestigePoints() > 0L) {
            return Material.NETHER_STAR;
        }
        if (reward.forgedBookCount() > 0) {
            return Material.ENCHANTED_BOOK;
        }
        if (reward.randomTagCount() > 0 || (reward.grantedTag() != null && !reward.grantedTag().isBlank())) {
            return Material.NAME_TAG;
        }
        return Material.CHEST;
    }

    private static InventoryType inventoryType(int count) {
        return count <= 5 ? InventoryType.HOPPER : count <= 27 ? InventoryType.CHEST_3_ROW : InventoryType.CHEST_6_ROW;
    }

    private static int crateNumericId(String crateId) {
        return switch (crateId.toLowerCase(Locale.ROOT)) {
            case "iron" -> 10;
            case "gold" -> 20;
            case "diamond" -> 30;
            case "emerald" -> 40;
            case "netherrite" -> 50;
            case "vote" -> 99;
            case "koth" -> 120;
            default -> -1;
        };
    }

    private static String crateIdForKey(int crateNumericId) {
        return switch (crateNumericId) {
            case 10 -> "iron";
            case 20 -> "gold";
            case 30 -> "diamond";
            case 40 -> "emerald";
            case 50 -> "netherrite";
            case 99 -> "vote";
            case 120 -> "koth";
            default -> null;
        };
    }

    private static boolean isTagReward(CrateReward reward) {
        return reward != null && (reward.randomTagCount() > 0 || (reward.grantedTag() != null && !reward.grantedTag().isBlank()));
    }

    public void register() {
        eventNode.addListener(PlayerBlockInteractEvent.class, this::onInteract);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
    }

    private void onInteract(PlayerBlockInteractEvent event) {
        CrateLocation pedestal = find(event.getPlayer(), event.getBlockPosition().x(), event.getBlockPosition().y(), event.getBlockPosition().z());
        if (pedestal == null) {
            return;
        }

        event.setBlockingItemUse(true);
        event.setCancelled(true);

        var player = event.getPlayer();
        UserProfile profile = profileService.getOrCreate(player);

        ItemStack held = player.getItemInMainHand();
        if (held == null || held.isAir() || customItemService.customItemId(held) != 20) {
            player.sendMessage(CommandMessages.error("You require a key to open a crate."));
            return;
        }

        var customData = held.get(net.minestom.server.component.DataComponents.CUSTOM_DATA);
        Integer keyType = customData == null ? null : customData.getTag(net.minestom.server.tag.Tag.Integer("cratekey"));
        if (keyType == null) {
            player.sendMessage(CommandMessages.error("You require a key to open a crate."));
            return;
        }

        String crateId = crateIdForKey(keyType.intValue());
        if (crateId == null) {
            player.sendMessage(CommandMessages.error("You require a key to open a crate."));
            return;
        }

        if (player.isSneaking()) {
            previewCrate(player, crateId);
            return;
        }

        int nextAmount = held.amount() - 1;
        player.setItemInMainHand(nextAmount <= 0 ? ItemStack.AIR : held.withAmount(nextAmount));
        CrateService.OpenResult result = crateService.openWithExternalKey(crateId, player, profile);
        profileService.save(profile);
        String rewardText = formatRewardDisplay(result.reward());
        if (!isTagReward(result.reward())) {
            player.sendMessage(CommandMessages.success("You received §b" + rewardText + "§r§7 from the crate."));
        }
        playAnimation(player, pedestal.pos(), rewardText);
    }

    private void previewCrate(net.minestom.server.entity.Player player, String crateId) {
        CrateDefinition crate = crateService.findCrate(crateId).orElse(null);
        if (crate == null) {
            player.sendMessage(CommandMessages.error("That crate could not be found."));
            return;
        }
        Inventory inventory = new Inventory(inventoryType(crate.rewards().size()), net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§l§o§b" + crate.displayName() + "§r§8 crate items"));
        inventory.eventNode().addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        double total = crate.rewards().stream().mapToInt(WeightedCrateReward::weight).sum();
        for (int i = 0; i < crate.rewards().size(); i++) {
            WeightedCrateReward weighted = crate.rewards().get(i);
            inventory.setItemStack(i, displayReward(weighted, total));
        }
        InventoryOpeners.replace(player, inventory);
    }

    private CrateLocation find(net.minestom.server.entity.Player player, double x, double y, double z) {
        if (player.getInstance() != defaultWorldService.currentWorld().instance()) {
            return null;
        }
        for (CrateLocation location : LOCATIONS) {
            if (location.pos().blockX() == (int) Math.floor(x)
                    && location.pos().blockY() == (int) Math.floor(y)
                    && location.pos().blockZ() == (int) Math.floor(z)) {
                return location;
            }
        }
        return null;
    }

    private String formatRewardDisplay(CrateReward reward) {
        if (reward == null) {
            return "nothing";
        }
        if (reward.randomTagCount() > 0) {
            return reward.randomTagCount() + "x " + reward.description();
        }
        if (reward.grantedTag() != null && !reward.grantedTag().isBlank()) {
            return "1x " + reward.description();
        }
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            return customItemService.createFromCustomId(reward.customItemId())
                    .map(this::formatItemStackReward)
                    .orElse(formatReward(reward));
        }
        return "1x " + formatReward(reward);
    }

    private String formatItemStackReward(ItemStack itemStack) {
        Component name = itemStack.get(DataComponents.CUSTOM_NAME);
        String displayName = name != null ? LEGACY.serialize(name).replace("§r", "") : itemStack.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return itemStack.amount() + "x " + displayName;
    }

    private ItemStack displayReward(WeightedCrateReward weighted, double totalWeight) {
        CrateReward reward = weighted.reward();
        ItemStack base = ItemStack.AIR;
        if (reward.customItemId() != null && !reward.customItemId().isBlank()) {
            base = customItemService.createFromCustomId(reward.customItemId()).orElse(ItemStack.AIR);
        }
        if (base.isAir()) {
            base = ItemStack.of(fallbackMaterial(reward), reward.randomTagCount() > 0 ? reward.randomTagCount() : 1);
        }
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(LEGACY.deserialize("§r§7Drop chance: §b" + CHANCE.format((weighted.weight() / totalWeight) * 100D) + "%"));
        return base.with(DataComponents.CUSTOM_NAME, normalizeItemText(LEGACY.deserialize("§r" + reward.description()))).with(DataComponents.LORE, normalizeItemLore(lore));
    }

    private static Component normalizeItemText(Component component) {
        if (component == null) {
            return Component.empty()
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return component.style(component.style()
                .colorIfAbsent(NamedTextColor.WHITE)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    private static List<Component> normalizeItemLore(List<Component> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                .map(CrateInteractionModule::normalizeItemText)
                .toList();
    }

    private void playAnimation(net.minestom.server.entity.Player player, Pos cratePos, String rewardText) {
        player.playSound(Sound.sound(Key.key("minecraft:block.chest.open"), Sound.Source.MASTER, 1f, 1f));
        Entity hologram = new Entity(EntityType.ARMOR_STAND);
        hologram.setNoGravity(true);
        hologram.setInvisible(true);
        hologram.setCustomNameVisible(true);
        hologram.setCustomName(LEGACY.deserialize("§r§b" + rewardText));
        if (hologram.getEntityMeta() instanceof ArmorStandMeta armorStandMeta) {
            armorStandMeta.setMarker(true);
            armorStandMeta.setSmall(true);
            armorStandMeta.setHasNoBasePlate(true);
        }
        hologram.setInstance(player.getInstance(), cratePos.add(0.5, 1.5, 0.5)).join();
        MinecraftServer.getSchedulerManager().buildTask(() ->
                player.playSound(Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.MASTER, 1f, 1.8f))
        ).delay(TaskSchedule.tick(1)).schedule();
        MinecraftServer.getSchedulerManager().buildTask(() ->
                player.playSound(Sound.sound(Key.key("minecraft:block.chest.close"), Sound.Source.MASTER, 1f, 1f))
        ).delay(TaskSchedule.tick(40)).schedule();
        MinecraftServer.getSchedulerManager().buildTask(hologram::remove).delay(TaskSchedule.tick(60)).schedule();
    }

    private record CrateLocation(Pos pos, String crateId) {
    }
}
