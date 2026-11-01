package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.tetris.Tetris;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * A glow around the screen edges while you're hurt: red and pulsing while you bleed (faster the worse it is),
 * white for a fracture, grey for a destroyed part, the hazard's colour while a biome hazard drains you. Stronger with
 * severity, and they stack.
 */
final class InjuryGlow {
    private InjuryGlow() {}

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !Tetris.active(mc.player) || !mc.player.isAlive()) {
            return;
        }
        var body = ClientTarkov.body;
        float bleed = 0;
        float fracture = 0;
        float destroyed = 0;
        for (int i = 0; i < 7; i++) {
            bleed += body.bleed()[i];
            fracture = Math.max(fracture, body.fracture()[i]);
            if (body.body()[i] <= 0.0001f) {
                destroyed = Math.min(1, destroyed + 0.6f);
            }
        }
        bleed = Math.min(1, bleed);
        float t = mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true);
        boolean hazard = ClientHazard.hazard != null && ClientHazard.state == com.cavmkii.seancraft.hazard.Hazards.DRAINING;
        if (hazard) {
            // A slow throb in the hazard's colour while it drains you.
            float throb = 0.5f + 0.5f * Mth.sin(t * 0.08f);
            g.flush();
            edges(g, ClientHazard.hazard.glow, 0.45f + 0.25f * throb, 0.6f + 0.2f * throb);
            g.flush();
        }
        // Draw the bands as their own batch, so they don't mix with the item icons drawn later this frame.
        g.flush();
        if (destroyed > 0) {
            edges(g, 0x606068, 0.35f + 0.25f * destroyed, 0.5f + 0.3f * destroyed);
        }
        if (fracture > 0) {
            edges(g, 0xF0F0F0, 0.2f + 0.3f * fracture, 0.25f + 0.35f * fracture);
        }
        if (bleed > 0) {
            // A heartbeat: quick double pulse, quicker when it's bad.
            float speed = 0.18f + 0.22f * bleed;
            float beat = Math.max(0, Mth.sin(t * speed)) * 0.6f + Math.max(0, Mth.sin(t * speed * 2 + 1)) * 0.4f;
            edges(g, 0xD01010, 0.3f + 0.35f * bleed + 0.15f * beat, 0.4f + 0.4f * bleed * (0.7f + 0.3f * beat));
        }
        g.flush();
    }

    /** Four soft bands fading inward. {@code alpha} at the very edge, {@code size} as a share of the shorter side / 4. */
    private static void edges(GuiGraphics g, int rgb, float alpha, float size) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int t = Math.max(4, (int) (Math.min(w, h) * 0.25f * Math.min(1, size)));
        int edge = (int) (Mth.clamp(alpha, 0, 1) * 0.55f * 255) << 24 | rgb;
        int clear = rgb;
        g.fillGradient(0, 0, w, t, edge, clear);
        g.fillGradient(0, h - t, w, h, clear, edge);
        g.pose().pushPose();
        g.pose().translate(t, 0, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        g.fillGradient(0, 0, h, t, clear, edge);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w - t, h, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
        g.fillGradient(0, 0, h, t, clear, edge);
        g.pose().popPose();
    }
}
