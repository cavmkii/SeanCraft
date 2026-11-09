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

/**
 * Safe ground (2.1): hostile mobs don't spawn naturally at a home base, a spawn point or a camp. Fish spawn in step with
 * their patch's stock (17.3).
 */
@Mixin(NaturalSpawner.class)
abstract class SafeGroundSpawnMixin {
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void seancraft$safeGround(ServerLevel level, Mob mob, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof Enemy && Tarkov.safeGround(level, mob.position())) {
            cir.setReturnValue(false);
        } else if (mob instanceof net.minecraft.world.entity.animal.AbstractFish
                && !com.cavmkii.seancraft.ecology.Ecology.fishSpawnAllowed(level, mob.blockPosition(), level.getRandom())) {
            // Fish show to match the patch's stock (17.3).
            cir.setReturnValue(false);
        }
    }
}
