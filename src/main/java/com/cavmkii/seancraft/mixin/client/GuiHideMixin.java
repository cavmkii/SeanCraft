package com.cavmkii.seancraft.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No HUD behind SeanCraft's own screens (UI-SPEC Part 2): the hotbar, hearts and our overlays would show through the
 * dimmed backdrop and duplicate what the screen already shows. Vanilla screens keep the HUD.
 */
@Mixin(Gui.class)
abstract class GuiHideMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void seancraft$hideUnderScreens(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (minecraft.screen != null && minecraft.screen.getClass().getName().startsWith("com.cavmkii.seancraft.")) {
            ci.cancel();
        }
    }
}
