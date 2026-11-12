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

    /** A harvest moon (18.1): half the time a crop grows an extra stage on its tick. */
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void seancraft$harvestMoon(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CropBlock self = (CropBlock) (Object) this;
        BlockState now = level.getBlockState(pos);
        if (now.is(self) && !self.isMaxAge(now) && random.nextBoolean() && com.cavmkii.seancraft.compat.Celestials.harvestMoon(level)
                && level.getRawBrightness(pos, 0) >= 9) {
            level.setBlock(pos, self.getStateForAge(self.getAge(now) + 1), 2);
        }
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private void seancraft$uptake(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        Ecology.cropGrew(level, pos);
    }
}
