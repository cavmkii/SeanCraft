package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.food.Food;
import com.cavmkii.seancraft.hazard.Hazards;
import com.cavmkii.seancraft.tarkov.Body;
import com.cavmkii.seancraft.tarkov.BodyPart;
import com.cavmkii.seancraft.tarkov.Injury;
import com.cavmkii.seancraft.tarkov.Med;
import com.cavmkii.seancraft.tarkov.TarkovContent;
import com.cavmkii.seancraft.tarkov.TarkovNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bottom left, above the raid readout: injuries (with what fixes them) and timed effects as small icons with the
 * time left. Stands in for the vanilla effect icons, which show no time.
 */
final class EffectHud {
    private static final int ICON = 12;

    private EffectHud() {}

    private record Wound(ItemStack icon, String text, String cure, int color) {}

    private static int pct(float f) {
        return Math.round(f * 100);
    }

    private static List<Wound> wounds() {
        TarkovNet.BodySync body = ClientTarkov.body;
        List<Wound> out = new ArrayList<>();
        hazard(out);
        for (BodyPart p : BodyPart.values()) {
            int i = p.ordinal();
            int c = body.conditions()[i];
            if (body.body()[i] <= 0.0001f) {
                // A destroyed part is the whole story: its bleeds, breaks and lasting damage don't need their own lines.
                out.add(new Wound(new ItemStack(Items.BARRIER), "Destroyed - " + p.label, "surgery", Kit.ASH));
                continue;
            }
            if ((c & Body.HEAVY_BLEED) != 0) {
                out.add(new Wound(new ItemStack(Items.RED_DYE), "Heavy bleed " + pct(body.bleed()[i]) + "% - " + p.label, "tourniquet", Kit.BLOOD_LIGHT));
            }
            if ((c & Body.LIGHT_BLEED) != 0) {
                out.add(new Wound(new ItemStack(Items.RED_DYE), "Light bleed " + pct(body.bleed()[i]) + "% - " + p.label, "bandage", Kit.BLOOD_LIGHT));
            }
            if ((c & Body.FRACTURE) != 0) {
                out.add(new Wound(new ItemStack(Items.BONE), "Fracture " + pct(body.fracture()[i]) + "% - " + p.label, "splint", Kit.BONE));
            }
            if (body.scar()[i] > 0.001f && body.body()[i] > 0.0001f) {
                out.add(new Wound(new ItemStack(Items.GRAY_DYE), "Lasting damage " + pct(body.scar()[i]) + "% - " + p.label, "surgery case",
                        Kit.ASH));
            }
        }
        return out;
    }

    /** The biome hazard you're in: draining you, or held off by its key or a potion pass (9.3.3). */
    private static void hazard(List<Wound> out) {
        Hazards.Hazard h = ClientHazard.hazard;
        if (h == null) {
            return;
        }
        ItemStack icon = new ItemStack(switch (h) {
            case DARKNESS -> Items.SCULK;
            case HEAT -> Items.BLAZE_POWDER;
            case COLD -> Items.SNOWBALL;
        });
        switch (ClientHazard.state) {
            case Hazards.DRAINING -> out.add(new Wound(icon, h.label + " - draining health", h.potion + " or " + Hazards.keyName(h), Kit.EMBER));
            case Hazards.PASS -> {
                int s = ClientHazard.passLeft();
                out.add(new Wound(icon, h.label + " - " + h.potion + " " + s / 60 + ":" + String.format("%02d", s % 60), "", Kit.MOSS));
            }
            default -> out.add(new Wound(icon, h.label + " - " + Hazards.keyName(h), "", Kit.ASH));
        }
    }

    /** What the injuries are doing to you right now, as one or two short lines. */
    private static List<String> penalties() {
        Injury inj = ClientTarkov.body.injury();
        List<String> lines = new ArrayList<>();
        StringBuilder legs = new StringBuilder();
        add(legs, "Walk", inj.move());
        if (inj.noSprint()) {
            legs.append(legs.isEmpty() ? "" : "  ").append("No sprint");
        } else {
            // Both modifiers multiply.
            add(legs, "Sprint", (1 + inj.move()) * (1 + inj.sprint()) - 1);
        }
        add(legs, "Jump", inj.jump());
        StringBuilder arms = new StringBuilder();
        add(arms, "Swing", inj.damage());
        add(arms, "Mining", inj.mining());
        if (inj.aim() >= 0.5f) {
            arms.append(arms.isEmpty() ? "" : "  ").append("Aim ±").append(Math.round(inj.aim())).append("°");
        }
        // One line when both fit, two when they don't.
        if (!legs.isEmpty() && !arms.isEmpty() && legs.length() + arms.length() <= 44) {
            lines.add(legs + "  " + arms);
        } else {
            if (!legs.isEmpty()) {
                lines.add(legs.toString());
            }
            if (!arms.isEmpty()) {
                lines.add(arms.toString());
            }
        }
        return lines;
    }

