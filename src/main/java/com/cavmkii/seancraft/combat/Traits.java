package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.parts.Family;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Weapon family traits the movesets don't already cover (design doc 9.4, after Spartan Weaponry). Reach, sweep and
 * two-handed come from each family's Better Combat file; these are the rest: armor piercing, shield breach, quick
 * strike and the backstab bonus.
 */
public final class Traits {
    /** Armor piercing: this share of armor is ignored, on top of pierce's own 25% (9.3.1), up to 75% in all. */
    static final float ARMOR_PIERCING = 0.25f;
    static final float MAX_PENETRATION = 0.75f;
    static final float SHIELD_BREACH_CHANCE = 0.35f;
    static final float BACKSTAB = 1.5f;
    /** Quick strike: a dagger leaves the target open again after this many ticks (0.1 s) instead of vanilla's 10 (9.6). */
    static final int QUICK_STRIKE_TICKS = 2;

    private Traits() {}

    static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (family(source) == Family.DAGGER || family(source) == Family.SWORD && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp
                    && com.cavmkii.seancraft.rpg.Rpg.stats(sp).has(com.cavmkii.seancraft.rpg.Stat.QUICK_STRIKES)) {
                // Hurt time runs from 20 and blocks hits while above 10; a dagger leaves only a short window.
                entity.invulnerableTime = Math.min(entity.invulnerableTime, 10 + QUICK_STRIKE_TICKS);
            }
            // An Arcane wrap (9.4.3): each melee hit restores 2 mana.
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && source.getDirectEntity() == sp
                    && "arcane".equals(com.cavmkii.seancraft.rpg.Scaling.infusionName(sp.getMainHandItem()))) {
                com.cavmkii.seancraft.rpg.PlayerData d = com.cavmkii.seancraft.rpg.Rpg.data(sp);
                d.mana = (float) Math.min(com.cavmkii.seancraft.rpg.Rpg.stats(sp).maxMana(), d.mana + 2);
            }
        });
    }

    /** The family of the weapon behind a melee hit, or null. */
    static Family family(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
            return null;
        }
        ItemStack weapon = attacker.getMainHandItem();
        Family.Stock stock = Family.stock(weapon);
        return stock != null ? stock.family() : null;
    }

    /** Extra share of armor ignored by this hit's weapon. */
    static float armorPiercing(Family family) {
        return family == Family.WARHAMMER || family == Family.SPEAR ? ARMOR_PIERCING : 0;
    }

    /** The armor a physical hit counts, after pierce's share, the weapon's armor piercing and Breach. */
    static float effectiveArmor(float armor, boolean pierce, Family family, float breach) {
        float ignored = Math.max(0, Math.min(MAX_PENETRATION, (pierce ? Damage.PIERCE_IGNORES : 0) + armorPiercing(family) + breach));
        return armor * (1 - ignored);
    }

    /**
     * Before the hit lands: daggers do half again from behind, and maces, axes and warhammers can knock a raised
     * shield out of use, so the blow goes through. Returns the damage to deal.
     */
    static float beforeHit(LivingEntity victim, DamageSource source, float amount) {
        Family family = family(source);
        if (family == null) {
            return amount;
        }
        LivingEntity attacker = (LivingEntity) source.getEntity();
        if (family == Family.DAGGER) {
            Vec3 facing = victim.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1, 0, 1).normalize();
            if (facing.dot(toAttacker) < -0.5) {
                amount *= BACKSTAB;
                victim.level().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1.3f);
            }
        }
        boolean breach = family == Family.MACE || family == Family.AXE || family == Family.WARHAMMER;
        if (breach && victim instanceof Player player && player.isBlocking() && attacker.getRandom().nextFloat() < SHIELD_BREACH_CHANCE) {
            player.disableShield();
        }
        return amount;
    }
}
