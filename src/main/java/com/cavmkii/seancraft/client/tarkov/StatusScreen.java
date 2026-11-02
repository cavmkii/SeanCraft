package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tarkov.Body;
import com.cavmkii.seancraft.tarkov.Cures;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import com.cavmkii.seancraft.tarkov.BodyPart;
import com.cavmkii.seancraft.tarkov.MedItem;
import com.cavmkii.seancraft.tarkov.TarkovNet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** STATUS: the body, each part's health and conditions, and the meds you carry. Click a med, then a part. */
public class StatusScreen extends Screen {
    private int selectedSlot = -1;

    public StatusScreen() {
        super(Component.literal("Status"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** Body part rectangles: x, y, w, h in units, relative to the figure's top-left. */
    private static final int[][] LAYOUT = {
            {3, 0, 2, 2},    // head
            {2, 2, 4, 3},    // thorax
            {2, 5, 4, 2},    // stomach
            {0, 2, 2, 5},    // left arm (drawn on the viewer's left)
            {6, 2, 2, 5},    // right arm
            {2, 7, 2, 5},    // left leg
            {4, 7, 2, 5},    // right leg
    };

    private int unit() {
        return Math.max(8, Math.min(16, (height - 60) / 13));
    }

    private int figX() {
        return width / 2 - 8 * unit() - 20;
    }

    private int figY() {
        return 34;
    }

    private int[] rect(BodyPart p) {
        int u = unit();
        int[] l = LAYOUT[p.ordinal()];
        return new int[] {figX() + l[0] * u, figY() + l[1] * u, l[2] * u, l[3] * u};
    }

    private List<Integer> medSlots() {
        List<Integer> out = new ArrayList<>();
        List<ItemStack> carried = ClientTetris.carried();
        for (int i = 0; i < carried.size(); i++) {
            if (carried.get(i).getItem() instanceof MedItem) {
                out.add(i);
            }
        }
        return out;
    }

    private static int pct(float f) {
        return Math.round(f * 100);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TarkovNet.BodySync body = ClientTarkov.body;
        float max = minecraft.player.getMaxHealth();
        Kit.label(g, font, "STATUS", 10, 10, Kit.GOLD_LIGHT);
        g.drawString(font, String.format("Health %.0f / %.0f", minecraft.player.getHealth(), max), 70, 10, Kit.BONE, false);
        if (body.painkillerTicks() > 0) {
            g.drawString(font, "Painkillers: " + body.painkillerTicks() / 20 + "s", 190, 10, Kit.CYAN, false);
        }

        BodyPart hovered = null;
        for (BodyPart p : BodyPart.values()) {
            int[] r = rect(p);
            float frac = body.body()[p.ordinal()];
            boolean destroyed = frac <= 0.0001f;
            // The kit's part tints (status/tint_*): healthy, injured, critical, destroyed.
            int color = destroyed ? 0xFF3A3430 : frac > 0.66f ? Kit.MOSS : frac > 0.33f ? Kit.EMBER : Kit.BLOOD_LIGHT;
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (hover) {
                hovered = p;
            }
            g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, hover && selectedSlot >= 0 ? Kit.GOLD_LIGHT : Kit.VOID);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], color);
            int c = body.conditions()[p.ordinal()];
            StringBuilder tags = new StringBuilder();
            int i = p.ordinal();
            if ((c & Body.HEAVY_BLEED) != 0) tags.append("HB").append(pct(body.bleed()[i])).append(' ');
            else if ((c & Body.LIGHT_BLEED) != 0) tags.append("B").append(pct(body.bleed()[i])).append(' ');
            if ((c & Body.FRACTURE) != 0) tags.append("F").append(pct(body.fracture()[i]));
            if (tags.length() > 0) {
                g.drawString(font, tags.toString().trim(), r[0] + 2, r[1] + 2, Kit.BONE, true);
            }
        }

        // Part list on the right of the figure.
        int lx = figX() + 8 * unit() + 16;
        int ly = figY();
        for (BodyPart p : BodyPart.values()) {
            float frac = body.body()[p.ordinal()];
            float cap = 1 - body.scar()[p.ordinal()];
            String state = frac <= 0.0001f ? "DESTROYED" : String.format("%.0f / %.0f", frac * p.share * max, cap * p.share * max);
            g.drawString(font, p.label, lx, ly, Kit.BONE, false);
            g.drawString(font, state, lx + 62, ly, frac <= 0.0001f ? Kit.BLOOD_LIGHT : Kit.ASH, false);
            int c = body.conditions()[p.ordinal()];
            List<String> conds = new ArrayList<>();
            int i = p.ordinal();
            if ((c & Body.HEAVY_BLEED) != 0) conds.add("heavy bleed " + pct(body.bleed()[i]) + "%");
            if ((c & Body.LIGHT_BLEED) != 0) conds.add("light bleed " + pct(body.bleed()[i]) + "%");
            if ((c & Body.FRACTURE) != 0) conds.add("fracture " + pct(body.fracture()[i]) + "%");
            if (body.scar()[i] > 0.001f) conds.add("lasting damage " + pct(body.scar()[i]) + "%");
            if (!conds.isEmpty()) {
                g.drawString(font, String.join(", ", conds), lx + 8, ly + 9, Kit.BLOOD_LIGHT, false);
                ly += 9;
            }
            ly += 12;
        }

        // Meds carried.
        ly += 6;
        g.drawString(font, selectedSlot >= 0 ? "Now click a body part" : "Meds (click one, then a part)", lx, ly, Kit.GOLD_LIGHT, false);
        ly += 12;
        int mx = lx;
        String medTip = null;
        List<ItemStack> carried = ClientTetris.carried();
        for (int slot : medSlots()) {
            ItemStack stack = carried.get(slot);
            boolean sel = slot == selectedSlot;
            Kit.slot(g, mx, ly, 20, sel ? Kit.SlotState.SELECTED : Kit.SlotState.NORMAL);
            g.renderItem(stack, mx + 2, ly + 2);
            g.renderItemDecorations(font, stack, mx + 2, ly + 2);
            if (mouseX >= mx && mouseX < mx + 20 && mouseY >= ly && mouseY < ly + 20) {
                medTip = stack.getHoverName().getString() + ": " + ((MedItem) stack.getItem()).med.describe();
            }
            mx += 22;
        }
        if (medSlots().isEmpty()) {
            g.drawString(font, "none (Hideout Bench, Medstation)", lx, ly + 4, Kit.ASH, false);
        }

        // Status effects, each with the cures you carry.
        int ey = medsTop() + 26;
        List<EffectRow> rows = effectRows();
        g.drawString(font, rows.isEmpty() ? "No status effects" : "Status effects (click a cure to use it)", lx, ey, Kit.GOLD_LIGHT, false);
        for (EffectRow row : rows) {
            MobEffectInstance e = row.effect;
            boolean bad = Cures.harmful(e.getEffect());
            g.blit(row.x, row.y, 0, 18, 18, minecraft.getMobEffectTextures().get(e.getEffect()));
            String name = Component.translatable(e.getDescriptionId()).getString()
                    + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            g.drawString(font, name, row.x + 22, row.y + 1, bad ? Kit.BLOOD_LIGHT : Kit.MOSS, false);
            g.drawString(font, MobEffectUtil.formatDuration(e, 1.0f, minecraft.level.tickRateManager().tickrate()).getString(),
                    row.x + 22, row.y + 10, 0xFF888890, false);
            if (row.cures.isEmpty() && e.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) {
                g.drawString(font, "cure: milk" + (e.getEffect().is(MobEffects.POISON) ? ", honey" : "") + (bad ? ", antidote" : ""),
                        row.cureX, row.y + 5, 0xFF666670, false);
            }
            for (int i = 0; i < row.cures.size(); i++) {
                int cx = row.cureX + i * 22;
                ItemStack cure = carried.get(row.cures.get(i));
                boolean hover = mouseX >= cx && mouseX < cx + 20 && mouseY >= row.y && mouseY < row.y + 20;
                Kit.buttonBg(g, cx, row.y, 20, 20, true, hover, false);
                g.renderItem(cure, cx + 2, row.y + 2);
                if (hover) {
                    medTip = "Use " + cure.getHoverName().getString() + " to cure " + name;
                }
            }
        }

        if (medTip != null) {
            g.renderTooltip(font, List.of(Component.literal(medTip)), Optional.empty(), mouseX, mouseY);
        } else if (hovered != null) {
            float frac = body.body()[hovered.ordinal()];
            g.renderTooltip(font, List.of(Component.literal(hovered.label).withStyle(ChatFormatting.WHITE),
                    Component.literal(frac <= 0.0001f ? "Destroyed: needs a surgical kit" : (int) (frac * 100) + "%").withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private record EffectRow(MobEffectInstance effect, int x, int y, int cureX, List<Integer> cures) {}

    /** Active effects, harmful first, with the carried items (indices into ClientTetris.carried) that cure each. */
    private List<EffectRow> effectRows() {
        List<MobEffectInstance> effects = new ArrayList<>(minecraft.player.getActiveEffects());
        effects.sort((a, b) -> Boolean.compare(Cures.harmful(b.getEffect()), Cures.harmful(a.getEffect())));
        List<ItemStack> carried = ClientTetris.carried();
        int lx = figX() + 8 * unit() + 16;
        int y = medsTop() + 38;
        List<EffectRow> rows = new ArrayList<>();
        for (MobEffectInstance e : effects) {
            List<Integer> cures = new ArrayList<>();
            List<net.minecraft.world.item.Item> seen = new ArrayList<>();
            for (int i = 0; i < carried.size(); i++) {
                ItemStack s = carried.get(i);
                if (!s.isEmpty() && !seen.contains(s.getItem()) && Cures.cures(s, e.getEffect())) {
                    seen.add(s.getItem());
                    cures.add(i);
                }
            }
            rows.add(new EffectRow(e, lx, y, lx + 130, cures));
            y += 22;
        }
        return rows;
    }

    private int medsTop() {
        int ly = figY();
        for (BodyPart p : BodyPart.values()) {
            ly += 12 + (ClientTarkov.body.conditions()[p.ordinal()] != 0 ? 9 : 0);
        }
        return ly + 18;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EffectRow row : effectRows()) {
            for (int i = 0; i < row.cures.size(); i++) {
                int cx = row.cureX + i * 22;
                if (mouseX >= cx && mouseX < cx + 20 && mouseY >= row.y && mouseY < row.y + 20) {
                    ClientPlayNetworking.send(new TarkovNet.CureEffect(
                            net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(row.effect.getEffect().value()).toString(),
                            row.cures.get(i)));
                    return true;
                }
            }
        }
        int lx = figX() + 8 * unit() + 16;
        int ly = medsTop();
        int mx = lx;
        for (int slot : medSlots()) {
            if (mouseX >= mx && mouseX < mx + 20 && mouseY >= ly && mouseY < ly + 20) {
                selectedSlot = selectedSlot == slot ? -1 : slot;
                return true;
            }
            mx += 22;
        }
        if (selectedSlot >= 0) {
            for (BodyPart p : BodyPart.values()) {
                int[] r = rect(p);
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    ClientPlayNetworking.send(new TarkovNet.TreatPart(selectedSlot, p.ordinal()));
                    selectedSlot = -1;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientTarkov.OPEN_STATUS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
