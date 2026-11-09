package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.tarkov.Tarkov;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Safe ground (1.1): hostile mobs don't spawn naturally in a home base, a spawn point's safe radius or by a lit camp. */
@Mixin(NaturalSpawner.class)
abstract class SafeGroundSpawnMixin {
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void seancraft$safeGround(ServerLevel level, Mob mob, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof Enemy && Tarkov.safeGround(level, mob.position())) {
            cir.setReturnValue(false);
        }
    }
}
