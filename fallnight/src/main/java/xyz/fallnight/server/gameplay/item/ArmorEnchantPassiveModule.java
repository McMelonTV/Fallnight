package xyz.fallnight.server.gameplay.item;

import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import java.util.Map;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

public final class ArmorEnchantPassiveModule {
    private final LegacyCustomItemService itemService;
    private Task task;

    public ArmorEnchantPassiveModule(LegacyCustomItemService itemService) {
        this.itemService = itemService;
    }

    public void register() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask(this::tick)
            .repeat(TaskSchedule.tick(30))
            .schedule();
    }

    public void unregister() {
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        PotionEffect speed = PotionEffect.fromKey("minecraft:speed");
        PotionEffect jump = PotionEffect.fromKey("minecraft:jump_boost");
        PotionEffect nightVision = PotionEffect.fromKey("minecraft:night_vision");
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            applyToPlayer(player, speed, jump, nightVision);
        }
    }

    public void applyToPlayer(Player player) {
        applyToPlayer(
            player,
            PotionEffect.fromKey("minecraft:speed"),
            PotionEffect.fromKey("minecraft:jump_boost"),
            PotionEffect.fromKey("minecraft:night_vision")
        );
    }

    private void applyToPlayer(Player player, PotionEffect speed, PotionEffect jump, PotionEffect nightVision) {
        int bonusHealth = 0;
        int runnerLevel = 0;
        int leaperLevel = 0;
        boolean nightVisionEnabled = false;
        boolean tankEnabled = false;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS}) {
            ItemStack armor = player.getInventory().getEquipment(slot, player.getHeldSlot());
            Map<String, Integer> enchants = itemService.customEnchants(armor);
            bonusHealth += enchants.getOrDefault(FallnightCustomEnchantRegistry.HEALTH, 0) * 2;
            runnerLevel = Math.max(runnerLevel, enchants.getOrDefault(FallnightCustomEnchantRegistry.RUNNER, 0));
            leaperLevel = Math.max(leaperLevel, enchants.getOrDefault(FallnightCustomEnchantRegistry.LEAPER, 0));
            nightVisionEnabled = nightVisionEnabled || enchants.containsKey(FallnightCustomEnchantRegistry.NIGHT_VISION);
            if (slot == EquipmentSlot.CHESTPLATE && enchants.containsKey(FallnightCustomEnchantRegistry.TANK)) {
                tankEnabled = true;
            }
        }

        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20D + bonusHealth);
        if (player.getHealth() > player.getAttributeValue(Attribute.MAX_HEALTH)) {
            player.setHealth((float) player.getAttributeValue(Attribute.MAX_HEALTH));
        }

        if (runnerLevel > 0 && speed != null) {
            player.addEffect(new Potion(speed, Math.max(0, runnerLevel - 1), 100));
        } else if (speed != null) {
            player.removeEffect(speed);
        }

        if (leaperLevel > 0 && jump != null) {
            player.addEffect(new Potion(jump, Math.max(0, leaperLevel - 1), 100));
        } else if (jump != null) {
            player.removeEffect(jump);
        }

        if (nightVisionEnabled && nightVision != null) {
            player.addEffect(new Potion(nightVision, 0, 400));
        } else if (nightVision != null) {
            player.removeEffect(nightVision);
        }

        PotionEffect slowness = PotionEffect.fromKey("minecraft:slowness");
        if (tankEnabled && slowness != null) {
            player.addEffect(new Potion(slowness, 2, 2147483647));
        } else if (!tankEnabled && slowness != null) {
            player.removeEffect(slowness);
        }
    }
}
