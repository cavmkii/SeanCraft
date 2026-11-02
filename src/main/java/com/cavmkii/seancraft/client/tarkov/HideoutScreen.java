package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tarkov.TarkovNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Hideout Bench's crafts, or a module block's build-and-upgrade screen (design doc 2.5). Materials come from you
 * and the stash.
 */
public class HideoutScreen extends Screen {
    private static final int ROW = 46;
    private static final int TRACK_W = 40;

    final BlockPos pos;
    private TarkovNet.HideoutView view;
    private double scroll;
    private int ticks;

    HideoutScreen(TarkovNet.HideoutView view) {
        super(Component.literal("Hideout"));
        this.pos = view.pos();
        this.view = view;
    }

    void update(TarkovNet.HideoutView view) {
        this.view = view;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // Refresh once a second for build progress and stash counts.
        if (++ticks % 20 == 0) {
            ClientPlayNetworking.send(new TarkovNet.HideoutAction(pos, -1, ""));
        }
    }

    private int panelWidth() {
        return Math.min(420, width - 16);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int listTop() {
        return 44;
    }

    private int listBottom() {
        return height - 8;
    }

    /** Everything in the view: the bench's crafts, or the one module a module block opened. */
    private List<TarkovNet.Entry> shown() {
        return new ArrayList<>(view.entries());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Kit.BACKDROP);
        int x0 = left();
        int w = panelWidth();
        Kit.panel(g, x0 - 8, 2, w + 16, height - 4);
        // The station's name as a heading (Jacquard 12, C.6).
        Component heading = Kit.heading(StationTabs.label(view.station()));
        Kit.text(g, font, heading, x0, 9, Kit.GOLD_LIGHT);
        // Cut short of the CRAFT button when there is one.
        int room = w - font.width(heading) - 12 - (craftModule() != null ? 56 : 0);
        g.drawString(font, font.plainSubstrByWidth(view.summary(), Math.max(0, room)), x0 + font.width(heading) + 12, 10, Kit.ASH, false);
        StationTabs.render(g, font, x0, 24, w, view.station(), mouseX, mouseY);
        if (craftModule() != null) {
            Kit.button(g, font, "CRAFT", x0 + w - 50, 6, 50, 14, true, overCraft(mouseX, mouseY), false);
        }

        List<TarkovNet.Entry> entries = shown();
        int top = listTop();
        int bottom = listBottom();
        double maxScroll = Math.max(0, entries.size() * ROW - (bottom - top));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.enableScissor(x0, top, x0 + w, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = top + i * ROW - (int) scroll;
            if (y + ROW < top || y > bottom) {
                continue;
            }
            drawEntry(g, entries.get(i), x0, y, w, mouseX, mouseY);
        }
        g.disableScissor();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No vanilla blur; render() fills its own backdrop.
    }

