package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.combat.Damage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Daylan Little, tier 1 (design doc A.4). Three careers in one fight: the Influencer hides behind bought followers and
 * is open while he types; the Soft Launch pulls everyone in; the Firefighter hoses and chops; the Hard Launch pulls twice
 * as hard and ends in a shockwave; the Policeman whistles, tases, cuffs and hides behind a riot shield.
 */
public class Daylan extends BossEntity {
    static final int MAX_FOLLOWERS = 6;
    static final int FOLLOWER_COUNT = 40_000;
    static final int DIVE_EVERY = 60;
    static final float FOLLOWER_DAMAGE = 15;
    private static final Map<Damage.Type, Float> SPIRIT = new EnumMap<>(Map.of(Damage.Type.SPIRIT, 1f));

    private final List<Follower> followers = new ArrayList<>();
    /** Freeze!: where each player stood when the check began, and the farthest they've moved since. */
    private final Map<UUID, Vec3> freezeFrom = new HashMap<>();
    private final Map<UUID, Double> freezeMoved = new HashMap<>();
    private long shieldUntil;

    public Daylan(EntityType<? extends Daylan> type, Level level) {
        super(type, level);
    }

    @Override
    protected String defId() {
        return "daylan";
    }

    int livingFollowers() {
        followers.removeIf(f -> !f.isAlive());
        return followers.size();
    }

    @Override
    protected String barName() {
        String name = super.barName();
        if (phase == 0) {
            int alive = livingFollowers();
            int shown = alive == 0 ? 3 : Math.round((float) FOLLOWER_COUNT * alive / MAX_FOLLOWERS);
            return name + " · " + String.format("%,d", shown) + " followers";
        }
        return name;
    }

    // ================================================================== damage taken

    /** Firefighter: half damage from fire. He's a volunteer, and not a good one. */
    @Override
    public float resistance(Damage.Type type) {
        float r = super.resistance(type);
        return phase == 1 && type == Damage.Type.FIRE ? r * 0.5f : r;
    }

