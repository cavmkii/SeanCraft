package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tetris.GearItem;
import com.cavmkii.seancraft.tetris.Grid;
import com.cavmkii.seancraft.tetris.GridId;
import com.cavmkii.seancraft.tetris.InvData;
import com.cavmkii.seancraft.tetris.ItemSize;
import com.cavmkii.seancraft.tetris.Loc;
import com.cavmkii.seancraft.tetris.Placed;
import com.cavmkii.seancraft.tetris.Tetris;
import com.cavmkii.seancraft.tetris.TetrisNet;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Tetris grids beside vanilla container screens (crafting table, furnace, anvil...), whose own 27 main
 * inventory slots are always empty. Clicks swap with the menu's cursor: pick a grid item up and drop it in a
 * vanilla slot, or carry something from a vanilla slot onto a grid cell. Right-click takes half or puts down one,
 * Shift+click sends a grid item into the menu, R rotates what you carry.
 */
public final class GridPanel {
    private static boolean rotated;
    private static boolean swallowRelease;

    private GridPanel() {}

    private record Box(GridId id, String title, int x, int y, int w, int h, int c, List<Placed> items) {}

    /** Does this screen get the panel? Survival screens that show the player's main inventory. */
    public static boolean applies(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Tetris.active(mc.player) || screen instanceof CreativeModeInventoryScreen) {
            return false;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (deadSlot(slot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean deadSlot(Slot slot) {
        return slot.container instanceof Inventory && slot.getContainerSlot() >= Inventory.getSelectionSize()
                && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
    }

    private static List<Box> layout(Screen screen, int left, int top, int imageWidth, int imageHeight) {
        TetrisNet.Sync s = ClientTetris.sync;
        int rigW = s.rig().getItem() instanceof GearItem g ? g.spec.gridWidth : 0;
        int rigH = s.rig().getItem() instanceof GearItem g ? g.spec.gridHeight : 0;
        int packW = s.backpack().getItem() instanceof GearItem g ? g.spec.gridWidth : 0;
        int packH = s.backpack().getItem() instanceof GearItem g ? g.spec.gridHeight : 0;
        boolean book = screen instanceof RecipeUpdateListener l && l.getRecipeBookComponent().isVisible();
        int spaceLeft = book ? 0 : left;
        int spaceRight = screen.width - left - imageWidth;
        int widest = Math.max(InvData.POCKET_W, Math.max(rigW, Math.max(packW, s.pouchW())));
        int rowsA = InvData.POCKET_H + rigH + s.pouchH();
        int labelsA = 10 * (rigH > 0 ? 3 : 2);
        int c;
        if (book) {
            c = (spaceRight - 16) / (2 * widest);
        } else {
            c = (Math.min(spaceLeft, spaceRight) - 8) / widest;
        }
        c = Math.min(c, (screen.height - 8 - labelsA) / Math.max(1, rowsA));
        c = Math.max(8, Math.min(18, c));

        int xa;
        int xb;
        if (book) {
            xa = left + imageWidth + 4;
            xb = xa + widest * c + 8;
        } else {
            xa = left - 4 - widest * c;
            xb = left + imageWidth + 4;
        }
        int heightA = labelsA + rowsA * c;
        int ya = Math.max(4, Math.min(top, screen.height - heightA - 4));
        List<Box> out = new ArrayList<>();
        int y = ya;
        out.add(new Box(GridId.POCKETS, "POCKETS", xa, y + 10, InvData.POCKET_W, InvData.POCKET_H, c, s.pockets()));
        y += 10 + InvData.POCKET_H * c;
        if (rigH > 0) {
            out.add(new Box(GridId.RIG, "RIG", xa, y + 10, rigW, rigH, c, s.rigItems()));
            y += 10 + rigH * c;
        }
        out.add(new Box(GridId.POUCH, "SECURE POUCH", xa, y + 10, s.pouchW(), s.pouchH(), c, s.pouch()));
        if (packH > 0) {
            int yb = Math.max(4, Math.min(top, screen.height - packH * c - 14));
            out.add(new Box(GridId.BACKPACK, "BACKPACK", xb, yb + 10, packW, packH, c, s.backpackItems()));
        }
        return out;
    }

    /** Where the furnace panel goes: under the backpack on the right, or at the top there if you have none. x, y, width. */
    private static int[] furnaceArea(Screen screen, List<Box> boxes, int left, int top, int imageWidth) {
        Box pack = box(boxes, GridId.BACKPACK);
        Box pockets = box(boxes, GridId.POCKETS);
        int x = pack != null ? pack.x : pockets != null && pockets.x > left ? pockets.x + InvData.POCKET_W * pockets.c + 8 : left + imageWidth + 4;
        int y = pack != null ? pack.y + pack.h * pack.c + 6 : top;
        return new int[] {x, y, screen.width - x - 4};
    }

    private static Loc locAt(List<Box> boxes, double mx, double my) {
        for (Box b : boxes) {
            if (mx >= b.x && mx < b.x + b.w * b.c && my >= b.y && my < b.y + b.h * b.c) {
                return Loc.grid(b.id, (int) ((mx - b.x) / b.c), (int) ((my - b.y) / b.c));
            }
        }
        return null;
    }

    private static Box box(List<Box> boxes, GridId id) {
        for (Box b : boxes) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my, int left, int top, int imageWidth,
            int imageHeight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) {
            rotated = false;
        }

        // Cover the dead vanilla main slots.
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (deadSlot(slot)) {
                minX = Math.min(minX, left + slot.x);
                minY = Math.min(minY, top + slot.y);
                maxX = Math.max(maxX, left + slot.x + 16);
                maxY = Math.max(maxY, top + slot.y + 16);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        if (minX < maxX) {
            Kit.inset(g, minX - 1, minY - 1, maxX - minX + 2, maxY - minY + 2);
            String hint = "Your inventory is in the grids";
            g.drawString(font, hint, (minX + maxX - font.width(hint)) / 2, (minY + maxY) / 2 - 4, Kit.ASH, false);
        }

        List<Box> boxes = layout(screen, left, top, imageWidth, imageHeight);
        Loc hover = locAt(boxes, mx, my);
        ItemStack tip = ItemStack.EMPTY;
        for (Box b : boxes) {
            Kit.label(g, font, b.title, b.x, b.y - 10, b.id == GridId.POUCH ? Kit.GOLD : Kit.ASH);
            for (int cy = 0; cy < b.h; cy++) {
                for (int cx = 0; cx < b.w; cx++) {
                    int x = b.x + cx * b.c;
                    int y = b.y + cy * b.c;
                    boolean over = mx >= x && mx < x + b.c && my >= y && my < y + b.c;
                    Kit.slot(g, x, y, b.c, over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
                }
            }
            for (Placed p : b.items) {
                drawPlaced(g, font, p.stack, b.x + p.x * b.c, b.y + p.y * b.c, p.width(), p.height(), b.c, p.rotated);
                if (hover != null && hover.a() == b.id.ordinal() && p.covers(hover.b(), hover.c())) {
                    tip = p.stack;
                }
            }
        }
        if (!carried.isEmpty() && hover != null) {
            Box b = box(boxes, GridId.values()[hover.a()]);
            int[] size = ItemSize.of(carried);
            int w = rotated ? size[1] : size[0];
            int h = rotated ? size[0] : size[1];
            Grid grid = new Grid(b.w, b.h, b.items);
            Placed there = grid.at(hover.b(), hover.c());
            boolean merge = there != null && ItemStack.isSameItemSameComponents(there.stack, carried);
            boolean ok = merge || grid.fits(w, h, hover.b(), hover.c(), null);
            int gx = b.x + (merge ? there.x : hover.b()) * b.c;
            int gy = b.y + (merge ? there.y : hover.c()) * b.c;
            g.pose().translate(0, 0, 150);
            g.fill(gx, gy, gx + (merge ? there.width() : w) * b.c - 1, gy + (merge ? there.height() : h) * b.c - 1,
                    ok ? 0x60B08A3E : 0x608E1F1F);
        }
        g.pose().popPose();
        if (FurnacePanel.applies(screen)) {
            int[] a = furnaceArea(screen, boxes, left, top, imageWidth);
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            ItemStack furnaceTip = FurnacePanel.render(g, a[0], a[1], a[2], mx, my);
            g.pose().popPose();
            if (tip.isEmpty()) {
                tip = furnaceTip;
            }
        }
        if (!tip.isEmpty() && carried.isEmpty()) {
            g.renderTooltip(font, tip, mx, my);
        }
    }

    private static void drawPlaced(GuiGraphics g, Font font, ItemStack stack, int px, int py, int w, int h, int c, boolean rot) {
        int pw = w * c;
        int ph = h * c;
        if (w > 1 || h > 1) {
            Kit.inset(g, px + 1, py + 1, pw - 2, ph - 2);
        }
        float k = Math.max(1, Math.min(2, Math.min(w, h))) * c / 18f;
        g.pose().pushPose();
        g.pose().translate(px + pw / 2f - 0.5f, py + ph / 2f - 0.5f, 0);
        if (rot) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        }
        g.pose().scale(k, k, 1);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (stack.getCount() > 1) {
            String n = String.valueOf(stack.getCount());
            Kit.text(g, font, n, px + pw - 1 - font.width(n), py + ph - 8, Kit.BONE);
        }
        if (stack.isBarVisible()) {
            int bw = (pw - 4) * stack.getBarWidth() / 13;
            g.fill(px + 2, py + ph - 3, px + pw - 2, py + ph - 2, 0xFF000000);
            g.fill(px + 2, py + ph - 3, px + 2 + bw, py + ph - 2, 0xFF000000 | stack.getBarColor());
        }
        g.pose().popPose();
    }

    /** True if the click was on the panel (and was handled). */
    public static boolean click(AbstractContainerScreen<?> screen, double mx, double my, int button, int left, int top, int imageWidth,
            int imageHeight) {
        List<Box> boxes = layout(screen, left, top, imageWidth, imageHeight);
        if (FurnacePanel.applies(screen) && screen.getMenu().getCarried().isEmpty() && (button == 0 || button == 1)) {
            int[] a = furnaceArea(screen, boxes, left, top, imageWidth);
            if (FurnacePanel.click(a[0], a[1], a[2], mx, my, button)) {
                swallowRelease = true;
                return true;
            }
        }
        Loc loc = locAt(boxes, mx, my);
        if (loc == null || (button != 0 && button != 1)) {
            return false;
        }
        boolean shift = Screen.hasShiftDown();
        int mode = button == 1 ? 1 : shift && screen.getMenu().getCarried().isEmpty() ? 2 : 0;
        ClientPlayNetworking.send(new TetrisNet.PanelClick(loc, rotated, mode));
        swallowRelease = true;
        return true;
    }

    /** The release after a panel click must not reach vanilla, which would throw the cursor item. */
    public static boolean release() {
        boolean swallow = swallowRelease;
        swallowRelease = false;
        return swallow;
    }

    public static boolean key(AbstractContainerScreen<?> screen, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_R && !screen.getMenu().getCarried().isEmpty()) {
            int[] s = ItemSize.of(screen.getMenu().getCarried());
            if (s[0] != s[1]) {
                rotated = !rotated;
            }
            return true;
        }
        return false;
    }
}
