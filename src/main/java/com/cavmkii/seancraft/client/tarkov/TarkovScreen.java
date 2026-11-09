package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.tetris.Grid;
import com.cavmkii.seancraft.tetris.ItemSize;
import com.cavmkii.seancraft.tetris.Loc;
import com.cavmkii.seancraft.tetris.Placed;
import com.cavmkii.seancraft.tetris.StorageCategory;
import com.cavmkii.seancraft.tetris.TetrisNet;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import com.cavmkii.seancraft.client.ui.Kit;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The inventory screen (UI-SPEC Part 2, inventory v5). INVENTORY: equipment and the player model on the left,
 * crafting over the player inventory in the middle, the stash or open container on the right, the hotbar at the
 * bottom. RECIPES opens the recipe book over the equipment zone. STATUS: body and effects. CHARACTER: the passive
 * web with level, attributes, skills and the loadout.
 *
 * <p>Controls, mostly vanilla's: click to pick up and put down, right-click to pick up half or put down one,
 * Shift+click (or Ctrl) to transfer, R to rotate, 1-9 to send the hovered item to a hotbar slot, Q to drop one
 * (Ctrl+Q the stack), Esc to let go (or close the recipe book).
 */
public class TarkovScreen extends Screen {
    /** Equipment, crafting, the grids, the stash and the hotbar. */
    public static final int INVENTORY = 0;
    /** Old names for the INVENTORY tab, for callers that open it (E, a crafting table). */
    public static final int GEAR = INVENTORY;
    public static final int CRAFTING = INVENTORY;
    public static final int STATUS = 1;
    /** CHARACTER: the passive web with level, attributes, skills and the loadout. */
    public static final int WEB = 2;
    public static final int CHARACTER = WEB;
    public static final int SKILLS = WEB;
    /** Open INVENTORY from a crafting table: the 3x3 grid with the recipe book open. */
    public static final int CRAFT_MODE = 3;
    private static final String[] TABS = {"INVENTORY", "STATUS", "CHARACTER"};

    private static final int C = 18;
    private static final int TOP = 24;

    private int tab;
    private float scale = 1;
    private float ox;
    private float oy;
    private boolean announced;

    private Loc heldFrom;
    private ItemStack held = ItemStack.EMPTY;
    private boolean heldRot;
    private int heldCount;
    /** After dropping onto a full hotbar or gear slot: the sync count to wait past, then pick up what was bumped. */
    private int swapSync = -1;
    private int swapTick;

    private String search = "";
    private boolean searchFocused;
    private int ticks;
    private StatusScreen status;
    private com.cavmkii.seancraft.client.rpg.WebScreen web;

    public TarkovScreen(int tab) {
        super(Component.literal("Inventory"));
        this.tab = tab == CRAFT_MODE ? INVENTORY : tab;
        if (tab == CRAFT_MODE) {
            bookOpen = true;
            openStation = StationTabs.CRAFTING_TABLE;
        }
    }

    void setTab(int tab) {
        if (tab == CRAFT_MODE) {
            bookOpen = true;
            openStation = StationTabs.CRAFTING_TABLE;
            tab = INVENTORY;
        }
        this.tab = tab;
        layout();
        if (tab == INVENTORY) {
            ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
        }
    }

    @Override
    protected void init() {
        layout();
        status = new StatusScreen();
        status.init(minecraft, width, height - TOP);
        if (web == null) {
            web = new com.cavmkii.seancraft.client.rpg.WebScreen();
        }
        web.init(minecraft, width, height - TOP);
        if (!announced) {
            announced = true;
            minecraft.getTutorial().onOpenInventory();
            ClientPlayNetworking.send(new TetrisNet.Action(4));
            if (tab == INVENTORY) {
                ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
            }
        }
    }

    /** Scale and place the 480 x 270 canvas: content under the tab bar at y 18, scaled down only if it must be. */
    private void layout() {
        scale = Math.min(1f, Math.min((width - 4) / (float) VW, (height - 18 - 1) / (float) (VH - 18)));
        ox = (width - VW * scale) / 2f;
        oy = 18 - 18 * scale;
    }


    @Override
    public void removed() {
        ClientPlayNetworking.send(new TetrisNet.Action(5));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No vanilla blur; render() fills its own backdrop.
    }

    @Override
    public void tick() {
        ticks++;
        if (tab == CRAFTING && ticks % 40 == 0) {
            ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
        }
        if (status != null) {
            status.tick();
        }
        if (web != null && tab == WEB) {
            // The web's one-time reveal animation runs on ticks.
            web.tick();
        }
    }

    private double vx(double mouseX) {
        return (mouseX - ox) / scale;
    }

    private double vy(double mouseY) {
        return (mouseY - oy) / scale;
    }

    // ================================================================== layout

    /** x, y, size of an equipment slot in its box; null if the book or gather list covers it. */
    private int[] equipRect(int slot) {
        if (slot < 0 || slot >= EQUIP_BOX.length || EQUIP_BOX[slot] == null) {
            return null;
        }
        return covered() ? null : new int[] {EQUIP_BOX[slot][0] + 7, EQUIP_BOX[slot][1] + 10, C};
    }

    /** Outlines shown in empty equipment slots (ghost/*). */
    private static final String[] EQUIP_GHOSTS = {"helmet", "chestplate", "leggings", "boots", "rig", "backpack", "offhand"};

    /** Top-left of a main inventory slot (9-35) in the PLAYER INVENTORY zone. */
    private static int invX(int slot) {
        return INV_X + ((slot - 9) % 9) * C;
    }

    private static int invY(int slot) {
        return INV_Y + ((slot - 9) / 9) * C;
    }

    /** What's under a point (virtual coords) on the INVENTORY tab. */
    private Loc locAt(double x, double y) {
        if (tab != INVENTORY) {
            return null;
        }
        int cell = cellAt(x, y);
        if (cell >= 0) {
            return Loc.craft(cell);
        }
        for (int slot = 0; slot < 7; slot++) {
            int[] r = equipRect(slot);
            if (r != null && in(x, y, r[0], r[1], r[2], r[2])) {
                return Loc.equip(slot);
            }
        }
        for (int i = 0; i < 9; i++) {
            if (in(x, y, HB_X + i * HB_STEP, HB_Y, C, C)) {
                return Loc.hand(i);
            }
        }
        if (in(x, y, INV_X, INV_Y, 9 * C, 3 * C)) {
            return Loc.hand(9 + (int) ((y - INV_Y) / C) * 9 + (int) ((x - INV_X) / C));
        }
        return null;
    }

    /** The container entry at a CONTAINER location, or null. */
    private Placed placedAt(Loc loc) {
        if (loc != null && loc.kind() == Loc.CONTAINER && loc.b() >= 0) {
            List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
            if (loc.a() >= cs.size()) {
                return null;
            }
            TetrisNet.ContainerView c = cs.get(loc.a());
            return new Grid(c.width(), c.height(), c.items()).at(loc.b(), loc.c());
        }
        return null;
    }

