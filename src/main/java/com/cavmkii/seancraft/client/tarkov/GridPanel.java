package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tetris.Tetris;
import com.cavmkii.seancraft.tetris.TetrisNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Beside vanilla container screens (crafting table, furnace, anvil, Weapon Bench...): the home stash on the left at
 * Storehouse 3 (2.4.1), and for furnaces the fuel and smeltables you and the stash hold, on the right (3.6). The
 * screen's own inventory slots are vanilla's.
 */
public final class GridPanel {
    private static boolean swallowRelease;
    /** The home stash, from the server, for the STASH box on station screens. */
    public static TetrisNet.StashStock stash = new TetrisNet.StashStock(false, List.of());
    private static int stashPage;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TetrisNet.StashStock.TYPE, (p, ctx) -> stash = p);
    }

    private static final int SC = 18;

    /** Stash cells that fit the box (a column is kept for the page arrow when there are more). */
    private static int stashCols(int[] a) {
        return Math.max(1, (a[2] - a[0] + 2) / SC);
    }

    private static int stashRows(int[] a) {
        return Math.max(1, (a[3] - a[1] + 2) / SC);
    }

    /** Index into the stash list of the cell at mx, my; -2 for the page arrow, -1 for empty space in the box, null off it. */
    private static Integer stashAt(int[] a, double mx, double my) {
        if (a == null || mx < a[0] - 1 || mx >= a[2] + 1 || my < a[1] - 1 || my >= a[3] + 1) {
            return null;
        }
        int cols = stashCols(a);
        int per = cols * stashRows(a);
        int n = stash.items().size();
        boolean paged = n > per;
        int shown = paged ? per - 1 : per;
        int cx = (int) ((mx - a[0] + 1) / SC);
        int cy = (int) ((my - a[1] + 1) / SC);
        int cell = cy * cols + cx;
        if (paged && cell == per - 1) {
            return -2;
        }
        int i = stashPage * shown + cell;
        return cell < shown && i < n ? i : -1;
    }

    private GridPanel() {}

    /** Does this screen get the panel? Survival screens that show the player's main inventory. */
    public static boolean applies(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Tetris.active(mc.player) || screen instanceof CreativeModeInventoryScreen) {
            return false;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() >= Inventory.getSelectionSize()
                    && slot.getContainerSlot() < Inventory.INVENTORY_SIZE) {
                return true;
            }
        }
        return false;
    }

    /** The stash box left of the screen: x, y, right, bottom; null if there's no room or no stash. */
    private static int[] stashArea(AbstractContainerScreen<?> screen, int left, int top, int imageHeight) {
        boolean book = screen instanceof RecipeUpdateListener l && l.getRecipeBookComponent().isVisible();
        if (!stash.home() || book) {
            return null;
        }
        int cols = Math.min(9, (left - 10) / SC);
        if (cols < 3) {
            return null;
        }
        int x = left - 4 - cols * SC;
        int rows = Math.max(3, Math.min(10, (imageHeight - 14) / SC));
        int y = top + 12;
        return new int[] {x + 1, y + 1, x + cols * SC - 1, y + rows * SC - 1};
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my, int left, int top, int imageWidth,
            int imageHeight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ItemStack carried = screen.getMenu().getCarried();
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        ItemStack tip = ItemStack.EMPTY;
        int[] a = stashArea(screen, left, top, imageHeight);
        if (a != null) {
            Kit.inset(g, a[0] - 2, a[1] - 2, a[2] - a[0] + 4, a[3] - a[1] + 4);
            tip = renderStash(g, font, a, mx, my, carried);
        }
        if (FurnacePanel.applies(screen)) {
            ItemStack furnaceTip = FurnacePanel.render(g, left + imageWidth + 4, top, screen.width - left - imageWidth - 8, mx, my);
            if (tip.isEmpty()) {
                tip = furnaceTip;
            }
        }
        g.pose().popPose();
        if (!tip.isEmpty() && carried.isEmpty()) {
            g.renderTooltip(font, tip, mx, my);
        }
    }

    private static ItemStack renderStash(GuiGraphics g, Font font, int[] a, int mx, int my, ItemStack carried) {
        List<ItemStack> items = stash.items();
        int cols = stashCols(a);
        int per = cols * stashRows(a);
        boolean paged = items.size() > per;
        int shown = paged ? per - 1 : per;
        int pages = paged ? (items.size() + shown - 1) / shown : 1;
        stashPage = Math.max(0, Math.min(pages - 1, stashPage));
        // The kit's label sits on the box's top edge.
        Kit.label(g, font, "STASH" + (pages > 1 ? "  " + (stashPage + 1) + "/" + pages : ""), a[0], a[1] - 11, Kit.GOLD);
        Integer hover = stashAt(a, mx, my);
        ItemStack tip = ItemStack.EMPTY;
        if (items.isEmpty()) {
            String hint = "Stash is empty";
            g.drawString(font, hint, (a[0] + a[2] - font.width(hint)) / 2, (a[1] + a[3]) / 2 - 4, Kit.ASH, false);
        }
        for (int cell = 0; cell < shown; cell++) {
            int i = stashPage * shown + cell;
            if (i >= items.size()) {
                break;
            }
            int x = a[0] - 1 + (cell % cols) * SC;
            int y = a[1] - 1 + (cell / cols) * SC;
            boolean over = hover != null && hover == i;
            Kit.slot(g, x, y, SC, over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            ItemStack s = items.get(i);
            g.renderItem(s, x + 1, y + 1);
            g.renderItemDecorations(font, s, x + 1, y + 1, s.getCount() > 999 ? "999+" : null);
            if (over) {
                tip = s;
            }
        }
        if (paged) {
            int x = a[0] - 1 + ((per - 1) % cols) * SC;
            int y = a[1] - 1 + ((per - 1) / cols) * SC;
            boolean over = hover != null && hover == -2;
            Kit.slot(g, x, y, SC, over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            g.drawString(font, ">", x + 6, y + 5, over ? Kit.GOLD_LIGHT : Kit.BONE, false);
        }
        if (!carried.isEmpty() && hover != null) {
            g.renderOutline(a[0] - 1, a[1] - 1, a[2] - a[0] + 2, a[3] - a[1] + 2, Kit.GOLD_LIGHT);
        }
        return tip;
    }

    /** True if the click was on the panel (and was handled). */
    public static boolean click(AbstractContainerScreen<?> screen, double mx, double my, int button, int left, int top, int imageWidth,
            int imageHeight) {
        if (FurnacePanel.applies(screen) && screen.getMenu().getCarried().isEmpty() && (button == 0 || button == 1)) {
            if (FurnacePanel.click(left + imageWidth + 4, top, screen.width - left - imageWidth - 8, mx, my, button)) {
                swallowRelease = true;
                return true;
            }
        }
        int[] a = stashArea(screen, left, top, imageHeight);
        if (a != null && (button == 0 || button == 1)) {
            Integer at = stashAt(a, mx, my);
            if (at != null) {
                ItemStack carried = screen.getMenu().getCarried();
                if (at == -2) {
                    stashPage++;
                    int per = stashCols(a) * stashRows(a) - 1;
                    if (stashPage * per >= stash.items().size()) {
                        stashPage = 0;
                    }
                } else if (!carried.isEmpty() && (at < 0 || !ItemStack.isSameItemSameComponents(carried, stash.items().get(at)))) {
                    // Something on the cursor dropped on the stash: put it away.
                    ClientPlayNetworking.send(new TetrisNet.StashTake(ItemStack.EMPTY, 0));
                } else if (at >= 0) {
                    // Left: a stack onto the cursor; right: one.
                    ClientPlayNetworking.send(new TetrisNet.StashTake(stash.items().get(at), button == 1 ? 1 : 0));
                }
                swallowRelease = true;
                return true;
            }
        }
        return false;
    }

    /** The release after a panel click must not reach vanilla, which would throw the cursor item. */
    public static boolean release() {
        boolean swallow = swallowRelease;
        swallowRelease = false;
        return swallow;
    }

    public static boolean key(AbstractContainerScreen<?> screen, int keyCode) {
        return false;
    }
}
