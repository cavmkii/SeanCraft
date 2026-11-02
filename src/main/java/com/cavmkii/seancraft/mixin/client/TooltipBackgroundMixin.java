package com.cavmkii.seancraft.mixin.client;

import com.cavmkii.seancraft.client.ui.Kit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every tooltip uses the kit's tooltip background (Appendix C.3); 1.21.1 draws tooltips in code, not from a sprite. */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundMixin {
    @Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void seancraft$kitTooltip(GuiGraphics g, int x, int y, int width, int height, int z, CallbackInfo ci) {
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        // Vanilla's frame reaches 3 px outside the text box; the sprite gives 5 px left and 3 px top of padding.
        Kit.hudPanel(g, x - 5, y - 3, width + 10, height + 6);
        g.pose().popPose();
        ci.cancel();
    }
}
