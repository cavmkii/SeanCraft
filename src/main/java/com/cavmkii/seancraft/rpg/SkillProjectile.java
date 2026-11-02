package com.cavmkii.seancraft.rpg;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** An ability's projectile (Fireball, Blade Fan daggers). Rendered as its item. Never breaks blocks. */
public class SkillProjectile extends ThrowableItemProjectile {
    private float damage;
    private float radius;
    private boolean fire;
    /** A wand or staff bolt's element (9.4.5), or null for an ability's own damage. */
    private com.cavmkii.seancraft.combat.Damage.Type element;

    /** A magic bolt: one element, damage on the 9.5 scale already divided down. */
    public static SkillProjectile bolt(Level level, LivingEntity owner, ItemStack look, float damage, float radius,
            com.cavmkii.seancraft.combat.Damage.Type element) {
        SkillProjectile p = new SkillProjectile(level, owner, look, damage, radius, element == com.cavmkii.seancraft.combat.Damage.Type.FIRE);
        p.element = element;
        return p;
    }

    private void hit(ServerPlayer owner, LivingEntity target) {
        if (element == null) {
            SkillEffects.damage(owner, target, damage);
            return;
        }
        com.cavmkii.seancraft.combat.Damage.forced(java.util.Map.of(element, 1f));
        try {
            SkillEffects.damage(owner, target, damage);
        } finally {
            com.cavmkii.seancraft.combat.Damage.forced(null);
        }
    }

    public SkillProjectile(EntityType<? extends SkillProjectile> type, Level level) {
        super(type, level);
    }

    SkillProjectile(Level level, LivingEntity owner, ItemStack look, float damage, float radius, boolean fire) {
        super(RpgContent.SKILL_PROJECTILE, owner, level);
        setItem(look);
        this.damage = damage;
        this.radius = radius;
        this.fire = fire;
        setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 60) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !(target instanceof SkillProjectile) && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (radius <= 0 && getOwner() instanceof ServerPlayer owner && result.getEntity() instanceof LivingEntity target) {
            hit(owner, target);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        if (radius > 0 && getOwner() instanceof ServerPlayer owner) {
            for (LivingEntity e : SkillEffects.enemiesAround(owner, result.getLocation(), radius)) {
                hit(owner, e);
                if (fire) {
                    e.igniteForSeconds(4);
                }
            }
            level.sendParticles(fire ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT, getX(), getY(), getZ(), 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 25, radius / 3, 0.4, radius / 3, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        discard();
    }
}