    @Override
    protected float incoming(DamageSource source, float amount) {
        if (level().getGameTime() < shieldUntil && source.getDirectEntity() instanceof Projectile shot) {
            // The riot shield stops anything thrown or shot from the front (same facing check as Mark's Tenure).
            Vec3 from = shot.position().subtract(position()).normalize();
            if (getLookAngle().dot(from) > 0.3) {
                level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 1f);
                return 0;
            }
        }
        // Each follower takes 10% off.
        return amount * Math.max(0, 1 - 0.1f * livingFollowers());
    }

    // ================================================================== phases

    @Override
    protected void onPhase(int phase) {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY(0.6), getZ(), 60, 0.6, 0.9, 0.6, 0.05);
        level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 1.5f, 1f);
        if (phase == 1) {
            followers.forEach(f -> f.discard());
            followers.clear();
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            say("firefighter");
        } else if (phase == 2) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            say("policeman");
        }
    }

    /** The Soft Launch and the Hard Launch: a speech that pulls everyone in; the second ends in a shockwave. */
    @Override
    protected void transitionTick(int next, int tick) {
        ServerLevel level = (ServerLevel) level();
        boolean hard = next == 2;
        if (tick == 1) {
            say(hard ? "hard_launch" : "soft_launch");
            level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        if (tick % 5 == 0) {
            ring(level, ParticleTypes.CHERRY_LEAVES, position(), 3 + (tick % 20) / 4.0, 20);
        }
        float strength = hard ? 0.24f : 0.12f;
        for (ServerPlayer p : playersInRing()) {
            Vec3 in = position().subtract(p.position()).multiply(1, 0, 1);
            if (in.length() > 1.5) {
                Vec3 pull = in.normalize().scale(strength);
                p.push(pull.x, 0, pull.z);
                p.hurtMarked = true;
            }
        }
        if (hard && tick == BossEntity.TRANSITION_TICKS - 1) {
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.8f);
            ring(level, ParticleTypes.EXPLOSION, position().add(0, 0.5, 0), 4, 16);
            for (ServerPlayer p : playersInRing()) {
                Vec3 out = p.position().subtract(position()).multiply(1, 0, 1);
                Vec3 dir = out.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : out.normalize();
                // About 8 blocks outward.
                p.setDeltaMovement(dir.x * 2.2, 0.5, dir.z * 2.2);
                p.hurtMarked = true;
            }
        }
    }

    // ================================================================== the fight

    @Override
    protected boolean allowed(BossDef.Attack attack, LivingEntity target) {
        return switch (attack.action()) {
            // Re-summons when fewer than two are left.
            case "followers" -> livingFollowers() < 2;
            // Only types with nobody within 6 blocks.
            case "post" -> playersInRing().stream().noneMatch(p -> p.distanceTo(this) < 6);
            case "brag" -> livingFollowers() > 0;
            default -> true;
        };
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved() || !(level() instanceof ServerLevel level) || def() == null) {
            return;
        }
        long now = level.getGameTime();
        if (phase == 0 && now % DIVE_EVERY == 0 && livingFollowers() > 0) {
            List<ServerPlayer> players = playersInRing();
            List<Follower> idle = followers.stream().filter(f -> !f.diving()).toList();
            if (!players.isEmpty() && !idle.isEmpty()) {
                idle.get(getRandom().nextInt(idle.size())).dive(players.get(getRandom().nextInt(players.size())));
            }
        }
        if (now < shieldUntil) {
            if (getOffhandItem().isEmpty()) {
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        } else if (!getOffhandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        if (phase == 2 && now % 10 == 0) {
            // Red and blue light flickers across him.
            float red = (now / 10) % 2 == 0 ? 1 : 0;
            level.sendParticles(new DustParticleOptions(new Vector3f(red, 0.1f, 1 - red), 1.2f), getX(), getY(1.1), getZ(), 3, 0.3, 0.3, 0.3, 0);
        }
    }

    /** A diving follower reached a player. */
    void followerHit(ServerPlayer player) {
        if (strike(player, FOLLOWER_DAMAGE, SPIRIT) && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1), player.getZ(), 10, 0.2, 0.3, 0.2, 0.05);
        }
    }

    @Override
    protected void windupSound(BossDef.Attack attack) {
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "followers" -> level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2f, 1.4f);
            case "post" -> say("typing");
            case "flash" -> level.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.8f);
            case "hose" -> level.playSound(null, blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.HOSTILE, 1.5f, 0.7f);
            case "drill" -> level.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 1.2f);
            case "freeze" -> {
                say("freeze");
                level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 3f, 2f);
            }
            case "taser" -> say("taser");
            case "shield" -> level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1.5f, 0.8f);
            default -> super.windupSound(attack);
        }
    }

    @Override
    protected void telegraph(BossDef.Attack attack, int tick, LivingEntity target) {
        super.telegraph(attack, tick, target);
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "post" -> {
                // Head down, typing: the window to hit him hard.
                if (tick % 2 == 0) {
                    level.playSound(null, blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.HOSTILE, 0.5f, 1.6f + getRandom().nextFloat() * 0.4f);
                }
                level.sendParticles(ParticleTypes.GLOW, getX(), getY(0.8), getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
            case "flash" -> {
                Vec3 look = getLookAngle();
                level.sendParticles(ParticleTypes.FLASH, getX() + look.x, getY(0.9) + look.y, getZ() + look.z, 1, 0, 0, 0, 0);
            }
            case "hose", "taser", "cuffs" -> {
                if (tick < attack.windup() / 2 && target != null) {
                    aim = target.position();
                }
                if (tick % 2 == 0) {
                    Vec3 dir = aim.add(0, 1, 0).subtract(getEyePosition()).normalize();
                    for (int i = 1; i < 5; i++) {
                        Vec3 p = getEyePosition().add(dir.scale(i));
                        level.sendParticles(attack.action().equals("hose") ? ParticleTypes.SPLASH : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                }
            }
            case "drill" -> {
                if (tick % 3 == 0) {
                    // Cones mark the zone, then the alarm rings.
                    ring(level, new DustParticleOptions(new Vector3f(1f, 0.45f, 0f), 1.5f), aim, attack.number("radius", 4), 10);
                    ring(level, ParticleTypes.DRIPPING_WATER, aim.add(0, 2.5, 0), attack.number("radius", 4) * 0.7, 12);
                }
                if (tick % 8 == 0) {
                    level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5f, 1.6f);
                }
            }
            case "chop", "nightstick" -> level.sendParticles(ParticleTypes.CRIT, getX(), getY(1.2), getZ(), 2, 0.3, 0.3, 0.3, 0.05);
            case "freeze" -> {
                if (tick == attack.windup() / 2) {
                    level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 3f, 2f);
                }
            }
            default -> {}
        }
    }

    @Override
    protected void perform(BossDef.Attack attack, int tick, LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "followers" -> {
                if (tick == 1) {
                    summonFollowers(level);
                }
            }
            case "post" -> {
                if (tick == 1) {
                    post(level, attack, target);
                }
            }
            case "brag" -> {
                if (tick == 1) {
                    say("brag");
                }
                if (tick % 20 == 0) {
                    // 1% a second for each follower still around.
                    int alive = livingFollowers();
                    if (alive > 0) {
                        setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * 0.01f * alive));
                        level.sendParticles(ParticleTypes.HEART, getX(), getY(1.1), getZ(), alive, 0.4, 0.3, 0.4, 0);
                    }
                }
            }
            case "flash" -> {
                if (tick == 1) {
                    flash(level, attack);
                }
            }
            case "hose" -> {
                if (tick == 1) {
                    hose(level, attack);
                }
            }
            case "chop", "nightstick" -> {
                if (tick == 1) {
                    level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.5f, 0.6f);
                    Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 1.5, getY(0.6), getZ() + look.z * 1.5, 2, 0.4, 0.2, 0.4, 0);
                    for (ServerPlayer p : playersInRing()) {
                        if (inArc(p, attack.number("radius", 4), attack.number("angle", 90))) {
                            hit(p, attack, attack.damage());
                        }
                    }
                }
            }
            case "drill" -> {
                if (tick == 1) {
                    float r = attack.number("radius", 4);
                    level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2f, 0.6f);
                    level.sendParticles(ParticleTypes.SPLASH, aim.x, aim.y + 1, aim.z, 120, r / 2, 0.6, r / 2, 0.3);
                    for (ServerPlayer p : playersInRing()) {
                        if (near(p, aim, r)) {
                            hit(p, attack, attack.damage());
                            extinguish(p);
                        }
                    }
                }
            }
            case "freeze" -> freezeCheck(level, attack, tick);
            case "taser" -> {
                if (tick == 1) {
                    line(level, ParticleTypes.ELECTRIC_SPARK, attack);
                    level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.6f, 2f);
                    for (ServerPlayer p : playersInRing()) {
                        if (onLine(p, aim.add(0, 1, 0), attack.maxRange(), attack.number("width", 1f)) && hit(p, attack, attack.damage())) {
                            Restraint.root(p, Math.round(attack.number("stun", 0.6f) * 20), false, "Stunned!");
                        }
                    }
                }
            }
            case "cuffs" -> {
                if (tick == 1) {
                    line(level, ParticleTypes.CRIT, attack);
                    level.playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5f, 1.2f);
                    for (ServerPlayer p : playersInRing()) {
                        if (onLine(p, aim.add(0, 1, 0), attack.maxRange(), attack.number("width", 1f))
                                && Restraint.root(p, Math.round(attack.number("root", 2) * 20), true, "Cuffed! A slash breaks them.")) {
                            say("cuffs");
                        }
                    }
                }
            }
            case "shield" -> {
                if (tick == 1) {
                    shieldUntil = level.getGameTime() + Math.round(attack.number("stance", 4) * 20);
                }
            }
            default -> {}
        }
    }

    private void summonFollowers(ServerLevel level) {
        say("followers");
        int have = livingFollowers();
        for (int i = have; i < MAX_FOLLOWERS; i++) {
            Follower f = BossContent.FOLLOWER.create(level);
            if (f == null) {
                continue;
            }
            f.setOwner(this, i);
            f.moveTo(getX(), getY(1.5), getZ(), 0, 0);
            level.addFreshEntity(f);
            followers.add(f);
            level.playSound(null, blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.HOSTILE, 0.8f, 1.2f + i * 0.1f);
        }
        level.sendParticles(ParticleTypes.FLASH, getX(), getY(1.2), getZ(), 1, 0, 0, 0, 0);
    }

    /** Generated Post: five text bubbles in a fan. */
    private void post(ServerLevel level, BossDef.Attack attack, LivingEntity target) {
        say("post");
        Vec3 to = (target != null ? target.position() : aim).subtract(position()).multiply(1, 0, 1).normalize();
        int count = Math.round(attack.number("count", 5));
        double spread = Math.toRadians(attack.number("angle", 50));
        for (int i = 0; i < count; i++) {
            double a = Math.atan2(to.z, to.x) - spread / 2 + spread * i / (count - 1);
            BossShot shot = new BossShot(level, this, attack, new ItemStack(i % 2 == 0 ? Items.PAPER : Items.WRITABLE_BOOK));
            shot.setPos(getX(), getEyeY() - 0.3, getZ());
            shot.shoot(Math.cos(a), 0, Math.sin(a), 0.8f, 0);
            level.addFreshEntity(shot);
        }
        level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.5f, 1.8f);
    }

    /** Ring Light Flash: blinds anyone in front of him who's looking at him. */
    private void flash(ServerLevel level, BossDef.Attack attack) {
        level.playSound(null, blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.HOSTILE, 2f, 1.6f);
        int ticks = Math.round(attack.number("blind", 2) * 20);
        for (ServerPlayer p : playersInRing()) {
            if (!inArc(p, attack.number("radius", 8), attack.number("angle", 90))) {
                continue;
            }
            Vec3 toHim = getEyePosition().subtract(p.getEyePosition()).normalize();
            if (p.getLookAngle().dot(toHim) > 0.3) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks + 20, 0, false, false));
                say("flash");
            }
        }
    }

    /** Hose Blast: a stream along a line; it pushes people back and puts out fire, buffs included. */
    private void hose(ServerLevel level, BossDef.Attack attack) {
        line(level, ParticleTypes.SPLASH, attack);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5f, 0.8f);
        for (ServerPlayer p : playersInRing()) {
            if (onLine(p, aim.add(0, 1, 0), attack.maxRange(), attack.number("width", 1.5f)) && hit(p, attack, attack.damage())) {
                Vec3 back = p.position().subtract(position()).multiply(1, 0, 1).normalize().scale(attack.number("push", 8) / 3.6);
                p.setDeltaMovement(back.x, 0.35, back.z);
                p.hurtMarked = true;
                extinguish(p);
            }
        }
    }

    private static void extinguish(ServerPlayer p) {
        p.clearFire();
        p.removeEffect(MobEffects.FIRE_RESISTANCE);
    }

    private void line(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, BossDef.Attack attack) {
        Vec3 from = getEyePosition();
        Vec3 dir = aim.add(0, 1, 0).subtract(from).normalize();
        for (int i = 0; i < attack.maxRange() * 2; i++) {
            Vec3 p = from.add(dir.scale(i / 2.0));
            level.sendParticles(particle, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Freeze!: after the whistle, anyone who moves more than half a block during the check is ticketed. Stunned and
     * cuffed players are left alone, and small adjustments are allowed so lag isn't punished.
     */
    private void freezeCheck(ServerLevel level, BossDef.Attack attack, int tick) {
        List<ServerPlayer> players = playersInRing();
        if (tick == 1) {
            freezeFrom.clear();
            freezeMoved.clear();
            for (ServerPlayer p : players) {
                freezeFrom.put(p.getUUID(), p.position());
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("FREEZE!").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return;
        }
        for (ServerPlayer p : players) {
            Vec3 from = freezeFrom.get(p.getUUID());
            if (from != null) {
                freezeMoved.merge(p.getUUID(), p.position().multiply(1, 0, 1).distanceTo(from.multiply(1, 0, 1)), Math::max);
            }
        }
        if (tick < attack.active()) {
            return;
        }
        int ticks = Math.round(attack.number("slow_time", 3) * 20);
        for (ServerPlayer p : players) {
            if (freezeMoved.getOrDefault(p.getUUID(), 0.0) > 0.5 && !Restraint.rooted(p)) {
                if (hit(p, attack, attack.damage())) {
                    Restraint.slow(p, attack.number("slow", 0.4f), ticks);
                    say("ticket");
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY(2.1), p.getZ(), 3, 0.3, 0.1, 0.3, 0);
                }
            }
        }
    }

    // ================================================================== death and drops

    @Override
    public void die(DamageSource source) {
        followers.forEach(Follower::discard);
        followers.clear();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            followers.forEach(Follower::discard);
        }
        super.remove(reason);
    }

    @Override
    protected List<ItemStack> drops(ServerPlayer player, boolean firstKill) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(BossContent.FOLLOWER_LANTERN));
        if (firstKill) {
            out.add(new ItemStack(BossContent.TROPHY_CLOUT));
            out.add(new ItemStack(BossContent.BLUE_CHECK));
        }
        // A share of rare tier 1 materials.
        out.add(new ItemStack(Items.COPPER_INGOT, 6 + getRandom().nextInt(7)));
        out.add(new ItemStack(Items.RABBIT_HIDE, 2 + getRandom().nextInt(3)));
        if (getRandom().nextFloat() < 0.5f) {
            out.add(new ItemStack(Items.EMERALD, 1 + getRandom().nextInt(3)));
        }
        return out;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
