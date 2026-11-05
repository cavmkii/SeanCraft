package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.ecology.Ecology;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Breeding adds to the patch population (17.3). */
@Mixin(Animal.class)
abstract class EcoBreedMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void seancraft$born(ServerLevel level, Animal partner, AgeableMob baby, CallbackInfo ci) {
        if (baby != null) {
            Ecology.born(level, baby);
        }
    }
}
