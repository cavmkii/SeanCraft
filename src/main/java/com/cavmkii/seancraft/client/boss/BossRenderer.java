package com.cavmkii.seancraft.client.boss;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.boss.BossEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bosses on the vanilla humanoid model until the shared boss rig (design doc B.2) exists, with one texture per phase
 * for bosses who change outfits. Wind-ups raise the arm and the strike brings it down, so tells read even without
 * custom animations.
 */
public class BossRenderer<T extends BossEntity> extends HumanoidMobRenderer<T, BossRenderer.Model<T>> {
    private final ResourceLocation[] textures;

    /** {@code outfits} are texture names under textures/entity, one per phase; the last one covers later phases. */
    public BossRenderer(EntityRendererProvider.Context context, String... outfits) {
        super(context, new Model<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        textures = new ResourceLocation[outfits.length];
        for (int i = 0; i < outfits.length; i++) {
            textures[i] = SeanCraft.id("textures/entity/" + outfits[i] + ".png");
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return textures[Math.min(entity.phaseIndex(), textures.length - 1)];
    }

    public static class Model<T extends BossEntity> extends HumanoidModel<T> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(T boss, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
            super.setupAnim(boss, limbSwing, limbSwingAmount, age, yaw, pitch);
            BossEntity.State state = boss.state();
            if (state == BossEntity.State.WINDUP) {
                rightArm.xRot = -2.6f + (float) Math.sin(age * 0.8f) * 0.08f;
                rightArm.zRot = -0.2f;
            } else if (state == BossEntity.State.ACTIVE) {
                rightArm.xRot = -0.4f;
            } else if (state == BossEntity.State.TRANSITION) {
                rightArm.xRot = -1.2f;
                leftArm.xRot = -1.2f;
                head.xRot = 0.5f;
            }
        }
    }
}
