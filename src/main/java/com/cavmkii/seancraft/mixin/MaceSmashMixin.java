package com.cavmkii.seancraft.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The vanilla Mace's smash is capped at +50% and does nothing inside a boss arena (9.7.3). */
@Mixin(MaceItem.class)
public abstract class MaceSmashMixin {
    @Inject(method = "getAttackDamageBonus", at = @At("RETURN"), cancellable = true)
    private void seancraft$capSmash(Entity target, float damage, DamageSource source, CallbackInfoReturnable<Float> cir) {
        if (source.getEntity() instanceof ServerPlayer player && com.cavmkii.seancraft.boss.Arenas.inFight(player)) {
            cir.setReturnValue(0f);
        } else {
            cir.setReturnValue(Math.min(cir.getReturnValueF(), damage * 0.5f));
        }
    }
}
