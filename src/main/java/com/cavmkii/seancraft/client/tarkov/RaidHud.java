package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.tarkov.Distance;

import com.cavmkii.seancraft.tarkov.TarkovNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Bottom left (C.5): where you are and the danger band, Blood light in the Frontier and Far lands; injuries and
 * effects above it. Also the banners at the top of the screen.
 */
final class RaidHud {
    private RaidHud() {}

    private static final long BANNER_MS = 4000;

    /** Raid banners sit at the top of the screen and fade in and out. */
    private static void banner(GuiGraphics g, Minecraft mc) {
        TarkovNet.Banner b = ClientTarkov.banner;
        if (b == null) {
            return;
        }
        long age = net.minecraft.Util.getMillis() - ClientTarkov.bannerAt;
        if (age > BANNER_MS) {
            return;
        }
        float alpha = Math.min(1f, Math.min(age / 300f, (BANNER_MS - age) / 600f));
        int a = Math.max(8, (int) (alpha * 255)) << 24;
        int cx = g.guiWidth() / 2;
        int y = 6;
        int w = Math.max((int) (mc.font.width(b.title()) * 1.5f), mc.font.width(b.subtitle())) + 16;
        if (alpha > 0.5f) {
            Kit.hudPanel(g, cx - w / 2, y - 4, w, 31);
        }
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(mc.font, b.title(), -mc.font.width(b.title()) / 2, 0, a | (b.color() & 0xFFFFFF), true);
        g.pose().popPose();
        g.drawString(mc.font, b.subtitle(), cx - mc.font.width(b.subtitle()) / 2, y + 15, a | (Kit.BONE & 0xFFFFFF), true);
    }

    /**
     * Top of what chat is showing right now (recent messages, or the whole history while it's open), so injuries and
     * effects sit above it instead of underneath. Vanilla draws chat upward from 40 above the bottom of the screen.
     */
    private static int chatTop(GuiGraphics g, Minecraft mc) {
        net.minecraft.client.gui.components.ChatComponent chat = mc.gui.getChat();
        java.util.List<net.minecraft.client.GuiMessage.Line> lines = ((com.cavmkii.seancraft.mixin.client.ChatAccessor) chat).seancraft$lines();
        int visible;
        if (chat.isChatFocused()) {
            visible = Math.min(lines.size(), chat.getLinesPerPage());
        } else {
            int now = mc.gui.getGuiTicks();
            visible = 0;
            for (net.minecraft.client.GuiMessage.Line line : lines) {
                if (now - line.addedTime() >= 200 || visible >= chat.getLinesPerPage()) {
                    break;
                }
                visible++;
            }
        }
        if (visible == 0 || mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
            return Integer.MAX_VALUE;
        }
        double lineHeight = 9 * (mc.options.chatLineSpacing().get() + 1) * chat.getScale();
        return (int) (g.guiHeight() - 40 - visible * lineHeight) - 4;
    }

    private static String band(TarkovNet.PlaceState s) {
        return Distance.values()[Math.min(s.band(), Distance.values().length - 1)].label.toUpperCase();
    }

    /** Night in the overworld: elites spawn twice as often, and hunting parties come out in the Frontier and Far lands. */
    private static boolean night() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD && !mc.level.isDay();
    }

    /** One line for the inventory's top right (C.4): HOME, SPAWN or CAMP on safe ground, else the band (and NIGHT). */
    static String stateLine() {
        TarkovNet.PlaceState s = ClientTarkov.place;
        if (s == null || s.place() == TarkovNet.PlaceState.UNKNOWN) {
            return "";
        }
        String where = switch (s.place()) {
            case TarkovNet.PlaceState.HOME -> "HOME";
            case TarkovNet.PlaceState.SPAWN -> "SPAWN (SAFE)";
            case TarkovNet.PlaceState.CAMP -> "CAMP";
            default -> "";
        };
        String band = band(s) + (night() ? "  NIGHT" : "");
        return where.isEmpty() ? band : where + "  " + band;
    }

    /** The state line from longest to shortest, for wherever it has to fit. */
    static java.util.List<String> stateLines() {
        String full = stateLine();
        TarkovNet.PlaceState s = ClientTarkov.place;
        if (full.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.of(full, band(s));
    }

    /** Blood light in the Frontier and Far lands (C.5); Moss on safe ground; Ash otherwise. */
    static int stateColor() {
        TarkovNet.PlaceState s = ClientTarkov.place;
        if (s == null) {
            return Kit.ASH;
        }
        if (s.safe()) {
            return Kit.MOSS;
        }
        return s.band() >= Distance.FRONTIER.ordinal() ? Kit.BLOOD_LIGHT : Kit.ASH;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            banner(g, mc);
        }
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int x = 4;
        int y = g.guiHeight() - 20;
        TarkovNet.PlaceState s = ClientTarkov.place;
        EffectHud.render(g, mc, x, Math.min(s.place() == TarkovNet.PlaceState.UNKNOWN ? g.guiHeight() - 4 : y - 4, chatTop(g, mc)));
        EffectHud.topLeft(g, mc);
        // The longest line that clears the hotbar; if even the short one doesn't, sit above the hotbar.
        int room = g.guiWidth() / 2 - 91 - 6 - x;
        String line = "";
        for (String l : stateLines()) {
            line = l;
            if (mc.font.width(l) + 5 <= room) {
                break;
            }
        }
        if (line.isEmpty()) {
            return;
        }
        if (mc.font.width(line) + 5 > room) {
            y = g.guiHeight() - 42;
        }
        Kit.hudPanel(g, x - 5, y - 4, mc.font.width(line) + 10, 15);
        Kit.text(g, mc.font, line, x, y, stateColor());
    }
}
