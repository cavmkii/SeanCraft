package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.parts.Parts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crossbows load at their parts' pace (string and stock wood), like bows draw at theirs. */
@Mixin(CrossbowItem.class)
abstract class CrossbowReloadMixin {
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void seancraft$reload(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() / Parts.drawFactor(stack))));
    }
}
