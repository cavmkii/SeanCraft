package com.cavmkii.seancraft.mixin.client;

import com.cavmkii.seancraft.client.world.Atmosphere;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Band fog (18.2): the terrain fog closes in a little in the Frontier and more in the Far lands. */
@Mixin(FogRenderer.class)
abstract class BandFogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void seancraft$bandFog(Camera camera, FogRenderer.FogMode mode, float farPlane, boolean thick, float partialTick, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || thick || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        float f = Atmosphere.fogFactor();
        if (f < 1) {
            RenderSystem.setShaderFogStart(RenderSystem.getShaderFogStart() * f);
            RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * f);
        }
    }
}
