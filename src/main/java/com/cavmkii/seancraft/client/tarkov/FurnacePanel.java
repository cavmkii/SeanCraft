package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tetris.TetrisNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Under the backpack on furnace screens: your fuel, the stash's fuel, and what this furnace can cook from each.
 * Click to put a stack in (fuel into the fuel slot, the rest into the input), right-click for one.
 */
public final class FurnacePanel {
    private static final int CELL = 18;
    public static TetrisNet.FurnaceStock stock = new TetrisNet.FurnaceStock(false, List.of(), List.of(), List.of(), List.of());

    private FurnacePanel() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TetrisNet.FurnaceStock.TYPE, (p, ctx) -> stock = p);
    }

    static boolean applies(AbstractContainerScreen<?> screen) {
        return screen.getMenu() instanceof AbstractFurnaceMenu;
    }

    private record Row(String title, List<ItemStack> items, boolean stash, boolean fuel) {}

    private static List<Row> rows() {
        List<Row> rows = new java.util.ArrayList<>();
        rows.add(new Row("FUEL - YOU", stock.fuelYou(), false, true));
        if (stock.stash()) {
            rows.add(new Row("FUEL - STASH", stock.fuelStash(), true, true));
        }
        rows.add(new Row("TO COOK - YOU", stock.smeltYou(), false, false));
        if (stock.stash()) {
            rows.add(new Row("TO COOK - STASH", stock.smeltStash(), true, false));
        }
        return rows;
    }

    private static int perRow(int w) {
        return Math.max(1, w / CELL);
    }

    /** Draws at x, y within width w; returns the hovered item for a tooltip. */
    static ItemStack render(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        ItemStack tip = ItemStack.EMPTY;
        int n = perRow(w);
        for (Row row : rows()) {
            Kit.label(g, font, row.title, x, y);
            y += 10;
            if (row.items.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x, y + 4, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.drawString(font, "none", 0, 0, Kit.ASH, false);
                g.pose().popPose();
            }
            int shown = row.items.size() > n ? n - 1 : row.items.size();
            for (int i = 0; i < shown; i++) {
                ItemStack s = row.items.get(i);
                int cx = x + i * CELL;
                boolean over = mx >= cx && mx < cx + CELL - 1 && my >= y && my < y + CELL - 1;
                Kit.slot(g, cx, y, CELL, over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
                g.renderItem(s, cx + 1, y + 1);
                g.renderItemDecorations(font, s, cx + 1, y + 1, s.getCount() > 999 ? "999+" : null);
                if (over) {
                    tip = s;
                }
            }
            if (shown < row.items.size()) {
                String more = "+" + (row.items.size() - shown);
                g.drawString(font, more, x + shown * CELL + 2, y + 5, Kit.ASH, false);
            }
            y += CELL + 4;
        }
        return tip;
    }

    static int height() {
        return rows().size() * (10 + CELL + 4);
    }

    static boolean click(int x, int y, int w, double mx, double my, int button) {
        int n = perRow(w);
        for (Row row : rows()) {
            y += 10;
            int shown = row.items.size() > n ? n - 1 : row.items.size();
            if (my >= y && my < y + CELL - 1 && mx >= x && mx < x + shown * CELL) {
                int i = (int) ((mx - x) / CELL);
                ClientPlayNetworking.send(new TetrisNet.FurnaceFill(row.items.get(i), row.stash, row.fuel, button == 1 ? 1 : 0));
                return true;
            }
            y += CELL + 4;
        }
        return false;
    }
}
