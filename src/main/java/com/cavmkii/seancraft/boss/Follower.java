package com.cavmkii.seancraft.boss;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One of Daylan's bought followers (design doc A.4): a wisp that orbits him, gives him damage reduction while it lives
 * and now and then dives at a player. Any hit from a player destroys it. It is never saved; a reload clears them.
 */
public class Follower extends Mob implements ItemSupplier {
    static final int DIVE_TICKS = 30;
    private static final ItemStack LOOK = new ItemStack(Items.SNOWBALL);

    private UUID owner;
    private int slot;
    private ServerPlayer diving;
    private int diveTicks;

    public Follower(EntityType<? extends Follower> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1);
    }

    void setOwner(Daylan daylan, int slot) {
        this.owner = daylan.getUUID();
        this.slot = slot;
    }

    Daylan owner() {
        return level() instanceof ServerLevel level && owner != null && level.getEntity(owner) instanceof Daylan d && d.isAlive() ? d : null;
    }

    boolean diving() {
        return diving != null;
    }

    void dive(ServerPlayer target) {
        diving = target;
        diveTicks = 0;
        level().playSound(null, blockPosition(), SoundEvents.ALLAY_HURT, SoundSource.HOSTILE, 1f, 1.6f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Daylan daylan = owner();
        if (daylan == null) {
            pop(level);
            return;
        }
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 1, 0.05, 0.05, 0.05, 0.005);
        Vec3 to;
        if (diving != null) {
            if (!diving.isAlive() || ++diveTicks > DIVE_TICKS) {
                diving = null;
                return;
            }
            to = diving.position().add(0, 1.2, 0);
            if (position().distanceTo(to) < 1.0) {
                daylan.followerHit(diving);
                diving = null;
                return;
            }
            setDeltaMovement(to.subtract(position()).normalize().scale(0.45));
        } else {
            double a = slot * Math.PI * 2 / Daylan.MAX_FOLLOWERS + tickCount * 0.06;
            to = daylan.position().add(Math.cos(a) * 2.2, 1.6 + Math.sin(tickCount * 0.1 + slot) * 0.3, Math.sin(a) * 2.2);
            Vec3 step = to.subtract(position());
            setDeltaMovement(step.length() > 0.6 ? step.normalize().scale(0.6) : step);
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
    }

    /** Any hit from a player pops it. Nothing else touches it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !(source.getEntity() instanceof Player)) {
            return false;
        }
        if (level() instanceof ServerLevel level) {
            pop(level);
        }
        return true;
    }

    private void pop(ServerLevel level) {
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY(0.5), getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 1.5f, 1.4f);
        discard();
    }

    @Override
    public ItemStack getItem() {
        return LOOK;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
