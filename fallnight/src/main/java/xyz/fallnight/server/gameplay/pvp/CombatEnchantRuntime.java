package xyz.fallnight.server.gameplay.pvp;

import xyz.fallnight.server.service.LegacyCustomItemService;
import xyz.fallnight.server.service.FallnightCustomEnchantRegistry;
import java.util.Map;
import java.util.Random;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.timer.TaskSchedule;

public final class CombatEnchantRuntime {
    private final LegacyCustomItemService itemService;
    private final Random random;

    public CombatEnchantRuntime(LegacyCustomItemService itemService) {
        this(itemService, new Random());
    }

    public CombatEnchantRuntime(LegacyCustomItemService itemService, Random random) {
        this.itemService = itemService;
        this.random = random;
    }

    public void onAttack(Player attacker, Player victim, Damage damage, ItemStack weapon) {
        if (damage == null || weapon == null || weapon.isAir()) {
            return;
        }
        Map<String, Integer> enchants = itemService.customEnchants(weapon);
        if (enchants.isEmpty()) {
            return;
        }

        float base = damage.getAmount();
        Integer damageLevel = enchants.get(FallnightCustomEnchantRegistry.DAMAGE);
        if (damageLevel != null) {
            damage.setAmount((float) (damage.getAmount() + (base / 25D * damageLevel)));
        }

        Integer deathbringerLevel = enchants.get(FallnightCustomEnchantRegistry.DEATHBRINGER);
        if (deathbringerLevel != null && roll(10 + 3 * deathbringerLevel)) {
            double bonusFactor = (35 + random.nextInt(31)) / 100D;
            damage.setAmount((float) (damage.getAmount() + (base * bonusFactor)));
        }

        Integer criticalLevel = enchants.get(FallnightCustomEnchantRegistry.CRITICAL);
        if (criticalLevel != null && !hasVanillaCriticalContext(attacker) && !isHeadInWater(attacker) && rollOneBasedLessThan(5 + 3 * criticalLevel, 75)) {
            damage.setAmount(damage.getAmount() + (damage.getAmount() / 2F));
        }

        Integer freezeLevel = enchants.get(FallnightCustomEnchantRegistry.FREEZE);
        if (freezeLevel != null && freezeLevel > 0 && roll(5 + 2 * Math.max(0, freezeLevel - 1))) {
            applyEffect(victim, "minecraft:slowness", 1, 60);
        }

        Integer aerialLevel = enchants.get(FallnightCustomEnchantRegistry.AERIAL);
        if (aerialLevel != null && !victim.isOnGround()) {
            damage.setAmount((float) (damage.getAmount() + (base / 40D * aerialLevel)));
        }

        Integer fireAspectLevel = enchants.get(FallnightCustomEnchantRegistry.FIRE_ASPECT);
        if (fireAspectLevel != null && roll(13 + 7 * Math.max(0, fireAspectLevel - 1))) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 5 * 20));
        }

        Integer poisonLevel = enchants.get(FallnightCustomEnchantRegistry.POISON);
        if (poisonLevel != null && roll(8 + poisonLevel)) {
            victim.addEffect(new Potion(PotionEffect.fromKey("minecraft:poison"), 0, 100));
        }

        Integer lightningLevel = enchants.get(FallnightCustomEnchantRegistry.LIGHTNING);
        if (lightningLevel != null && roll(lightningLevel)) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 6 * 20));
            float lightningDmg = lightningDamage(victim);
            float newHealth = Math.max(0, victim.getHealth() - lightningDmg);
            victim.setHealth(newHealth);
            if (newHealth <= 0) {
                victim.kill();
            }
            spawnLightningEffect(victim);
        }

        DefenseModifiers defense = collectDefenseModifiers(victim);
        if (defense.tankLevel() > 0) {
            damage.setAmount((float) Math.max(0D, damage.getAmount() - ((base / 20D) * defense.tankLevel())));
        }
        if (defense.toughnessLevel() > 0 && weapon.material().name().toLowerCase().endsWith("sword")) {
            damage.setAmount((float) Math.max(0D, damage.getAmount() - ((base / 50D) * defense.toughnessLevel())));
        }
        if (defense.fireThornsLevel() > 0 && roll(8 + 3 * Math.max(0, defense.fireThornsLevel() - 1))) {
            attacker.setFireTicks(Math.max(attacker.getFireTicks(), 4 * 20));
        }
        if (defense.iceThornsLevel() > 0 && roll(4 + Math.max(0, defense.iceThornsLevel() - 1))) {
            applyEffect(attacker, "minecraft:slowness", 1, 60);
        }
        if (defense.blessingLevel() > 0 && roll(10 + defense.blessingLevel() * 3)) {
            clearNegativeEffects(victim);
        }
    }

    public void onVictimDamaged(Player victim, Damage damage) {
        // PHP Heatshield's condition always returns before modifying fire damage.
    }

    public void onKill(Player attacker, ItemStack weapon) {
        if (attacker == null || weapon == null || weapon.isAir()) {
            return;
        }
        Integer level = itemService.customEnchants(weapon).get(FallnightCustomEnchantRegistry.LIFESTEAL);
        if (level == null || level <= 0) {
            return;
        }
        float next = Math.max((float) attacker.getAttributeValue(Attribute.MAX_HEALTH), attacker.getHealth() + level);
        attacker.setHealth(next);
    }

    public void onDeath(Player victim) {
        if (victim == null || victim.getInstance() == null) {
            return;
        }
        Map<String, Integer> chestEnchants = itemService.customEnchants(victim.getInventory().getEquipment(EquipmentSlot.CHESTPLATE, victim.getHeldSlot()));
        if (chestEnchants.getOrDefault(FallnightCustomEnchantRegistry.EXPLOSIVE, 0) <= 0) {
            return;
        }
        victim.getInstance().explode((float) victim.getPosition().x(), (float) victim.getPosition().y(), (float) victim.getPosition().z(), 8F);
    }

    private boolean roll(int chancePercent) {
        return roll(chancePercent, 100);
    }

    private boolean roll(int chancePercent, int ceiling) {
        return random.nextInt(Math.max(1, ceiling) + 1) < Math.max(0, chancePercent);
    }

    private boolean rollOneBasedLessThan(int threshold, int ceiling) {
        return (random.nextInt(Math.max(1, ceiling)) + 1) < Math.max(0, threshold);
    }

    private static void applyEffect(Player player, String effectKey, int amplifier, int durationTicks) {
        PotionEffect effect = PotionEffect.fromKey(effectKey);
        if (player != null && effect != null) {
            player.addEffect(new Potion(effect, amplifier, durationTicks));
        }
    }

    private float lightningDamage(Player victim) {
        int damage = 6;
        ItemStack helmet = victim.getInventory().getEquipment(EquipmentSlot.HELMET, victim.getHeldSlot());
        Integer insulator = itemService.customEnchants(helmet).get(FallnightCustomEnchantRegistry.INSULATOR);
        if (insulator != null) {
            damage = switch (insulator) {
                case 1 -> 4;
                case 2 -> 3;
                default -> 2;
            };
        }
        return damage;
    }

    private void spawnLightningEffect(Player victim) {
        if (victim == null || victim.getInstance() == null) {
            return;
        }
        Entity lightning = new Entity(EntityType.LIGHTNING_BOLT);
        lightning.setInstance(victim.getInstance(), victim.getPosition());
        MinecraftServer.getSchedulerManager().buildTask(lightning::remove).delay(TaskSchedule.tick(1)).schedule();
    }

    private DefenseModifiers collectDefenseModifiers(Player victim) {
        int tank = 0;
        int toughness = 0;
        int blessing = 0;
        int fireThorns = 0;
        int iceThorns = 0;
        Map<String, Integer> helmetEnchants = itemService.customEnchants(victim.getInventory().getEquipment(EquipmentSlot.HELMET, victim.getHeldSlot()));
        Map<String, Integer> chestEnchants = itemService.customEnchants(victim.getInventory().getEquipment(EquipmentSlot.CHESTPLATE, victim.getHeldSlot()));
        tank = chestEnchants.getOrDefault(FallnightCustomEnchantRegistry.TANK, 0);
        toughness = chestEnchants.getOrDefault(FallnightCustomEnchantRegistry.TOUGHNESS, 0);
        blessing = helmetEnchants.getOrDefault(FallnightCustomEnchantRegistry.BLESSING, 0);
        fireThorns = chestEnchants.getOrDefault(FallnightCustomEnchantRegistry.FIRE_THORNS, 0);
        iceThorns = chestEnchants.getOrDefault(FallnightCustomEnchantRegistry.ICE_THORNS, 0);
        return new DefenseModifiers(tank, toughness, blessing, fireThorns, iceThorns);
    }

    private static final String[] NEGATIVE_EFFECTS = {
        "minecraft:poison", "minecraft:slowness", "minecraft:weakness",
        "minecraft:blindness", "minecraft:mining_fatigue", "minecraft:wither",
        "minecraft:nausea", "minecraft:hunger",
        "minecraft:levitation", "minecraft:darkness"
    };

    private void clearNegativeEffects(Player victim) {
        for (String key : NEGATIVE_EFFECTS) {
            PotionEffect effect = PotionEffect.fromKey(key);
            if (effect != null && victim.hasEffect(effect)) {
                victim.removeEffect(effect);
            }
        }
    }

    private boolean hasVanillaCriticalContext(Player attacker) {
        PotionEffect blindness = PotionEffect.fromKey("minecraft:blindness");
        boolean blind = blindness != null && attacker.hasEffect(blindness);
        return !attacker.isSprinting() && !attacker.isFlying() && !attacker.isOnGround() && !blind;
    }

    private boolean isHeadInWater(Player player) {
        if (player.getInstance() == null) {
            return false;
        }
        var block = player.getInstance().getBlock(player.getPosition().blockX(), (int) Math.floor(player.getPosition().y() + player.getEyeHeight()), player.getPosition().blockZ());
        return block.isLiquid();
    }

    private record DefenseModifiers(int tankLevel, int toughnessLevel, int blessingLevel, int fireThornsLevel, int iceThornsLevel) {
    }
}