    private static void add(StringBuilder sb, String name, float value) {
        int v = Math.round(value * 100);
        if (v != 0) {
            sb.append(sb.isEmpty() ? "" : "  ").append(name).append(' ').append(v).append('%');
        }
    }

    /**
     * The three food slots (design doc 11): each meal's icon with a bar for the time it has left, amber once it's
     * fading, and empty frames for open slots. Draws upward from {@code bottom}; returns the new top.
     */
    private static int meals(GuiGraphics g, Minecraft mc, int x, int bottom) {
        List<Food.Meal> meals = com.cavmkii.seancraft.client.food.ClientFood.meals;
        if (mc.player.isCreative()) {
            return bottom;
        }
        int y = bottom - ICON - 5;
        for (int i = 0; i < Food.SLOTS; i++) {
            int cx = x + i * (ICON + 4);
            Kit.blit(g, "hud/food_slot", cx - 2, y - 2, ICON + 4, ICON + 4);
            if (i >= meals.size()) {
                continue;
            }
            Food.Meal meal = meals.get(i);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(ICON / 16f, ICON / 16f, 1);
            g.renderItem(new ItemStack(meal.itemType()), 0, 0);
            g.pose().popPose();
            float left = meal.left() / (float) Math.max(1, meal.duration());
            int w = Math.max(1, Math.round(ICON * left));
            g.fill(cx, y + ICON + 1, cx + w, y + ICON + 2, meal.strength() < 1 ? Kit.EMBER : Kit.MOSS);
        }
        if (meals.isEmpty()) {
            Kit.text(g, mc.font, "Hungry", x + Food.SLOTS * (ICON + 4) + 2, y + 2, Kit.ASH);
        }
        return y - 2;
    }

    private static final String[] PART_SPRITES = {"head", "thorax", "stomach", "arm_left", "arm_right", "leg_left", "leg_right"};
    static final int FIG_W = 28;

    /** Bottom of the top-left block (body figure, food, penalty lines), for the gather list to go under. */
    static int topBottom = 48 + 16;

