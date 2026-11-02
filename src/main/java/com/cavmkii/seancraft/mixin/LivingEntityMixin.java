package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.combat.Damage;
import com.cavmkii.seancraft.rpg.RpgDamage;
import com.cavmkii.seancraft.rpg.RpgHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected abstract void hurtArmor(DamageSource source, float amount);

    /**
     * Character damage rules, then SeanCraft's pipeline (5x scale, damage types, resistances, armor), before
     * vanilla's blocking and invulnerability.
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float seancraft$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return Damage.apply(self, source, RpgDamage.modify(self, source, amount));
    }

    /** Fire Resistance no longer makes players immune to fire and lava; it gives resistance points instead (9.7.2). */
    @Redirect(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean seancraft$noFireImmunity(LivingEntity self, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        if (self instanceof Player && effect.equals(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
            return false;
        }
        return self.hasEffect(effect);
    }

    /** Resistance becomes armor on every part for players (9.7.2), not vanilla's flat 20% a level. */
    @Redirect(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean seancraft$resistanceAsArmor(LivingEntity self, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        if (self instanceof Player && effect.equals(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)) {
            return false;
        }
        return self.hasEffect(effect);
    }

    /** Players' shields are handled by SeanCraft (combat/Blocking), so vanilla's full block never happens for them. */
    @Inject(method = "isDamageSourceBlocked", at = @At("HEAD"), cancellable = true)
    private void seancraft$ownBlocking(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof net.minecraft.server.level.ServerPlayer) {
            cir.setReturnValue(false);
        }
    }

    /** Rabbit-hide wraps and padding (the Quiet trait, 4.4.1): a sneaking player is harder for mobs to spot. */
    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void seancraft$quiet(net.minecraft.world.entity.Entity looker, CallbackInfoReturnable<Double> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) {
            double v = cir.getReturnValue() * com.cavmkii.seancraft.parts.MaterialTraits.quiet(self);
            // Inside a Smoke Bomb a player counts as sneaking (6.5.5).
            if (!self.isCrouching() && com.cavmkii.seancraft.rpg.SkillEffects.inSmoke(self)) {
                v *= 0.8;
            }
            cir.setReturnValue(v);
        }
    }

    /** Armor is applied in SeanCraft's pipeline instead. The armor still wears as it would have at vanilla scale. */
    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void seancraft$noVanillaArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            hurtArmor(source, amount / Damage.SCALE);
        }
        cir.setReturnValue(amount);
    }

    /** Players' protection enchantments count as armor and resistance points in the pipeline (9.7.1), not here. */
    @Redirect(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)F"))
    private float seancraft$noEnchantProtection(ServerLevel level, LivingEntity entity, DamageSource source) {
        return entity instanceof Player ? 0 : EnchantmentHelper.getDamageProtection(level, entity, source);
    }

    /** Mobs from further out give more XP (2.3.1). */
    @org.spongepowered.asm.mixin.injection.Inject(method = "getExperienceReward", at = @At("RETURN"), cancellable = true)
    private void seancraft$distanceXp(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity killer,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        float multiplier = com.cavmkii.seancraft.tarkov.Tarkov.xpMultiplier((LivingEntity) (Object) this);
        if (multiplier != 1) {
            cir.setReturnValue(Math.round(cir.getReturnValue() * multiplier));
        }
    }

    /** Healing is on the 5x scale too; destroyed body parts lock away their share of health. */
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float seancraft$capHeal(float amount) {
        return com.cavmkii.seancraft.tarkov.Health.capHeal((LivingEntity) (Object) this, Damage.heal(amount));
    }

    /** Brewing skill: potions you drink last longer. Hazard potions also start a pass. */
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance seancraft$longerPotions(MobEffectInstance effect) {
        MobEffectInstance result = RpgHooks.extendDrunkPotion((LivingEntity) (Object) this, effect);
        com.cavmkii.seancraft.hazard.Hazards.onEffect((LivingEntity) (Object) this, result);
        return result;
    }
}