    private ItemStack stackAt(Loc loc) {
        if (loc == null) {
            return ItemStack.EMPTY;
        }
        Inventory inv = minecraft.player.getInventory();
        TetrisNet.Sync s = ClientTetris.sync;
        return switch (loc.kind()) {
            case Loc.HAND -> inv.items.get(loc.a());
            case Loc.EQUIP -> switch (loc.a()) {
                case 4, 5 -> ItemStack.EMPTY;
                case 6 -> inv.offhand.get(0);
                default -> inv.armor.get(3 - loc.a());
            };
            case Loc.CONTAINER -> {
                if (loc.b() >= 0) {
                    Placed p = placedAt(loc);
                    yield p == null ? ItemStack.EMPTY : p.stack;
                }
                if (loc.a() >= s.containers().size()) {
                    yield ItemStack.EMPTY;
                }
                TetrisNet.ContainerView c = s.containers().get(loc.a());
                int i = c.overflowSlots().indexOf(loc.c());
                yield i < 0 ? ItemStack.EMPTY : c.overflow().get(i);
            }
            case Loc.CRAFT -> loc.a() < ClientTetris.craft.grid().size() ? ClientTetris.craft.grid().get(loc.a()) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    // ================================================================== render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        settleSwap();
        if (tab == INVENTORY) {
            // The world stays visible behind it, dimmed.
            layout();
            g.fill(0, 0, width, height, 0xC70A0908);
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            g.pose().scale(scale, scale, 1);
            int mx = (int) Math.floor(vx(mouseX));
            int my = (int) Math.floor(vy(mouseY));
            hoverRecipe = null;
            uiTip = null;
            ItemStack tip = renderInventory(g, mx, my, mouseX, mouseY);
            g.pose().popPose();
            hoverStack = tip;
            // Next frame's grid preview follows what's hovered now.
            shownHover = hoverRecipe;
            if (uiTip != null && held.isEmpty()) {
                g.renderTooltip(font, Component.literal(uiTip), mouseX, mouseY);
            } else if (!tip.isEmpty() && held.isEmpty()) {
                if (hoverRecipe != null) {
                    // A recipe: its layout under the name.
                    g.renderTooltip(font, Screen.getTooltipFromItem(minecraft, tip),
                            java.util.Optional.of(new RecipeGridTooltip.Data(cellsFor(hoverRecipe))), mouseX, mouseY);
                } else {
                    g.renderTooltip(font, tip, mouseX, mouseY);
                }
            }
        } else {
            g.fill(0, 0, width, height, Kit.BACKDROP);
            Kit.panel(g, 2, PANEL_TOP, width - 4, height - PANEL_TOP - 2);
            g.pose().pushPose();
            g.pose().translate(0, TOP, 0);
            (tab == STATUS ? status : web).render(g, mouseX, mouseY - TOP, partialTick);
            g.pose().popPose();
        }
        renderTabs(g, mouseX, mouseY);
    }

    /** Top of the STATUS and CHARACTER panel; the tab row sits on its edge. */
    private static final int PANEL_TOP = 18;
    /** The line under the tab bar, and the active tab, in rust (inventory v5). */
    private static final int RUST = 0xFF5A3220;

    private int tabW(int t) {
        return font.width(TABS[t]) + 26;
    }

    private int tabX(int t) {
        int x = 4;
        for (int i = 0; i < t; i++) {
            x += tabW(i) + 1;
        }
        return x;
    }

    private boolean overTab(int t, double mouseX, double mouseY) {
        int x = tabX(t);
        return mouseX >= x && mouseX < x + tabW(t) && mouseY >= 1 && mouseY < 16;
    }

    private static final String[] TAB_ICONS = {"gear", "status", "web"};

    /** The tab bar: each tab with its icon, a rust line under them, the station row and where you are at the right. */
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 16, width, 17, RUST);
        for (int t = 0; t < TABS.length; t++) {
            int x = tabX(t);
            int w = tabW(t);
            boolean active = t == tab;
            boolean over = !active && overTab(t, mouseX, mouseY);
            if (active) {
                Kit.blit(g, "tab/active_rust", x, 1, w, 16);
            } else {
                Kit.blit(g, over ? "tab/inactive_hover" : "tab/inactive", x, 2, w, 14);
            }
            Kit.blit(g, "icon/tab/" + TAB_ICONS[t], x + 6, active ? 4 : 5, 9, 9);
            Kit.text(g, font, TABS[t], x + 18, active ? 5 : 6, active ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH);
        }
        int tabsEnd = tabX(TABS.length - 1) + tabW(TABS.length - 1) + 8;
        // Station row (inside a hideout), right-aligned; the open one is widened with its name.
        int right = width - 4;
        for (Object[] st : stationButtons()) {
            String kind = (String) st[0];
            int x = (int) st[1];
            int w = (int) st[2];
            boolean open = kind.equals(openStation);
            boolean over = in(mouseX, mouseY, x, 2, w, 13);
            Kit.buttonBg(g, x, 2, w, 13, true, over, open);
            Kit.blit(g, stationIcon(kind), x + 2, 4, 9, 9);
            if (open) {
                Kit.text(g, font, StationTabs.label(kind), x + 15, 5, Kit.GOLD_LIGHT);
            }
            if (over && !open) {
                g.renderTooltip(font, Component.literal(StationTabs.label(kind)), mouseX, mouseY + 12);
            }
            right = Math.min(right, x - 6);
        }
        if (ClientTarkov.place.home()) {
            String t = "HOME";
            int rx = right - font.width(t);
            // Only when no station is open (the open station's button names where you are).
            if (openStation == null && rx - 11 >= tabsEnd) {
                Kit.blit(g, "icon/tab/hideout", rx - 11, 4, 9, 9);
                Kit.text(g, font, t, rx, 5, Kit.MOSS);
            }
            return;
        }
        // The raid state, always in view (C.4); shorter forms when the bar leaves too little room.
        for (String raid : RaidHud.stateLines()) {
            int rx = right - font.width(raid);
            if (rx >= tabsEnd) {
                Kit.text(g, font, raid, rx, 5, RaidHud.stateColor());
                break;
            }
        }
    }


    private void slot(GuiGraphics g, int x, int y, int size, ItemStack stack, boolean hover, String empty) {
        Kit.slot(g, x, y, size, hover ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
        if (!stack.isEmpty() && !(heldFrom != null && stack == stackAt(heldFrom) && heldCount == 0)) {
            if (size >= 32) {
                g.pose().pushPose();
                g.pose().translate(x + size / 2f, y + size / 2f, 0);
                g.pose().scale(2, 2, 1);
                g.renderItem(stack, -8, -8);
                g.pose().popPose();
            } else {
                int o = (size - 16) / 2;
                g.renderItem(stack, x + o, y + o);
                g.renderItemDecorations(font, stack, x + o, y + o);
            }
        } else if (stack.isEmpty() && empty != null) {
            g.pose().pushPose();
            g.pose().translate(x + size / 2f, y + size / 2f - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.drawString(font, empty, -font.width(empty) / 2, 0, Kit.ASH, false);
            g.pose().popPose();
        }
    }

    private void label(GuiGraphics g, String text, int x, int y) {
        Kit.label(g, font, text, x, y);
    }

    private int lastMx;
    private int lastMy;


    /**
     * The stack a held bow or crossbow will shoot next (3.3), outlined in green: vanilla's own choice (off-hand, then
     * the hotbar, then the inventory). Null when no bow is held or there's nothing to shoot.
     */
    private ItemStack nextArrow() {
        // The held bow or crossbow, else the first one carried: the arrows it would draw (C.5).
        ItemStack bow = minecraft.player.getMainHandItem();
        if (!(bow.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
            bow = minecraft.player.getOffhandItem();
        }
        for (int i = 0; i < 36 && !(bow.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem); i++) {
            bow = minecraft.player.getInventory().getItem(i);
        }
        if (!(bow.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
            return null;
        }
        ItemStack ammo = minecraft.player.getProjectile(bow);
        return ammo.isEmpty() ? null : ammo;
    }

    private void drawPlaced(GuiGraphics g, ItemStack stack, int px, int py, int w, int h, boolean rotated, int bg) {
        drawPlaced(g, stack, px, py, w, h, rotated, bg, true);
    }

    private void drawPlaced(GuiGraphics g, ItemStack stack, int px, int py, int w, int h, boolean rotated, int bg, boolean framed) {
        int pw = w * C;
        int ph = h * C;
        if (bg != 0) {
            g.fill(px, py, px + pw - 1, py + ph - 1, bg);
        }
        if (framed && (w > 1 || h > 1)) {
            // A multi-cell item reads as one piece: an inset across its whole footprint.
            Kit.inset(g, px + 1, py + 1, pw - 2, ph - 2);
        }
        int k = Math.max(1, Math.min(2, Math.min(w, h)));
        g.pose().pushPose();
        g.pose().translate(px + pw / 2f - 0.5f, py + ph / 2f - 0.5f, 0);
        if (rotated) {
            g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        }
        g.pose().scale(k, k, 1);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (stack.getCount() > 1) {
            String n = String.valueOf(stack.getCount());
            Kit.text(g, font, n, px + pw - 2 - font.width(n), py + ph - 9, Kit.BONE);
        }
        if (stack.isBarVisible()) {
            int bw = (pw - 4) * stack.getBarWidth() / 13;
            g.fill(px + 2, py + ph - 3, px + pw - 2, py + ph - 2, 0xFF000000);
            g.fill(px + 2, py + ph - 3, px + 2 + bw, py + ph - 2, 0xFF000000 | stack.getBarColor());
        }
        g.pose().popPose();
    }


    private boolean equipAccepts(int slot, ItemStack stack) {
        return switch (slot) {
            case 0, 1, 2, 3 -> minecraft.player.getEquipmentSlotForItem(stack) == new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}[slot];
            default -> false;
        };
    }

    /** Container being renamed (index), and the text typed so far. */
    private int renameIndex = -1;
    private String renameText = "";


    private int filterOpen = -1;
    private int filterY;
    private int filterX;
    private static final int FILTER_W = 110;
    private static final int FILTER_ROW = 11;

    /** The storage filter checklist for one container, drawn over the right column. */
    private void renderFilter(GuiGraphics g, int mx, int my) {
        if (filterOpen < 0 || filterOpen >= ClientTetris.sync.containers().size()) {
            filterOpen = -1;
            return;
        }
        int mask = ClientTetris.sync.containers().get(filterOpen).filter();
        var cats = com.cavmkii.seancraft.tetris.StorageCategory.values();
        int x = filterX;
        int y = Math.min(filterY, ZONE_Y + ZONE_H - (cats.length + 2) * FILTER_ROW - 6);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Kit.hudPanel(g, x - 1, y - 1, FILTER_W + 2, (cats.length + 2) * FILTER_ROW + 6);
        Kit.label(g, font, "FILTERS", x + 4, y + 3);
        for (int i = 0; i < cats.length; i++) {
            int ry = y + (i + 1) * FILTER_ROW + 3;
            boolean on = (mask & cats[i].bit()) != 0;
            boolean hover = mx >= x && mx < x + FILTER_W && my >= ry && my < ry + FILTER_ROW;
            if (hover) {
                Kit.blit(g, "list/row_hover", x + 1, ry, FILTER_W - 2, FILTER_ROW);
            }
            Kit.blit(g, "checkbox/" + (on ? "on" : "off") + (hover ? "_hover" : ""), x + 4, ry + 1, 9, 9);
            g.drawString(font, cats[i].label, x + 16, ry + 1, on ? (hover ? Kit.GOLD_LIGHT : Kit.BONE) : Kit.ASH, false);
        }
        int by = y + (cats.length + 1) * FILTER_ROW + 4;
        for (int b = 0; b < 2; b++) {
            int bx = x + 4 + b * 52;
            boolean hover = mx >= bx && mx < bx + 48 && my >= by && my < by + 10;
            Kit.smallButton(g, font, b == 0 ? "ALLOW ALL" : "CLEAR", bx, by, 48, 10, true, hover, false, 0.75f);
        }
        g.pose().popPose();
    }

    /** Clicks on the open filter. Clicking outside closes it. Returns true if the click was used. */
    private boolean clickFilter(double mx, double my) {
        int mask = ClientTetris.sync.containers().get(filterOpen).filter();
        var cats = com.cavmkii.seancraft.tetris.StorageCategory.values();
        int x = filterX;
        int y = Math.min(filterY, ZONE_Y + ZONE_H - (cats.length + 2) * FILTER_ROW - 6);
        if (mx < x || mx >= x + FILTER_W || my < y || my >= y + (cats.length + 2) * FILTER_ROW + 4) {
            filterOpen = -1;
            return false;
        }
        for (int i = 0; i < cats.length; i++) {
            int ry = y + (i + 1) * FILTER_ROW + 3;
            if (my >= ry && my < ry + FILTER_ROW) {
                ClientPlayNetworking.send(new TetrisNet.ContainerAction(filterOpen, 2, mask ^ cats[i].bit()));
                return true;
            }
        }
        int by = y + (cats.length + 1) * FILTER_ROW + 4;
        if (my >= by && my < by + 10) {
            if (mx >= x + 4 && mx < x + 52) {
                ClientPlayNetworking.send(new TetrisNet.ContainerAction(filterOpen, 2, com.cavmkii.seancraft.tetris.StorageCategory.ALL));
            } else if (mx >= x + 56 && mx < x + 104) {
                ClientPlayNetworking.send(new TetrisNet.ContainerAction(filterOpen, 2, 0));
            }
        }
        return true;
    }


    private void renderHeld(GuiGraphics g, int mx, int my, Loc hover) {
        if (held.isEmpty()) {
            return;
        }
        ItemStack shown = heldCount > 0 ? held.copyWithCount(heldCount) : held;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.renderItem(shown, mx - 8, my - 8);
        g.renderItemDecorations(font, shown, mx - 8, my - 8);
        g.pose().popPose();
    }

    // ================================================================== INVENTORY layout (UI-SPEC Part 2, inventory v5: 480 x 270)

    private static final int VW = 480;
    private static final int VH = 270;
    // Zones: x, y, w, h. Each gets an ornate frame 3 px outside its bounds.
    private static final int[] EQ = {6, 21, 112, 214};
    private static final int[] CR = {124, 21, 204, 90};
    private static final int[] PI = {124, 117, 204, 118};
    private static final int[] ST = {334, 21, 140, 214};
    private static final int[] HB = {144, 241, 192, 28};
    /** The stash zone's top and height (the filter popup keeps inside them). */
    private static final int ZONE_Y = 21;
    private static final int ZONE_H = 214;
    /** Equipment boxes (32 x 30) for head, chest, legs, feet and off-hand; the rig and backpack sit by their grids. */
    private static final int[][] EQUIP_BOX = {{10, 41}, {10, 73}, {10, 105}, {10, 137}, null, null, {82, 91}};
    private static final String[] EQUIP_LABEL = {"HEAD", "CHEST", "LEGS", "FEET", "RIG", "BACKPACK", "OFF"};
    /** MAIN: a read-only mirror of the selected hotbar item. */
    // The spec says x 88 for OFF and MAIN, which runs past the zone; the mock-up has them at 82.
    private static final int MAIN_X = 82;
    private static final int MAIN_Y = 123;
    /** The player model's box. */
    private static final int[] MODEL = {44, 49, 40, 84};
    // Player inventory: vanilla's 27 main slots, 9 x 3, centred in the zone.
    private static final int INV_X = 145;
    private static final int INV_Y = 141;
    // Crafting.
    private static final int GX = 132;
    private static final int GRID_Y = 43;
    private static final int GC = 18;
    private static final int RESULT_X = 220;
    private static final int RESULT_Y = 57;
    private static final int[] CLEAR = {220, 87, 34, 14};
    private static final int[] RECIPES = {268, 24, 56, 12};
    private static final int[] PIN = {254, 24, 12, 12};
    private static final int NEEDS_X = 254;
    // Stash.
    private static final int SGX = 340;
    private static final int SGY = 70;
    private static final int SCOLS = 7;
    private static final int SW_ROWS = 8;
    // Hotbar.
    private static final int HB_X = 150;
    private static final int HB_Y = 246;
    private static final int HB_STEP = 20;
    private static final int TOGGLE_X = 340;
    private static final int TOGGLE_Y = 249;
    // Recipe book, over the equipment zone.
    private static final int[] BOOK = {8, 23, 108, 211};
    private static final int BOOK_X = 11;
    private static final int BOOK_W = 100;
    private static final int BOOK_TOP = 68;
    private static final int BOOK_BOTTOM = 232;
    private static final int BOOK_ROW = 17;
    private static final int BOOK_HEAD = 11;

    /** RECIPES: the recipe book over the equipment zone. */
    private boolean bookOpen;
    /** GATHER LIST: the gather list over the equipment zone. */
    private boolean gatherOpen;
    private static final int[] GATHER_BUTTON = {8, 245, 126, 14};
    private static final int GATHER_TOP = 56;
    private static final int GATHER_ROW = 26;

    /** True while the book or the gather list covers the equipment zone. */
    private boolean covered() {
        return bookOpen || gatherOpen;
    }
    /** The station this screen was opened from (crafting table), shown widened in the station row; null for none. */
    private String openStation;
    private static boolean canMakeOnly;
    private double storageScroll;
    private double bookScroll;
    /** Storage tab: -1 for ALL, else a container index. */
    private int storageTab = -1;
    private String storageSearch = "";
    private boolean storageSearchFocused;
    /** Tooltip for whatever small control is under the mouse this frame. */
    private String uiTip;

    private static boolean in(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }

    private static boolean in(double x, double y, int[] r) {
        return in(x, y, r[0], r[1], r[2], r[3]);
    }

    /** A zone: the ornate iron frame with rust trim 3 px outside its bounds, and a title bar with an optional icon. */
    private void zone(GuiGraphics g, int[] z, String title, String icon, int titleColor) {
        Kit.blit(g, "panel/ornate", z[0] - 3, z[1] - 3, z[2] + 6, z[3] + 6);
        if (title != null) {
            titleBar(g, z[0] + 3, z[1] + 3, z[2] - 6, title, icon, titleColor);
        }
    }

    private void titleBar(GuiGraphics g, int x, int y, int w, String title, String icon, int color) {
        Kit.blit(g, "panel/title", x, y, w, 13);
        int tx = x + 4;
        if (icon != null) {
            Kit.blit(g, icon, x + 3, y + 2, 9, 9);
            tx = x + 15;
        }
        Kit.text(g, font, title, tx, y + 3, color);
    }

    private void small(GuiGraphics g, String text, float x, float y, int color, float k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Text fitted to a width: scaled down to 0.75 if needed, then cut. */
    private void fitted(GuiGraphics g, String text, float x, float y, int color, int maxW, float k) {
        float s = Math.max(Math.min(k, 0.75f), Math.min(k, maxW / (float) Math.max(1, font.width(text))));
        String shown = font.plainSubstrByWidth(text, (int) (maxW / s));
        small(g, shown, x, y, color, s);
    }

    /** An icon-only button (button_small/name_*); hovering it sets the tooltip. */
    private void iconButton(GuiGraphics g, String name, int x, int y, int size, boolean enabled, int mx, int my, String tip) {
        boolean over = in(mx, my, x, y, size, size);
        Kit.blit(g, "button_small/" + name + (!enabled ? "_disabled" : over ? "_hover" : "_normal"), x, y, size, size);
        if (over && tip != null) {
            uiTip = tip;
        }
    }

    // ------------------------------------------------------------------ stash

    /** A storage cell: a stack in one of the shown containers (b, c its grid origin; slot for an overflow stack). */
    private record SCell(int index, Placed placed, ItemStack overflow, int slot) {
        ItemStack stack() {
            return placed != null ? placed.stack : overflow;
        }

        Loc loc() {
            return placed != null ? Loc.container(index, placed.x, placed.y) : Loc.containerOverflow(index, slot);
        }
    }

    private boolean anyStorage() {
        return !ClientTetris.sync.containers().isEmpty() || ClientTetris.sync.stash();
    }

    /** The tab's stacks in reading order, filtered by the search field. */
    private List<SCell> storageCells() {
        List<SCell> out = new ArrayList<>();
        List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
        if (storageTab >= cs.size()) {
            storageTab = -1;
        }
        String q = storageSearch.toLowerCase(Locale.ROOT);
        for (int i = 0; i < cs.size(); i++) {
            if (storageTab >= 0 && i != storageTab) {
                continue;
            }
            TetrisNet.ContainerView c = cs.get(i);
            List<Placed> items = new ArrayList<>(c.items());
            items.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
            for (Placed p : items) {
                if (q.isEmpty() || p.stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new SCell(i, p, null, -1));
                }
            }
            for (int k = 0; k < c.overflow().size(); k++) {
                ItemStack st = c.overflow().get(k);
                if (q.isEmpty() || st.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new SCell(i, null, st, c.overflowSlots().get(k)));
                }
            }
        }
        return out;
    }

    private SCell storageAt(double x, double y) {
        if (x < SGX || x >= SGX + SCOLS * C || y < SGY || y >= SGY + SW_ROWS * C) {
            return null;
        }
        int i = ((int) ((y - SGY + storageScroll) / C)) * SCOLS + (int) ((x - SGX) / C);
        List<SCell> cells = storageCells();
        return i >= 0 && i < cells.size() ? cells.get(i) : null;
    }

    /** True if a point is in the storage grid area (dropping there puts things away). */
    private boolean overStorageGrid(double x, double y) {
        return anyStorage() && in(x, y, SGX, SGY, SCOLS * C, SW_ROWS * C);
    }

    private static final int STAB_Y = 55;

    /** Storage tab rectangles: index (-1 for ALL), x, width. Only in a hideout. */
    private List<int[]> storageTabs() {
        List<int[]> out = new ArrayList<>();
        List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
        if (!ClientTetris.sync.stash()) {
            return out;
        }
        int avail = ST[2] - 10;
        int full = font.width("ALL") + 10;
        for (TetrisNet.ContainerView c : cs) {
            full += font.width(c.name().toUpperCase(Locale.ROOT)) + 11;
        }
        float k = full <= avail ? 1f : avail / (float) full;
        int x = ST[0] + 5;
        for (int i = -1; i < cs.size(); i++) {
            String label = i < 0 ? "ALL" : cs.get(i).name().toUpperCase(Locale.ROOT);
            int w = Math.max(14, (int) ((font.width(label) + 10) * k));
            out.add(new int[] {i, x, w});
            x += w + 1;
        }
        return out;
    }

    /** Stash title-bar buttons, right to left: filter, sort, take all, stash all, refill stacks. */
    private static final String[] STASH_BUTTONS = {"filter", "sort", "take_all", "stash_all", "refill"};

    private static int stashButtonX(int i) {
        return 457 - i * 14;
    }

    private void renderStorage(GuiGraphics g, int mx, int my) {
        TetrisNet.Sync s = ClientTetris.sync;
        List<TetrisNet.ContainerView> cs = s.containers();
        boolean hideout = s.stash();
        String title = hideout ? "STASH" : !cs.isEmpty() ? cs.get(0).name().toUpperCase(Locale.ROOT) : "STORAGE";
        zone(g, ST, null, null, 0);
        Kit.blit(g, "panel/title", ST[0] + 3, ST[1] + 3, ST[2] - 6, 13);
        Kit.blit(g, "icon/tab/stash", ST[0] + 6, ST[1] + 5, 9, 9);
        if (!anyStorage()) {
            Kit.text(g, font, title, ST[0] + 18, ST[1] + 6, Kit.BONE);
            g.drawWordWrap(font, Component.literal("Open a chest or barrel to see it here. At your home base, the stash shows here once you build a Storehouse."),
                    ST[0] + 6, ST[1] + 24, ST[2] - 12, Kit.ASH);
            return;
        }
        int textMax = stashButtonX(4) - (ST[0] + 18) - 3;
        if (font.width(title) <= textMax) {
            Kit.text(g, font, title, ST[0] + 18, ST[1] + 6, Kit.BONE);
        } else {
            fitted(g, title, ST[0] + 18, ST[1] + 7, Kit.BONE, textMax, 1f);
        }
        boolean chestTab = storageTab >= 0 || !hideout;
        int fi = storageTab >= 0 ? storageTab : 0;
        int by = ST[1] + 3;
        boolean sh2 = s.storehouse() >= 2;
        iconButton(g, "filter", stashButtonX(0), by, 12, chestTab && sh2, mx, my, !sh2 ? "Filters need a Storehouse level 2"
                : chestTab ? "Filter: what this chest takes" : "Pick a chest's tab to set its filter");
        if (chestTab && fi < cs.size() && cs.get(fi).filter() != StorageCategory.ALL) {
            g.renderOutline(stashButtonX(0) - 1, by - 1, 14, 14, Kit.GOLD);
        }
        iconButton(g, "sort", stashButtonX(1), by, 12, chestTab || sh2, mx, my, chestTab ? "Sort this chest"
                : sh2 ? "Organize: merge and sort across the stash" : "Organize needs a Storehouse level 2");
        iconButton(g, "take_all", stashButtonX(2), by, 12, !cs.isEmpty(), mx, my, chestTab ? "Take all from this chest" : "Take all from every chest");
        iconButton(g, "stash_all", stashButtonX(3), by, 12, hideout, mx, my,
                hideout ? "Stash all: your main inventory (not the hotbar)" : "Stash all: at your home base only");
        boolean refillOk = !cs.isEmpty() && (!hideout || sh2);
        iconButton(g, "refill", stashButtonX(4), by, 12, refillOk, mx, my, hideout && !sh2 ? "Refill from the stash needs a Storehouse level 2"
                : "Refill stacks: top up the partial stacks you carry from " + (hideout ? "the stash" : "this container"));
        // Search field.
        int fy = 40;
        Kit.blit(g, storageSearchFocused ? "text_field/focused" : "text_field/normal", 339, fy, 130, 12);
        Kit.blit(g, "icon/hud/search", 341, fy + 2, 9, 9);
        String shown = storageSearch.isEmpty() && !storageSearchFocused ? "SEARCH" : storageSearch + (storageSearchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        small(g, font.plainSubstrByWidth(shown, (int) (112 / 0.75f), true), 353, fy + 3,
                storageSearch.isEmpty() && !storageSearchFocused ? Kit.ASH : Kit.BONE, 0.75f);
        // Tabs: ALL, then one per chest or barrel (the active one in rust).
        for (int[] t : storageTabs()) {
            boolean active = t[0] == storageTab;
            boolean over = !active && in(mx, my, t[1], STAB_Y, t[2], 13);
            if (active) {
                Kit.blit(g, "tab/active_rust", t[1], STAB_Y, t[2], 14);
            } else {
                Kit.blit(g, over ? "tab/inactive_hover" : "tab/inactive", t[1], STAB_Y + 2, t[2], 12);
            }
            String label = t[0] < 0 ? "ALL" : cs.get(t[0]).name().toUpperCase(Locale.ROOT);
            if (renameIndex == t[0] && t[0] >= 0) {
                label = renameText + (ticks / 6 % 2 == 0 ? "_" : "");
            }
            float k = Math.min(0.75f, (t[2] - 6) / (float) Math.max(1, font.width(label)));
            small(g, label, t[1] + 5, STAB_Y + 5, active ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH, k);
            if (over && t[0] >= 0) {
                uiTip = "Right-click to rename";
            }
        }
        // The grid: stacks in reading order, scrolling.
        List<SCell> cells = storageCells();
        int rows = Math.max(SW_ROWS, (cells.size() + SCOLS - 1) / SCOLS);
        double max = Math.max(0, (rows - SW_ROWS) * C);
        storageScroll = Math.max(0, Math.min(max, storageScroll));
        g.enableScissor((int) (ox + SGX * scale), (int) (oy + SGY * scale), (int) Math.ceil(ox + (SGX + SCOLS * C) * scale),
                (int) Math.ceil(oy + (SGY + SW_ROWS * C) * scale));
        boolean dropHere = !held.isEmpty() && overStorageGrid(mx, my);
        for (int i = 0; i < rows * SCOLS; i++) {
            int cxx = SGX + (i % SCOLS) * C;
            int cyy = SGY + (i / SCOLS) * C - (int) storageScroll;
            if (cyy + C < SGY || cyy > SGY + SW_ROWS * C) {
                continue;
            }
            boolean over = in(mx, my, cxx, cyy, C, C) && in(mx, my, SGX, SGY, SCOLS * C, SW_ROWS * C);
            Kit.slot(g, cxx, cyy, C, over && !dropHere ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            if (i < cells.size()) {
                SCell c = cells.get(i);
                boolean lifted = heldFrom != null && heldCount == 0 && heldFrom.equals(c.loc());
                if (!lifted) {
                    g.renderItem(c.stack(), cxx + 1, cyy + 1);
                    g.renderItemDecorations(font, c.stack(), cxx + 1, cyy + 1);
                }
            }
        }
        g.disableScissor();
        if (dropHere) {
            g.renderOutline(SGX - 1, SGY - 1, SCOLS * C + 2, SW_ROWS * C + 2, Kit.GOLD_LIGHT);
        }
        if (max > 0) {
            int span = SW_ROWS * C;
            int barH = Math.max(10, (int) (span * (double) span / (span + max)));
            Kit.blit(g, "scroll/handle", SGX + SCOLS * C + 1, SGY + (int) ((span - barH) * storageScroll / max), 4, barH);
        }
        int used = 0;
        int total = 0;
        for (int i = 0; i < cs.size(); i++) {
            if (storageTab >= 0 && i != storageTab) {
                continue;
            }
            used += cs.get(i).items().size() + cs.get(i).overflow().size();
            total += cs.get(i).width() * cs.get(i).height();
        }
        small(g, used + " / " + total + " SLOTS", SGX, SGY + SW_ROWS * C + 4, Kit.ASH, 0.75f);
    }

    // ------------------------------------------------------------------ equipment

    private ItemStack renderEquipment(GuiGraphics g, int mx, int my, Loc hover) {
        ItemStack tip = ItemStack.EMPTY;
        zone(g, EQ, "EQUIPMENT", null, Kit.BONE);
        if (bookOpen) {
            return renderBook(g, mx, my);
        }
        if (gatherOpen) {
            return renderGather(g, mx, my);
        }
        drawPlayer(g, mx, my);
        for (int slot = 0; slot < 7; slot++) {
            if (EQUIP_BOX[slot] == null) {
                continue;
            }
            equipBox(g, EQUIP_BOX[slot][0], EQUIP_BOX[slot][1], EQUIP_LABEL[slot]);
            equipSlot(g, slot, hover);
        }
        // MAIN: the selected hotbar item, read-only.
        equipBox(g, MAIN_X, MAIN_Y, "MAIN");
        ItemStack main = minecraft.player.getInventory().getSelected();
        boolean overMain = in(mx, my, MAIN_X + 7, MAIN_Y + 10, C, C);
        slot(g, MAIN_X + 7, MAIN_Y + 10, C, main, overMain, null);
        if (overMain) {
            if (!main.isEmpty()) {
                tip = main;
            } else {
                uiTip = "Main hand: your selected hotbar slot";
            }
        }
        renderStats(g, 11, 176, mx, my);
        return tip;
    }

    private void equipBox(GuiGraphics g, int x, int y, String label) {
        Kit.blit(g, "panel/equip_box", x, y, 32, 30);
        small(g, label, x + 3, y + 2.5f, Kit.ASH, Math.min(0.75f, 26f / font.width(label)));
    }

    /** Two stat lines: load and class, health; then stamina, and Rage or mana if the build uses them. */
    private void renderStats(GuiGraphics g, int x, int y, int mx, int my) {
        com.cavmkii.seancraft.combat.Load.State load = com.cavmkii.seancraft.client.combat.ClientCombat.load();
        com.cavmkii.seancraft.combat.Load.Tier lt = load.tier();
        Kit.blit(g, "icon/tab/load", x, y, 9, 9);
        int lc = lt == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED ? Kit.BLOOD_LIGHT
                : lt == com.cavmkii.seancraft.combat.Load.Tier.HEAVY ? Kit.EMBER : Kit.BONE;
        String cls = switch (lt) {
            case LIGHT -> "LIGHT";
            case MEDIUM -> "MED";
            case HEAVY -> "HEAVY";
            case OVERLOADED -> "OVER";
        };
        small(g, Math.round(load.fraction() * 100) + "% " + cls, x + 11, y + 1.5f, lc, 0.75f);
        if (in(mx, my, x, y, 50, 9)) {
            uiTip = "Load " + Math.round(load.fraction() * 100) + "% (" + lt.label + "): "
                    + (lt.walk != 0 ? "walk " + Math.round(lt.walk * 100) + "%, " : "no walk penalty, ")
                    + (lt.rollTicks() == 0 ? "no roll" : String.format("roll protects %.1f s", lt.rollTicks() / 20f));
        }
        Kit.blit(g, "icon/filter/meds", x + 53, y, 9, 9);
        String hp = Math.round(minecraft.player.getHealth()) + "/" + Math.round(minecraft.player.getMaxHealth());
        small(g, hp, x + 64, y + 1.5f, Kit.BLOOD_LIGHT, 0.75f);
        com.cavmkii.seancraft.rpg.Stats st = com.cavmkii.seancraft.client.rpg.ClientRpg.stats;
        int y2 = y + 10;
        stat(g, x, y2, "stamina", com.cavmkii.seancraft.client.rpg.ClientRpg.stamina,
                st.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina(), Kit.MOSS);
        java.util.Set<com.cavmkii.seancraft.rpg.Resource> used = java.util.EnumSet.noneOf(com.cavmkii.seancraft.rpg.Resource.class);
        com.cavmkii.seancraft.rpg.PlayerData data = com.cavmkii.seancraft.client.rpg.ClientRpg.data;
        for (int i = 0; i < com.cavmkii.seancraft.rpg.PlayerData.SLOTS; i++) {
            com.cavmkii.seancraft.rpg.ActiveSkill sk = data.slot(i);
            if (sk != null) {
                used.add(sk.resource);
                if (sk.alt != null) {
                    used.add(sk.alt);
                }
            }
        }
        if (used.contains(com.cavmkii.seancraft.rpg.Resource.MANA)) {
            stat(g, x + 53, y2, "mana", com.cavmkii.seancraft.client.rpg.ClientRpg.mana, st.maxMana(), Kit.ARCANE);
        } else if (used.contains(com.cavmkii.seancraft.rpg.Resource.RAGE)) {
            stat(g, x + 53, y2, "rage", com.cavmkii.seancraft.client.rpg.ClientRpg.rage, st.maxRage(), Kit.EMBER);
        }
    }

    private void stat(GuiGraphics g, int x, int y, String icon, float value, float max, int color) {
        Kit.blit(g, "icon/resource/" + icon, x, y, 9, 9);
        small(g, Math.round(value) + "/" + Math.round(max), x + 11, y + 1.5f, color, 0.75f);
    }

    /** The real player, turning to follow the mouse, as vanilla's inventory draws it. */
    private void drawPlayer(GuiGraphics g, int mx, int my) {
        var player = minecraft.player;
        float cx = MODEL[0] + MODEL[2] / 2f;
        float cy = MODEL[1] + MODEL[3] / 2f;
        float yaw = (float) Math.atan((cx - mx) / 40f);
        float pitch = (float) Math.atan((cy - my) / 40f);
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotateX(pitch * 20f * ((float) Math.PI / 180f));
        pose.mul(camera);
        float bodyRot = player.yBodyRot;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float headRotO = player.yHeadRotO;
        float headRot = player.yHeadRot;
        player.yBodyRot = 180f + yaw * 20f;
        player.setYRot(180f + yaw * 40f);
        player.setXRot(-pitch * 20f);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();
        float size = player.getScale();
        org.joml.Vector3f offset = new org.joml.Vector3f(0, player.getBbHeight() / 2f + 0.0625f * size, 0);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, cx, cy, 40f / size, offset, pose, camera, player);
        player.yBodyRot = bodyRot;
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yHeadRotO = headRotO;
        player.yHeadRot = headRot;
    }

    private void equipSlot(GuiGraphics g, int slot, Loc hover) {
        int[] r = equipRect(slot);
        if (r == null) {
            return;
        }
        slot(g, r[0], r[1], r[2], stackAt(Loc.equip(slot)), hover != null && hover.equals(Loc.equip(slot)), null);
        if (stackAt(Loc.equip(slot)).isEmpty()) {
            Kit.blit(g, "ghost/" + EQUIP_GHOSTS[slot], r[0] + (r[2] - 16) / 2, r[1] + (r[2] - 16) / 2, 16, 16);
        }
        if (!held.isEmpty() && equipAccepts(slot, held)) {
            int c = hover != null && hover.equals(Loc.equip(slot)) ? Kit.GOLD_LIGHT : Kit.GOLD;
            g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[2] + 2, c);
        }
    }

    // ------------------------------------------------------------------ crafting

    /** A recipe-book row: a category header, or a recipe (known or not). */
    private record BookRow(StorageCategory header, RecipeCell cell, int y, int h) {}

    /** Times you can make a recipe now (from the server's list); 0 if missing ingredients, -1 if it needs a crafting table. */
    private int makeable(String id) {
        for (TetrisNet.CraftEntry e : ClientTetris.craft.entries()) {
            if (e.id().equals(id)) {
                return e.times();
            }
        }
        return 0;
    }

    private static final String BENCH_ID = com.cavmkii.seancraft.SeanCraft.id("hideout_bench").toString();

    /** Categories with their recipes; outside a hideout the Hideout Bench is pinned on top (4.1). */
    /** The Hideout Bench row pinned on top outside a hideout; shown by name even before it's discovered. */
    private RecipeCell pinnedBench;

    private List<BookRow> bookRows() {
        List<BookRow> out = new ArrayList<>();
        int y = 0;
        String q = search.toLowerCase(Locale.ROOT);
        RecipeCell bench = null;
        pinnedBench = null;
        if (!ClientTetris.craft.inHideout() && q.isEmpty() && !canMakeOnly) {
            for (RecipeSection sec : recipeSections()) {
                for (RecipeCell c : sec.cells()) {
                    if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.result().getItem()).toString().equals(BENCH_ID)) {
                        bench = c;
                    }
                }
            }
            pinnedBench = bench;
            if (bench != null) {
                out.add(new BookRow(null, bench, y, BOOK_ROW));
                y += BOOK_ROW + 2;
            }
        }
        for (RecipeSection sec : recipeSections()) {
            List<RecipeCell> cells = new ArrayList<>();
            for (RecipeCell c : sec.cells()) {
                if (c == bench) {
                    continue;
                }
                if (canMakeOnly && (!c.known() || makeable(c.holder().id().toString()) <= 0)) {
                    continue;
                }
                if (!q.isEmpty() && (!c.known() || !c.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))) {
                    continue;
                }
                cells.add(c);
            }
            if (cells.isEmpty()) {
                continue;
            }
            out.add(new BookRow(sec.category(), null, y, BOOK_HEAD));
            y += BOOK_HEAD;
            if (COLLAPSED.contains(sec.category()) && q.isEmpty()) {
                continue;
            }
            for (RecipeCell c : cells) {
                out.add(new BookRow(null, c, y, BOOK_ROW));
                y += BOOK_ROW;
            }
        }
        return out;
    }

    /** The gather list over the equipment zone: each line with how many you carry, -, + and a remove button. */
    private ItemStack renderGather(GuiGraphics g, int mx, int my) {
        ItemStack tip = ItemStack.EMPTY;
        g.fill(BOOK[0], BOOK[1], BOOK[0] + BOOK[2], BOOK[1] + BOOK[3], Kit.VOID);
        Kit.text(g, font, "GATHER LIST", BOOK_X + 1, 27, Kit.GOLD_LIGHT);
        g.fill(BOOK_X, 36, BOOK_X + BOOK_W + 2, 37, Kit.GOLD_DARK);
        small(g, "T over any item adds it,", BOOK_X, 40, Kit.ASH, 0.625f);
        small(g, "T again takes it off.", BOOK_X, 47, Kit.ASH, 0.625f);
        List<ClientTracker.Entry> list = ClientTracker.entries();
        if (list.isEmpty()) {
            g.drawWordWrap(font, Component.literal("Nothing on it. Pin a recipe, press T over an item, or TRACK a module."),
                    BOOK_X, GATHER_TOP + 2, BOOK_W, Kit.ASH);
            return tip;
        }
        String[] icons = {"minus", "plus", "close"};
        String[] tips = {"Want fewer (Shift: 10 fewer)", "Want more (Shift: 10 more)", "Take it off the list"};
        for (int i = 0; i < list.size(); i++) {
            ClientTracker.Entry e = list.get(i);
            int y = GATHER_TOP + i * GATHER_ROW;
            int have = ClientTracker.have(e);
            boolean done = have >= e.goal();
            Kit.blit(g, "list/row", BOOK_X, y, BOOK_W + 2, GATHER_ROW - 2);
            ItemStack icon = new ItemStack(e.item());
            g.renderItem(icon, BOOK_X + 2, y + 3);
            if (in(mx, my, BOOK_X + 2, y + 3, 16, 16)) {
                tip = icon;
            }
            small(g, font.plainSubstrByWidth(e.name(), (int) ((BOOK_W - 22) / 0.75f)), BOOK_X + 21, y + 3, done ? Kit.MOSS : Kit.BONE, 0.75f);
            small(g, Math.min(have, 9999) + " / " + e.goal() + (done ? "  DONE" : ""), BOOK_X + 21, y + 13, done ? Kit.MOSS : Kit.ASH, 0.75f);
            for (int b = 0; b < 3; b++) {
                iconButton(g, icons[b], gatherButtonX(b), y + 10, 12, true, mx, my, tips[b]);
            }
        }
        boolean clearOver = in(mx, my, BOOK_X, 216, BOOK_W + 2, 13);
        Kit.button(g, font, "CLEAR ALL", BOOK_X, 216, BOOK_W + 2, 13, true, clearOver, false);
        if (clearOver) {
            uiTip = "Take everything off the gather list";
        }
        return tip;
    }

    private static int gatherButtonX(int b) {
        return BOOK_X + BOOK_W - 40 + b * 14;
    }

    private boolean clickGather(double x, double y) {
        List<ClientTracker.Entry> list = ClientTracker.entries();
        for (int i = 0; i < list.size(); i++) {
            int ry = GATHER_TOP + i * GATHER_ROW;
            for (int b = 0; b < 3; b++) {
                if (in(x, y, gatherButtonX(b), ry + 10, 12, 12)) {
                    int step = hasShiftDown() ? 10 : 1;
                    if (b == 0) {
                        ClientTracker.change(i, -step);
                    } else if (b == 1) {
                        ClientTracker.change(i, step);
                    } else {
                        ClientTracker.remove(i);
                    }
                    return true;
                }
            }
        }
        if (!list.isEmpty() && in(x, y, BOOK_X, 216, BOOK_W + 2, 13)) {
            ClientTracker.clear();
            return true;
        }
        // Anywhere else on the panel does nothing.
        return in(x, y, BOOK[0], BOOK[1], BOOK[2], BOOK[3]);
    }

    /** The recipe book over the equipment zone: search, CAN MAKE NOW, folding categories and recipe rows. */
    private ItemStack renderBook(GuiGraphics g, int mx, int my) {
        ItemStack tip = ItemStack.EMPTY;
        g.fill(BOOK[0], BOOK[1], BOOK[0] + BOOK[2], BOOK[1] + BOOK[3], Kit.VOID);
        Kit.text(g, font, "RECIPE BOOK", BOOK_X + 1, 27, Kit.GOLD_LIGHT);
        g.fill(BOOK_X, 36, BOOK_X + BOOK_W + 2, 37, Kit.GOLD_DARK);
        Kit.blit(g, searchFocused ? "text_field/focused" : "text_field/normal", BOOK_X, 40, BOOK_W + 2, 12);
        Kit.blit(g, "icon/hud/search", BOOK_X + 2, 42, 9, 9);
        String shown = search.isEmpty() && !searchFocused ? "SEARCH" : search + (searchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        small(g, font.plainSubstrByWidth(shown, (int) ((BOOK_W - 16) / 0.75f), true), BOOK_X + 14, 43,
                search.isEmpty() && !searchFocused ? Kit.ASH : Kit.BONE, 0.75f);
        Kit.blit(g, canMakeOnly ? "checkbox/on" : "checkbox/off", BOOK_X, 55, 10, 10);
        Kit.text(g, font, "CAN MAKE NOW", BOOK_X + 14, 56, in(mx, my, BOOK_X, 55, BOOK_W, 10) ? Kit.GOLD_LIGHT : Kit.BONE);
        List<BookRow> rows = bookRows();
        int total = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).h;
        double max = Math.max(0, total - (BOOK_BOTTOM - BOOK_TOP));
        bookScroll = Math.max(0, Math.min(max, bookScroll));
        g.enableScissor((int) (ox + BOOK_X * scale), (int) (oy + BOOK_TOP * scale), (int) Math.ceil(ox + (BOOK_X + BOOK_W) * scale),
                (int) Math.ceil(oy + BOOK_BOTTOM * scale));
        for (BookRow r : rows) {
            int y = BOOK_TOP + r.y - (int) bookScroll;
            if (y + r.h < BOOK_TOP || y > BOOK_BOTTOM) {
                continue;
            }
            boolean over = in(mx, my, BOOK_X, y, BOOK_W, r.h - 1) && my >= BOOK_TOP && my < BOOK_BOTTOM;
            if (r.header != null) {
                boolean open = !COLLAPSED.contains(r.header) || !search.isEmpty();
                Kit.blit(g, open ? "list/category_open" : "list/category_closed", BOOK_X + 1, y + 2, 7, 7);
                Kit.text(g, font, r.header.label.toUpperCase(Locale.ROOT), BOOK_X + 11, y + 1, open ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH);
                continue;
            }
            RecipeCell c = r.cell;
            String id = c.holder().id().toString();
            boolean selected = id.equals(ghostRecipe);
            if (!c.known() && c != pinnedBench) {
                Kit.blit(g, "list/row_locked", BOOK_X, y, BOOK_W, 16);
                g.drawString(font, "??????", BOOK_X + 19, y + 4, Kit.DISABLED, false);
                continue;
            }
            int can = makeable(id);
            Kit.blit(g, selected ? "list/row_selected" : over ? "list/row_hover" : "list/row", BOOK_X, y, BOOK_W, 16);
            g.renderItem(c.result(), BOOK_X + 1, y);
            String name = font.plainSubstrByWidth(c.result().getHoverName().getString(), BOOK_W - (can != 0 ? 33 : 21));
            int color = can > 0 ? (over || selected ? Kit.GOLD_LIGHT : Kit.BONE) : Kit.ASH;
            if (can > 0) {
                Kit.text(g, font, name, BOOK_X + 19, y + 4, color);
                Kit.blit(g, "icon/hud/gather_check", BOOK_X + BOOK_W - 12, y + 3, 9, 9);
            } else {
                g.drawString(font, name, BOOK_X + 19, y + 4, color, false);
                if (can < 0) {
                    Kit.blit(g, "list/lock", BOOK_X + BOOK_W - 12, y + 3, 9, 9);
                }
            }
            if (over) {
                tip = c.result();
                hoverRecipe = id;
            }
        }
        g.disableScissor();
        if (max > 0) {
            int span = BOOK_BOTTOM - BOOK_TOP;
            int barH = Math.max(10, (int) (span * (double) span / (span + max)));
            Kit.blit(g, "scroll/handle", BOOK_X + BOOK_W + 1, BOOK_TOP + (int) ((span - barH) * bookScroll / max), 4, barH);
        }
        return tip;
    }

    /** CRAFTING: the grid with the selected recipe's ghost, arrow, result, CLEAR, NEEDS and the hint line. */
    private ItemStack renderCraftZone(GuiGraphics g, int mx, int my) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        ItemStack tip = ItemStack.EMPTY;
        zone(g, CR, "CRAFTING", "icon/tab/crafting", Kit.BONE);
        boolean table = craft.table();
        String where = table ? (craft.inHideout() ? "3X3  HOME" : "3X3  TABLE") : "2X2";
        small(g, where, CR[0] + 18 + font.width("CRAFTING") + 6, CR[1] + 7, table ? Kit.MOSS : Kit.ASH, 0.75f);
        if (in(mx, my, CR[0] + 18 + font.width("CRAFTING") + 6, CR[1] + 4, 50, 10)) {
            uiTip = table ? "3x3 grid: " + (craft.inHideout() ? "you're at your home base" : "a crafting table is close")
                    : "2x2 grid: 3x3 needs a crafting table within 6 blocks, or your home base";
        }
        boolean recipesOver = in(mx, my, RECIPES);
        Kit.blit(g, recipesOver || bookOpen ? "button_small/recipes_hover" : "button_small/recipes_normal", RECIPES[0], RECIPES[1], RECIPES[2], RECIPES[3]);
        Kit.text(g, font, "RECIPES", RECIPES[0] + 4, RECIPES[1] + 2, bookOpen || recipesOver ? Kit.GOLD_LIGHT : Kit.BONE);
        // The down (or, while open, up) arrow.
        int ax = RECIPES[0] + RECIPES[2] - 10;
        int ay = RECIPES[1] + 4;
        for (int i = 0; i < 3; i++) {
            int row = bookOpen ? 2 - i : i;
            g.fill(ax + i, ay + row, ax + 5 - i, ay + row + 1, bookOpen || recipesOver ? Kit.GOLD_LIGHT : Kit.BONE);
        }
        if (recipesOver) {
            uiTip = bookOpen ? "Close the recipe book" : "Open the recipe book";
        }
        if (ghostRecipe != null) {
            iconButton(g, "pin", PIN[0], PIN[1], 12, true, mx, my, "Pin to HUD: put this recipe's ingredients on your gather list");
        }
        String sel = shownHover != null ? shownHover : ghostRecipe;
        var recipe = recipeFor(sel);
        if (!craft.result().isEmpty()) {
            ghostRecipe = null;
        }
        ItemStack[] ghostAt = ghostCells();
        for (int c = 0; c < 9; c++) {
            int x = GX + (c % 3) * GC;
            int y = GRID_Y + (c / 3) * GC;
            boolean over = cellAt(mx, my) == c || dragging(Loc.craft(c));
            boolean locked = !table && (c % 3 == 2 || c / 3 == 2);
            ItemStack in = c < craft.grid().size() ? craft.grid().get(c) : ItemStack.EMPTY;
            if (in.isEmpty() && !locked && ghostAt[c] != null && !ghostAt[c].isEmpty()) {
                Kit.blit(g, "slot/ghost", x, y, GC, GC);
                g.renderItem(ghostAt[c], x + 1, y + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                Kit.blit(g, "slot/ghost_overlay", x + 1, y + 1, 16, 16);
                g.pose().popPose();
                if (over) {
                    g.renderOutline(x, y, GC, GC, Kit.GOLD_LIGHT);
                }
                continue;
            }
            Kit.slot(g, x, y, GC, locked ? Kit.SlotState.LOCKED : over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            boolean lifted = heldFrom != null && heldFrom.kind() == Loc.CRAFT && heldFrom.a() == c && heldCount == 0;
            if (!in.isEmpty() && !lifted) {
                g.renderItem(in, x + 1, y + 1);
                g.renderItemDecorations(font, in, x + 1, y + 1);
            }
        }
        Kit.blit(g, craft.result().isEmpty() ? "progress/arrow_empty" : "progress/arrow_full", 192, 63, 22, 15);
        boolean resultHover = overResult(mx, my);
        if (craft.result().isEmpty()) {
            Kit.blit(g, "slot/result", RESULT_X, RESULT_Y, 26, 26);
            // Faded icon of what the selected recipe makes, until the pattern matches.
            if (recipe != null) {
                ItemStack out = recipe.getResultItem(minecraft.level.registryAccess());
                g.renderItem(out, RESULT_X + 5, RESULT_Y + 5);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                Kit.blit(g, "slot/ghost_overlay", RESULT_X + 5, RESULT_Y + 5, 16, 16);
                g.pose().popPose();
            }
        } else {
            Kit.blit(g, "slot/result_ready", RESULT_X, RESULT_Y, 26, 26);
            g.renderItem(craft.result(), RESULT_X + 5, RESULT_Y + 5);
            g.renderItemDecorations(font, craft.result(), RESULT_X + 5, RESULT_Y + 5);
            if (resultHover) {
                g.renderOutline(RESULT_X, RESULT_Y, 26, 26, Kit.GOLD_LIGHT);
                tip = craft.result();
            }
        }
        boolean clearHover = in(mx, my, CLEAR);
        Kit.button(g, font, "CLEAR", CLEAR[0], CLEAR[1], CLEAR[2], CLEAR[3], true, clearHover, false);
        if (clearHover) {
            uiTip = "Put everything in the grid back";
        }
        // NEEDS: one row per ingredient, have / need in Moss, or Blood light with the missing icon when short.
        small(g, "NEEDS", NEEDS_X, 42, Kit.ASH, 0.75f);
        if (recipe != null) {
            List<IngredientLine> lines = ingredientsOf(recipe);
            if (lines.size() > 3) {
                small(g, "+" + (lines.size() - 3) + " MORE", NEEDS_X + 28, 42, Kit.ASH, 0.75f);
            }
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                IngredientLine line = lines.get(i);
                int y = 51 + i * 18;
                Kit.slot(g, NEEDS_X, y, C, Kit.SlotState.NORMAL);
                g.renderItem(line.display(), NEEDS_X + 1, y + 1);
                int have = haveFor(line.ingredient());
                boolean enough = have >= line.need();
                fitted(g, line.display().getHoverName().getString().toUpperCase(Locale.ROOT), NEEDS_X + 20, y + 2, Kit.BONE, 51, 0.75f);
                String hn = have + "/" + line.need();
                small(g, hn, NEEDS_X + 20, y + 10, enough ? Kit.MOSS : Kit.BLOOD_LIGHT, 0.75f);
                if (!enough) {
                    Kit.blit(g, "icon/hud/missing", NEEDS_X + 22 + (int) (font.width(hn) * 0.75f), y + 9, 9, 9);
                }
                if (in(mx, my, NEEDS_X, y, C, C)) {
                    tip = line.display();
                }
            }
        }
        // Hint line.
        // Hint line under the grid, kept left of NEEDS: the recipe's name (cut if it must be), CLICK: 1, SHIFT: ALL.
        String keys = "  CLICK: 1  SHIFT: ALL";
        String hint = "PICK ONE FROM RECIPES";
        if (recipe != null) {
            String name = recipe.getResultItem(minecraft.level.registryAccess()).getHoverName().getString().toUpperCase(Locale.ROOT);
            int room = (int) (118 / 0.625f) - font.width(keys);
            hint = font.plainSubstrByWidth(name, room) + keys;
        }
        small(g, hint, GX, 102.5f, Kit.ASH, font.width(hint) * 0.75f <= 118 ? 0.75f : 0.625f);
        return tip;
    }

    private net.minecraft.world.item.crafting.CraftingRecipe recipeFor(String id) {
        var key = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = key == null ? null : minecraft.level.getRecipeManager().byKey(key).orElse(null);
        return holder != null && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe r ? r : null;
    }

    // ------------------------------------------------------------------ the whole tab

    private ItemStack renderInventory(GuiGraphics g, int mx, int my, int mouseX, int mouseY) {
        lastMx = mx;
        lastMy = my;
        TetrisNet.Sync s = ClientTetris.sync;
        Loc hover = locAt(mx, my);
        ItemStack tip = stackAt(hover);
        if (hover == null) {
            SCell sc = storageAt(mx, my);
            if (sc != null) {
                tip = sc.stack();
            }
        }
        ItemStack left = renderEquipment(g, mx, my, hover);
        if (!left.isEmpty()) {
            tip = left;
        }
        ItemStack mid = renderCraftZone(g, mx, my);
        if (!mid.isEmpty()) {
            tip = mid;
        }

        // Player inventory: vanilla's 27 main slots (3.1).
        zone(g, PI, "PLAYER INVENTORY", null, Kit.BONE);
        iconButton(g, "sort", 311, PI[1] + 3, 12, true, mx, my, "Sort your inventory");
        ItemStack next = nextArrow();
        for (int i = 9; i < 36; i++) {
            int x = invX(i);
            int y = invY(i);
            Loc loc = Loc.hand(i);
            ItemStack stack = stackAt(loc);
            slot(g, x, y, C, stack, hover != null && hover.equals(loc) || dragging(loc), null);
            if (next != null && stack == next) {
                arrowOutline(g, x, y);
            }
        }
        small(g, "SHIFT-CLICK: TO STORAGE, OR BETWEEN HOTBAR AND INVENTORY", INV_X, INV_Y + 3 * C + 5, Kit.ASH, 0.5f);

        renderStorage(g, mx, my);

        // Hotbar in its ornate frame; the selected slot glows.
        Kit.blit(g, "hud/hotbar_frame", HB[0], HB[1], HB[2], HB[3]);
        int selected = minecraft.player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = HB_X + i * HB_STEP;
            boolean over = hover != null && hover.equals(Loc.hand(i)) || dragging(Loc.hand(i));
            slot(g, x, HB_Y, C, stackAt(Loc.hand(i)), over, null);
            if (next != null && stackAt(Loc.hand(i)) == next) {
                arrowOutline(g, x, HB_Y);
            }
            if (i == selected) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 150);
                Kit.blit(g, "slot/selected_glow", x - 2, HB_Y - 2, 22, 22);
                g.pose().popPose();
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            Kit.blit(g, "slot/key_badge", x, HB_Y, 6, 7);
            small(g, String.valueOf(i + 1), x + 1.5f, HB_Y + 1.5f, i == selected ? Kit.GOLD_LIGHT : Kit.BONE, 0.5f);
            g.pose().popPose();
        }
        // Auto-refill toggles beside the frame.
        iconButton(g, "refill", TOGGLE_X, TOGGLE_Y, 12, true, mx, my, "Auto-refill the hotbar: " + (s.autoRefill() ? "ON" : "OFF"));
        if (s.autoRefill()) {
            g.renderOutline(TOGGLE_X - 1, TOGGLE_Y - 1, 14, 14, Kit.GOLD);
        }
        boolean overStash = in(mx, my, TOGGLE_X + 16, TOGGLE_Y, 12, 12);
        Kit.buttonBg(g, TOGGLE_X + 16, TOGGLE_Y, 12, 12, true, overStash, s.refillFromStash());
        Kit.blit(g, "icon/tab/stash", TOGGLE_X + 17, TOGGLE_Y + 1, 9, 9);
        if (overStash) {
            uiTip = "Auto-refill from the stash too: " + (s.refillFromStash() ? "ON" : "OFF");
        }
        // GATHER LIST: under the equipment zone, opens the list over it.
        int n = ClientTracker.entries().size();
        boolean gatherOver = in(mx, my, GATHER_BUTTON);
        Kit.button(g, font, "GATHER LIST (" + n + ")", GATHER_BUTTON[0], GATHER_BUTTON[1], GATHER_BUTTON[2], GATHER_BUTTON[3], true, gatherOver, gatherOpen);
        if (gatherOver) {
            uiTip = gatherOpen ? "Close the gather list" : "Your gather list: change amounts or take things off";
        }
        renderFilter(g, mx, my);
        renderHeld(g, mx, my, hover);
        return tip;
    }

    /** The green rim on the stack a bow or crossbow shoots next (C.2: green outlines where arrows come from). */
    private void arrowOutline(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        g.renderOutline(x, y, C, C, Kit.GREEN);
        g.renderOutline(x + 1, y + 1, C - 2, C - 2, Kit.GREEN);
        g.pose().popPose();
    }

    /** True if a point is on a zone or the hotbar (a click anywhere else with something held drops it). */
    private boolean onPanel(double x, double y) {
        for (int[] z : new int[][] {EQ, CR, PI, ST, HB}) {
            if (in(x, y, z[0] - 3, z[1] - 3, z[2] + 6, z[3] + 6)) {
                return true;
            }
        }
        return in(x, y, TOGGLE_X, TOGGLE_Y, 28, 12) || in(x, y, GATHER_BUTTON) || y < 18;
    }

    /** Clicks on the small controls: RECIPES, the book, CLEAR, PIN, the stash header, tabs, search, toggles. */
    private boolean clickControls(double x, double y, int button) {
        TetrisNet.Sync s = ClientTetris.sync;
        List<TetrisNet.ContainerView> cs = s.containers();
        // Storage tabs (right-click renames).
        for (int[] t : storageTabs()) {
            if (in(x, y, t[1], STAB_Y, t[2], 14)) {
                if (button == 1 && t[0] >= 0) {
                    renameIndex = t[0];
                    renameText = cs.get(t[0]).name();
                } else if (button == 0) {
                    storageTab = t[0];
                    storageScroll = 0;
                }
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        storageSearchFocused = anyStorage() && in(x, y, 339, 40, 130, 12);
        if (storageSearchFocused) {
            searchFocused = false;
            return true;
        }
        if (in(x, y, RECIPES)) {
            toggleBook();
            return true;
        }
        if (in(x, y, GATHER_BUTTON)) {
            toggleGather();
            return true;
        }
        if (gatherOpen && clickGather(x, y)) {
            return true;
        }
        if (ghostRecipe != null && in(x, y, PIN)) {
            pinRecipe(ghostRecipe);
            return true;
        }
        if (in(x, y, CLEAR)) {
            dropHeld();
            ClientPlayNetworking.send(new TetrisNet.Craft("#clear", 0));
            return true;
        }
        if (bookOpen) {
            searchFocused = in(x, y, BOOK_X, 40, BOOK_W + 2, 12);
            if (searchFocused) {
                return true;
            }
            if (in(x, y, BOOK_X, 55, BOOK_W, 10)) {
                canMakeOnly = !canMakeOnly;
                bookScroll = 0;
                return true;
            }
            if (held.isEmpty() && in(x, y, BOOK_X, BOOK_TOP, BOOK_W, BOOK_BOTTOM - BOOK_TOP)) {
                double at = y - BOOK_TOP + bookScroll;
                for (BookRow r : bookRows()) {
                    if (at >= r.y && at < r.y + r.h) {
                        if (r.header != null) {
                            if (!COLLAPSED.remove(r.header)) {
                                COLLAPSED.add(r.header);
                            }
                        } else if (r.cell.known() || r.cell == pinnedBench) {
                            String id = r.cell.holder().id().toString();
                            // Shows the pattern; nothing places items for the player (4.1).
                            ghostRecipe = id.equals(ghostRecipe) ? null : id;
                        }
                        return true;
                    }
                }
                return true;
            }
        }
        if (in(x, y, TOGGLE_X, TOGGLE_Y, 12, 12)) {
            ClientPlayNetworking.send(new TetrisNet.Action(6));
            return true;
        }
        if (in(x, y, TOGGLE_X + 16, TOGGLE_Y, 12, 12)) {
            ClientPlayNetworking.send(new TetrisNet.Action(7));
            return true;
        }
        if (in(x, y, 311, PI[1] + 3, 12, 12)) {
            ClientPlayNetworking.send(new TetrisNet.Action(0));
            return true;
        }
        if (anyStorage()) {
            boolean hideout = s.stash();
            boolean chestTab = storageTab >= 0 || !hideout;
            int chest = storageTab >= 0 ? storageTab : 0;
            int by = ST[1] + 3;
            if (in(x, y, stashButtonX(0), by, 12, 12)) {
                if (chestTab && chest < cs.size() && s.storehouse() >= 2) {
                    filterOpen = filterOpen == chest ? -1 : chest;
                    filterX = ST[0] + ST[2] - FILTER_W - 4;
                    filterY = ZONE_Y + 17;
                }
                return true;
            }
            if (in(x, y, stashButtonX(1), by, 12, 12)) {
                if (chestTab && chest < cs.size()) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(chest, 0, 0));
                } else if (s.storehouse() >= 2) {
                    ClientPlayNetworking.send(new TetrisNet.Action(8));
                }
                return true;
            }
            if (in(x, y, stashButtonX(2), by, 12, 12)) {
                if (chestTab && chest < cs.size()) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(chest, 1, 0));
                } else {
                    for (int i = 0; i < cs.size(); i++) {
                        ClientPlayNetworking.send(new TetrisNet.ContainerAction(i, 1, 0));
                    }
                }
                return true;
            }
            if (in(x, y, stashButtonX(3), by, 12, 12)) {
                if (hideout) {
                    ClientPlayNetworking.send(new TetrisNet.Action(1));
                }
                return true;
            }
            if (in(x, y, stashButtonX(4), by, 12, 12)) {
                if (!cs.isEmpty() && (!hideout || s.storehouse() >= 2)) {
                    ClientPlayNetworking.send(new TetrisNet.Action(9));
                }
                return true;
            }
        }
        return false;
    }

    private void toggleGather() {
        gatherOpen = !gatherOpen;
        bookOpen = false;
        searchFocused = false;
    }

    private void toggleBook() {
        bookOpen = !bookOpen;
        gatherOpen = false;
        searchFocused = false;
        bookScroll = 0;
    }

    /** A click on the storage grid: pick a stack up, or put what you hold away (into the tab's chest, or by filters on ALL). */
    private boolean clickStorage(double x, double y, int button) {
        if (!overStorageGrid(x, y)) {
            return false;
        }
        if (!held.isEmpty()) {
            int target = storageTab >= 0 ? storageTab : ClientTetris.sync.stash() ? -1 : 0;
            ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.container(target, -2, 0), false, button == 1 ? 1 : heldCount));
            if (button == 1 && carrying() > 1) {
                heldCount = carrying() - 1;
            } else {
                dropHeld();
            }
            return true;
        }
        SCell c = storageAt(x, y);
        if (c == null) {
            return true;
        }
        if (button == 0 && (hasShiftDown() || hasControlDown())) {
            ClientPlayNetworking.send(new TetrisNet.QuickMove(c.loc()));
            return true;
        }
        heldFrom = c.loc();
        held = c.stack();
        heldRot = c.placed() != null && c.placed().rotated;
        heldCount = button == 1 && held.getCount() > 1 ? (held.getCount() + 1) / 2 : 0;
        return true;
    }

    /** The station row (top right, inside a hideout): kind, x, width, index. */
    private List<Object[]> stationButtons() {
        List<Object[]> out = new ArrayList<>();
        if (!ClientTarkov.place.home()) {
            return out;
        }
        List<String> kinds = StationTabs.stations.kinds();
        int x = width - 4;
        for (int i = kinds.size() - 1; i >= 0; i--) {
            String kind = kinds.get(i);
            boolean open = kind.equals(openStation);
            int w = open ? 13 + 4 + font.width(StationTabs.label(kind)) + 4 : 13;
            x -= w;
            out.add(0, new Object[] {kind, x, w, i});
            x -= 2;
        }
        return out;
    }

    private static String stationIcon(String kind) {
        return switch (kind) {
            case "hideout" -> "icon/tab/hideout";
            case "crafting_table" -> "icon/tab/crafting";
            case "weapon_bench" -> "icon/station/weapon_bench";
            case "module_map_room" -> "icon/station/intel_center";
            case "module_storehouse" -> "icon/tab/stash";
            default -> kind.startsWith("module_") ? "icon/station/" + kind.substring(7) : "icon/tab/hideout";
        };
    }

    // ================================================================== crafting

    /** Categories folded shut in the recipe book; kept while the game runs. */
    private static final java.util.Set<StorageCategory> COLLAPSED = java.util.EnumSet.noneOf(StorageCategory.class);

    /** Recipe whose pattern is drawn as a ghost in the CRAFTING grid, or null. */
    private String ghostRecipe;

    /** The ghost's item for each of the 9 squares (null where the pattern has nothing). Tag ingredients cycle. */
    /** Recipe under the mouse in the list or book this frame: previewed in the grid and drawn in its tooltip. */
    private String hoverRecipe;
    private String shownHover;

    /** The ghost in the grid: the recipe you're hovering, else the one you picked. */
    private ItemStack[] ghostCells() {
        return cellsFor(shownHover != null ? shownHover : ghostRecipe);
    }

    /** A recipe's ingredients laid out on the 3x3 grid (cycling through each ingredient's options once a second). */
    private ItemStack[] cellsFor(String id) {
        ItemStack[] out = new ItemStack[9];
        var key = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = key == null ? null : minecraft.level.getRecipeManager().byKey(key).orElse(null);
        if (holder == null || !(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) {
            return out;
        }
        var ingredients = recipe.getIngredients();
        int w = recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped ? shaped.getWidth() : 3;
        int frame = (int) (minecraft.level.getGameTime() / 20);
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            ItemStack[] options = ingredients.get(i).getItems();
            int cell = (i / w) * 3 + i % w;
            if (options.length > 0 && cell < 9) {
                out[cell] = options[frame % options.length];
            }
        }
        return out;
    }

    private void pinRecipe(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = key == null ? null : minecraft.level.getRecipeManager().byKey(key).orElse(null);
        if (holder != null && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe) {
            for (IngredientLine line : ingredientsOf(recipe)) {
                ClientTracker.add(line.display.getItem(), line.need);
            }
        }
    }

    private int cellAt(double x, double y) {
        if (x < GX || x >= GX + 3 * GC || y < GRID_Y || y >= GRID_Y + 3 * GC) {
            return -1;
        }
        return (int) ((y - GRID_Y) / GC) * 3 + (int) ((x - GX) / GC);
    }

    private boolean overResult(double x, double y) {
        return x >= RESULT_X && x < RESULT_X + 26 && y >= RESULT_Y && y < RESULT_Y + 26;
    }

    private ItemStack hoverStack = ItemStack.EMPTY;
    private List<RecipeSection> recipeCache;
    private int recipeCacheTick = -1000;

    private record RecipeCell(net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder,
            ItemStack result, boolean known) {}

    private record RecipeSection(com.cavmkii.seancraft.tetris.StorageCategory category, List<RecipeCell> cells) {}

    private record IngredientLine(net.minecraft.world.item.crafting.Ingredient ingredient, ItemStack display, int need) {}

    /** Every crafting recipe by category; discovered ones (in your recipe book) first within each, by name. */
    private List<RecipeSection> recipeSections() {
        if (recipeCache != null && ticks - recipeCacheTick < 40) {
            return recipeCache;
        }
        recipeCacheTick = ticks;
        var registries = minecraft.level.registryAccess();
        java.util.Map<com.cavmkii.seancraft.tetris.StorageCategory, List<RecipeCell>> map =
                new java.util.EnumMap<>(com.cavmkii.seancraft.tetris.StorageCategory.class);
        for (var holder : minecraft.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            boolean known = minecraft.player.getRecipeBook().contains(holder);
            map.computeIfAbsent(com.cavmkii.seancraft.tetris.StorageCategory.of(result), k -> new ArrayList<>())
                    .add(new RecipeCell(holder, result, known));
        }
        List<RecipeSection> out = new ArrayList<>();
        map.forEach((cat, cells) -> {
            cells.sort((a, b) -> a.known != b.known ? (a.known ? -1 : 1)
                    : a.result.getHoverName().getString().compareToIgnoreCase(b.result.getHoverName().getString()));
            out.add(new RecipeSection(cat, cells));
        });
        recipeCache = out;
        return out;
    }

    /** What a recipe takes, merged: each line shows an item you have if any option matches, else the first option. */
    private List<IngredientLine> ingredientsOf(net.minecraft.world.item.crafting.CraftingRecipe recipe) {
        List<IngredientLine> lines = new ArrayList<>();
        for (var ing : recipe.getIngredients()) {
            if (ing.isEmpty()) {
                continue;
            }
            boolean merged = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).ingredient.equals(ing)) {
                    lines.set(i, new IngredientLine(ing, lines.get(i).display, lines.get(i).need + 1));
                    merged = true;
                    break;
                }
            }
            if (merged) {
                continue;
            }
            ItemStack[] options = ing.getItems();
            ItemStack display = options.length > 0 ? options[0] : ItemStack.EMPTY;
            for (ItemStack m : ClientTetris.craft.materials()) {
                if (ing.test(m)) {
                    display = m.copyWithCount(1);
                    break;
                }
            }
            lines.add(new IngredientLine(ing, display, 1));
        }
        return lines;
    }

    private int haveFor(net.minecraft.world.item.crafting.Ingredient ing) {
        int n = 0;
        for (ItemStack m : ClientTetris.craft.materials()) {
            if (ing.test(m)) {
                n += m.getCount();
            }
        }
        return n;
    }

    // ================================================================== skills

    // ================================================================== input

    /** Once the server has answered a swap, hold the bumped item where it landed, or let go if it went elsewhere. */
    private void settleSwap() {
        if (swapSync < 0 || ClientTetris.syncs <= swapSync) {
            return;
        }
        ItemStack now = stackAt(heldFrom);
        if (!held.isEmpty() && ItemStack.isSameItemSameComponents(now, held)) {
            held = now;
            Placed p = placedAt(heldFrom);
            heldRot = p != null && p.rotated;
            swapSync = -1;
        } else if (ticks - swapTick > 10) {
            dropHeld();
        }
    }

    private void dropHeld() {
        drag.clear();
        swapSync = -1;
        heldFrom = null;
        held = ItemStack.EMPTY;
        heldCount = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] st : stationButtons()) {
            if (in(mouseX, mouseY, (int) st[1], 2, (int) st[2], 13)) {
                if (st[0].equals(openStation) && StationTabs.CRAFTING_TABLE.equals(st[0])) {
                    dropHeld();
                    setTab(CRAFT_MODE);
                } else {
                    ClientPlayNetworking.send(new com.cavmkii.seancraft.tarkov.TarkovNet.OpenStation(StationTabs.stations.positions().get((int) st[3])));
                }
                return true;
            }
        }
        for (int t = 0; t < TABS.length; t++) {
            if (overTab(t, mouseX, mouseY)) {
                dropHeld();
                setTab(t);
                return true;
            }
        }
        if (tab == STATUS) {
            return status.mouseClicked(mouseX, mouseY - TOP, button);
        }
        if (tab == WEB) {
            return web.mouseClicked(mouseX, mouseY - TOP, button);
        }
        double x = vx(mouseX);
        double y = vy(mouseY);
        if (renameIndex >= 0) {
            commitRename();
        }
        return clickCrafting(x, y, button);
    }


    /**
     * A click on your grids, hotbar, equipment, the stash column, or (on CRAFTING) a craft cell: pick up, put down, split
     * and swap like vanilla. Holding something over a hotbar slot or craft cell starts a drag (see {@link #mouseDragged}).
     */
    private boolean clickInventory(double x, double y, int button) {
        if (filterOpen >= 0 && clickFilter(x, y)) {
            return true;
        }
        Loc loc = locAt(x, y);
        if (!held.isEmpty() && loc != null && dragTarget(loc) && !(loc.equals(heldFrom) && heldCount == 0)) {
            // Vanilla drag: nothing happens until release; dragging over more slots spreads the stack.
            drag.clear();
            drag.add(loc);
            dragButton = button;
            return true;
        }
        if (!held.isEmpty()) {
            if (loc == null && !onPanel(x, y)) {
                // Off every panel: drop it, like clicking outside vanilla's inventory.
                ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.discard(), false, button == 1 ? 1 : heldCount));
                if (button == 1 && carrying() > 1) {
                    heldCount = carrying() - 1;
                } else {
                    dropHeld();
                }
                return true;
            }
            return placeHeld(loc, button);
        }
        ItemStack stack = stackAt(loc);
        if (loc == null || stack.isEmpty() || loc.kind() == Loc.DISCARD) {
            return true;
        }
        if (button == 0 && (hasShiftDown() || hasControlDown())) {
            // Shift-click (or Ctrl) sends it across, like vanilla.
            ClientPlayNetworking.send(new TetrisNet.QuickMove(loc));
            return true;
        }
        Placed p = placedAt(loc);
        heldFrom = p == null ? loc : Loc.container(loc.a(), p.x, p.y);
        held = stack;
        heldRot = p != null && p.rotated;
        // Right-click picks up half (the bigger half), like vanilla.
        heldCount = button == 1 && stack.getCount() > 1 ? (stack.getCount() + 1) / 2 : 0;
        return true;
    }

    /** Slots a drag can spread over: hotbar slots and craft cells. */
    private boolean dragTarget(Loc loc) {
        return loc.kind() == Loc.HAND || loc.kind() == Loc.CRAFT;
    }

    private final List<Loc> drag = new ArrayList<>();
    private int dragButton;

    private boolean dragging(Loc loc) {
        return drag.size() > 1 && drag.contains(loc);
    }

    /** How many of the held item are on the cursor. */
    private int carrying() {
        return heldCount > 0 ? heldCount : held.getCount();
    }

    /** Can a dragged stack go here: empty, or the same item with room. */
    private boolean dragAccepts(Loc loc) {
        ItemStack there = stackAt(loc);
        return there.isEmpty() || ItemStack.isSameItemSameComponents(there, held) && there.getCount() < there.getMaxStackSize();
    }

    /** Release of a drag: one slot is a plain click; several split the stack (left) or get one each (right). */
    private void finishDrag() {
        List<Loc> cells = new ArrayList<>(drag);
        drag.clear();
        if (cells.isEmpty() || held.isEmpty()) {
            return;
        }
        if (cells.size() == 1) {
            placeHeld(cells.get(0), dragButton);
            return;
        }
        int have = carrying();
        int each = dragButton == 1 ? 1 : Math.max(1, have / cells.size());
        int placed = 0;
        for (Loc loc : cells) {
            if (placed + each > have) {
                break;
            }
            ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, loc, false, each));
            placed += each;
        }
        if (have - placed <= 0) {
            dropHeld();
        } else {
            heldCount = have - placed;
        }
    }

    /** Put down what's held at a place: right-click one, left-click all (swapping onto a full slot), like vanilla. */
    private boolean placeHeld(Loc loc, int button) {
        {
            if (button == 1 && loc != null && !loc.equals(heldFrom)) {
                // Right-click: put down one and keep holding the rest, like vanilla.
                int carrying = heldCount > 0 ? heldCount : held.getCount();
                ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, loc, heldRot, 1));
                if (carrying - 1 <= 0) {
                    dropHeld();
                } else {
                    heldCount = carrying - 1;
                }
                return true;
            }
            if (button == 0 && loc != null && !(loc.equals(heldFrom) && heldCount == 0 && !rotationChanged())) {
                ItemStack there = stackAt(loc);
                // A whole stack onto a full hotbar or gear slot swaps; the bumped item lands where this one was
                // and ends up on the cursor, like vanilla.
                boolean swap = heldCount == 0 && (loc.kind() == Loc.HAND || loc.kind() == Loc.EQUIP || loc.kind() == Loc.CRAFT) && !there.isEmpty()
                        && !ItemStack.isSameItemSameComponents(there, held)
                        && !(heldFrom.kind() == Loc.CONTAINER && heldFrom.b() < 0);
                ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, loc, heldRot, heldCount));
                if (swap) {
                    Loc back = heldFrom;
                    dropHeld();
                    heldFrom = back;
                    held = there;
                    swapSync = ClientTetris.syncs;
                    swapTick = ticks;
                    return true;
                }
            }
            dropHeld();
            return true;
        }
    }

    private boolean rotationChanged() {
        Placed p = placedAt(heldFrom);
        return p != null && p.rotated != heldRot;
    }

    private boolean clickCrafting(double x, double y, int button) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        if (filterOpen >= 0 && clickFilter(x, y)) {
            return true;
        }
        if (clickControls(x, y, button)) {
            return true;
        }
        if (overResult(x, y)) {
            if (!craft.result().isEmpty() && held.isEmpty()) {
                ClientPlayNetworking.send(new TetrisNet.Craft("#grid", hasShiftDown() ? 64 : 1));
            }
            return true;
        }
        if (button != 0 && button != 1) {
            return true;
        }
        if (clickStorage(x, y, button)) {
            return true;
        }
        return clickInventory(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!drag.isEmpty() && button == dragButton && !held.isEmpty()) {
            Loc loc = locAt(vx(mouseX), vy(mouseY));
            if (loc != null && dragTarget(loc) && !drag.contains(loc) && !loc.equals(heldFrom) && dragAccepts(loc)
                    && drag.size() < carrying()) {
                drag.add(loc);
            }
            return true;
        }
        return tab == WEB ? web.mouseDragged(mouseX, mouseY - TOP, button, dragX, dragY) : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!drag.isEmpty() && button == dragButton) {
            finishDrag();
            return true;
        }
        return tab == WEB ? web.mouseReleased(mouseX, mouseY - TOP, button) : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == WEB) {
            return web.mouseScrolled(mouseX, mouseY - TOP, scrollX, scrollY);
        }
        if (tab == INVENTORY) {
            double x = vx(mouseX);
            double y = vy(mouseY);
            if (bookOpen && in(x, y, BOOK[0], BOOK[1], BOOK[2], BOOK[3])) {
                bookScroll -= scrollY * BOOK_ROW;
            } else if (in(x, y, ST)) {
                storageScroll -= scrollY * 18;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T && renameIndex < 0 && !searchFocused && !storageSearchFocused && !hoverStack.isEmpty()) {
            // T on any item: track it (a stack's worth, or one if it doesn't stack); T again takes it off.
            List<ClientTracker.Entry> list = ClientTracker.entries();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).tag() == null && list.get(i).item() == hoverStack.getItem()) {
                    ClientTracker.remove(i);
                    return true;
                }
            }
            ClientTracker.add(hoverStack.getItem(), hoverStack.getMaxStackSize() > 1 ? hoverStack.getMaxStackSize() : 1);
            return true;
        }
        if (renameIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !renameText.isEmpty()) {
                renameText = renameText.substring(0, renameText.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renameIndex = -1;
            }
            return true;
        }
        if (tab == INVENTORY && storageSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !storageSearch.isEmpty()) {
                storageSearch = storageSearch.substring(0, storageSearch.length() - 1);
                storageScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                storageSearchFocused = false;
                return true;
            }
            return keyCode != GLFW.GLFW_KEY_TAB || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (tab == CRAFTING && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                bookScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return keyCode != GLFW.GLFW_KEY_TAB || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !held.isEmpty()) {
            dropHeld();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && tab == INVENTORY && bookOpen) {
            toggleBook();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && tab == INVENTORY && gatherOpen) {
            toggleGather();
            return true;
        }
        if ((tab == GEAR || tab == CRAFTING) && keyCode == GLFW.GLFW_KEY_R && !held.isEmpty()) {
            int[] s = ItemSize.of(held);
            if (s[0] != s[1]) {
                heldRot = !heldRot;
            }
            return true;
        }
        if ((tab == GEAR || tab == CRAFTING) && held.isEmpty() && minecraft.options.keyDrop.matches(keyCode, scanCode)) {
            // Q drops one of the hovered item, Ctrl+Q the whole stack.
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            Loc loc = locAt(vx(mx), vy(my));
            if (loc != null && !stackAt(loc).isEmpty()) {
                ClientPlayNetworking.send(new TetrisNet.Move(loc, Loc.discard(), false, hasControlDown() ? 0 : 1));
            }
            return true;
        }
        if ((tab == GEAR || tab == CRAFTING) && held.isEmpty() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            Loc loc = locAt(vx(mx), vy(my));
            if (loc != null && !stackAt(loc).isEmpty()) {
                ClientPlayNetworking.send(new TetrisNet.Move(loc, Loc.hand(keyCode - GLFW.GLFW_KEY_1), false, 0));
            }
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode) || ClientTarkov.OPEN_SECURE.matches(keyCode, scanCode)
                || ClientTarkov.OPEN_STATUS.matches(keyCode, scanCode)
                || com.cavmkii.seancraft.client.rpg.ClientRpg.OPEN_WEB.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitRename() {
        if (renameIndex >= 0) {
            ClientPlayNetworking.send(new TetrisNet.ContainerName(renameIndex, renameText));
            renameIndex = -1;
        }
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (renameIndex >= 0) {
            if (c >= ' ' && renameText.length() < 40) {
                renameText += c;
            }
            return true;
        }
        if (tab == INVENTORY && storageSearchFocused && c >= ' ' && storageSearch.length() < 40) {
            storageSearch += c;
            storageScroll = 0;
            return true;
        }
        if (tab == CRAFTING && searchFocused && c >= ' ' && search.length() < 40) {
            search += c;
            bookScroll = 0;
            return true;
        }
        return super.charTyped(c, modifiers);
    }
}
