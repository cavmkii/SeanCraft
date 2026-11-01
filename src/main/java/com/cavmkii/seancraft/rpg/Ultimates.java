package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.combat.Damage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** What each ultimate does (design doc 6.5.4). Ranks 2 and 3 (mastery, capstone) make each one last or hit 25% more a rank. */
final class Ultimates {
    /** Doomed targets, until the game time it wears off. */
    static final Map<UUID, Long> DOOMED = new HashMap<>();
    static final double HALLOWED_RADIUS = 6;
    static final double CATACLYSM_RADIUS = 12;

    private Ultimates() {}

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Long until = DOOMED.remove(entity.getUUID());
            if (until != null && entity.level() instanceof ServerLevel level && level.getGameTime() < until) {
                // Doom: it bursts into poison.
                AreaEffectCloud cloud = new AreaEffectCloud(level, entity.getX(), entity.getY(), entity.getZ());
                cloud.setRadius(3.5f);
                cloud.setDuration(100);
                cloud.setRadiusPerTick(-0.01f);
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                if (source.getEntity() instanceof LivingEntity owner) {
                    cloud.setOwner(owner);
                }
                level.addFreshEntity(cloud);
            }
        });
    }

    /** True while a target is under Doom: its weaknesses count double. */
    static boolean doomed(LivingEntity target) {
        Long until = DOOMED.get(target.getUUID());
        return until != null && target.level().getGameTime() < until;
    }

    private static double scale(int rank) {
        return 1 + 0.25 * (rank - 1);
    }

    static boolean cast(ServerPlayer player, Ultimate ult, int rank, Stats stats, Rpg.Runtime rt) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        double s = scale(rank);
        switch (ult) {
            case RAMPAGE -> {
                rt.rampageUntil = now + Math.round(160 * s);
                level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 1.1f);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY(1.2), player.getZ(), 12, 0.6, 0.6, 0.6, 0);
            }
            case BULWARK -> {
                rt.bulwarkUntil = now + Math.round(120 * s);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY(1), player.getZ(), 30, 1.5, 0.6, 1.5, 0.1);
            }
            case HALLOWED_GROUND -> {
                rt.hallowedAt = player.position();
                rt.hallowedUntil = now + Math.round(160 * s);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
            }
            case DEADEYE -> {
                rt.deadeyeShots = 2 + rank;
                level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1f, 0.7f);
            }
            case THOUSAND_CUTS -> {
                return thousandCuts(player, level, s);
            }
            case VANISH -> {
                int ticks = (int) Math.round(100 * s);
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32))) {
                    if (mob.getTarget() == player) {
                        mob.setTarget(null);
                    }
                }
                rt.vanishStrike = true;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.6), player.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1f, 1f);
            }
            case CATACLYSM -> {
                rt.cataclysmUntil = now + Math.round(100 * s);
                rt.cataclysmPower = (float) (25 * s);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 1.2f);
            }
            case DOOM -> {
                LivingEntity target = SkillEffects.lookTarget(player, 20);
                if (target == null) {
                    return false;
                }
                DOOMED.put(target.getUUID(), now + Math.round(160 * s));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.round(160 * s), 0), player);
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(1), target.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            case MIRACLE -> {
                float scar = rank >= 3 ? 0.15f : rank == 2 ? 0.25f : com.cavmkii.seancraft.tarkov.Med.KIT_SCAR;
                int revived = 0;
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8))) {
                    revived += com.cavmkii.seancraft.tarkov.Health.revive(p, scar);
                }
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1), player.getZ(), 60, 2, 1, 2, 0.3);
                level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
                if (revived == 0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Nobody nearby has a destroyed part, but the miracle happened anyway."), true);
                }
            }
        }
        return true;
    }

    /** Dash ahead through up to five enemies, three quick cuts on each. */
    private static boolean thousandCuts(ServerPlayer player, ServerLevel level, double s) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : SkillEffects.enemiesAround(player, player.position(), 8)) {
            Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (to.length() > 0.5 && to.normalize().dot(look) > 0.5) {
                hit.add(e);
            }
        }
        hit.sort((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));
        float per = (float) (player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.6 * s);
        for (LivingEntity e : hit.subList(0, Math.min(5, hit.size()))) {
            for (int i = 0; i < 3; i++) {
                e.invulnerableTime = 0;
                typed(player, e, per, Damage.Type.SLASH);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY(0.6), e.getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
        player.setDeltaMovement(look.x * 2.4, 0.15, look.z * 2.4);
        player.hurtMarked = true;
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.3f);
        return true;
    }

    /** Ultimate damage of one type, on the 9.5 scale already (it skips the pipeline's x5). */
    static void typed(ServerPlayer player, LivingEntity target, float amount, Damage.Type type) {
        Damage.forced(Map.of(type, 1f));
        try {
            SkillEffects.damage(player, target, amount / Damage.SCALE);
        } finally {
            Damage.forced(null);
        }
    }

    /** Every tick for a player: Hallowed Ground and Cataclysm at work. */
    static void tick(ServerPlayer player, Rpg.Runtime rt, long now) {
        ServerLevel level = player.serverLevel();
        if (now < rt.hallowedUntil && rt.hallowedAt != null) {
            if (now % 5 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    level.sendParticles(ParticleTypes.END_ROD, rt.hallowedAt.x + Math.cos(a) * HALLOWED_RADIUS, rt.hallowedAt.y + 0.2,
                            rt.hallowedAt.z + Math.sin(a) * HALLOWED_RADIUS, 1, 0, 0, 0, 0);
                }
            }
            if (now % 20 == 0) {
                for (LivingEntity e : SkillEffects.enemiesAround(player, rt.hallowedAt, HALLOWED_RADIUS)) {
                    if (e.getType().is(EntityTypeTags.UNDEAD)) {
                        typed(player, e, 20, Damage.Type.SPIRIT);
                    }
                }
            }
        }
        if (now < rt.cataclysmUntil && now % 10 == 0) {
            List<LivingEntity> targets = SkillEffects.enemiesAround(player, player.position(), CATACLYSM_RADIUS);
            for (int i = 0; i < Math.min(3, targets.size()); i++) {
                LivingEntity e = targets.get(player.getRandom().nextInt(targets.size()));
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(e.getX(), e.getY(), e.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                e.invulnerableTime = 0;
                typed(player, e, rt.cataclysmPower, Damage.Type.LIGHTNING);
            }
        }
    }

    /** Inside someone's Hallowed Ground (allies bleed slower there). */
    static boolean hallowed(ServerPlayer player) {
        for (ServerPlayer p : player.serverLevel().players()) {
            Rpg.Runtime rt = Rpg.runtime(p);
            if (rt.hallowedAt != null && player.serverLevel().getGameTime() < rt.hallowedUntil
                    && player.position().distanceTo(rt.hallowedAt) <= HALLOWED_RADIUS) {
                return true;
            }
        }
        return false;
    }
}
