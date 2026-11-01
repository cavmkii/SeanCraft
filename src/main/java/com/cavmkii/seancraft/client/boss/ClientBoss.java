package com.cavmkii.seancraft.client.boss;

import com.cavmkii.seancraft.boss.BossContent;
import com.cavmkii.seancraft.boss.Trophies;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.lwjgl.glfw.GLFW;

/** Boss renderers and the trophy power key. */
public final class ClientBoss {
    public static final KeyMapping USE_POWER = new KeyMapping("key.seancraft.trophy_power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y,
            "key.categories.seancraft");

    private ClientBoss() {}

    public static void init() {
        EntityRendererRegistry.register(BossContent.MARK, ctx -> new BossRenderer<>(ctx, "mark"));
        EntityRendererRegistry.register(BossContent.BO_DADDY, ctx -> new BossRenderer<>(ctx, "bo_daddy_country", "bo_daddy_rnb"));
        EntityRendererRegistry.register(BossContent.DAYLAN, ctx -> new BossRenderer<>(ctx, "daylan_influencer", "daylan_firefighter", "daylan_police"));
        EntityRendererRegistry.register(BossContent.FOLLOWER, ctx -> new ThrownItemRenderer<>(ctx, 0.8f, true));
        EntityRendererRegistry.register(BossContent.BOSS_SHOT, ctx -> new ThrownItemRenderer<>(ctx, 1.2f, true));
        KeyBindingHelper.registerKeyBinding(USE_POWER);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (USE_POWER.consumeClick()) {
                if (mc.player != null) {
                    ClientPlayNetworking.send(new Trophies.Use());
                }
            }
        });
    }
}
