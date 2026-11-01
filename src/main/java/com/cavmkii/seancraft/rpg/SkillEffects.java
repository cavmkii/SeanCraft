package com.cavmkii.seancraft.rpg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What each active skill does. Damage scales with the skill's attribute (Strength for rage skills,
 * Agility for stamina skills, Intelligence for mana skills) and with Casting and ability-damage nodes.
 * Returns false from {@link #cast} when there was nothing to cast at, so no cost is paid.
 */
public final class SkillEffects {
    private SkillEffects() {}

    static boolean cast(ServerPlayer player, ActiveSkill skill, Stats stats, Rpg.Runtime rt) {
        ServerLevel level = player.serverLevel();
        return switch (skill) {
            case LEAP_SLAM -> {
                Vec3 look = flatLook(player);
                launch(player, look.scale(1.4).add(0, 0.75, 0));
                rt.leapTicks = 40;
                sound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
                yield true;
            }
            case WAR_CRY -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));
                for (LivingEntity e : enemiesAround(player, player.position(), 8)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0), player);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0), player);
                }
                sound(player, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 1f, 1f);
                ring(level, player.position(), 8, ParticleTypes.ANGRY_VILLAGER);
                yield true;
            }
            case GROUND_SLAM -> {
                slam(player, stats, 5, 5);
                yield true;
            }
            case BERSERK -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 240, 0));
                sound(player, SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY(1.2), player.getZ(), 8, 0.5, 0.3, 0.5, 0);
                yield true;
            }
            case LUNGE -> {
                LivingEntity target = lookTarget(player, 10);
                if (target == null) {
                    yield false;
                }
                Vec3 to = target.position().subtract(player.position());
                Vec3 stop = player.position().add(to.normalize().scale(Math.max(0, to.length() - 1.5)));
                player.teleportTo(stop.x, target.getY(), stop.z);
                damage(player, target, (float) (weaponDamage(player) * 1.5 * power(player, stats, Attr.AGI)));
                sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                yield true;
            }
            case RIPOSTE -> {
                rt.parryUntil = level.getGameTime() + 40;
                sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.3f);
                yield true;
            }
            case WHIRLWIND -> {
                for (int i = 0; i < 3; i++) {
                    Scheduler.later(i * 6 + 1, () -> {
                        if (!player.isAlive()) {
                            return;
                        }
                        for (LivingEntity e : enemiesAround(player, player.position(), 3.5)) {
                            damage(player, e, (float) (weaponDamage(player) * 0.8 * power(player, stats, Attr.STR)));
                        }
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.6), player.getZ(), 6, 1.5, 0.1, 1.5, 0);
                        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                    });
                }
                yield true;
            }
            case BLADE_FLURRY -> {
                LivingEntity target = lookTarget(player, 5);
                if (target == null) {
                    yield false;
                }
                for (int i = 0; i < 5; i++) {
                    Scheduler.later(i * 3 + 1, () -> {
                        if (player.isAlive() && target.isAlive() && target.distanceTo(player) < 6) {
                            damage(player, target, (float) (weaponDamage(player) * 0.5 * power(player, stats, Attr.AGI)));
                            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 5, 0.3, 0.3, 0.3, 0.2);
                            sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.5f);
                        }
                    });
                }
                yield true;
            }
            case DASH -> {
                launch(player, flatLook(player).scale(2.2).add(0, 0.15, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8, 4, false, false, false));
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.3), player.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
                sound(player, SoundEvents.PHANTOM_FLAP, 1f, 1.6f);
                yield true;
            }
            case VOLLEY -> {
                for (int i = -2; i <= 2; i++) {
                    shootArrow(player, player.getYRot() + i * 8, player.getXRot(), 2.8f, 4 * power(player, stats, Attr.AGI));
                }
                sound(player, SoundEvents.ARROW_SHOOT, 1f, 1f);
                yield true;
            }
            case RAIN_OF_ARROWS -> {
                Vec3 point = lookPoint(player, 30);
                double dmg = 3 * power(player, stats, Attr.AGI);
                for (int i = 0; i < 24; i++) {
                    Scheduler.later(1 + i * 2, () -> {
                        double dx = (level.random.nextDouble() - 0.5) * 7;
                        double dz = (level.random.nextDouble() - 0.5) * 7;
                        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
                        arrow.setPos(point.x + dx, point.y + 15, point.z + dz);
                        arrow.shoot(0, -1, 0, 2.2f, 2f);
                        arrow.setBaseDamage(dmg / 2.2);
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                        level.addFreshEntity(arrow);
                    });
                }
                yield true;
            }
            case HUNTERS_MARK -> {
                LivingEntity target = lookTarget(player, 30);
                if (target == null) {
                    yield false;
                }
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
                RpgDamage.MARKED.put(target.getUUID(), level.getGameTime() + 200);
                sound(player, SoundEvents.ARROW_HIT_PLAYER, 1f, 0.6f);
                yield true;
            }
            case SMOKE_BOMB -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0));
                for (LivingEntity e : enemiesAround(player, player.position(), 8)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), player);
                    if (e instanceof Mob mob && mob.getTarget() == player) {
                        mob.setTarget(null);
                    }
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.5), player.getZ(), 60, 1.5, 1, 1.5, 0.02);
                sound(player, SoundEvents.FIRE_EXTINGUISH, 1f, 0.8f);
                yield true;
            }
            case SHADOW_STEP -> {
                LivingEntity target = lookTarget(player, 20);
                if (target == null) {
                    yield false;
                }
                Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(1.5));
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(0.5), player.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                player.teleportTo(level, behind.x, target.getY(), behind.z, java.util.Set.of(), target.getYRot(), 0);
                sound(player, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
                yield true;
            }
            case POISON_CLOUD -> {
                Vec3 point = lookPoint(player, 20);
                AreaEffectCloud cloud = new AreaEffectCloud(level, point.x, point.y, point.z);
                cloud.setOwner(player);
                cloud.setRadius(3.5f);
                cloud.setRadiusPerTick(0);
                cloud.setDuration(120);
                cloud.setWaitTime(0);
                cloud.setParticle(ParticleTypes.ITEM_SLIME);
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                level.addFreshEntity(cloud);
                sound(player, SoundEvents.SPLASH_POTION_BREAK, 1f, 0.8f);
                yield true;
            }
            case BLADE_FAN -> {
                for (int i = 0; i < 8; i++) {
                    SkillProjectile dagger = new SkillProjectile(level, player, new ItemStack(Items.IRON_SWORD),
                            (float) (3 * power(player, stats, Attr.AGI)), 0, false);
                    dagger.setPos(player.getX(), player.getEyeY() - 0.4, player.getZ());
                    dagger.shootFromRotation(player, 0, player.getYRot() + i * 45, 0, 1.6f, 0);
                    level.addFreshEntity(dagger);
                }
                sound(player, SoundEvents.TRIDENT_THROW.value(), 1f, 1.5f);
                yield true;
            }
            case FIREBALL -> {
                SkillProjectile ball = new SkillProjectile(level, player, new ItemStack(Items.FIRE_CHARGE),
                        (float) (6 * power(player, stats, Attr.INT)), 2.5f, true);
                ball.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.8f, 0);
                level.addFreshEntity(ball);
                sound(player, SoundEvents.BLAZE_SHOOT, 1f, 1f);
                yield true;
            }
            case FROST_NOVA -> {
                for (LivingEntity e : enemiesAround(player, player.position(), 6)) {
                    damage(player, e, (float) (4 * power(player, stats, Attr.INT)));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2), player);
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
                }
                ring(level, player.position(), 6, ParticleTypes.SNOWFLAKE);
                sound(player, SoundEvents.GLASS_BREAK, 1f, 0.7f);
                yield true;
            }
            case BLINK -> {
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getLookAngle().scale(12));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 dest = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.6));
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(0.5), player.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                player.teleportTo(dest.x, dest.y - player.getEyeHeight() + 0.2, dest.z);
                player.fallDistance = 0;
                sound(player, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.5f);
                yield true;
            }
            case CHAIN_LIGHTNING -> {
                LivingEntity first = lookTarget(player, 25);
                if (first == null) {
                    yield false;
                }
                float dmg = (float) (6 * power(player, stats, Attr.INT));
                Set<LivingEntity> hit = new HashSet<>();
                LivingEntity current = first;
                Vec3 from = player.getEyePosition();
                for (int jump = 0; jump < 5 && current != null; jump++) {
                    hit.add(current);
                    beam(level, from, current.getEyePosition());
                    damage(player, current, dmg);
                    dmg *= 0.8f;
                    from = current.getEyePosition();
                    LivingEntity next = null;
                    double best = 36;
                    for (LivingEntity e : enemiesAround(player, current.position(), 6)) {
                        double d = e.distanceToSqr(current);
                        if (!hit.contains(e) && d < best) {
                            best = d;
                            next = e;
                        }
                    }
                    current = next;
                }
                sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
                yield true;
            }
            case CONSECRATE -> {
                Vec3 center = player.position();
                double heal = 1 + stats.intel() * 0.03;
                float burn = (float) (2 * power(player, stats, Attr.INT));
                for (int pulse = 0; pulse < 8; pulse++) {
                    Scheduler.later(pulse * 20 + 1, () -> {
                        ring(level, center, 4, ParticleTypes.END_ROD);
                        for (Player p : level.getEntitiesOfClass(Player.class, Rpg.around(center, 4))) {
                            p.heal((float) heal);
                        }
                        for (LivingEntity e : enemiesAround(player, center, 4)) {
                            damage(player, e, e.isInvertedHealAndHarm() ? burn * 2 : burn);
                        }
                    });
                }
                sound(player, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
                yield true;
            }
            case HOLY_SMITE -> {
                Vec3 point = lookPoint(player, 30);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(point);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                for (LivingEntity e : enemiesAround(player, point, 3)) {
                    damage(player, e, (float) (8 * power(player, stats, Attr.INT)));
                }
                yield true;
            }
            case DIVINE_SHIELD -> {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1), player.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
                sound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1f, 0.8f);
                yield true;
            }
            case JUDGMENT -> {
                for (LivingEntity e : enemiesAround(player, player.position(), 5)) {
                    damage(player, e, (float) (4 * power(player, stats, Attr.STR)));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4), player);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), player);
                }
                ring(level, player.position(), 5, ParticleTypes.ELECTRIC_SPARK);
                sound(player, SoundEvents.ANVIL_LAND, 1f, 0.7f);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------ multi-tick and passive effects

    /** Leap Slam: once you land, smash. */
    static void tickLeap(ServerPlayer player, Rpg.Runtime rt) {
        rt.leapTicks--;
        if (rt.leapTicks < 36 && (player.onGround() || player.isInWater())) {
            rt.leapTicks = 0;
            player.fallDistance = 0;
            slam(player, Rpg.stats(player), 4, 6);
        } else if (rt.leapTicks == 0) {
            player.fallDistance = 0;
        }
    }

    private static void slam(ServerPlayer player, Stats stats, double radius, double baseDamage) {
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : enemiesAround(player, player.position(), radius)) {
            damage(player, e, (float) (baseDamage * power(player, stats, Attr.STR)));
            e.push(0, 0.5, 0);
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 3, radius / 3, 0.1, radius / 3, 0);
        ring(level, player.position(), radius, ParticleTypes.CAMPFIRE_COSY_SMOKE);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
    }

    /** Templar keystone: burn monsters within 4 blocks every second. */
    static void holyAura(ServerPlayer player, Stats stats) {
        for (LivingEntity e : enemiesAround(player, player.position(), 4)) {
            damage(player, e, (float) (1 + stats.intel() * 0.03));
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(1), e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Ability damage that the melee rules don't scale again. */
    static void damage(ServerPlayer player, LivingEntity target, float amount) {
        RpgDamage.applyingSkill = true;
        try {
            target.hurt(player.damageSources().indirectMagic(player, player), amount);
        } finally {
            RpgDamage.applyingSkill = false;
        }
    }

    /** The rank of the skill being cast (6.5.2), set around each cast. */
    static int rank = 1;

    /**
     * Multiplier from the skill's attribute (+1% per point, 6.3.1), ability-damage nodes, Casting and the skill's rank
     * (+15% a rank).
     */
    private static double power(ServerPlayer player, Stats stats, Attr attr) {
        int casting = Rpg.data(player).skillLevel(Skill.CASTING);
        return (1 + stats.attr(attr) * 0.01) * (1 + stats.spellDamagePct() / 100) * (1 + Skill.CASTING.effectValue(casting) / 100.0)
                * (1 + 0.15 * (rank - 1));
    }

    private static double weaponDamage(ServerPlayer player) {
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, Rpg.around(center, radius))) {
            if (e == player || !e.isAlive() || e.position().distanceTo(center) > radius) {
                continue;
            }
            // Monsters and anything hunting you. Other players and passive mobs are left alone.
            if (e instanceof Enemy && !(e instanceof Player) || e instanceof com.cavmkii.seancraft.entity.BabyGoblin
                    || e instanceof Mob mob && mob.getTarget() == player) {
                out.add(e);
            }
        }
        return out;
    }

    private static LivingEntity lookTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static Vec3 lookPoint(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }
        return end;
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
    }

    private static void launch(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
    }

    private static void shootArrow(ServerPlayer player, float yaw, float pitch, float speed, double damage) {
        Arrow arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
        arrow.shootFromRotation(player, pitch, yaw, 0, speed, 1f);
        arrow.setBaseDamage(damage / speed);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        player.level().addFreshEntity(arrow);
    }

    private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
        int points = (int) (radius * 10);
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.2, center.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0);
        }
    }

    private static void beam(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = (int) (step.length() * 3);
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) Math.max(1, points)));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }
}
