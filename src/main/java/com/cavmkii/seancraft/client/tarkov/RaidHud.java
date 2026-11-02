package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.tarkov.Distance;

import com.cavmkii.seancraft.tarkov.TarkovNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bottom left: HIDEOUT, or RAID with the timer, threat level and when it rises next; injuries and effects above. */
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

    /** One line for the inventory's top right (C.4): IN RAID, the band, threat and FOB time; or HIDEOUT / SPAWN. */
    static String stateLine() {
        TarkovNet.RaidState s = ClientTarkov.raid;
        if (s == null || s.state() == 0) {
            return "";
        }
        if (s.state() == 1) {
            return "HIDEOUT";
        }
        if (s.state() == 3) {
            return "SPAWN (SAFE)";
        }
        StringBuilder out = new StringBuilder("IN RAID ").append(String.format("%02d:%02d", s.raidSeconds() / 60, s.raidSeconds() % 60));
        if (s.band() > 0) {
            out.append("   ").append(Distance.values()[Math.min(s.band(), Distance.values().length - 1)].label.toUpperCase());
        }
        out.append("   THREAT ").append(s.threat());
        if (s.fobLeft() >= 0) {
            out.append("   FOB ").append(String.format("%d:%02d", s.fobLeft() / 60, s.fobLeft() % 60));
        }
        return out.toString();
    }

    /** The state line from longest to shortest, for wherever it has to fit. */
    static java.util.List<String> stateLines() {
        String full = stateLine();
        TarkovNet.RaidState s = ClientTarkov.raid;
        if (full.isEmpty() || s == null || s.state() != 2) {
            return full.isEmpty() ? java.util.List.of() : java.util.List.of(full);
        }
        String time = String.format("%02d:%02d", s.raidSeconds() / 60, s.raidSeconds() % 60);
        return java.util.List.of(full, "IN RAID " + time + "   THREAT " + s.threat(), "RAID " + time + " T" + s.threat(), time);
    }

    static int stateColor() {
        TarkovNet.RaidState s = ClientTarkov.raid;
        return s != null && s.state() == 2 ? com.cavmkii.seancraft.client.ui.Kit.BLOOD_LIGHT : com.cavmkii.seancraft.client.ui.Kit.ASH;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            banner(g, mc);
        }
        TarkovNet.RaidState s = ClientTarkov.raid;
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int x = 4;
        int y = g.guiHeight() - 30;
        EffectHud.render(g, mc, x, Math.min(s.state() == 0 ? g.guiHeight() - 4 : y - 4, chatTop(g, mc)));
        // Limb status: against the hotbar's left side when there's room past the raid panel, else the top-right corner.
        int fx = EffectHud.limit(g) - EffectHud.FIG_W - 2;
        if (EffectHud.figureBeside(g, x)) {
            EffectHud.body(g, mc, fx, g.guiHeight() - EffectHud.FIG_H - 6);
        } else {
            EffectHud.body(g, mc, g.guiWidth() - EffectHud.FIG_W - 6, 6);
        }
        if (s.state() == 0) {
            return;
        }
        if (s.state() == 1 || s.state() == 3) {
            // Safe places are gold: gold marks what's secure (C.2).
            String safe = s.state() == 1 ? "HIDEOUT" : "SPAWN (SAFE)";
            Kit.hudPanel(g, x - 5, y - 4, mc.font.width(safe) + 10, 15);
            Kit.text(g, mc.font, safe, x, y, Kit.GOLD);
            return;
        }
        boolean fobLine = s.fobLeft() >= 0 && (s.fobPaused() || s.fobLeft() < Cfg.fobPauseMinutes * 60);
        Kit.hudPanel(g, x - 5, y - 4, 150, fobLine ? 35 : 25);
        String time = String.format("%02d:%02d", s.raidSeconds() / 60, s.raidSeconds() % 60);
        String raid = "RAID " + time;
        Kit.text(g, mc.font, raid, x, y, Kit.BLOOD_LIGHT);
        int rx = x + mc.font.width(raid) + 6;
        if (s.band() > 0) {
            String band = Distance.values()[Math.min(s.band(), Distance.values().length - 1)].label;
            Kit.text(g, mc.font, band.toUpperCase(), rx, y, Kit.BLOOD_LIGHT);
        }
        // FOB time left this raid, under the threat line; bright while a FOB table is holding the timer.
        if (fobLine) {
            String fob = (s.fobPaused() ? "FOB: timer paused, " : "FOB time ") + String.format("%d:%02d", s.fobLeft() / 60, s.fobLeft() % 60)
                    + (s.fobPaused() ? " left" : "");
            Kit.text(g, mc.font, fob, x, y + 20, s.fobPaused() ? Kit.GOLD : Kit.ASH);
        }
        // Threat as the kit's pips, then when it rises next.
        Kit.text(g, mc.font, "THREAT", x, y + 10, s.threat() == 0 ? Kit.ASH : Kit.BLOOD_LIGHT);
        int px = x + mc.font.width("THREAT") + 4;
        for (int i = 1; i <= 4; i++) {
            Kit.blit(g, i <= s.threat() ? "hud/threat_pip_on" : "hud/threat_pip_off", px + (i - 1) * 7, y + 11, 5, 5);
        }
        if (s.nextThreat() >= 0) {
            Kit.text(g, mc.font, String.format("next %d:%02d", s.nextThreat() / 60, s.nextThreat() % 60), px + 32, y + 10, Kit.ASH);
        }
    }
}
