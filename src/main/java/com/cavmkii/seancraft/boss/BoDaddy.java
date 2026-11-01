package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.combat.Damage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Bo Daddy Harris, tier 1 (design doc A.5). Outlaw Country fights close with roars, a guitar sweep, a lasso and an
 * off-beat line dance; Silky R&B slows the ground with croons and Slow Jam zones, tosses roses and saves up for the
 * Drop. Every roar and Drop leaves him out of breath and wide open. The music never lines up with his attacks.
 */
public class BoDaddy extends BossEntity {
    public static final net.minecraft.resources.ResourceLocation TWINKLE = com.cavmkii.seancraft.SeanCraft.id("music_disc.twinkle");
    static final Holder<SoundEvent> MUSIC = Holder.direct(SoundEvent.createFixedRangeEvent(TWINKLE, 64));
    static final int MUSIC_TICKS = 46 * 20;
    static final int METER_FULL = 4;
    static final int PULSE_TICKS = 100;
    static final int SHRIEKER_RADIUS = 16;

    /** Out of breath until this game time: everything hurts him half again as much. */
    private long breathUntil;
    private long nextComplaint;
    private long nextMusic;
    /** Roars and Drops since a shrieker was last broken. */
    private int meter;
    private long nextPulse;
    /** Slow Jam zones on the ground: where, and until when. */
    private final List<Zone> zones = new ArrayList<>();
    /** Ticks into the line dance at which each stomp lands, rolled when it starts. */
    private final int[] stomps = new int[3];

    private record Zone(Vec3 at, float radius, long until) {}

    public BoDaddy(EntityType<? extends BoDaddy> type, Level level) {
        super(type, level);
    }

    @Override
    protected String defId() {
        return "bo_daddy";
    }

    @Override
    void start() {
        super.start();
        placeShriekers((ServerLevel) level());
    }

