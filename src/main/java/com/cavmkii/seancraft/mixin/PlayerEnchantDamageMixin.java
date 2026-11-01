package com.cavmkii.seancraft.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sharpness, Smite and Bane of Arthropods no longer add flat vanilla damage to a player's swing; the damage pipeline
 * maps them instead (design doc 9.7.1), so they never stack as a separate layer.
 */
@Mixin(Player.class)
public abstract class PlayerEnchantDamageMixin {
    @Inject(method = "getEnchantedDamage", at = @At("HEAD"), cancellable = true)
    private void seancraft$noFlatEnchantDamage(Entity target, float damage, DamageSource source, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(damage);
    }
}
