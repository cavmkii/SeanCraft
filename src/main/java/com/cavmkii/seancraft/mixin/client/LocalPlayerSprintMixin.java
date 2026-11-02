package com.cavmkii.seancraft.mixin.client;

import com.cavmkii.seancraft.client.tarkov.InjuryCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legs at full severity (no painkillers) can't sprint, and neither can someone carrying a chest or out of stamina. Vanilla checks this to start a sprint and to keep one going,
 * the same way it stops you when you're too hungry.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerSprintMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void seancraft$injuredLegs(CallbackInfoReturnable<Boolean> cir) {
        if (InjuryCamera.noSprint() || com.cavmkii.seancraft.client.carry.ClientCarry.carrying()
                || com.cavmkii.seancraft.client.combat.ClientCombat.exhausted()
                || com.cavmkii.seancraft.client.combat.ClientCombat.overloaded()) {
            cir.setReturnValue(false);
        }
    }
}
