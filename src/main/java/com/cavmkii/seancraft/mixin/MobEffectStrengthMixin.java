package com.cavmkii.seancraft.mixin;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Strength's flat attack damage is replaced by 30% increased damage a level in the pipeline (9.7.2). */
@Mixin(MobEffect.class)
public abstract class MobEffectStrengthMixin {
    @Inject(method = "addAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void seancraft$noFlatStrength(AttributeMap attributes, int amplifier, CallbackInfo ci) {
        if ((Object) this == MobEffects.DAMAGE_BOOST.value()) {
            ci.cancel();
        }
    }
}
