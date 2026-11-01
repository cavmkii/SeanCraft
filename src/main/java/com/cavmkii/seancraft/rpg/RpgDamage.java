package com.cavmkii.seancraft.rpg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.cavmkii.seancraft.parts.Parts;

/**
 * Damage rules for characters: weapon skills, node stats, crits, backstab, lifesteal, rage, blocking,
 * dodging, parries and Mind over Matter. {@link #modify} runs from a mixin at the start of
 * LivingEntity.hurt; {@link #allowDamage} from Fabric's ALLOW_DAMAGE event.
 */
public final class RpgDamage {
    /** Set while an ability deals its damage, so it isn't scaled again as a melee hit. */
    static boolean applyingSkill;

    /** Hunter's Mark: target id to game time the mark ends. */
    static final Map<UUID, Long> MARKED = new HashMap<>();

    private RpgDamage() {}

    /** Return false to cancel the hit entirely (dodge, parry). */
    static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (com.cavmkii.seancraft.combat.Combat.rollDodges(player, source)) {
            return false;
        }
        Stats stats = Rpg.stats(player);
        Rpg.Runtime rt = Rpg.runtime(player);
        long now = player.serverLevel().getGameTime();

        if (source.getEntity() instanceof LivingEntity attacker && attacker != player && now < rt.parryUntil) {
            rt.parryUntil = 0;
            float counter = (float) (player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 2);
            SkillEffects.damage(player, attacker, counter);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.4f);
            player.serverLevel().sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(1), attacker.getZ(), 15, 0.3, 0.3, 0.3, 0.2);
            return false;
        }
        boolean dodgeable = source.getEntity() != null && !source.is(DamageTypes.FALL);
        if (dodgeable && player.getRandom().nextDouble() * 100 < stats.get(Stat.DODGE)) {
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Dodged!"), true);
            return false;
        }
        if (source.is(DamageTypes.FALL) && stats.has(Stat.NO_FALL)) {
            return false;
        }
        return true;
    }

    /** Called at the head of LivingEntity.hurt on the server. Returns the adjusted amount. */
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || !(victim.level() instanceof ServerLevel level)) {
            return amount;
        }
        long now = level.getGameTime();

        // Marked targets take more from everyone.
        Long markEnd = MARKED.get(victim.getUUID());
        if (markEnd != null) {
            if (markEnd > now) {
                amount *= 1.3f;
            } else {
                MARKED.remove(victim.getUUID());
            }
        }

        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && !applyingSkill) {
            Stats stats = Rpg.stats(attacker);
            PlayerData data = Rpg.data(attacker);
            if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker) {
                amount = melee(attacker, victim, data, stats, amount) * com.cavmkii.seancraft.combat.Combat.swingMultiplier(attacker);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                int bows = data.skillLevel(Skill.BOWS);
                ItemStack bow = arrow.getWeaponItem();
                Attr[] scaling = bow != null ? Scaling.of(bow) : new Attr[] {Attr.AGI, null};
                amount = (float) (amount * (1 + Skill.BOWS.effectValue(bows) / 100.0 + stats.projectileDamagePct() / 100
                        + stats.scalingPct(scaling[0], scaling[1]) / 100 + crit(attacker, victim, stats)));
                if (bow != null && Parts.modular(bow)) {
                    amount *= Math.max(0.05f, 1 + Parts.totals(bow).damage);
                }
                Rpg.addSkillXp(attacker, Skill.BOWS, Math.min(amount, 15) * 0.5);
            }
        }

        if (victim instanceof ServerPlayer player) {
            amount = defend(player, source, amount, now);
        }
        return amount;
    }

    /** Rolls a crit: returns the extra damage multiplier (0 if it didn't crit), with a flash and a sound when it does. */
    private static double crit(ServerPlayer attacker, LivingEntity victim, Stats stats) {
        if (attacker.getRandom().nextDouble() * 100 >= stats.critChance()) {
            return 0;
        }
        ServerLevel level = (ServerLevel) attacker.level();
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 14, 0.3, 0.3, 0.3, 0.35);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.2f);
        return stats.critDamage() / 100;
    }

    private static float melee(ServerPlayer attacker, LivingEntity victim, PlayerData data, Stats stats, float amount) {
        ItemStack weapon = attacker.getMainHandItem();
        // Increased damage: melee nodes, and +1% per point of the weapon's scaling attribute (6.3.1, 9.5 step 2).
        Attr[] scaling = Scaling.of(weapon);
        double mult = 1 + stats.meleeDamagePct() / 100 + stats.scalingPct(scaling[0], scaling[1]) / 100;
        Skill weaponSkill = weapon.is(ItemTags.SWORDS) ? Skill.SWORDS : weapon.is(ItemTags.AXES) ? Skill.AXES : null;
        if (weaponSkill != null) {
            mult += weaponSkill.effectValue(data.skillLevel(weaponSkill)) / 100.0;
        }
        if (stats.has(Stat.BACKSTAB) && isBehind(attacker, victim)) {
            mult += stats.get(Stat.BACKSTAB) / 100;
        }
        mult += crit(attacker, victim, stats);
        amount *= (float) mult;

        // Weapon parts: gold heads hurt undead more, netherite heads set things alight.
        if (Parts.modular(weapon)) {
            Parts.Totals parts = Parts.totals(weapon);
            if (parts.spirit > 0 && victim.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
                amount *= 1 + parts.spirit;
            }
            if (parts.fire > 0) {
                victim.igniteForSeconds(parts.fire * 40);
            }
        }

        if (weaponSkill != null) {
            Rpg.addSkillXp(attacker, weaponSkill, Math.min(amount, 15) * 0.5);
        }
        if (stats.has(Stat.LIFESTEAL)) {
            attacker.heal((float) (amount * stats.get(Stat.LIFESTEAL) / 100));
        }
        if (stats.has(Stat.VENOM)) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (stats.get(Stat.VENOM) * 20), 0), attacker);
        }
        Rpg.gainRage(attacker, 5);
        return amount;
    }

    private static float defend(ServerPlayer player, DamageSource source, float amount, long now) {
        Stats stats = Rpg.stats(player);
        PlayerData data = Rpg.data(player);
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            Rpg.gainRage(player, 10);
            if (player.isBlocking() && player.isDamageSourceBlocked(source)) {
                int blocking = data.skillLevel(Skill.BLOCKING);
                Rpg.addSkillXp(player, Skill.BLOCKING, 1 + Math.min(amount, 15) * 2);
                float reflect = amount * Skill.BLOCKING.effectValue(blocking) / 100f;
                ItemStack shield = player.getUseItem();
                if (Parts.modular(shield)) {
                    // Better shield rims and straps than standard add to what is thrown back.
                    reflect += amount * Math.max(0, Parts.totals(shield).block);
                }
                if (reflect > 0 && source.getDirectEntity() == attacker) {
                    attacker.hurt(player.damageSources().thorns(player), reflect);
                }
            }
        }
        double mom = stats.get(Stat.MIND_OVER_MATTER);
        if (mom > 0 && data.mana > 0) {
            float absorbed = Math.min((float) (amount * mom / 100), data.mana / 2);
            data.mana -= absorbed * 2;
            amount -= absorbed;
        }
        return amount;
    }

    private static boolean isBehind(LivingEntity attacker, LivingEntity victim) {
        Vec3 facing = Vec3.directionFromRotation(0, victim.getYRot());
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1, 0, 1).normalize();
        return facing.dot(toAttacker) < -0.3;
    }
}