    private void drawEntry(GuiGraphics g, TarkovNet.Entry e, int x, int y, int w, int mouseX, int mouseY) {
        Kit.inset(g, x, y, w, ROW - 3);
        g.renderItem(new ItemStack(item(e.icon())), x + 4, y + 4);
        g.drawString(font, e.title(), x + 24, y + 4, Kit.BONE, false);

        g.pose().pushPose();
        g.pose().translate(x + 24, y + 15, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, font.plainSubstrByWidth(e.subtitle(), (int) ((w - 156) / 0.75)), 0, 0, Kit.ASH, false);
        g.pose().popPose();

        // Costs: icon and have/need, red when short.
        int cx = x + 24;
        for (TarkovNet.CostLine c : e.costs()) {
            g.pose().pushPose();
            g.pose().translate(cx, y + 25, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(new ItemStack(item(c.icon())), 0, 0);
            g.pose().popPose();
            // Modules: put in so far / needed, plus what you could still add.
            String text = Math.min(c.have(), 9999) + "/" + c.need() + (c.carry() > 0 ? " +" + c.carry() : "");
            int color = c.have() >= c.need() ? Kit.MOSS : c.have() + c.carry() >= c.need() ? Kit.GOLD : Kit.BLOOD_LIGHT;
            g.drawString(font, text, cx + 14, y + 29, color, false);
            cx += 18 + font.width(text) + 6;
        }

        // Button / status on the right.
        int bx = x + w - 84;
        int by = y + 6;
        boolean ready = e.status() == TarkovNet.Entry.READY || e.status() == TarkovNet.Entry.GIVE;
        boolean hover = mouseX >= bx && mouseX < bx + 78 && mouseY >= by && mouseY < by + 18;

        String label = switch (e.status()) {
            case TarkovNet.Entry.READY -> e.kind() == TarkovNet.Entry.MODULE ? "BUILD" : "CRAFT";
            case TarkovNet.Entry.GIVE -> "CONTRIBUTE";
            case TarkovNet.Entry.BUILDING -> e.progress() + "%";
            case TarkovNet.Entry.MAXED -> "MAX";
            case TarkovNet.Entry.SHORT -> "NEED MORE";
            case TarkovNet.Entry.BUSY -> "BUSY";
            default -> "LOCKED";
        };
        Kit.button(g, font, label, bx, by, 78, 18, ready, hover, false);
        if (trackable(e)) {
            int tx = bx - TRACK_W - 4;
            boolean th = mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= by && mouseY < by + 18;
            Kit.button(g, font, "TRACK", tx, by, TRACK_W, 18, true, th, false);
        }
        if (e.status() == TarkovNet.Entry.BUILDING) {
            Kit.bar(g, "xp", bx, by + 18, 78, e.progress() / 100f);
        }
        g.pose().pushPose();
        g.pose().translate(bx, by + 24, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, font.plainSubstrByWidth(e.note(), (int) (100 / 0.75)), 0, 0,
                ready ? Kit.MOSS : e.status() == TarkovNet.Entry.MAXED ? Kit.GOLD : Kit.EMBER, false);
        g.pose().popPose();
    }

    /** Anything still missing, on something you can still build or craft. */
    private static boolean trackable(TarkovNet.Entry e) {
        if (e.status() == TarkovNet.Entry.MAXED || e.status() == TarkovNet.Entry.BUILDING) {
            return false;
        }
        return e.costs().stream().anyMatch(c -> c.have() < c.need());
    }

    /**
     * Puts what's missing on the gather list. For a module that's what hasn't been contributed yet; for a recipe
     * it's the full cost, since the list counts what you carry and not the stash.
     */
    private static void track(TarkovNet.Entry e) {
        for (TarkovNet.CostLine c : e.costs()) {
            int amount = e.kind() == TarkovNet.Entry.MODULE ? c.need() - c.have() : c.need();
            if (amount > 0) {
                ClientTracker.add(c.track(), amount);
            }
        }
    }

    /** The module this screen is for, if it makes things (its CRAFT button opens its grid; locked recipes say the level). */
    private com.cavmkii.seancraft.tarkov.Module craftModule() {
        for (com.cavmkii.seancraft.tarkov.Module m : com.cavmkii.seancraft.tarkov.Module.values()) {
            if (com.cavmkii.seancraft.tarkov.Stations.moduleKind(m).equals(view.station())
                    && com.cavmkii.seancraft.tarkov.HideoutCraftMenu.crafts(m)) {
                return m;
            }
        }
        return null;
    }

    private boolean overCraft(double mx, double my) {
        int bx = left() + panelWidth() - 50;
        return mx >= bx && mx < bx + 50 && my >= 6 && my < 20;
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null ? BuiltInRegistries.ITEM.get(key) : net.minecraft.world.item.Items.BARRIER;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x0 = left();
        if (StationTabs.click(x0, 24, panelWidth(), view.station(), mouseX, mouseY)) {
            return true;
        }
        if (craftModule() != null && overCraft(mouseX, mouseY)) {
            ClientPlayNetworking.send(new TarkovNet.HideoutAction(pos, -3, craftModule().name()));
            return true;
        }
        if (mouseY >= listTop() && mouseY < listBottom()) {
            List<TarkovNet.Entry> entries = shown();
            int i = (int) ((mouseY - listTop() + scroll) / ROW);
            if (i >= 0 && i < entries.size()) {
                TarkovNet.Entry e = entries.get(i);
                int y = listTop() + i * ROW - (int) scroll;
                int bx = x0 + panelWidth() - 84;
                if ((e.status() == TarkovNet.Entry.READY || e.status() == TarkovNet.Entry.GIVE) && mouseX >= bx && mouseX < bx + 78 && mouseY >= y + 6 && mouseY < y + 24) {
                    ClientPlayNetworking.send(new TarkovNet.HideoutAction(pos, e.kind(), e.id()));
                    return true;
                }
                int tx = bx - TRACK_W - 4;
                if (trackable(e) && mouseX >= tx && mouseX < tx + TRACK_W && mouseY >= y + 6 && mouseY < y + 24) {
                    track(e);
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 20;
        return true;
    }
}
