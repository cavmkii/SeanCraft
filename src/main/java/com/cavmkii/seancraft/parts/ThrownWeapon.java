package com.cavmkii.seancraft.parts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown part-built weapon (9.4). It's an arrow to the game, so the damage pipeline, skills and ladders see it as one. */
public class ThrownWeapon extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> RETURNS = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BOOLEAN);
    private float hitDamage;
    private int grounded;
    private boolean returning;

    public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
        super(type, level);
    }

    ThrownWeapon(Level level, LivingEntity owner, ItemStack stack, float damage, boolean returns) {
        super(Throwing.THROWN, owner, level, stack, stack);
        entityData.set(STACK, stack.copy());
        entityData.set(RETURNS, returns);
        this.hitDamage = damage;
        setBaseDamage(damage);
        pickup = Pickup.ALLOWED;
        if (returns) {
            addTag(com.cavmkii.seancraft.combat.Damage.BLUNT_ARROW_TAG);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(RETURNS, false);
    }

    public ItemStack stack() {
        return entityData.get(STACK);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.STICK);
    }

    @Override
    protected ItemStack getPickupItem() {
        return stack().copy();
    }

    @Override
    public ItemStack getWeaponItem() {
        return stack();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target instanceof LivingEntity living && target != owner) {
            // The arrow branch of the pipeline scales this like a shot (AGI, the weapon's parts).
            living.hurt(damageSources().arrow(this, owner), hitDamage);
            if (owner instanceof ServerPlayer sp) {
                ItemStack s = stack();
                s.hurtAndBreak(1, sp.serverLevel(), sp, item -> { });
                entityData.set(STACK, s);
            }
        }
        setDeltaMovement(getDeltaMovement().multiply(-0.05, -0.1, -0.05));
        playSound(SoundEvents.TRIDENT_HIT, 1f, 1f);
        if (entityData.get(RETURNS)) {
            returning = true;
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void tick() {
        if (entityData.get(RETURNS) && !level().isClientSide && (returning || tickCount > 25 || inGround)) {
            comeBack();
            return;
        }
        super.tick();
        if (!level().isClientSide && inGround) {
            // Glows where it landed so it can be found (9.4.4).
            setGlowingTag(true);
        }
    }

    /** The boomerang flies back to its thrower and into their hands. */
    private void comeBack() {
        Entity owner = getOwner();
        if (!(owner instanceof Player player) || !player.isAlive()) {
            returning = false;
            entityData.set(RETURNS, false);
            return;
        }
        setNoPhysics(true);
        Vec3 to = player.getEyePosition().subtract(position());
        if (to.length() < 1.5) {
            if (!player.getAbilities().instabuild) {
                com.cavmkii.seancraft.tetris.Tetris.give((ServerPlayer) player, stack().copy());
            }
            discard();
            return;
        }
        setDeltaMovement(to.normalize().scale(Math.min(1.6, 0.6 + tickCount * 0.02)));
        setPos(position().add(getDeltaMovement()));
        tickCount++;
    }

    @Override
    protected void tickDespawn() {
        if (++grounded >= Throwing.STAY_TICKS) {
            discard();
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (ownedBy(player) || getOwner() == null) {
            super.playerTouch(player);
        } else if (inGround) {
            super.playerTouch(player);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("hit_damage", hitDamage);
        tag.putBoolean("returns", entityData.get(RETURNS));
        if (!stack().isEmpty()) {
            tag.put("stack", stack().save(registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hitDamage = tag.getFloat("hit_damage");
        entityData.set(RETURNS, tag.getBoolean("returns"));
        if (tag.contains("stack")) {
            ItemStack.parse(registryAccess(), tag.get("stack")).ifPresent(s -> entityData.set(STACK, s));
        }
    }
}
