package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.ecology.Ecology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire is a disturbance: a burn resets a prairie patch's clock (17.16.1). */
@Mixin(FireBlock.class)
abstract class EcoFireMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void seancraft$burn(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        Ecology.burned(level, pos);
    }
}