    /**
     * Top left: limb status with the food slots beside it, and under the food, small lines for what the injuries cost
     * (cyan while painkillers take the edge off). The top right is left free for minimaps.
     */
    static void topLeft(GuiGraphics g, Minecraft mc) {
        body(g, mc, 9, 9);
        int tx = 9 + FIG_W + 10;
        int y = mc.player.isCreative() ? 9 : meals(g, mc, tx, 9 + ICON + 5) + ICON + 7;
        boolean pain = ClientTarkov.body.painkillerTicks() > 0;
        int bottom = 9 + FIG_H + 3;
        for (String line : wrap(mc, penalties(), limit(g) - tx)) {
            g.pose().pushPose();
            g.pose().translate(tx, y, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            Kit.text(g, mc.font, line, 0, 0, pain ? Kit.CYAN : Kit.BONE);
            g.pose().popPose();
            y += 7;
            bottom = Math.max(bottom, y);
        }
        topBottom = bottom + 4;
    }
    static final int FIG_H = 48;

    /**
     * Limb status (5.1): the STATUS tab's body at three-quarter size, each part tinted by its health (moss, ember, blood,
     * dark when destroyed). A bleeding part flashes red.
     */
    static void body(GuiGraphics g, Minecraft mc, int x, int y) {
        var body = ClientTarkov.body;
        if (body == null || body.body() == null) {
            return;
        }
        Kit.hudPanel(g, x - 3, y - 3, FIG_W + 6, FIG_H + 6);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        Kit.blit(g, "status/body_base", 0, 0, 28, 48);
        boolean flash = mc.player.tickCount / 8 % 2 == 0;
        for (int i = 0; i < PART_SPRITES.length && i < body.body().length; i++) {
            float frac = body.body()[i];
            int c = body.conditions()[i];
            boolean bleeding = (c & com.cavmkii.seancraft.tarkov.Body.HEAVY_BLEED) != 0 || (c & com.cavmkii.seancraft.tarkov.Body.LIGHT_BLEED) != 0;
            int color = frac <= 0.0001f ? 0xFF2A2522 : bleeding && flash ? 0xFFFF3020 : frac > 0.66f ? Kit.MOSS : frac > 0.33f ? Kit.EMBER : Kit.BLOOD_LIGHT;
            g.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
            Kit.blit(g, "status/part_" + PART_SPRITES[i], 0, 0, 28, 48);
        }
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        // What's wrong with each part, as small icons on it: destroyed, bleeding, broken.
        for (int i = 0; i < body.body().length && i < PART_CENTRE.length; i++) {
            List<ItemStack> marks = new ArrayList<>();
            int c = body.conditions()[i];
            if (body.body()[i] <= 0.0001f) {
                marks.add(new ItemStack(Items.BARRIER));
            } else {
                if ((c & (com.cavmkii.seancraft.tarkov.Body.HEAVY_BLEED | com.cavmkii.seancraft.tarkov.Body.LIGHT_BLEED)) != 0) {
                    marks.add(new ItemStack(Items.RED_DYE));
                }
                if ((c & com.cavmkii.seancraft.tarkov.Body.FRACTURE) != 0) {
                    marks.add(new ItemStack(Items.BONE));
                }
            }
            for (int m = 0; m < marks.size(); m++) {
                // Two marks on a limb stack up and down; on the head and body they sit side by side.
                boolean limb = i >= 3;
                float ox = marks.size() < 2 || limb ? 0 : (m == 0 ? -4 : 4);
                float oy = marks.size() < 2 || !limb ? 0 : (m == 0 ? -5 : 5);
                g.pose().pushPose();
                g.pose().translate(x + PART_CENTRE[i][0] + ox - 3.5f, y + PART_CENTRE[i][1] + oy - 3.5f, 150);
                g.pose().scale(0.4375f, 0.4375f, 1);
                g.renderItem(marks.get(m), 0, 0);
                g.pose().popPose();
            }
        }
    }

    /** Centre of each part on the 28x48 figure (head, thorax, stomach, left arm, right arm, left leg, right leg). */
    private static final int[][] PART_CENTRE = {{14, 5}, {14, 15}, {14, 24}, {5, 19}, {22, 19}, {11, 37}, {17, 37}};

    private record Chip(TextureAtlasSprite sprite, ItemStack item, int ticks, boolean bad) {}

    private static List<Chip> chips(Minecraft mc) {
        List<Chip> out = new ArrayList<>();
        if (ClientTarkov.body.painkillerTicks() > 0) {
            out.add(new Chip(null, new ItemStack(TarkovContent.MEDS.get(Med.PAINKILLERS)), ClientTarkov.body.painkillerTicks(), false));
        }
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (e.showIcon()) {
                out.add(new Chip(mc.getMobEffectTextures().get(e.getEffect()), ItemStack.EMPTY, e.isInfiniteDuration() ? -1 : e.getDuration(),
                        e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL));
            }
        }
        return out;
    }

    private static String time(int ticks) {
        if (ticks < 0) {
            return "∞";
        }
        int s = (ticks + 19) / 20;
        return s >= 60 ? s / 60 + ":" + String.format("%02d", s % 60) : s + "s";
    }

    /** Right edge the left-hand HUD stays inside: a little short of the hotbar and the health bar above it. */
    static int limit(GuiGraphics g) {
        return g.guiWidth() / 2 - 91 - 4;
    }

