package com.cavmkii.seancraft.combat;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Poise (design doc 9.6.1): blunt damage builds up on the target, and past a threshold it staggers for about 0.6
 * seconds. Heavy attacks build twice as much. It drains away after a few seconds without a blunt hit. Players use the
 * same stagger as sonic boss attacks (Restraint), so Bass Drop's immunity covers both. Bosses keep their own rules.
 */
public final class Poise {
    /** A mob staggers after blunt damage worth 30% of its max health. */
    static final float MOB_THRESHOLD = 0.30f;
    /** A player staggers after 60 blunt damage (on the 300-health scale). */
    static final float PLAYER_THRESHOLD = 60;
    static final int STAGGER_TICKS = 12;
    static final int RESET_TICKS = 60;

    private record Meter(float value, long lastHit) {}

    private static final Map<LivingEntity, Meter> METERS = new WeakHashMap<>();
    /** Scales a target's threshold (Hunter's Mark at rank 3 takes 20% off). */
    public static java.util.function.ToDoubleFunction<LivingEntity> thresholdMultiplier = target -> 1;

    private Poise() {}

    /** Adds poise damage from a hit; staggers and resets when it passes the threshold. */
    public static void add(LivingEntity target, float poise) {
        if (poise <= 0 || target instanceof com.cavmkii.seancraft.boss.BossEntity || target.level().isClientSide) {
            return;
        }
        long now = target.level().getGameTime();
        Meter m = METERS.get(target);
        float value = (m == null || now - m.lastHit > RESET_TICKS ? 0 : m.value) + poise;
        float threshold = (float) ((target instanceof ServerPlayer ? PLAYER_THRESHOLD : target.getMaxHealth() * MOB_THRESHOLD)
                * thresholdMultiplier.applyAsDouble(target));
        if (value >= threshold) {
            METERS.remove(target);
            stagger(target);
        } else {
            METERS.put(target, new Meter(value, now));
            if (value >= threshold * NEAR && !(target instanceof ServerPlayer)) {
                crack(target);
            }
        }
    }

    /** Past this share of the threshold a target shows it's about to break (9.6.1). */
    static final float NEAR = 0.7f;

    /**
     * Close to staggering: chips fly off the target's head and it flinches on the hit, so players can see a stagger
     * coming and press for it. (There's no mob health bar to crack, so the cracks are on the mob.)
     */
    static void crack(LivingEntity target) {
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
                    target.getX(), target.getY(1.05), target.getZ(), 10, 0.25, 0.1, 0.25, 0.05);
            level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 0.5f, 1.6f);
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /** About 0.6 seconds where the target can't move or act. */
    public static void stagger(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            com.cavmkii.seancraft.boss.Restraint.stagger(player);
        } else if (!(target instanceof com.cavmkii.seancraft.boss.BossEntity)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, 9, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 9, false, false));
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(1.1), target.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
        }
    }
}
