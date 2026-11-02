package com.cavmkii.seancraft.client.ui;

import com.cavmkii.seancraft.SeanCraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The SeanCraft UI kit (design doc Appendix C): the 18-colour palette and helpers that draw the GUI sprites under
 * {@code textures/gui/sprites/}. Every screen and HUD element draws through here so the look stays in one place.
 */
public final class Kit {
    // C.2 palette.
    public static final int VOID = 0xFF0E0C0B;
    public static final int SOOT = 0xFF1B1714;
    public static final int IRON_DARK = 0xFF2B2622;
    public static final int IRON = 0xFF3D3630;
    public static final int IRON_LIGHT = 0xFF5A5149;
    public static final int EDGE = 0xFF7D7266;
    public static final int BONE = 0xFFD9CBB0;
    public static final int ASH = 0xFF8F8371;
    public static final int GOLD_DARK = 0xFF6E5526;
    public static final int GOLD = 0xFFB08A3E;
    public static final int GOLD_LIGHT = 0xFFE0BC62;
    public static final int BLOOD = 0xFF8E1F1F;
    public static final int BLOOD_LIGHT = 0xFFC8402E;
    public static final int EMBER = 0xFFC8662A;
    public static final int MOSS = 0xFF7D9A3A;
    public static final int GREEN = 0xFF5FAE3C;
    public static final int ARCANE = 0xFF4A6FB0;
    public static final int CYAN = 0xFF4FB3B3;
    /** Disabled button text, drawn without a shadow. */
    public static final int DISABLED = 0xFF5A5048;
    public static final int SHADOW = 0xFF070605;
    /** The screen dim behind SeanCraft's full-screen views. */
    public static final int BACKDROP = 0xF00E0C0B;

    /** Jacquard 12, for headings in mixed case (C.6). */
    public static final ResourceLocation HEADING_FONT = SeanCraft.id("heading");

    public enum SlotState { NORMAL, HOVER, ARROW_SOURCE, LOCKED, SELECTED, RESULT }

    private Kit() {}

    public static ResourceLocation sprite(String path) {
        return SeanCraft.id(path);
    }

    public static void blit(GuiGraphics g, String sprite, int x, int y, int w, int h) {
        // Translucent sprites (the ghost overlay, tints) need blending on; an item drawn just before leaves it off,
        // which made the ghost overlay opaque and hid the item under it.
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blitSprite(sprite(sprite), x, y, w, h);
    }

    /** A window: forged-iron frame, soot inside. Content starts 8 px in. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        blit(g, "panel/frame", x, y, w, h);
    }

    /** A sunken area: lists, the body figure, the filter box. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        blit(g, "panel/inset", x, y, w, h);
    }

    /** Tooltips and every HUD panel. */
    public static void hudPanel(GuiGraphics g, int x, int y, int w, int h) {
        blit(g, "tooltip/background", x, y, w, h);
    }

    public static void slot(GuiGraphics g, int x, int y, int size, SlotState state) {
        String name = switch (state) {
            case NORMAL -> "slot/normal";
            case HOVER -> "slot/hover";
            case ARROW_SOURCE -> "slot/arrow_source";
            case LOCKED -> "slot/locked";
            case SELECTED -> "slot/selected";
            case RESULT -> "slot/result";
        };
        blit(g, name, x, y, size, size);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean hover) {
        slot(g, x, y, 18, hover ? SlotState.HOVER : SlotState.NORMAL);
    }

    /** Button background for a state; pressed doubles as a toggled-on filter. */
    public static void buttonBg(GuiGraphics g, int x, int y, int w, int h, boolean enabled, boolean hover, boolean pressed) {
        blit(g, !enabled ? "button/disabled" : pressed ? "button/pressed" : hover ? "button/hover" : "button/normal", x, y, w, h);
    }

    /** A button with centred text: Bone with a shadow, Gold light on hover, Disabled with no shadow. */
    public static void button(GuiGraphics g, Font font, String text, int x, int y, int w, int h, boolean enabled, boolean hover, boolean pressed) {
        buttonBg(g, x, y, w, h, enabled, hover, pressed);
        int tx = x + (w - font.width(text)) / 2;
        int ty = y + (h - 8) / 2 + (pressed ? 1 : 0);
        if (!enabled) {
            g.drawString(font, text, tx, ty, DISABLED, false);
        } else {
            text(g, font, text, tx, ty, hover ? GOLD_LIGHT : BONE);
        }
    }

    /** A button scaled down, for the dense layouts: text at {@code textScale}. */
    public static void smallButton(GuiGraphics g, Font font, String text, int x, int y, int w, int h, boolean enabled, boolean hover,
            boolean pressed, float textScale) {
        buttonBg(g, x, y, w, h, enabled, hover, pressed);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + (h - 8 * textScale) / 2f + (pressed ? 1 : 0), 0);
        g.pose().scale(textScale, textScale, 1);
        int tx = -font.width(text) / 2;
        if (!enabled) {
            g.drawString(font, text, tx, 0, DISABLED, false);
        } else {
            text(g, font, text, tx, 0, hover ? GOLD_LIGHT : BONE);
        }
        g.pose().popPose();
    }

    /** A tab on a panel's top edge. Active: 17 tall, joins the panel. Inactive: 13 tall, 2 px lower. */
    public static void tab(GuiGraphics g, Font font, String text, int x, int panelTop, int w, boolean active, boolean hover) {
        int y = panelTop - 12;
        if (active) {
            blit(g, "tab/active", x, y, w, 17);
        } else {
            blit(g, hover ? "tab/inactive_hover" : "tab/inactive", x, y + 2, w, 13);
        }
        text(g, font, text, x + (w - font.width(text)) / 2, y + 5, active ? GOLD_LIGHT : hover ? BONE : ASH);
    }

    public static int tabWidth(Font font, String text) {
        return font.width(text) + 14;
    }

    /** A bar: frame, then the fill stretched to {@code fraction} of the inside. */
    public static void bar(GuiGraphics g, String fill, int x, int y, int w, float fraction) {
        blit(g, "bar/frame", x, y, w, 7);
        int inner = Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (inner > 0) {
            blit(g, "bar/" + fill, x + 1, y + 1, inner, 5);
        }
    }

    /** Body text with the kit's 1 px near-black shadow. */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, SHADOW, false);
        g.drawString(font, text, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text.copy().withColor(SHADOW & 0xFFFFFF), x + 1, y + 1, SHADOW, false);
        g.drawString(font, text, x, y, color, false);
    }

    /** A section label: Ash, no shadow (labels sit on soot, so a shadow would only blur them). */
    public static void label(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, ASH, false);
    }

    public static void label(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** A heading in Jacquard 12 (mixed case only). */
    public static Component heading(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(HEADING_FONT));
    }
}