    /** Re-flows the penalty lines (drawn at 0.75 scale) so none runs past {@code width}. */
    private static List<String> wrap(Minecraft mc, List<String> lines, int width) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            StringBuilder cur = new StringBuilder();
            for (String part : line.split("  ")) {
                String next = cur.isEmpty() ? part : cur + "  " + part;
                if (!cur.isEmpty() && mc.font.width(next) * 0.75f > width) {
                    out.add(cur.toString());
                    cur = new StringBuilder(part);
                } else {
                    cur = new StringBuilder(next);
                }
            }
            if (!cur.isEmpty()) {
                out.add(cur.toString());
            }
        }
        return out;
    }

    /** Scale for an injury line so it fits left of the hotbar: 1, or smaller. */
    private static float fit(Minecraft mc, String text, int width) {
        int w = mc.font.width(text);
        return w <= width ? 1f : Math.max(0.5f, width / (float) w);
    }

    /** Draws upward from {@code bottom}; returns the new top. */
    static int render(GuiGraphics g, Minecraft mc, int x, int bottom) {
        int y = bottom;
        // Leave the body figure its place beside the hotbar (RaidHud puts it there when the screen is wide enough).
        int room = limit(g) - x - 2;
        // Injuries show on the body figure in the top left now; only the biome hazard keeps a line here.
        List<Wound> injuries = new ArrayList<>();
        hazard(injuries);
        List<String> penalties = List.of();
        // Injuries and penalties share one HUD panel (C.5), sized to what's in it.
        if (!injuries.isEmpty() || !penalties.isEmpty()) {
            int pw = 0;
            for (Wound i : injuries) {
                int cx = 12 + (int) (mc.font.width(i.text) * fit(mc, i.text, room - 10));
                boolean cureFits = x + cx + mc.font.width(i.cure) * 0.75f < limit(g);
                pw = Math.max(pw, cx + (cureFits ? (int) (mc.font.width(i.cure) * 0.75f) : 0));
            }
            for (String p : penalties) {
                pw = Math.max(pw, (int) (mc.font.width(p) * 0.75f));
            }
            pw = Math.min(pw, room);
            int ph = injuries.size() * 10 + (penalties.isEmpty() ? 0 : penalties.size() * 8 + 1);
            Kit.hudPanel(g, x - 5, bottom - ph - 4, pw + 10, ph + 6);
        }
        y -= injuries.size() * 10;
        int line = y;
        for (Wound i : injuries) {
            g.pose().pushPose();
            g.pose().translate(x, line, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.renderItem(i.icon, 0, 0);
            g.pose().popPose();
            float k = fit(mc, i.text, room - 10);
            g.pose().pushPose();
            g.pose().translate(x + 10, line + (1 - k) * 4, 0);
            g.pose().scale(k, k, 1);
            Kit.text(g, mc.font, i.text, 0, 0, i.color);
            g.pose().popPose();
            // What fixes it, small and grey, if it fits left of the hotbar.
            int cx = x + 12 + (int) (mc.font.width(i.text) * k);
            if (cx + mc.font.width(i.cure) * 0.75f < limit(g)) {
                g.pose().pushPose();
                g.pose().translate(cx, line + 2, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                Kit.text(g, mc.font, i.cure, 0, 0, Kit.ASH);
                g.pose().popPose();
            }
            line += 10;
        }

        // Penalties, small, above the injuries; cyan while painkillers are taking the edge off.
        if (!penalties.isEmpty()) {
            boolean pain = ClientTarkov.body.painkillerTicks() > 0;
            y -= penalties.size() * 8 + 1;
            int py = y;
            for (String p : penalties) {
                g.pose().pushPose();
                g.pose().translate(x, py, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                Kit.text(g, mc.font, p, 0, 0, pain ? Kit.CYAN : Kit.BONE);
                g.pose().popPose();
                py += 8;
            }
        }

        if (!injuries.isEmpty() || !penalties.isEmpty()) {
            y -= 6;
        }

        List<Chip> chips = chips(mc);
        if (chips.isEmpty()) {
            return y;
        }
        // One row of chips, wrapping before it gets in the way.
        int maxW = Math.min(200, limit(g) - x);
        List<List<Chip>> rows = new ArrayList<>();
        List<Chip> row = new ArrayList<>();
        int w = 0;
        for (Chip c : chips) {
            int cw = ICON + 2 + mc.font.width(time(c.ticks)) + 6;
            if (!row.isEmpty() && w + cw > maxW) {
                rows.add(row);
                row = new ArrayList<>();
                w = 0;
            }
            row.add(c);
            w += cw;
        }
        rows.add(row);
        y -= rows.size() * (ICON + 3) + 2;
        int ry = y;
        for (List<Chip> r : rows) {
            int cx = x;
            for (Chip c : r) {
                String t = time(c.ticks);
                // Blink for the last few seconds so you notice it running out.
                boolean ending = c.ticks >= 0 && c.ticks < 100;
                boolean dim = ending && (c.ticks / 5) % 2 == 0;
                int cw = ICON + 2 + mc.font.width(t) + 2;
                Kit.hudPanel(g, cx - 2, ry - 1, cw + 3, ICON + 2);
                if (c.sprite != null) {
                    g.setColor(1, 1, 1, dim ? 0.4f : 1f);
                    g.blit(cx, ry, 0, ICON, ICON, c.sprite);
                    g.setColor(1, 1, 1, 1);
                } else {
                    g.pose().pushPose();
                    g.pose().translate(cx, ry, 0);
                    g.pose().scale(ICON / 16f, ICON / 16f, 1);
                    g.renderItem(c.item, 0, 0);
                    g.pose().popPose();
                }
                int color = ending ? Kit.GOLD_LIGHT : c.bad ? Kit.BLOOD_LIGHT : Kit.BONE;
                Kit.text(g, mc.font, t, cx + ICON + 2, ry + 2, color);
                cx += cw + 4;
            }
            ry += ICON + 3;
        }
        return y;
    }
}
