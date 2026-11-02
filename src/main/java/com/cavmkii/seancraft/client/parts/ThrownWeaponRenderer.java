package com.cavmkii.seancraft.client.parts;

import com.cavmkii.seancraft.parts.ThrownWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws a thrown weapon as its item, pointing where it flies; a boomerang spins. */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon> {
    private final ItemRenderer items;

    public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ThrownWeapon entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) - 45));
        if (entity.getTags().contains(com.cavmkii.seancraft.combat.Damage.BLUNT_ARROW_TAG)) {
            pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 40));
        }
        pose.scale(1.2f, 1.2f, 1.2f);
        items.renderStatic(entity.stack(), ItemDisplayContext.FIXED, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownWeapon entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