    /** Three sculk shriekers at the edge of the stage, if they aren't there already (A.5 arena). */
    private void placeShriekers(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + 0.5;
            int x = center.getX() + (int) Math.round(Math.cos(a) * SHRIEKER_RADIUS);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * SHRIEKER_RADIUS);
            BlockPos ground = null;
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (level.getBlockState(p).is(Blocks.SCULK_SHRIEKER)) {
                    ground = null;
                    break;
                }
                if (ground == null && level.getBlockState(p).canBeReplaced() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                    ground = p;
                }
            }
            if (ground == null && level.getBlockState(new BlockPos(x, center.getY(), z)).isAir()) {
                ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
                if (Math.abs(ground.getY() - center.getY()) > 6) {
                    ground = null;
                }
            }
            if (ground != null) {
                level.setBlockAndUpdate(ground, Blocks.SCULK_SHRIEKER.defaultBlockState());
            }
        }
    }

    /** A shrieker in his ring was broken: the meter resets (A.5 custom code). */
    void shriekerBroken() {
        if (meter >= METER_FULL) {
            say("shrieker");
        }
        meter = 0;
    }

    boolean outOfBreath() {
        return level().getGameTime() < breathUntil;
    }

    private void loseBreath(int ticks) {
        breathUntil = level().getGameTime() + ticks;
        meter = Math.min(METER_FULL, meter + 1);
        if (meter == METER_FULL) {
            say("meter");
        }
    }

    @Override
    protected float incoming(DamageSource source, float amount) {
        Damage.Split split = Damage.split(source);
        long now = level().getGameTime();
        if (split != null && (split.get(Damage.Type.FIRE) > 0 || split.get(Damage.Type.FROST) > 0)) {
            // Too busy complaining about the temperature to get his breath back.
            if (outOfBreath()) {
                breathUntil += 10;
            }
            if (now >= nextComplaint) {
                nextComplaint = now + 200;
                say(split.get(Damage.Type.FIRE) > 0 ? "fire" : "frost");
            }
        }
        if (outOfBreath()) {
            if (level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, getX(), getY(0.7), getZ(), 8, 0.3, 0.4, 0.3, 0.2);
            }
            return amount * 1.5f;
        }
        return amount;
    }

    @Override
    protected boolean allowed(BossDef.Attack attack, LivingEntity target) {
        // Off-beat on purpose: attacks start at a random moment, never on the music's pulse.
        return getRandom().nextFloat() < 0.2f;
    }

    @Override
    protected void onPhase(int phase) {
        if (phase == 1) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
            say("rnb");
        }
    }

    @Override
    protected void transitionTick(int next, int tick) {
        // He steps behind a curtain of sculk light.
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY(0.5), getZ(), 4, 0.5, 0.9, 0.5, 0.02);
        if (tick == 30) {
            level.playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 2f, 0.6f);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved() || !(level() instanceof ServerLevel level) || def() == null) {
            return;
        }
        long now = level.getGameTime();
        if (now >= nextMusic) {
            nextMusic = now + MUSIC_TICKS;
            level.playSound(null, center.above(), MUSIC.value(), SoundSource.RECORDS, 3f, 1f);
        }
        if (outOfBreath() && now % 3 == 0) {
            // His breath glows while he gets it back.
            Vec3 mouth = getEyePosition().add(getLookAngle().scale(0.4));
            level.sendParticles(ParticleTypes.WHITE_ASH, mouth.x, mouth.y, mouth.z, 4, 0.15, 0.1, 0.15, 0.01);
        }
        List<ServerPlayer> players = playersInRing();
        zones.removeIf(z -> now >= z.until);
        for (Zone z : zones) {
            if (now % 4 == 0) {
                ring(level, new DustParticleOptions(new Vector3f(0.6f, 0.3f, 0.9f), 1.5f), z.at, z.radius, 28);
                level.sendParticles(ParticleTypes.NOTE, z.at.x, z.at.y + 0.5, z.at.z, 1, z.radius / 2, 0.2, z.radius / 2, 0);
            }
            for (ServerPlayer p : players) {
                if (near(p, z.at, z.radius)) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 1, false, false, true));
                }
            }
        }
        if (meter >= METER_FULL && now >= nextPulse) {
            nextPulse = now + PULSE_TICKS;
            level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2f, 0.8f);
            for (ServerPlayer p : players) {
                if (!BossItems.FollowerLantern.carries(p)) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                }
            }
        }
    }

    @Override
    protected void windupSound(BossDef.Attack attack) {
        switch (attack.action()) {
            case "roar", "drop" -> level().playSound(null, blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.HOSTILE, 2f, 0.5f);
            case "lasso" -> level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 1.5f, 0.7f);
            case "croon", "jam" -> level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 2f, 0.5f);
            default -> super.windupSound(attack);
        }
        if ((attack.action().equals("roar") || attack.action().equals("drop")) && getRandom().nextFloat() < 0.4f) {
            say("breath");
        } else if (attack.action().equals("lasso") || attack.action().equals("dance") || attack.action().equals("rose")
                || attack.action().equals("jam")) {
            say(attack.action());
        }
        if (attack.action().equals("dance")) {
            // Three stomps at uneven gaps, so counting to the music gets you hit.
            int t = 0;
            for (int i = 0; i < 3; i++) {
                t += 8 + getRandom().nextInt(10);
                stomps[i] = t;
            }
        }
    }

    @Override
    protected void telegraph(BossDef.Attack attack, int tick, LivingEntity target) {
        super.telegraph(attack, tick, target);
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "roar", "drop" -> {
                // The chest fills: a deep breath in, the tell for every low note.
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY(0.75), getZ(), 2, 0.2, 0.1, 0.2, -0.02);
                if (attack.action().equals("drop") && tick % 4 == 0) {
                    ring(level, ParticleTypes.SCULK_CHARGE_POP, position(), attack.number("radius", 14) * tick / attack.windup(), 40);
                }
            }
            case "lasso" -> {
                if (tick < attack.windup() / 2 && target != null) {
                    aim = target.position();
                }
            }
            case "croon" -> {
                if (tick % 3 == 0) {
                    Vec3 look = getLookAngle();
                    level.sendParticles(ParticleTypes.NOTE, getX() + look.x * 2, getY(1), getZ() + look.z * 2, 3, 1, 0.5, 1, 0);
                }
            }
            case "jam" -> {
                if (tick % 3 == 0) {
                    ring(level, new DustParticleOptions(new Vector3f(0.6f, 0.3f, 0.9f), 1.2f), aim, attack.number("radius", 4), 24);
                }
            }
            case "arc" -> level.sendParticles(ParticleTypes.CRIT, getX(), getY(1.2), getZ(), 2, 0.3, 0.3, 0.3, 0.05);
            default -> {}
        }
    }

    @Override
    protected void perform(BossDef.Attack attack, int tick, LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "roar" -> {
                if (tick == 1) {
                    roar(level, attack, Math.round(attack.number("breath", 1.5f) * 20));
                }
            }
            case "drop" -> {
                if (tick == 1) {
                    say("drop");
                    level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, 0.6f);
                    roar(level, attack, Math.round(attack.number("breath", 2.5f) * 20));
                }
            }
            case "arc" -> {
                if (tick == 1) {
                    level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.5f);
                    level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.HOSTILE, 2f, 0.6f);
                    Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2, getY(0.6), getZ() + look.z * 2, 3, 0.8, 0.2, 0.8, 0);
                    for (ServerPlayer p : playersInRing()) {
                        if (inArc(p, attack.number("radius", 4), attack.number("angle", 150))) {
                            hit(p, attack, attack.damage());
                        }
                    }
                }
            }
            case "lasso" -> {
                if (tick == 1) {
                    lasso(level, attack);
                }
            }
            case "dance" -> {
                for (int s : stomps) {
                    if (tick == s) {
                        stomp(level, attack);
                    }
                }
            }
            case "croon" -> {
                if (tick == 1) {
                    croon(level, attack);
                }
            }
            case "jam" -> {
                if (tick == 1) {
                    zones.add(new Zone(aim, attack.number("radius", 4), level.getGameTime() + Math.round(attack.number("duration", 8) * 20)));
                    level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 2f, 0.5f);
                }
            }
            case "rose" -> {
                if (tick == 1 && target != null) {
                    BossShot shot = new BossShot(level, this, attack, new ItemStack(Items.POPPY));
                    shot.setPos(getX(), getEyeY() - 0.2, getZ());
                    Vec3 to = target.getEyePosition().subtract(shot.position());
                    shot.shoot(to.x, to.y, to.z, 1.3f, 1f);
                    level.addFreshEntity(shot);
                }
            }
            default -> {}
        }
    }

    /** Baritone Roar and the Drop: a ring of sound, then he's out of breath. */
    private void roar(ServerLevel level, BossDef.Attack attack, int breath) {
        float r = attack.number("radius", 10);
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.4f);
        // A puff around him and a ring of sound at the edge of its reach.
        ring(level, ParticleTypes.POOF, position(), r * 0.4, 16);
        ring(level, ParticleTypes.SONIC_BOOM, position().add(0, 0.8, 0), r, 12);
        for (ServerPlayer p : playersInRing()) {
            if (near(p, position(), r)) {
                hit(p, attack, attack.damage());
            }
        }
        loseBreath(breath);
        if (getRandom().nextFloat() < 0.3f) {
            say("out_of_breath");
        }
    }

    /** Lasso: a loop along the line to where the target was when the aim locked; whoever it catches is pulled in. */
    private void lasso(ServerLevel level, BossDef.Attack attack) {
        Vec3 from = getEyePosition();
        Vec3 dir = aim.add(0, 1, 0).subtract(from).normalize();
        for (int i = 0; i < attack.maxRange() * 2; i++) {
            Vec3 p = from.add(dir.scale(i / 2.0));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        for (ServerPlayer p : playersInRing()) {
            if (onLine(p, aim.add(0, 1, 0), attack.maxRange(), attack.number("width", 1.2f))) {
                Vec3 pull = position().subtract(p.position()).normalize().scale(attack.number("pull", 5) / 4.0);
                p.setDeltaMovement(pull.x, 0.4, pull.z);
                p.hurtMarked = true;
                level.playSound(null, p.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.HOSTILE, 1.5f, 0.8f);
            }
        }
    }

    private void stomp(ServerLevel level, BossDef.Attack attack) {
        resetHits();
        float r = attack.number("radius", 8);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 1.6f);
        level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.HOSTILE, 1.5f, 1.4f);
        ring(level, ParticleTypes.POOF, position(), r * 0.5, 16);
        ring(level, ParticleTypes.POOF, position(), r, 28);
        for (ServerPlayer p : playersInRing()) {
            if (near(p, position(), r) && p.onGround()) {
                hit(p, attack, attack.damage());
            }
        }
    }

    /** Silky Croon: a cone of low notes that slows everyone in it. */
    private void croon(ServerLevel level, BossDef.Attack attack) {
        float r = attack.number("radius", 12);
        float angle = attack.number("angle", 60);
        Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
        for (int i = 2; i < r; i += 2) {
            level.sendParticles(ParticleTypes.NOTE, getX() + look.x * i, getY(1), getZ() + look.z * i, 3, i * 0.2, 0.4, i * 0.2, 0);
        }
        int ticks = Math.round(attack.number("slow_time", 4) * 20);
        for (ServerPlayer p : playersInRing()) {
            if (inArc(p, r, angle) && hit(p, attack, attack.damage())) {
                Restraint.slow(p, attack.number("slow", 0.4f), ticks);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        stopMusic();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            stopMusic();
        }
        super.remove(reason);
    }

    private void stopMusic() {
        if (level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(center)) < 96 * 96) {
                    p.connection.send(new ClientboundStopSoundPacket(TWINKLE, SoundSource.RECORDS));
                }
            }
        }
    }

    @Override
    protected List<ItemStack> drops(ServerPlayer player, boolean firstKill) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(BossContent.SUBWOOFER));
        if (firstKill) {
            out.add(new ItemStack(BossContent.TROPHY_TOO_MUCH_SOUL));
            out.add(new ItemStack(BossContent.TWINKLE_DISC));
        }
        // A share of rare tier 1 materials.
        out.add(new ItemStack(Items.COPPER_INGOT, 6 + getRandom().nextInt(7)));
        out.add(new ItemStack(Items.LEATHER, 2 + getRandom().nextInt(3)));
        if (getRandom().nextFloat() < 0.5f) {
            out.add(new ItemStack(Items.AMETHYST_SHARD, 2 + getRandom().nextInt(3)));
        }
        return out;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Meter", meter);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        meter = tag.getInt("Meter");
    }
}
