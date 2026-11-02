package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.boss.BossEntity;
import com.cavmkii.seancraft.combat.Damage;
import com.cavmkii.seancraft.combat.Poise;
import com.cavmkii.seancraft.parts.Family;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What each region skill does (design doc 6.5.5). Damage is on the 9.5 scale: W is the held weapon's damage, SP the
 * staff bolt's 35 times the crystal's factor, and "power" for Shadow and Templar the higher of the two. On top of that
 * every skill takes the region's attributes (+1% a point), spell-damage nodes and Casting as increased damage. Each rank
 * does what the table says and nothing else. Returns false from {@link #cast} when there was nothing to cast at, so no
 * cost is paid.
 */
public final class SkillEffects {
    /** The rank of the skill being cast, and what it was paid with, set around each cast. */
    static int rank = 1;
    static float paid;
    static Resource paidFrom = Resource.MANA;

    static final double STAFF_SP = 35;
    /** Bow damage of a full draw before the bow's parts (an arrow's 2 at speed 3, on the 9.5 scale). */
    static final double FULL_DRAW = 2 * 3 * Damage.SCALE;

    private static final ResourceLocation BERSERK_SPEED = SeanCraft.id("berserk");
    private static final ResourceLocation WHIRL_SLOW = SeanCraft.id("whirlwind");
    private static final ResourceLocation SHIELD_ABSORPTION = SeanCraft.id("divine_shield");

    enum Kind { SMOKE, POISON, CONSECRATE, BURN }

    static final class Zone {
        final Kind kind;
        final ServerLevel level;
        final Vec3 at;
        final double radius;
        final long start;
        final long until;
        final UUID owner;
        final int rank;
        final float amount;

        Zone(Kind kind, ServerLevel level, Vec3 at, double radius, int ticks, UUID owner, int rank, float amount) {
            this.kind = kind;
            this.level = level;
            this.at = at;
            this.radius = radius;
            this.start = level.getGameTime();
            this.until = start + ticks;
            this.owner = owner;
            this.rank = rank;
            this.amount = amount;
        }

        boolean contains(Entity e) {
            return e.level() == level && e.position().distanceTo(at) <= radius && Math.abs(e.getY() - at.y) < 3;
        }
    }

    record Bleed(UUID owner, float perSecond, long until) {}

    static final class Linger {
        final UUID owner;
        final float perSecond;
        long until;
        long lastPulse;

        Linger(UUID owner, float perSecond, long until) {
            this.owner = owner;
            this.perSecond = perSecond;
            this.until = until;
        }
    }

    record Rally(long until, float pct) {}

    record Mark(long until, float extra, UUID owner, int rank) {}

    static final class Shield {
        final long until;
        final int rank;
        final float amount;

        Shield(long until, int rank, float amount) {
            this.until = until;
            this.rank = rank;
            this.amount = amount;
        }
    }

    static final List<Zone> ZONES = new ArrayList<>();
    static final Map<LivingEntity, Bleed> BLEEDS = new WeakHashMap<>();
    static final Map<LivingEntity, Linger> LINGER = new WeakHashMap<>();
    static final Map<LivingEntity, Long> FROZEN = new WeakHashMap<>();
    static final Map<LivingEntity, Long> DAUNTED = new WeakHashMap<>();
    static final Map<UUID, Rally> RALLIED = new HashMap<>();
    static final Map<UUID, Mark> MARKED = new HashMap<>();
    static final Map<UUID, Shield> SHIELDS = new HashMap<>();

    private SkillEffects() {}

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SkillEffects::serverTick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Mark mark = MARKED.remove(entity.getUUID());
            if (mark != null && mark.rank() >= 4 && entity.level().getServer() != null) {
                ServerPlayer owner = entity.level().getServer().getPlayerList().getPlayer(mark.owner());
                if (owner != null) {
                    Rpg.resetCooldown(owner, ActiveSkill.HUNTERS_MARK);
                }
            }
            if (source.getEntity() instanceof ServerPlayer killer) {
                Rpg.Runtime rt = Rpg.runtime(killer);
                if (killer.serverLevel().getGameTime() < rt.rampageUntil && rt.ultRank >= 3) {
                    // Rampage at rank 3: each kill adds a second.
                    rt.rampageUntil += 20;
                }
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer player) || arrow.tickCount > 0) {
                return;
            }
            Rpg.Runtime rt = Rpg.runtime(player);
            long now = level.getGameTime();
            if (now < rt.fullDrawUntil && !volleying) {
                // Dash: the next shot is fully drawn.
                rt.fullDrawUntil = 0;
                Vec3 v = arrow.getDeltaMovement();
                if (v.lengthSqr() > 1.0e-4) {
                    arrow.setDeltaMovement(v.normalize().scale(3.0));
                }
                arrow.setCritArrow(true);
            }
            if (rt.deadeyeShots > 0 && rt.ultRank >= 3) {
                // Deadeye at rank 3: the arrows pierce.
                ((com.cavmkii.seancraft.mixin.AbstractArrowAccessor) arrow).seancraft$setPierceLevel((byte) 3);
            }
        });
        // Hunter's Mark at rank 3: 20% less poise.
        Poise.thresholdMultiplier = target -> {
            Mark m = MARKED.get(target.getUUID());
            return m != null && m.rank() >= 3 && target.level().getGameTime() < m.until() ? 0.8 : 1;
        };
    }

    private static boolean volleying;

    // ================================================================== requirements and numbers

    /** Why a skill can't be cast with what's in hand (6.5.5), or null if it can. */
    static String requirement(ServerPlayer player, ActiveSkill skill) {
        ItemStack held = player.getMainHandItem();
        return switch (skill.archetype) {
            case WARRIOR, DUELIST -> melee(held) ? null : "Needs a melee weapon in hand";
            case RANGER -> held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem ? null : "Needs a bow in hand";
            case MAGE -> Magic.family(held) != null ? null : "Needs a wand or staff in hand";
            default -> null;
        };
    }

    static boolean melee(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.TRIDENT) || stack.is(Items.MACE) || stack.is(com.cavmkii.seancraft.parts.WeaponItems.WOODEN_CLUB)) {
            return true;
        }
        Family.Stock stock = Family.stock(stack);
        return stock != null && stock.family().ordinal() <= Family.PIKE.ordinal();
    }

    /** W: the held weapon's damage on the 9.5 scale. */
    static double weapon(ServerPlayer player) {
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE) * Damage.SCALE;
    }

    /** SP: the staff bolt's 35 times the held crystal's factor; 0 without a wand or staff. */
    static double spell(ServerPlayer player) {
        return Magic.family(player.getMainHandItem()) != null ? STAFF_SP * Magic.spellPower(player) : 0;
    }

    /** Shadow and Templar: whichever of W and SP is higher. */
    static double power(ServerPlayer player) {
        return Math.max(weapon(player), spell(player));
    }

    /** Increased damage on every skill: the region's attributes (+1% a point, 6.3.1), spell-damage nodes and Casting. */
    static double increased(ServerPlayer player, Stats stats, Archetype region) {
        int casting = Rpg.data(player).skillLevel(Skill.CASTING);
        double attr = (stats.attr(region.first) + stats.attr(region.second)) / 2;
        return (1 + attr * 0.01) * (1 + stats.spellDamagePct() / 100) * (1 + Skill.CASTING.effectValue(casting) / 100.0);
    }

    /** Full-draw arrow damage with the held bow's parts. */
    static double fullDraw(ServerPlayer player) {
        ItemStack bow = player.getMainHandItem();
        double f = com.cavmkii.seancraft.parts.Parts.modular(bow) ? Math.max(0.05, 1 + com.cavmkii.seancraft.parts.Parts.totals(bow).damage) : 1;
        return FULL_DRAW * f;
    }

    static boolean boss(LivingEntity e) {
        return e instanceof BossEntity;
    }

    static boolean spiritWeak(LivingEntity e) {
        return Damage.resistance(e, Damage.Type.SPIRIT) > 1;
    }

    // ================================================================== casting

    static boolean cast(ServerPlayer player, ActiveSkill skill, Stats stats, Rpg.Runtime rt) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int r = rank;
        double inc = increased(player, stats, skill.archetype);
        double w = weapon(player) * inc;
        double sp = spell(player) * inc;
        double pw = power(player) * inc;
        return switch (skill) {
            // ---------------------------------------------------------- Warrior
            case LEAP_SLAM -> {
                // A 0.4 s crouch, then the leap; Rpg ticks the landing.
                rt.leapRank = r;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY(0.1), player.getZ(), 6, 0.4, 0, 0.4, 0.01);
                sound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
                Scheduler.later(8, () -> {
                    if (player.isAlive()) {
                        launch(player, flatLook(player).scale(1.4).add(0, 0.75, 0));
                        rt.leapTicks = 40;
                    }
                });
                yield true;
            }
            case WAR_CRY -> {
                int ticks = r >= 2 ? 180 : 120;
                float pct = r >= 4 ? 0.25f : 0.15f;
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, Rpg.around(player.position(), 8))) {
                    RALLIED.put(p.getUUID(), new Rally(now + ticks, pct));
                }
                for (LivingEntity e : enemiesAround(player, player.position(), 6)) {
                    if (e instanceof Mob mob) {
                        mob.setTarget(player);
                    }
                    DAUNTED.put(e, now + ticks);
                }
                if (r >= 3) {
                    rt.steadyUntil = now + 60;
                }
                sound(player, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 1f, 1f);
                ring(level, player.position(), 8, ParticleTypes.ANGRY_VILLAGER);
                yield true;
            }
            case GROUND_SLAM -> {
                // 0.6 s wind-up, then the cone.
                double length = r >= 2 ? 7 : 5;
                double mult = r >= 4 ? 2.5 : 2.0;
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(1.8), player.getZ(), 10, 0.3, 0.2, 0.3, 0.05);
                sound(player, SoundEvents.ANVIL_PLACE, 0.6f, 0.6f);
                Scheduler.later(12, () -> {
                    if (!player.isAlive()) {
                        return;
                    }
                    Vec3 look = flatLook(player);
                    for (LivingEntity e : enemiesAround(player, player.position(), length)) {
                        Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
                        if (to.lengthSqr() > 0.25 && to.normalize().dot(look) < 0.7) {
                            continue;
                        }
                        hit(player, e, (float) (weapon(player) * increased(player, Rpg.stats(player), Archetype.WARRIOR) * mult), Damage.Type.BLUNT);
                        if (!boss(e)) {
                            Poise.stagger(e);
                        }
                        if (r >= 3) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), player);
                        }
                    }
                    for (int i = 1; i <= (int) length; i++) {
                        Vec3 p = player.position().add(look.scale(i));
                        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0.3, 0, 0.3, 0);
                    }
                    sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
                });
                yield true;
            }
            case BERSERK -> {
                int ticks = r >= 2 ? 200 : 160;
                rt.berserkUntil = now + ticks;
                rt.berserkRank = r;
                AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
                if (speed != null) {
                    speed.addOrReplacePermanentModifier(new AttributeModifier(BERSERK_SPEED, r >= 4 ? 0.35 : 0.25,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                sound(player, SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY(1.2), player.getZ(), 8, 0.5, 0.3, 0.5, 0);
                yield true;
            }
            // ---------------------------------------------------------- Duelist
            case LUNGE -> {
                double reach = r >= 3 ? 7 : 5;
                LivingEntity target = lookTarget(player, reach + 1.5);
                if (target == null) {
                    yield false;
                }
                Vec3 to = target.position().subtract(player.position());
                Vec3 stop = player.position().add(to.normalize().scale(Math.max(0, to.length() - 1.5)));
                player.teleportTo(stop.x, target.getY(), stop.z);
                hit(player, target, (float) (w * (r >= 2 ? 1.7 : 1.4)), Damage.Type.PIERCE);
                if (windingUp(target)) {
                    interrupt(target);
                }
                sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                yield true;
            }
            case RIPOSTE -> {
                rt.parryUntil = now + (r >= 2 ? 16 : 12);
                rt.riposteRank = r;
                rt.riposteRefund = paid;
                rt.riposteFrom = paidFrom;
                sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.3f);
                yield true;
            }
            case WHIRLWIND -> {
                int ticks = r >= 2 ? 50 : 40;
                double radius = r >= 4 ? 3.5 : 2.5;
                rt.whirlUntil = now + ticks;
                AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (move != null) {
                    move.addOrReplacePermanentModifier(new AttributeModifier(WHIRL_SLOW, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                for (int i = 0; i < ticks / 8; i++) {
                    Scheduler.later(i * 8 + 1, () -> {
                        if (!player.isAlive()) {
                            return;
                        }
                        for (LivingEntity e : enemiesAround(player, player.position(), radius)) {
                            hit(player, e, (float) (weapon(player) * increased(player, Rpg.stats(player), Archetype.DUELIST) * 0.6), Damage.Type.SLASH);
                            if (r >= 3 && e.getBbWidth() <= 0.9 && !boss(e)) {
                                Vec3 pull = player.position().subtract(e.position()).multiply(1, 0, 1);
                                if (pull.lengthSqr() > 1) {
                                    e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.35)));
                                    e.hurtMarked = true;
                                }
                            }
                        }
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.6), player.getZ(), 6, radius / 2, 0.1, radius / 2, 0);
                        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                    });
                }
                Scheduler.later(ticks, () -> {
                    AttributeInstance m = player.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (m != null) {
                        m.removeModifier(WHIRL_SLOW);
                    }
                });
                yield true;
            }
            case BLADE_FLURRY -> {
                LivingEntity target = lookTarget(player, 5);
                if (target == null) {
                    yield false;
                }
                int strikes = r >= 2 ? 6 : 5;
                for (int i = 0; i < strikes; i++) {
                    int n = i;
                    Scheduler.later(i * 4 + 1, () -> {
                        if (!player.isAlive() || !target.isAlive() || target.distanceTo(player) > 6) {
                            return;
                        }
                        Stats s = Rpg.stats(player);
                        double amount = weapon(player) * increased(player, s, Archetype.DUELIST) * (r >= 4 && n == strikes - 1 ? 1.5 : 0.5);
                        double chance = s.critChance() + com.cavmkii.seancraft.parts.MaterialTraits.critBonus(player.getMainHandItem()) + (r >= 3 ? 5 * n : 0);
                        if (player.getRandom().nextDouble() * 100 < chance) {
                            amount *= 1 + s.critDamage() / 100;
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
                        }
                        hit(player, target, (float) amount, Damage.Type.SLASH);
                        if (player.getRandom().nextFloat() < 0.3f) {
                            bleed(player, target, (float) amount * 0.3f, 4);
                        }
                        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 5, 0.3, 0.3, 0.3, 0.2);
                        sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.5f);
                    });
                }
                yield true;
            }
            // ---------------------------------------------------------- Ranger
            case DASH -> {
                launch(player, flatLook(player).scale(2.2).add(0, 0.15, 0));
                rt.fullDrawUntil = now + 40;
                if (r >= 4) {
                    player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.3), player.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
                sound(player, SoundEvents.PHANTOM_FLAP, 1f, 1.6f);
                yield true;
            }
            case VOLLEY -> {
                ItemStack bow = player.getMainHandItem();
                ItemStack ammo = player.getProjectile(bow);
                if (ammo.isEmpty() && !player.isCreative()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("No arrows"), true);
                    yield false;
                }
                // Rank 3 fires your arrow type; before that, plain arrows.
                ItemStack shot = r >= 3 && !ammo.isEmpty() ? ammo.copyWithCount(1) : new ItemStack(Items.ARROW);
                ArrowItem item = shot.getItem() instanceof ArrowItem a ? a : (ArrowItem) Items.ARROW;
                int arrows = r >= 2 ? 7 : 5;
                double share = r >= 4 ? 0.9 : 0.6;
                volleying = true;
                try {
                    for (int i = 0; i < arrows; i++) {
                        float yaw = player.getYRot() - 20 + 40f * i / (arrows - 1);
                        AbstractArrow arrow = item.createArrow(level, shot, player, bow);
                        arrow.shootFromRotation(player, player.getXRot(), yaw, 0, 3.0f, 1f);
                        arrow.setBaseDamage(arrow.getBaseDamage() * share);
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                        level.addFreshEntity(arrow);
                    }
                } finally {
                    volleying = false;
                }
                if (!player.isCreative() && !ammo.isEmpty()) {
                    ammo.shrink(1);
                }
                sound(player, SoundEvents.ARROW_SHOOT, 1f, 1f);
                yield true;
            }
            case RAIN_OF_ARROWS -> {
                Vec3 point = lookPoint(player, 30);
                double radius = r >= 2 ? 5 : 4;
                int ticks = r >= 3 ? 80 : 60;
                float per = (float) (fullDraw(player) * inc * 0.4);
                boolean burn = r >= 4 && player.getInventory().contains(new ItemStack(com.cavmkii.seancraft.item.ModItems.FIRE_ARROW));
                // A 1 s tell on the ground, then eight volleys.
                for (int t = 0; t < 20; t += 5) {
                    Scheduler.later(t + 1, () -> ring(level, point, radius, ParticleTypes.CRIT));
                }
                for (int i = 0; i < 8; i++) {
                    Scheduler.later(20 + i * ticks / 8, () -> {
                        for (int k = 0; k < 12; k++) {
                            double a = level.random.nextDouble() * Math.PI * 2;
                            double d = Math.sqrt(level.random.nextDouble()) * radius;
                            level.sendParticles(ParticleTypes.CRIT, point.x + Math.cos(a) * d, point.y + 4, point.z + Math.sin(a) * d, 0, 0, -1, 0, 1.5);
                        }
                        for (LivingEntity e : enemiesAround(player, point, radius)) {
                            hit(player, e, per, Damage.Type.PIERCE);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), player);
                            if (burn) {
                                e.igniteForSeconds(3);
                            }
                        }
                        level.playSound(null, point.x, point.y, point.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8f, 1.1f);
                    });
                }
                yield true;
            }
            case HUNTERS_MARK -> {
                LivingEntity target = lookTarget(player, 40);
                if (target == null) {
                    yield false;
                }
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0), player);
                MARKED.put(target.getUUID(), new Mark(now + 240, r >= 2 ? 0.20f : 0.15f, player.getUUID(), r));
                sound(player, SoundEvents.ARROW_HIT_PLAYER, 1f, 0.6f);
                yield true;
            }
            // ---------------------------------------------------------- Shadow
            case SMOKE_BOMB -> {
                ZONES.add(new Zone(Kind.SMOKE, level, player.position(), r >= 4 ? 6 : 4, r >= 2 ? 120 : 100, player.getUUID(), r, 0));
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.5), player.getZ(), 60, 1.5, 1, 1.5, 0.02);
                sound(player, SoundEvents.FIRE_EXTINGUISH, 1f, 0.8f);
                yield true;
            }
            case SHADOW_STEP -> {
                LivingEntity target = lookTarget(player, r >= 2 ? 14 : 10);
                if (target == null) {
                    yield false;
                }
                Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(1.5));
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(0.5), player.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                player.teleportTo(level, behind.x, target.getY(), behind.z, Set.of(), target.getYRot(), 0);
                rt.backstabUntil = now + 40;
                rt.backstabPoison = r >= 4;
                sound(player, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.2f);
                yield true;
            }
            case POISON_CLOUD -> {
                Vec3 point = lookPoint(player, 20);
                ZONES.add(new Zone(Kind.POISON, level, point, r >= 4 ? 4 : 3, r >= 2 ? 160 : 120, player.getUUID(), r, (float) (pw * 0.3)));
                level.sendParticles(ParticleTypes.ITEM_SLIME, point.x, point.y + 0.5, point.z, 30, 1, 0.4, 1, 0.02);
                sound(player, SoundEvents.SPLASH_POTION_BREAK, 1f, 0.8f);
                yield true;
            }
            case BLADE_FAN -> {
                int knives = r >= 2 ? 7 : 5;
                float each = (float) (pw * (r >= 4 ? 0.9 : 0.7));
                float bleedShare = r >= 3 ? 0.45f : 0.3f;
                for (int i = 0; i < knives; i++) {
                    SkillProjectile knife = SkillProjectile.bolt(level, player, new ItemStack(Items.IRON_SWORD), each / Damage.SCALE, 0, Damage.Type.SLASH);
                    knife.onHit = e -> {
                        if (e.getRandom().nextFloat() < 0.5f) {
                            bleed(player, e, each * bleedShare, 4);
                        }
                    };
                    knife.setPos(player.getX(), player.getEyeY() - 0.2, player.getZ());
                    knife.shootFromRotation(player, player.getXRot(), player.getYRot() - 20 + 40f * i / (knives - 1), 0, 1.8f, 0);
                    level.addFreshEntity(knife);
                }
                sound(player, SoundEvents.TRIDENT_THROW.value(), 1f, 1.5f);
                yield true;
            }
            // ---------------------------------------------------------- Mage
            case FIREBALL -> {
                float direct = (float) (sp * (r >= 2 ? 2.4 : 2.0));
                float blast = (float) sp;
                float radius = r >= 3 ? 3 : 2;
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getEyeY(), player.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                Scheduler.later(r >= 4 ? 6 : 10, () -> {
                    if (!player.isAlive()) {
                        return;
                    }
                    SkillProjectile ball = SkillProjectile.bolt(level, player, new ItemStack(Items.FIRE_CHARGE), blast / Damage.SCALE, radius, Damage.Type.FIRE);
                    ball.direct = direct / Damage.SCALE;
                    if (r >= 3) {
                        ball.onImpact = at -> ZONES.add(new Zone(Kind.BURN, level, at, radius, 60, player.getUUID(), r, blast * 0.25f));
                    }
                    ball.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                    ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.8f, 0);
                    level.addFreshEntity(ball);
                    sound(player, SoundEvents.BLAZE_SHOOT, 1f, 1f);
                });
                yield true;
            }
            case FROST_NOVA -> {
                double radius = r >= 3 ? 6 : 5;
                int root = r >= 2 ? 60 : 40;
                for (LivingEntity e : enemiesAround(player, player.position(), radius)) {
                    hit(player, e, (float) (sp * 1.2), Damage.Type.FROST);
                    if (boss(e)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
                    } else {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, root, 9, false, false), player);
                        e.setTicksFrozen(Math.max(e.getTicksFrozen(), root + 140));
                        if (e instanceof Mob mob) {
                            mob.getNavigation().stop();
                        }
                        if (r >= 4) {
                            FROZEN.put(e, now + root);
                        }
                    }
                }
                ring(level, player.position(), radius, ParticleTypes.SNOWFLAKE);
                sound(player, SoundEvents.GLASS_BREAK, 1f, 0.7f);
                yield true;
            }
            case BLINK -> {
                double range = r >= 2 ? 10 : 8;
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getLookAngle().scale(range));
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
                float base = (float) (sp * (r >= 4 ? 1.9 : 1.6));
                int jumps = r >= 2 ? 4 : 3;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY(), player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                Scheduler.later(12, () -> {
                    if (!player.isAlive() || !first.isAlive()) {
                        return;
                    }
                    float dmg = base;
                    Set<LivingEntity> done = new HashSet<>();
                    LivingEntity current = first;
                    Vec3 from = player.getEyePosition();
                    for (int jump = 0; jump <= jumps && current != null; jump++) {
                        done.add(current);
                        beam(level, from, current.getEyePosition());
                        hit(player, current, dmg, Damage.Type.LIGHTNING);
                        if (!boss(current)) {
                            current.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 9, false, false), player);
                            current.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6, 9, false, false), player);
                        }
                        if (!(r >= 3 && jump == 0)) {
                            dmg *= 0.75f;
                        }
                        from = current.getEyePosition();
                        LivingEntity next = null;
                        double best = 36;
                        for (LivingEntity e : enemiesAround(player, current.position(), 6)) {
                            double d = e.distanceToSqr(current);
                            if (!done.contains(e) && d < best) {
                                best = d;
                                next = e;
                            }
                        }
                        current = next;
                    }
                    sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
                });
                yield true;
            }
            // ---------------------------------------------------------- Templar
            case CONSECRATE -> {
                ZONES.add(new Zone(Kind.CONSECRATE, level, player.position(), r >= 4 ? 5 : 4, r >= 2 ? 200 : 160, player.getUUID(), r, (float) (pw * 0.4)));
                sound(player, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
                yield true;
            }
            case HOLY_SMITE -> {
                // Rides on the next melee hit or bolt within 10 s.
                rt.smiteUntil = now + 200;
                rt.smiteRank = r;
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1), player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                sound(player, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
                yield true;
            }
            case DIVINE_SHIELD -> {
                LivingEntity looked = lookTarget(player, 8);
                ServerPlayer target = looked instanceof ServerPlayer p ? p : player;
                float amount = target.getMaxHealth() * (r >= 2 ? 0.30f : 0.25f);
                int ticks = r >= 3 ? 160 : 120;
                AttributeInstance max = target.getAttribute(Attributes.MAX_ABSORPTION);
                if (max != null) {
                    max.addOrReplacePermanentModifier(new AttributeModifier(SHIELD_ABSORPTION, amount, AttributeModifier.Operation.ADD_VALUE));
                }
                target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), amount));
                SHIELDS.put(target.getUUID(), new Shield(now + ticks, r, amount));
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY(1), target.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
                sound(target, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1f, 0.8f);
                yield true;
            }
            case JUDGMENT -> {
                Vec3 point = lookPoint(player, 20);
                double radius = r >= 3 ? 4 : 3;
                double mult = r >= 2 ? 3.0 : 2.5;
                for (int t = 0; t < 36; t += 6) {
                    Scheduler.later(t + 1, () -> ring(level, point, radius, ParticleTypes.END_ROD));
                }
                // 0.8 s cast, then the pillar a second later.
                Scheduler.later(36, () -> {
                    if (!player.isAlive()) {
                        return;
                    }
                    float base = (float) (power(player) * increased(player, Rpg.stats(player), Archetype.TEMPLAR) * mult);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(point);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    for (LivingEntity e : enemiesAround(player, point, radius)) {
                        hit(player, e, e.getHealth() < e.getMaxHealth() * 0.25f ? base * 2 : base, Damage.Type.SPIRIT);
                        if (r >= 4 && e.isAlive() && !boss(e) && e.getHealth() < e.getMaxHealth() * 0.15f) {
                            // Credited to the Templar so it drops loot and xp.
                            e.setLastHurtByPlayer(player);
                            e.kill();
                        }
                    }
                });
                yield true;
            }
        };
    }

    // ================================================================== hooks from the damage rules

    /** Riposte: a melee hit on you during the stance is parried and countered with a 200% W crit. */
    static void riposte(ServerPlayer player, LivingEntity attacker, Rpg.Runtime rt) {
        Stats s = Rpg.stats(player);
        float counter = (float) (weapon(player) * increased(player, s, Archetype.DUELIST) * 2 * (1 + s.critDamage() / 100));
        hit(player, attacker, counter, Damage.Type.SLASH);
        if (rt.riposteRank >= 3) {
            bleed(player, attacker, counter * 0.3f, 4);
        }
        if (rt.riposteRank >= 4 && rt.riposteFrom != null && rt.riposteRefund > 0) {
            Rpg.refund(player, rt.riposteFrom, rt.riposteRefund);
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.4f);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(1), attacker.getZ(), 15, 0.3, 0.3, 0.3, 0.2);
    }

    /** A melee hit landed: Shadow Step's backstab and Holy Smite. Returns the multiplier for the hit. */
    static double onMeleeHit(ServerPlayer attacker, LivingEntity victim) {
        Rpg.Runtime rt = Rpg.runtime(attacker);
        long now = attacker.serverLevel().getGameTime();
        double mult = 1;
        if (now < rt.backstabUntil) {
            rt.backstabUntil = 0;
            mult = 1.5;
            if (rt.backstabPoison) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker);
            }
        }
        smite(attacker, victim, rt, now);
        return mult;
    }

    /** A wand or staff bolt landed: Holy Smite rides on it too. */
    static void onBoltHit(ServerPlayer owner, LivingEntity target) {
        smite(owner, target, Rpg.runtime(owner), owner.serverLevel().getGameTime());
    }

    private static void smite(ServerPlayer player, LivingEntity target, Rpg.Runtime rt, long now) {
        if (now >= rt.smiteUntil) {
            return;
        }
        rt.smiteUntil = 0;
        int r = rt.smiteRank;
        float amount = (float) (power(player) * increased(player, Rpg.stats(player), Archetype.TEMPLAR) * (r >= 2 ? 2.0 : 1.5));
        // A tick later, so it isn't swallowed by the hit's own hurt cooldown.
        Scheduler.later(1, () -> {
            List<LivingEntity> hits = new ArrayList<>();
            hits.add(target);
            if (r >= 3) {
                for (LivingEntity e : enemiesAround(player, target.position(), 2)) {
                    if (e != target) {
                        hits.add(e);
                    }
                }
            }
            for (LivingEntity e : hits) {
                if (!e.isAlive()) {
                    continue;
                }
                hit(player, e, amount, Damage.Type.SPIRIT);
                if (spiritWeak(e) && !boss(e)) {
                    Poise.stagger(e);
                }
                player.serverLevel().sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(1), e.getZ(), 10, 0.2, 0.4, 0.2, 0.05);
            }
            sound(target, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.6f);
        });
    }

    /** More or less damage from a player's hit: Berserk (+20%) and War Cry. */
    static float dealtMultiplier(ServerPlayer attacker) {
        long now = attacker.serverLevel().getGameTime();
        float mult = 1;
        if (now < Rpg.runtime(attacker).berserkUntil) {
            mult *= 1.2f;
        }
        Rally rally = RALLIED.get(attacker.getUUID());
        if (rally != null) {
            if (now < rally.until()) {
                mult *= 1 + rally.pct();
            } else {
                RALLIED.remove(attacker.getUUID());
            }
        }
        return mult;
    }

    /** More or less damage to a target: Hunter's Mark, Frost Nova's freeze, Berserk (+20% taken), a daunted attacker. */
    static float takenMultiplier(LivingEntity victim, DamageSource source) {
        long now = victim.level().getGameTime();
        float mult = 1;
        Mark mark = MARKED.get(victim.getUUID());
        if (mark != null) {
            if (now < mark.until()) {
                mult *= 1 + mark.extra();
            } else {
                MARKED.remove(victim.getUUID());
            }
        }
        Long frozen = FROZEN.remove(victim);
        if (frozen != null && now < frozen + 100) {
            mult *= 1.25f;
        }
        if (victim instanceof ServerPlayer player && now < Rpg.runtime(player).berserkUntil) {
            mult *= 1.2f;
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            Long daunted = DAUNTED.get(attacker);
            if (daunted != null && now < daunted) {
                mult *= 0.9f;
            }
        }
        return mult;
    }

    /** Divine Shield: no new bleeds or fractures while it holds. */
    static boolean shielded(ServerPlayer player) {
        Shield s = SHIELDS.get(player.getUUID());
        return s != null && player.serverLevel().getGameTime() < s.until && player.getAbsorptionAmount() > 0;
    }

    /** The Consecrate the player stands in, or null. */
    static Zone consecrated(ServerPlayer player) {
        for (Zone z : ZONES) {
            if (z.kind == Kind.CONSECRATE && z.contains(player)) {
                return z;
            }
        }
        return null;
    }

    static boolean berserkPausesBleeds(ServerPlayer player) {
        return Rpg.runtime(player).berserkRank >= 3;
    }

    /** Inside a Smoke Bomb: players count as sneaking. */
    public static boolean inSmoke(LivingEntity entity) {
        for (Zone z : ZONES) {
            if (z.kind == Kind.SMOKE && z.contains(entity)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== ticking

    /** Leap Slam: once you land, smash. */
    static void tickLeap(ServerPlayer player, Rpg.Runtime rt) {
        rt.leapTicks--;
        if (rt.leapTicks < 36 && (player.onGround() || player.isInWater())) {
            rt.leapTicks = 0;
            player.fallDistance = 0;
            leapLand(player, rt.leapRank);
        } else if (rt.leapTicks == 0) {
            player.fallDistance = 0;
        }
    }

    private static void leapLand(ServerPlayer player, int r) {
        ServerLevel level = player.serverLevel();
        double radius = r >= 3 ? 4 : 3;
        float amount = (float) (weapon(player) * increased(player, Rpg.stats(player), Archetype.WARRIOR) * (r >= 2 ? 2.0 : 1.5));
        boolean killed = false;
        for (LivingEntity e : enemiesAround(player, player.position(), radius)) {
            hit(player, e, amount, Damage.Type.BLUNT);
            // Double poise: the blunt hit builds its own, this adds as much again.
            Poise.add(e, amount);
            if (r >= 3) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
            }
            killed |= !e.isAlive();
        }
        if (r >= 4 && killed) {
            Rpg.resetCooldown(player, ActiveSkill.LEAP_SLAM);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 3, radius / 3, 0.1, radius / 3, 0);
        ring(level, player.position(), radius, ParticleTypes.CAMPFIRE_COSY_SMOKE);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.2f);
    }

    /** Every tick for a player: ends Berserk, keeps Whirlwind from blocking. */
    static void tickPlayer(ServerPlayer player, Rpg.Runtime rt, long now) {
        if (rt.berserkUntil != 0 && now >= rt.berserkUntil) {
            rt.berserkUntil = 0;
            rt.berserkRank = 1;
            AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (speed != null) {
                speed.removeModifier(BERSERK_SPEED);
            }
        }
        if (now < rt.whirlUntil && player.isUsingItem() && player.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            player.stopUsingItem();
        }
        Shield shield = SHIELDS.get(player.getUUID());
        if (shield != null && (now >= shield.until || player.getAbsorptionAmount() <= 0)) {
            SHIELDS.remove(player.getUUID());
            boolean broke = player.getAbsorptionAmount() <= 0 && now < shield.until;
            AttributeInstance max = player.getAttribute(Attributes.MAX_ABSORPTION);
            if (max != null) {
                max.removeModifier(SHIELD_ABSORPTION);
            }
            player.setAbsorptionAmount(Math.min(player.getAbsorptionAmount(), player.getMaxAbsorption()));
            if (broke && shield.rank >= 4) {
                for (LivingEntity e : enemiesAround(player, player.position(), 4)) {
                    Vec3 push = e.position().subtract(player.position()).multiply(1, 0, 1);
                    if (push.lengthSqr() > 1.0e-4) {
                        e.push(push.normalize().x * 1.2, 0.4, push.normalize().z * 1.2);
                        e.hurtMarked = true;
                    }
                }
                player.serverLevel().sendParticles(ParticleTypes.FLASH, player.getX(), player.getY(1), player.getZ(), 1, 0, 0, 0, 0);
                sound(player, SoundEvents.SHIELD_BREAK, 1f, 1f);
            }
        }
    }

    private static void serverTick(MinecraftServer server) {
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            long now = z.level.getGameTime();
            if (now >= z.until) {
                it.remove();
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(z.owner);
            if (owner == null || owner.level() != z.level) {
                it.remove();
                continue;
            }
            zoneTick(z, owner, now);
        }
        // Bleeds and lingering poison on monsters, once a second each.
        for (Iterator<Map.Entry<LivingEntity, Bleed>> it = BLEEDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Bleed> e = it.next();
            LivingEntity target = e.getKey();
            Bleed b = e.getValue();
            long now = target.level().getGameTime();
            if (!target.isAlive() || now >= b.until()) {
                it.remove();
            } else if (now % 20 == 0) {
                ServerPlayer owner = server.getPlayerList().getPlayer(b.owner());
                target.invulnerableTime = 0;
                target.hurt(bleedSource(target, owner), b.perSecond());
                if (target.level() instanceof ServerLevel level) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()), target.getX(), target.getY(0.5), target.getZ(), 4, 0.2, 0.2, 0.2, 0);
                }
            }
        }
        List<LivingEntity> pending = new ArrayList<>();
        for (Iterator<Map.Entry<LivingEntity, Linger>> it = LINGER.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Linger> e = it.next();
            long now = e.getKey().level().getGameTime();
            if (!e.getKey().isAlive() || now >= e.getValue().until) {
                it.remove();
            } else if (now % 20 == 0 && e.getValue().lastPulse != now) {
                pending.add(e.getKey());
            }
        }
        for (LivingEntity target : pending) {
            Linger l = LINGER.get(target);
            ServerPlayer owner = l == null ? null : server.getPlayerList().getPlayer(l.owner);
            if (owner != null) {
                hit(owner, target, l.perSecond, Damage.Type.POISON);
            }
        }
        // Hunter's Mark at rank 3: no stealth.
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % 10 != 0) {
                continue;
            }
            for (Entity e : level.getAllEntities()) {
                if (e instanceof LivingEntity living) {
                    Mark m = MARKED.get(living.getUUID());
                    if (m != null && m.rank() >= 3 && level.getGameTime() < m.until()) {
                        living.removeEffect(MobEffects.INVISIBILITY);
                    }
                }
            }
        }
    }

    private static void zoneTick(Zone z, ServerPlayer owner, long now) {
        long age = now - z.start;
        ServerLevel level = z.level;
        if (age % 5 == 0) {
            ParticleOptions p = switch (z.kind) {
                case SMOKE -> ParticleTypes.LARGE_SMOKE;
                case POISON -> ParticleTypes.ITEM_SLIME;
                case CONSECRATE -> ParticleTypes.END_ROD;
                case BURN -> ParticleTypes.FLAME;
            };
            if (z.kind == Kind.SMOKE) {
                level.sendParticles(p, z.at.x, z.at.y + 1, z.at.z, 20, z.radius / 2, 0.8, z.radius / 2, 0.01);
            } else {
                ring(level, z.at, z.radius, p);
            }
        }
        switch (z.kind) {
            case SMOKE -> {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, Rpg.around(z.at, z.radius))) {
                    if (!z.contains(mob)) {
                        continue;
                    }
                    if (mob instanceof Enemy && mob.getTarget() != null && !(mob instanceof BossEntity)) {
                        mob.setTarget(null);
                    }
                    if (z.rank >= 3 && mob instanceof Enemy && age % 10 == 0) {
                        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), owner);
                    }
                }
            }
            case POISON, BURN, CONSECRATE -> {
                if (age % 20 != 0) {
                    return;
                }
                for (LivingEntity e : enemiesAround(owner, z.at, z.radius)) {
                    if (!z.contains(e)) {
                        continue;
                    }
                    switch (z.kind) {
                        case POISON -> {
                            hit(owner, e, z.amount, Damage.Type.POISON);
                            if (z.rank >= 3) {
                                Linger l = LINGER.computeIfAbsent(e, k -> new Linger(z.owner, z.amount, 0));
                                l.until = now + 60;
                                l.lastPulse = now;
                            }
                        }
                        case BURN -> {
                            hit(owner, e, z.amount, Damage.Type.FIRE);
                            e.igniteForSeconds(2);
                        }
                        case CONSECRATE -> {
                            if (spiritWeak(e)) {
                                hit(owner, e, z.amount, Damage.Type.SPIRIT);
                            }
                        }
                        default -> { }
                    }
                }
            }
        }
    }

    // ================================================================== helpers

    /** A skill hit of one damage type, on the 9.5 scale. Multi-hit skills skip the hurt cooldown. */
    static void hit(ServerPlayer player, LivingEntity target, float amount, Damage.Type type) {
        if (amount <= 0 || !target.isAlive()) {
            return;
        }
        target.invulnerableTime = 0;
        Ultimates.typed(player, target, amount, type);
    }

    /** A bleed on a monster: damage a second for a few seconds (players bleed by body part, 5). */
    static void bleed(ServerPlayer owner, LivingEntity target, float perSecond, int seconds) {
        if (target instanceof Player || boss(target)) {
            return;
        }
        double longer = 1 + Rpg.stats(owner).get(Stat.BLEED_DURATION) / 100;
        long until = target.level().getGameTime() + Math.round(seconds * 20 * longer);
        Bleed old = BLEEDS.get(target);
        BLEEDS.put(target, new Bleed(owner.getUUID(), Math.max(perSecond, old == null ? 0 : old.perSecond()), Math.max(until, old == null ? 0 : old.until())));
    }

    private static DamageSource bleedSource(LivingEntity target, ServerPlayer owner) {
        Holder<DamageType> type = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(com.cavmkii.seancraft.tarkov.Health.BLEED).map(h -> (Holder<DamageType>) h)
                .orElseGet(() -> target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC));
        return owner == null ? new DamageSource(type) : new DamageSource(type, owner);
    }

    /** About to attack: a creeper swelling, a skeleton drawing, an illager casting. */
    private static boolean windingUp(LivingEntity e) {
        if (e instanceof Creeper c) {
            return c.getSwellDir() > 0;
        }
        if (e instanceof SpellcasterIllager s && s.isCastingSpell()) {
            return true;
        }
        return e instanceof Mob && e.isUsingItem();
    }

    private static void interrupt(LivingEntity e) {
        if (e instanceof Creeper c) {
            c.setSwellDir(-1);
        }
        e.stopUsingItem();
        Poise.stagger(e);
    }

    /** Templar keystone: burn monsters within 4 blocks every second. */
    static void holyAura(ServerPlayer player, Stats stats) {
        for (LivingEntity e : enemiesAround(player, player.position(), 4)) {
            damage(player, e, (float) (1 + stats.intel() * 0.03));
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(1), e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    /** Ability damage that the melee rules don't scale again (vanilla scale: the pipeline multiplies by 5). */
    static void damage(ServerPlayer player, LivingEntity target, float amount) {
        double hex = Rpg.stats(player).get(Stat.HEX);
        if (hex > 0) {
            // The Warlock's Hex: the target takes more from everyone for 5 seconds.
            RpgDamage.HEXED.put(target.getUUID(), new RpgDamage.Hex(player.serverLevel().getGameTime() + 100, (float) hex / 100));
        }
        RpgDamage.applyingSkill = true;
        try {
            target.hurt(player.damageSources().indirectMagic(player, player), amount);
        } finally {
            RpgDamage.applyingSkill = false;
        }
    }

    static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
        // The Sorcerer's Wide Casting.
        radius *= 1 + Rpg.stats(player).get(Stat.SPELL_AREA) / 100;
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

    static LivingEntity lookTarget(ServerPlayer player, double range) {
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

    /** Where the crosshair lands: the monster under it, else the block, else the end of the range. */
    private static Vec3 lookPoint(ServerPlayer player, double range) {
        LivingEntity target = lookTarget(player, range);
        if (target != null) {
            return target.position();
        }
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

    private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
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
