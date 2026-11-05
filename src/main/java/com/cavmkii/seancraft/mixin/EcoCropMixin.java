package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.ecology.Ecology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crops grow slower on poor soil and take nitrogen and phosphorus from their patch as they grow (17.6). */
@Mixin(CropBlock.class)
abstract class EcoCropMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void seancraft$soil(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Ecology.cropMayGrow(level, pos, random)) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private void seancraft$uptake(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        Ecology.cropGrew(level, pos);
    }
}
