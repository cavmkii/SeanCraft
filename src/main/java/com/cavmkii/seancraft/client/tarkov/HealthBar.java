package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.tarkov.Body;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The health bar in place of the hearts: red for health, a dark crossed-out end for health locked away by destroyed
 * parts and lasting damage (5.6), gold for absorption, and the numbers on top.
 */
public final class HealthBar {
    private static final int WIDTH = 81;
    private static final int HEIGHT = 9;

    private HealthBar() {}

    public static void render(GuiGraphics g, Player player, int x, int y, float maxHealth, int absorption, boolean blinking) {
        Minecraft mc = Minecraft.getInstance();
        float max = Math.max(1, maxHealth);
        float health = Math.min(max, player.getHealth());
        float capFraction = player.isCreative() ? 1 : Body.capFraction(ClientTarkov.body.body(), ClientTarkov.body.scar());
        int inner = WIDTH - 2;
        int fill = Math.round(inner * health / max);
        int capAt = Math.round(inner * capFraction);

        g.fill(x, y, x + WIDTH, y + HEIGHT, blinking ? 0xFFD9CBB0 : 0xFF0E0C0B);
        g.fill(x + 1, y + 1, x + 1 + inner, y + HEIGHT - 1, 0xFF2B2622);
        int color = player.hasEffect(MobEffects.POISON) ? 0xFF7D9A3A : player.hasEffect(MobEffects.WITHER) ? 0xFF5A5149 : 0xFFC8402E;
        g.fill(x + 1, y + 1, x + 1 + fill, y + HEIGHT - 1, color);
        g.fill(x + 1, y + 1, x + 1 + fill, y + 3, 0x40FFFFFF);
        if (capAt < inner) {
            // Locked health: dark, with diagonal strokes.
            int lx = x + 1 + capAt;
            g.fill(lx, y + 1, x + 1 + inner, y + HEIGHT - 1, 0xFF3A3430);
            for (int sx = lx - (HEIGHT - 2); sx < x + 1 + inner; sx += 4) {
                for (int k = 0; k < HEIGHT - 2; k++) {
                    int px = sx + k;
                    if (px >= lx && px < x + 1 + inner) {
                        g.fill(px, y + HEIGHT - 2 - k, px + 1, y + HEIGHT - 1 - k, 0xFF8E1F1F);
                    }
                }
            }
        }
        if (absorption > 0) {
            int ab = Math.min(inner, Math.round(inner * absorption / max));
            g.fill(x + 1, y + 1, x + 1 + ab, y + 3, 0xFFE0BC62);
        }
        String text = Math.round(health) + (absorption > 0 ? "+" + absorption : "") + " / " + Math.round(max);
        g.pose().pushPose();
        g.pose().translate(x + WIDTH / 2f - mc.font.width(text) * 0.375f, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(mc.font, text, 0, 0, 0xFFD9CBB0, true);
        g.pose().popPose();
    }
}
