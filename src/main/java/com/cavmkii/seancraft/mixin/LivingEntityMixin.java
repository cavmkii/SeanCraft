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
