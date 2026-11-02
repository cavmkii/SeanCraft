package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.parts.Family;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy attacks (design doc 9.4.1): attacking while sneaking. The swing itself is Better Combat's; SeanCraft adds the
 * x1.75 damage and double poise (through Combat) and each family's heavy move on top: lunges, a ground slam, spins and a
 * sweeping arc. Each extra hit deals half the weapon's damage.
 */
public final class Heavy {
    public static final float DAMAGE = 1.75f;
    static final float EXTRA_HIT = 0.5f;

    private Heavy() {}

    static void perform(ServerPlayer player) {
        Family.Stock stock = Family.stock(player.getMainHandItem());
        Family family = stock != null ? stock.family() : null;
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        double reach = player.entityInteractionRange() + 0.5;
        if (family == null) {
            if (player.getMainHandItem().is(com.cavmkii.seancraft.parts.WeaponItems.WOODEN_CLUB)) {
                slam(player, level, 2.0);
            }
            return;
        }
        switch (family) {
            case SWORD, DAGGER -> {
                // Lunging thrust / lunge stab.
                player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.9, 0.05, look.z * 0.9));
                player.hurtMarked = true;
            }
            case MACE, WARHAMMER -> slam(player, level, family == Family.WARHAMMER ? 3.0 : 2.2);
            case AXE, HALBERD -> {
                // Spinning cleave / full spin: everything around.
                for (LivingEntity e : around(player, reach)) {
                    extra(player, e);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.6), player.getZ(), 6, reach / 2, 0.1, reach / 2, 0);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
            }
            case SPEAR, PIKE -> {
                // Sweeping arc: everything in front.
                for (LivingEntity e : around(player, reach + (family == Family.PIKE ? 1 : 0))) {
                    Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
                    if (to.lengthSqr() > 0.01 && to.normalize().dot(look) > 0.4) {
                        extra(player, e);
                    }
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x * 2, player.getY(0.6), player.getZ() + look.z * 2, 3, 0.5, 0.1, 0.5, 0);
            }
            default -> { }
        }
    }

    /** Ground slam: knocks back and staggers everything close (9.4.1). */
    private static void slam(ServerPlayer player, ServerLevel level, double radius) {
        for (LivingEntity e : around(player, radius)) {
            Poise.stagger(e);
            e.knockback(0.5, player.getX() - e.getX(), player.getZ() - e.getZ());
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1f, 0.9f);
    }

    private static List<LivingEntity> around(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1, radius),
                e -> e != player && e.isAlive() && (e instanceof Enemy || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player)
                        && e.distanceTo(player) <= radius + 0.5);
    }

    private static void extra(ServerPlayer player, LivingEntity target) {
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * EXTRA_HIT;
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
    }
}
