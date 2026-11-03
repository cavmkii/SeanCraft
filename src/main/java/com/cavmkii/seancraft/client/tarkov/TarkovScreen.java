package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.rpg.ClientRpg;
import com.cavmkii.seancraft.rpg.Attr;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Skill;
import com.cavmkii.seancraft.rpg.Stats;
import com.cavmkii.seancraft.tetris.GearItem;
import com.cavmkii.seancraft.tetris.Grid;
import com.cavmkii.seancraft.tetris.GridId;
import com.cavmkii.seancraft.tetris.InvData;
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
 * The Tarkov screen. INVENTORY: equipment, hotbar and the Tetris grids on the left, the crafting grid and its recipe
 * books in the middle, any open container or the stash on the right. STATUS: body and effects. CHARACTER: the passive
 * web with level, attributes and use-skills.
 * STATUS: body health and meds.
 *
 * <p>GEAR controls, mostly vanilla's: click to pick up and put down, right-click to pick up half or put down one,
 * Shift+click (or Ctrl) to transfer, R to rotate, 1-9 to send the hovered item to a hotbar slot, Q to drop one
 * (Ctrl+Q the stack), Esc to let go.
 */
public class TarkovScreen extends Screen {
    /** Equipment, grids, hotbar, the crafting grid with its recipe books, and the stash: GEAR and CRAFTING in one. */
    public static final int INVENTORY = 0;
    /** Old names for the INVENTORY tab, for callers that open it (E, a crafting table). */
    public static final int GEAR = INVENTORY;
    public static final int CRAFTING = INVENTORY;
    public static final int STATUS = 1;
    /** The passive web with level, attributes and use-skills beside it (SKILLS and WEB used to be two tabs). */
    public static final int WEB = 2;
    public static final int SKILLS = WEB;
    /** Open INVENTORY with crafting mode on (a crafting table). */
    public static final int CRAFT_MODE = 3;
    private static final String[] TABS = {"INVENTORY", "STATUS", "SKILLS"};

    private static final int C = 18;
    private static final int W = 426;
    private static final int H = 220;
    private static final int TOP = 24;
    private static final int ROW = 22;


    /** Virtual width of the recipe-book view (the INVENTORY layout is VW). */
    private int vw() {
        return W;
    }

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

    private double craftScroll;
    private String search = "";
    private boolean searchFocused;
    private int ticks;
    private StatusScreen status;
    private com.cavmkii.seancraft.client.rpg.WebScreen web;

    public TarkovScreen(int tab) {
        super(Component.literal("Inventory"));
        this.tab = tab == CRAFT_MODE ? INVENTORY : tab;
        if (tab == CRAFT_MODE) {
            craftMode = true;
            openStation = StationTabs.CRAFTING_TABLE;
        }
    }

    void setTab(int tab) {
        if (tab == CRAFT_MODE) {
            craftMode = true;
            openStation = StationTabs.CRAFTING_TABLE;
            tab = INVENTORY;
        }
        this.tab = tab;
        layout();
        if (tab == CRAFTING) {
            ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
        }
    }

    @Override
    protected void init() {
        // Room at the bottom for the station tabs (hideout, Weapon Bench...).
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
            if (tab == CRAFTING) {
                ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
            }
        }
    }

    /** Scale and place the virtual canvas for the current tab. */
    private void layout() {
        if (tab == INVENTORY && craftView == 0) {
            // GEAR-V2: a 480 x 270 canvas whose content starts under the tab bar at y 18, scaled down only if it must be.
            int bottom = 2;
            scale = Math.min(1f, Math.min((width - 4) / (float) VW, (height - 18 - bottom) / (float) (VH - 18)));
            ox = (width - VW * scale) / 2f;
            oy = 18 - 18 * scale;
            return;
        }
        // Room at the bottom for the station tabs (hideout, Weapon Bench...).
        int bottom = 16;
        int w = vw();
        // Keep clear of the window frame on both sides.
        scale = Math.min(1f, Math.min((width - 24) / (float) w, (height - TOP - 4 - bottom) / (float) H));
        ox = (width - w * scale) / 2f;
        oy = TOP + Math.max(0, (height - TOP - bottom - H * scale) / 2f - 4);
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

    private record Box(GridId id, int x, int y, int w, int h, List<Placed> items) {}

    private List<Box> boxes() {
        TetrisNet.Sync s = ClientTetris.sync;
        List<Box> out = new ArrayList<>();
        if (!craftMode) {
            out.add(new Box(GridId.POUCH, POUCH_GRID_X, POUCH_Y, s.pouchW(), s.pouchH(), s.pouch()));
        }
        for (ColSec sec : colSections()) {
            int gy = sec.y() + 11 - (int) colScroll;
            if (sec.kind() == 1 && s.rig().getItem() instanceof GearItem g) {
                out.add(new Box(GridId.RIG, secGridX(), gy, g.spec.gridWidth, g.spec.gridHeight, s.rigItems()));
            } else if (sec.kind() == 2) {
                out.add(new Box(GridId.POCKETS, secGridX(), gy, InvData.POCKET_W, InvData.POCKET_H, s.pockets()));
            } else if (sec.kind() == 3 && s.backpack().getItem() instanceof GearItem g) {
                out.add(new Box(GridId.BACKPACK, secGridX(), gy, g.spec.gridWidth, g.spec.gridHeight, s.backpackItems()));
            }
        }
        return out;
    }



    /** x, y, size of an equipment slot: in its box, or (rig, backpack) beside its grid; null if scrolled away. */
    private int[] equipRect(int slot) {
        if (EQUIP_BOX[slot] != null) {
            return craftMode ? null : new int[] {EQUIP_BOX[slot][0] + 11, EQUIP_BOX[slot][1] + 11, C};
        }
        for (ColSec sec : colSections()) {
            if (slot == 4 && sec.kind() == 1 || slot == 5 && sec.kind() == 3) {
                return new int[] {secX(), sec.y() + 11 - (int) colScroll, C};
            }
        }
        return null;
    }


    private static final String[] EQUIP_NAMES = {"Head", "Chest", "Legs", "Feet", "Rig", "Backpack", "Off-hand"};
    /** Outlines shown in empty equipment slots (ghost/*). */
    private static final String[] EQUIP_GHOSTS = {"helmet", "chestplate", "leggings", "boots", "rig", "backpack", "offhand"};




    /** A grid on screen: top-left, size in cells, and its items. */
    private record GridRef(int x, int y, int w, int h, List<Placed> items) {}

    /** The grid a GRID or container-cell location belongs to. */
    private GridRef gridFor(Loc loc) {
        if (loc == null) {
            return null;
        }
        if (loc.kind() == Loc.GRID) {
            Box b = box(GridId.values()[loc.a()]);
            return b == null ? null : new GridRef(b.x, b.y, b.w, b.h, b.items);
        }
        return null;
    }

    private static int rows(int n) {
        return Math.max(1, (n + 8) / 9);
    }


    /** What's under a point (virtual coords) on the INVENTORY tab. For grids it's the cell. */
    private Loc locAt(double x, double y) {
        if (tab == INVENTORY && craftMode) {
            int cell = cellAt(x, y);
            if (cell >= 0) {
                return Loc.craft(cell);
            }
        }
        for (int slot = 0; slot < 7; slot++) {
            int[] r = equipRect(slot);
            if (r == null || (slot == 4 || slot == 5) && !inCol(y)) {
                continue;
            }
            if (in(x, y, r[0], r[1], r[2], r[2])) {
                return Loc.equip(slot);
            }
        }
        for (int i = 0; i < 9; i++) {
            if (in(x, y, quX() + i * QU_STEP, QU_Y, C, C)) {
                return Loc.hand(i);
            }
        }
        for (Box b : boxes()) {
            if (b.id != GridId.POUCH && !inCol(y)) {
                continue;
            }
            if (x >= b.x && x < b.x + b.w * C && y >= b.y && y < b.y + b.h * C) {
                return Loc.grid(b.id, (int) ((x - b.x) / C), (int) ((y - b.y) / C));
            }
        }
        if (inCol(y)) {
            for (ColSec sec : colSections()) {
                if (sec.kind() != 0) {
                    continue;
                }
                int top = sec.y() + 11 - (int) colScroll;
                int n = ClientTetris.sync.unsorted().size();
                if (x >= secX() && x < secX() + 8 * C && y >= top) {
                    int i = (int) ((y - top) / C) * 8 + (int) ((x - secX()) / C);
                    if (i >= 0 && i < n && y < top + ((n + 7) / 8) * C) {
                        return Loc.unsorted(i);
                    }
                }
            }
        }
        return null;
    }

    private Box box(GridId id) {
        for (Box b : boxes()) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    private Placed placedAt(Loc loc) {
        if (loc != null && loc.kind() == Loc.CONTAINER && loc.b() >= 0) {
            List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
            if (loc.a() >= cs.size()) {
                return null;
            }
            TetrisNet.ContainerView c = cs.get(loc.a());
            return new Grid(c.width(), c.height(), c.items()).at(loc.b(), loc.c());
        }
        GridRef r = gridFor(loc);
        return r == null ? null : new Grid(r.w, r.h, r.items).at(loc.b(), loc.c());
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
                case 4 -> s.rig();
                case 5 -> s.backpack();
                case 6 -> inv.offhand.get(0);
                default -> inv.armor.get(3 - loc.a());
            };
            case Loc.GRID -> {
                Placed p = placedAt(loc);
                yield p == null ? ItemStack.EMPTY : p.stack;
            }
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
            case Loc.UNSORTED -> loc.a() < s.unsorted().size() ? s.unsorted().get(loc.a()) : ItemStack.EMPTY;
            case Loc.CRAFT -> loc.a() < ClientTetris.craft.grid().size() ? ClientTetris.craft.grid().get(loc.a()) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    // ================================================================== render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        settleSwap();
        boolean v2 = tab == INVENTORY && craftView == 0;
        if (v2) {
            // The world stays visible behind it, dimmed (GEAR-V2).
            layout();
            g.fill(0, 0, width, height, 0xC70A0908);
        } else {
            g.fill(0, 0, width, height, Kit.BACKDROP);
            int[] pr = panelRect();
            Kit.panel(g, pr[0], pr[1], pr[2], pr[3]);
        }
        if (tab == STATUS || tab == WEB) {
            g.pose().pushPose();
            g.pose().translate(0, TOP, 0);
            (tab == STATUS ? status : web).render(g, mouseX, mouseY - TOP, partialTick);
            g.pose().popPose();
        } else {
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            g.pose().scale(scale, scale, 1);
            int mx = (int) Math.floor(vx(mouseX));
            int my = (int) Math.floor(vy(mouseY));
            hoverRecipe = null;
            uiTip = null;
            ItemStack tip = tab == INVENTORY ? renderCrafting(g, mx, my) : ItemStack.EMPTY;
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
        }
        renderTabs(g, mouseX, mouseY);
    }

    /** Top of the panel; the tab row sits on its edge (Appendix C, spacing rules). */
    private static final int PANEL_TOP = 18;

    /** The window frame, in screen coordinates: around the scaled content, or the full width for STATUS and WEB. */
    private int[] panelRect() {
        if (tab == STATUS || tab == WEB) {
            return new int[] {2, PANEL_TOP, width - 4, height - PANEL_TOP - 2};
        }
        int x = Math.max(2, (int) Math.floor(ox - 8 * scale) - 2);
        int right = Math.min(width - 2, (int) Math.ceil(ox + vw() * scale + 8 * scale) + 2);
        int bottom = Math.min(height - (tab == CRAFTING ? 17 : 2), (int) Math.ceil(oy + H * scale + 8 * scale));
        return new int[] {x, PANEL_TOP, right - x, bottom - PANEL_TOP};
    }

    private int tabW(int t) {
        return font.width(TABS[t]) + 26;
    }

    private int tabX(int t) {
        int x = 4;
        for (int i = 0; i < t; i++) {
            x += tabW(i) + 2;
        }
        return x;
    }

    private boolean overTab(int t, double mouseX, double mouseY) {
        int x = tabX(t);
        return mouseX >= x && mouseX < x + tabW(t) && mouseY >= 1 && mouseY < 16;
    }

    private static final String[] TAB_ICONS = {"gear", "status", "skills"};

    /** The tab bar (GEAR-V2): each tab with its icon, a gold line under them, and where you are at the right. */
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 16, width, 17, Kit.GOLD_DARK);
        for (int t = 0; t < TABS.length; t++) {
            int x = tabX(t);
            int w = tabW(t);
            boolean active = t == tab;
            boolean over = !active && overTab(t, mouseX, mouseY);
            if (active) {
                Kit.blit(g, "tab/active", x, 1, w, 16);
            } else {
                Kit.blit(g, over ? "tab/inactive_hover" : "tab/inactive", x, 3, w, 13);
            }
            Kit.blit(g, "icon/tab/" + TAB_ICONS[t], x + 6, active ? 4 : 5, 9, 9);
            Kit.text(g, font, TABS[t], x + 18, active ? 5 : 6, active ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH);
        }
        int tabsEnd = tabX(TABS.length - 1) + tabW(TABS.length - 1) + 8;
        if (tab == INVENTORY) {
            // CRAFT toggle: crafting mode swaps the equipment zone for the crafting workspace.
            int[] cr = craftToggle();
            boolean over = in(mouseX, mouseY, cr[0], cr[1], cr[2], cr[3]);
            Kit.blit(g, craftMode || over ? "button/hover" : "button/normal", cr[0], cr[1], cr[2], cr[3]);
            if (craftMode) {
                g.fill(cr[0] + 2, cr[1] + cr[3] - 2, cr[0] + cr[2] - 2, cr[1] + cr[3] - 1, Kit.GOLD_LIGHT);
            }
            Kit.blit(g, "icon/tab/crafting", cr[0] + 4, cr[1] + 2, 9, 9);
            Kit.text(g, font, "CRAFT", cr[0] + 16, cr[1] + 3, craftMode ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH);
            tabsEnd = cr[0] + cr[2] + 8;
        }
        // Station row (inside a hideout), right-aligned; the open one is widened with its name.
        int right = width - 4;
        List<Object[]> stations = stationButtons();
        for (Object[] st : stations) {
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
        if (ClientTarkov.raid.state() == 1) {
            String t = "HIDEOUT";
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

    /** The CRAFT toggle's rectangle in screen coordinates. */
    private int[] craftToggle() {
        int x = tabX(TABS.length - 1) + tabW(TABS.length - 1) + 6;
        return new int[] {x, 2, font.width("CRAFT") + 20, 14};
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


    private void renderGrid(GuiGraphics g, Box b) {
        renderGrid(g, new GridRef(b.x, b.y, b.w, b.h, b.items));
    }

    private void renderGrid(GuiGraphics g, GridRef b) {
        boolean arrows = !held.isEmpty() && held.is(net.minecraft.tags.ItemTags.ARROWS) && arrowSource(b);
        for (int cy = 0; cy < b.h; cy++) {
            for (int cx = 0; cx < b.w; cx++) {
                int x = b.x + cx * C;
                int y = b.y + cy * C;
                boolean over = lastMx >= x && lastMx < x + C && lastMy >= y && lastMy < y + C;
                // While arrows are held, the grids a bow draws from get the green rim (3.3).
                Kit.slot(g, x, y, C, arrows ? Kit.SlotState.ARROW_SOURCE : over ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            }
        }
        Placed source = heldFrom != null && heldCount == 0 ? placedAt(heldFrom) : null;
        for (Placed p : b.items) {
            if (p == source) {
                continue;
            }
            drawPlaced(g, p.stack, b.x + p.x * C, b.y + p.y * C, p.width(), p.height(), p.rotated, 0);
        }
    }

    /** Bows draw arrows from the pockets, rig and backpack (not the pouch or open containers). */
    private boolean arrowSource(GridRef r) {
        for (Box b : boxes()) {
            if (b.x() == r.x() && b.y() == r.y()) {
                return b.id() != GridId.POUCH;
            }
        }
        return false;
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
            case 4 -> stack.getItem() instanceof GearItem g && g.spec.kind == com.cavmkii.seancraft.tetris.GearSpec.Kind.RIG;
            case 5 -> stack.getItem() instanceof GearItem g && g.spec.kind == com.cavmkii.seancraft.tetris.GearSpec.Kind.BACKPACK;
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
        if (gridFor(hover) == null) {
            // Off the grids, carry a plain icon so it doesn't hide the slot underneath.
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.renderItem(shown, mx - 8, my - 8);
            g.renderItemDecorations(font, shown, mx - 8, my - 8);
            g.pose().popPose();
            return;
        }
        int[] size = ItemSize.of(held);
        int w = heldRot ? size[1] : size[0];
        int h = heldRot ? size[0] : size[1];
        // Over a grid the held item snaps to the cells it would land in, so it and the highlight always line up.
        int drawX = mx - 9;
        int drawY = my - 9;
        if (gridFor(hover) != null) {
            GridRef b = gridFor(hover);
            if (b != null) {
                Placed self = heldCount == 0 ? placedAt(heldFrom) : null;
                Placed there = new Grid(b.w, b.h, b.items).at(hover.b(), hover.c());
                boolean merge = there != null && there != self && ItemStack.isSameItemSameComponents(there.stack, held);
                boolean ok = merge || new Grid(b.w, b.h, b.items).fits(w, h, hover.b(), hover.c(), self);
                int gx = b.x + hover.b() * C;
                int gy = b.y + hover.c() * C;
                g.pose().pushPose();
                g.pose().translate(0, 0, 150);
                g.fill(gx, gy, gx + (merge ? there.width() : w) * C - 1, gy + (merge ? there.height() : h) * C - 1, ok ? 0x60B08A3E : 0x608E1F1F);
                g.pose().popPose();
                drawX = merge ? b.x + there.x * C : gx;
                drawY = merge ? b.y + there.y * C : gy;
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // See-through, so the highlight and the cells under it still show.
        drawPlaced(g, shown, drawX, drawY, w, h, heldRot && size[0] != size[1], 0x400E0C0B, false);
        g.pose().popPose();
    }

    // ================================================================== INVENTORY layout (UI-SPEC Part 2, v3: 480 x 270 GUI pixels)

    private static final int VW = 480;
    private static final int VH = 270;
    /** Equipment boxes (40 x 30) for head, chest, legs, feet and off-hand; the rig and backpack sit by their grids. */
    private static final int[][] EQUIP_BOX = {{10, 23}, {10, 55}, {10, 87}, {10, 119}, null, null, {72, 119}};
    private static final String[] EQUIP_LABEL = {"HEAD", "CHEST", "LEGS", "FEET", "RIG", "BACKPACK", "OFF-HAND"};
    private static final int POUCH_Y = 165;
    private static final int POUCH_GRID_X = 32;
    private static final int ZONE_Y = 18;
    private static final int ZONE_H = 221;
    private static final int COL_TOP = 21;
    private static final int COL_BOTTOM = 237;
    private static final int QU_Y = 245;
    private static final int QU_STEP = 19;
    // Crafting mode: the crafting zone replaces the equipment zone.
    private static final int BOOK_X = 8;
    private static final int BOOK_W = 84;
    private static final int GX = 100;
    private static final int GRID_Y = 56;
    private static final int GC = 18;
    private static final int RESULT_X = 166;
    private static final int RESULT_Y = 70;
    private static final int BOOK_ROW = 17;
    private static final int BOOK_HEAD = 11;
    private static final int BOOK_TOP = 49;

    /** CRAFT toggle: crafting mode replaces the equipment zone with the crafting workspace. */
    private boolean craftMode;
    /** The station this screen was opened from (crafting table), shown widened in the station row; null for none. */
    private String openStation;
    private static boolean canMakeOnly;
    private double colScroll;
    private double storageScroll;
    private double bookScroll;
    /** Storage tab: -1 for ALL, else a container index. */
    private int storageTab = -1;
    private String storageSearch = "";
    private boolean storageSearchFocused;
    /** Tooltip for whatever small control is under the mouse this frame. */
    private String uiTip;

    // Zone geometry, which moves when crafting mode widens the left zone.
    private int cx() {
        return craftMode ? 208 : 122;
    }

    private int secX() {
        return cx() + 4;
    }

    private int secGridX() {
        return cx() + 26;
    }

    private int sx() {
        return craftMode ? 370 : 284;
    }

    private int sw() {
        return craftMode ? 106 : 192;
    }

    private int scols() {
        return craftMode ? 5 : 10;
    }

    private int quX() {
        return craftMode ? 248 : 200;
    }

    private static final int SW_ROWS = 8;

    private static boolean in(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }

    private static boolean inCol(double y) {
        return y >= COL_TOP && y < COL_BOTTOM;
    }

    /** A zone background (UI-SPEC: tiled column texture, 1 px Iron dark border, Iron top inner line). */
    private void zone(GuiGraphics g, int x, int y, int w, int h) {
        Kit.blit(g, "panel/column_texture", x + 1, y + 1, w - 2, h - 2);
        g.fill(x, y, x + w, y + 1, Kit.IRON_DARK);
        g.fill(x, y + h - 1, x + w, y + h, Kit.IRON_DARK);
        g.fill(x, y, x + 1, y + h, Kit.IRON_DARK);
        g.fill(x + w - 1, y, x + w, y + h, Kit.IRON_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Kit.IRON);
    }

    private void small(GuiGraphics g, String text, float x, float y, int color, float k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** An icon-only button (button_small/name_*); hovering it sets the tooltip. */
    private void iconButton(GuiGraphics g, String name, int x, int y, int size, boolean enabled, int mx, int my, String tip) {
        boolean over = in(mx, my, x, y, size, size);
        Kit.blit(g, "button_small/" + name + (!enabled ? "_disabled" : over ? "_hover" : "_normal"), x, y, size, size);
        if (over && tip != null) {
            uiTip = tip;
        }
    }

    // ------------------------------------------------------------------ carried zone

    /** One of the carried zone's sections: 0 unsorted, 1 rig, 2 pockets, 3 backpack. y is before scrolling. */
    private record ColSec(int kind, String title, int y, int h) {}

    private List<ColSec> colSections() {
        TetrisNet.Sync s = ClientTetris.sync;
        List<ColSec> out = new ArrayList<>();
        int y = COL_TOP;
        if (!s.unsorted().isEmpty()) {
            int h = 11 + ((s.unsorted().size() + 7) / 8) * C;
            out.add(new ColSec(0, "UNSORTED (NO ROOM)", y, h));
            y += h + 2;
        }
        int rigW = s.rig().getItem() instanceof GearItem g ? g.spec.gridWidth : 0;
        int rigH = s.rig().getItem() instanceof GearItem g ? g.spec.gridHeight : 0;
        String rig = s.rig().isEmpty() ? "RIG" : s.rig().getHoverName().getString().toUpperCase(Locale.ROOT) + "  " + rigW + "X" + rigH;
        out.add(new ColSec(1, rig, y, 11 + Math.max(1, rigH) * C));
        y += 11 + Math.max(1, rigH) * C + 2;
        out.add(new ColSec(2, "POCKETS  " + InvData.POCKET_W + "X" + InvData.POCKET_H, y, 11 + InvData.POCKET_H * C));
        y += 11 + InvData.POCKET_H * C + 2;
        int packW = s.backpack().getItem() instanceof GearItem g ? g.spec.gridWidth : 0;
        int packH = s.backpack().getItem() instanceof GearItem g ? g.spec.gridHeight : 0;
        String pack = s.backpack().isEmpty() ? "BACKPACK" : s.backpack().getHoverName().getString().toUpperCase(Locale.ROOT) + "  " + packW + "X" + packH;
        out.add(new ColSec(3, pack, y, 11 + Math.max(1, packH) * C));
        return out;
    }

    private int colContent() {
        List<ColSec> secs = colSections();
        ColSec last = secs.get(secs.size() - 1);
        return last.y + last.h - COL_TOP;
    }

    // ------------------------------------------------------------------ storage zone

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

    private int storageGridY() {
        return ZONE_Y + 47;
    }

    private SCell storageAt(double x, double y) {
        int gx = sx() + 5;
        int gy = storageGridY();
        if (x < gx || x >= gx + scols() * C || y < gy || y >= gy + SW_ROWS * C) {
            return null;
        }
        int i = ((int) ((y - gy + storageScroll) / C)) * scols() + (int) ((x - gx) / C);
        List<SCell> cells = storageCells();
        return i >= 0 && i < cells.size() ? cells.get(i) : null;
    }

    /** True if a point is in the storage grid area (dropping there puts things away). */
    private boolean overStorageGrid(double x, double y) {
        return anyStorage() && in(x, y, sx() + 5, storageGridY(), scols() * C, SW_ROWS * C);
    }

    /** Storage tab rectangles: x, width for ALL (index -1) and each container. */
    private List<int[]> storageTabs() {
        List<int[]> out = new ArrayList<>();
        List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
        if (!ClientTetris.sync.stash()) {
            return out;
        }
        int avail = sw() - 10;
        int n = cs.size() + 1;
        int full = font.width("ALL") + 10;
        for (TetrisNet.ContainerView c : cs) {
            full += font.width(c.name().toUpperCase(Locale.ROOT)) + 11;
        }
        float k = full <= avail ? 1f : avail / (float) full;
        int x = sx() + 5;
        for (int i = -1; i < cs.size(); i++) {
            String label = i < 0 ? "ALL" : cs.get(i).name().toUpperCase(Locale.ROOT);
            int w = Math.max(14, (int) ((font.width(label) + 10) * k));
            out.add(new int[] {i, x, w});
            x += w + 1;
        }
        return out;
    }

    private void renderStorage(GuiGraphics g, int mx, int my) {
        TetrisNet.Sync s = ClientTetris.sync;
        int x0 = sx();
        int w = sw();
        zone(g, x0, ZONE_Y, w, ZONE_H);
        List<TetrisNet.ContainerView> cs = s.containers();
        if (!anyStorage()) {
            g.drawWordWrap(font, Component.literal("Open a chest or barrel to see it here. Inside a hideout, the storage shows here."),
                    x0 + 6, ZONE_Y + 8, w - 12, Kit.ASH);
            return;
        }
        // Header: icon and title; buttons right to left: filter, sort, STASH ALL, TAKE ALL (crafting mode: filter, sort).
        boolean hideout = s.stash();
        Kit.blit(g, "icon/tab/stash", x0 + 5, ZONE_Y + 4, 9, 9);
        String title = craftMode ? "STORAGE" : hideout ? "HIDEOUT STORAGE" : cs.get(0).name().toUpperCase(Locale.ROOT);
        Kit.text(g, font, font.plainSubstrByWidth(title, w - (craftMode ? 50 : 80)), x0 + 17, ZONE_Y + 5, Kit.BONE);
        int bx = x0 + w - 16;
        boolean chestTab = storageTab >= 0 || !hideout;
        iconButton(g, "filter", bx, ZONE_Y + 3, 12, chestTab, mx, my, chestTab ? "Filter: what this chest takes" : "Pick a chest's tab to set its filter");
        int fi = storageTab >= 0 ? storageTab : 0;
        if (chestTab && fi < cs.size() && cs.get(fi).filter() != com.cavmkii.seancraft.tetris.StorageCategory.ALL) {
            g.renderOutline(bx - 1, ZONE_Y + 2, 14, 14, Kit.GOLD);
        }
        iconButton(g, "sort", bx - 14, ZONE_Y + 3, 12, true, mx, my, chestTab ? "Sort this chest" : "Organize: merge and sort across the storage");
        if (!craftMode) {
            iconButton(g, "stash_all", bx - 28, ZONE_Y + 3, 12, hideout, mx, my, hideout ? "Stash all: your pockets, rig and backpack" : "Stash all: inside a hideout only");
            iconButton(g, "take_all", bx - 42, ZONE_Y + 3, 12, !cs.isEmpty(), mx, my, chestTab ? "Take all from this chest" : "Take all from every chest");
        }
        // Search field.
        int fy = ZONE_Y + 18;
        Kit.blit(g, storageSearchFocused ? "text_field/focused" : "text_field/normal", x0 + 5, fy, w - 10, 12);
        Kit.blit(g, "icon/hud/search", x0 + 7, fy + 2, 9, 9);
        String shown = storageSearch.isEmpty() && !storageSearchFocused ? "SEARCH" : storageSearch + (storageSearchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        small(g, font.plainSubstrByWidth(shown, (int) ((w - 30) / 0.75f), true), x0 + 19, fy + 3, storageSearch.isEmpty() && !storageSearchFocused ? Kit.ASH : Kit.BONE, 0.75f);
        // Tabs: ALL, then one per chest or barrel.
        int ty = ZONE_Y + 33;
        for (int[] t : storageTabs()) {
            boolean active = t[0] == storageTab;
            boolean over = !active && in(mx, my, t[1], ty, t[2], 13);
            if (active) {
                Kit.blit(g, "tab/active", t[1], ty, t[2], 14);
            } else {
                Kit.blit(g, over ? "tab/inactive_hover" : "tab/inactive", t[1], ty + 2, t[2], 12);
            }
            String label = t[0] < 0 ? "ALL" : cs.get(t[0]).name().toUpperCase(Locale.ROOT);
            if (renameIndex == t[0] && t[0] >= 0) {
                label = renameText + (ticks / 6 % 2 == 0 ? "_" : "");
            }
            float k = Math.min(0.75f, (t[2] - 6) / (float) Math.max(1, font.width(label)));
            small(g, label, t[1] + 5, ty + 5, active ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH, k);
            if (over && t[0] >= 0) {
                uiTip = "Right-click to rename";
            }
        }
        // The grid: stacks in reading order, scrolling.
        List<SCell> cells = storageCells();
        int gx = x0 + 5;
        int gy = storageGridY();
        int rows = Math.max(SW_ROWS, (cells.size() + scols() - 1) / scols());
        double max = Math.max(0, (rows - SW_ROWS) * C);
        storageScroll = Math.max(0, Math.min(max, storageScroll));
        g.enableScissor((int) (ox + gx * scale), (int) (oy + gy * scale), (int) (ox + (gx + scols() * C) * scale), (int) (oy + (gy + SW_ROWS * C) * scale));
        boolean dropHere = !held.isEmpty() && overStorageGrid(mx, my);
        for (int i = 0; i < rows * scols(); i++) {
            int cxx = gx + (i % scols()) * C;
            int cyy = gy + (i / scols()) * C - (int) storageScroll;
            if (cyy + C < gy || cyy > gy + SW_ROWS * C) {
                continue;
            }
            boolean over = in(mx, my, cxx, cyy, C, C) && in(mx, my, gx, gy, scols() * C, SW_ROWS * C);
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
            g.renderOutline(gx - 1, gy - 1, scols() * C + 2, SW_ROWS * C + 2, Kit.GOLD_LIGHT);
        }
        if (max > 0) {
            int span = SW_ROWS * C;
            int barH = Math.max(10, (int) (span * (double) span / (span + max)));
            Kit.blit(g, "scroll/handle", gx + scols() * C + 1, gy + (int) ((span - barH) * storageScroll / max), 3, barH);
        }
        // Slot count.
        int used = 0;
        int total = 0;
        for (int i = 0; i < cs.size(); i++) {
            if (storageTab >= 0 && i != storageTab) {
                continue;
            }
            used += cs.get(i).items().size() + cs.get(i).overflow().size();
            total += cs.get(i).width() * cs.get(i).height();
        }
        small(g, used + " / " + total + " SLOTS", gx, gy + SW_ROWS * C + 3, Kit.ASH, 0.75f);
    }

    // ------------------------------------------------------------------ equipment zone

    /** The secure pouch's own item, from its size. */
    private static ItemStack pouchItem() {
        for (com.cavmkii.seancraft.tarkov.SecureTier t : com.cavmkii.seancraft.tarkov.SecureTier.values()) {
            if (t.width == ClientTetris.sync.pouchW() && t.height == ClientTetris.sync.pouchH()) {
                return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        com.cavmkii.seancraft.SeanCraft.id("secure_" + t.name().toLowerCase(Locale.ROOT))));
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack renderEquipment(GuiGraphics g, int mx, int my, Loc hover) {
        ItemStack tip = ItemStack.EMPTY;
        zone(g, 4, ZONE_Y, 114, ZONE_H);
        Kit.blit(g, "gear/silhouette", 54, 28, 56, 118);
        for (int slot = 0; slot < 7; slot++) {
            if (EQUIP_BOX[slot] == null) {
                continue;
            }
            int bx = EQUIP_BOX[slot][0];
            int by = EQUIP_BOX[slot][1];
            Kit.blit(g, "panel/equip_box", bx, by, 40, 30);
            float k = Math.min(0.75f, 34f / font.width(EQUIP_LABEL[slot]));
            small(g, EQUIP_LABEL[slot], bx + 3, by + 3, Kit.ASH, k);
            equipSlot(g, slot, hover);
        }
        ItemStack pi = pouchItem();
        Kit.blit(g, "panel/label", 10, 154, 102, 9);
        String tier = (pi.isEmpty() ? "SECURE POUCH" : pi.getHoverName().getString().toUpperCase(Locale.ROOT)) + "  KEPT";
        small(g, tier, 13, 155.5f, Kit.GOLD, Math.min(0.75f, 94f / font.width(tier)));
        Kit.slot(g, 10, POUCH_Y, C, Kit.SlotState.NORMAL);
        if (!pi.isEmpty()) {
            g.renderItem(pi, 11, POUCH_Y + 1);
            if (in(mx, my, 10, POUCH_Y, C, C)) {
                tip = pi;
            }
        }
        Box pouch = box(GridId.POUCH);
        if (pouch != null) {
            renderGrid(g, pouch);
        }
        // Load and roll, then health, stamina, and Rage or mana if the build uses them.
        com.cavmkii.seancraft.combat.Load.State load = com.cavmkii.seancraft.client.combat.ClientCombat.load();
        com.cavmkii.seancraft.combat.Load.Tier lt = load.tier();
        Kit.blit(g, "icon/tab/load", 10, 206, 9, 9);
        int lc = lt == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED ? Kit.BLOOD_LIGHT : lt == com.cavmkii.seancraft.combat.Load.Tier.HEAVY ? Kit.EMBER : Kit.BONE;
        small(g, "LOAD " + Math.round(load.fraction() * 100) + "%  " + lt.label, 22, 207.5f, lc, 0.75f);
        String pen = (lt.walk != 0 ? "WALK " + Math.round(lt.walk * 100) + "%  " : "") + (lt.rollTicks == 0 ? "NO ROLL" : String.format("ROLL %.1f S", lt.rollTicks / 20f));
        small(g, pen, 22, 216.5f, Kit.ASH, 0.75f);
        Kit.blit(g, "icon/filter/meds", 10, 227, 9, 9);
        String hp = Math.round(minecraft.player.getHealth()) + "/" + Math.round(minecraft.player.getMaxHealth());
        small(g, hp, 21, 228.5f, Kit.BLOOD_LIGHT, 0.75f);
        com.cavmkii.seancraft.rpg.Stats st = com.cavmkii.seancraft.client.rpg.ClientRpg.stats;
        float x = stat(g, 52, "stamina", com.cavmkii.seancraft.client.rpg.ClientRpg.stamina,
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
        if (used.contains(com.cavmkii.seancraft.rpg.Resource.MANA) && x < 100) {
            stat(g, x, "mana", com.cavmkii.seancraft.client.rpg.ClientRpg.mana, st.maxMana(), Kit.ARCANE);
        } else if (used.contains(com.cavmkii.seancraft.rpg.Resource.RAGE) && x < 100) {
            stat(g, x, "rage", com.cavmkii.seancraft.client.rpg.ClientRpg.rage, st.maxRage(), Kit.EMBER);
        }
        return tip;
    }

    private float stat(GuiGraphics g, float x, String icon, float value, float max, int color) {
        g.pose().pushPose();
        g.pose().translate(x, 227, 0);
        Kit.blit(g, "icon/resource/" + icon, 0, 0, 9, 9);
        g.pose().popPose();
        String t = Math.round(value) + "/" + Math.round(max);
        small(g, t, x + 10, 228.5f, color, 0.75f);
        return x + 10 + font.width(t) * 0.75f + 4;
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

    // ------------------------------------------------------------------ crafting zone

    /** A recipe-book row: a category header, or a recipe (known or not). */
    private record BookRow(com.cavmkii.seancraft.tetris.StorageCategory header, RecipeCell cell, int y, int h) {}

    /** Times you can make a recipe now (from the server's list); 0 if missing ingredients, -1 if it needs a crafting table. */
    private int makeable(String id) {
        for (TetrisNet.CraftEntry e : ClientTetris.craft.entries()) {
            if (e.id().equals(id)) {
                return e.times();
            }
        }
        return 0;
    }

    private List<BookRow> bookRows() {
        List<BookRow> out = new ArrayList<>();
        int y = 0;
        String q = search.toLowerCase(Locale.ROOT);
        for (RecipeSection sec : recipeSections()) {
            List<RecipeCell> cells = new ArrayList<>();
            for (RecipeCell c : sec.cells()) {
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

    private ItemStack renderCraftZone(GuiGraphics g, int mx, int my) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        ItemStack tip = ItemStack.EMPTY;
        zone(g, 4, ZONE_Y, 200, ZONE_H);
        // Recipe book: search, CAN MAKE NOW, categories and rows.
        Kit.blit(g, searchFocused ? "text_field/focused" : "text_field/normal", BOOK_X, 22, BOOK_W, 12);
        Kit.blit(g, "icon/hud/search", BOOK_X + 2, 24, 9, 9);
        String shown = search.isEmpty() && !searchFocused ? "SEARCH" : search + (searchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        small(g, font.plainSubstrByWidth(shown, (int) ((BOOK_W - 16) / 0.75f), true), BOOK_X + 13, 25, search.isEmpty() && !searchFocused ? Kit.ASH : Kit.BONE, 0.75f);
        Kit.blit(g, canMakeOnly ? "checkbox/on" : "checkbox/off", BOOK_X, 37, 10, 10);
        small(g, "CAN MAKE NOW", BOOK_X + 13, 39, in(mx, my, BOOK_X, 37, BOOK_W, 10) ? Kit.GOLD_LIGHT : Kit.BONE, 0.75f);
        List<BookRow> rows = bookRows();
        int bottom = ZONE_Y + ZONE_H - 3;
        int total = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).h;
        double max = Math.max(0, total - (bottom - BOOK_TOP));
        bookScroll = Math.max(0, Math.min(max, bookScroll));
        g.enableScissor((int) (ox + BOOK_X * scale), (int) (oy + BOOK_TOP * scale), (int) (ox + (BOOK_X + BOOK_W) * scale), (int) (oy + bottom * scale));
        for (BookRow r : rows) {
            int y = BOOK_TOP + r.y - (int) bookScroll;
            if (y + r.h < BOOK_TOP || y > bottom) {
                continue;
            }
            boolean over = in(mx, my, BOOK_X, y, BOOK_W, r.h - 1) && my >= BOOK_TOP && my < bottom;
            if (r.header != null) {
                boolean open = !COLLAPSED.contains(r.header) || !search.isEmpty();
                Kit.blit(g, open ? "list/category_open" : "list/category_closed", BOOK_X + 1, y + 2, 7, 7);
                small(g, r.header.label.toUpperCase(Locale.ROOT), BOOK_X + 11, y + 2.5f, open ? Kit.GOLD_LIGHT : over ? Kit.BONE : Kit.ASH, 0.75f);
                continue;
            }
            RecipeCell c = r.cell;
            String id = c.holder().id().toString();
            boolean selected = id.equals(ghostRecipe);
            if (!c.known()) {
                Kit.blit(g, "list/row_locked", BOOK_X, y, BOOK_W, 16);
                small(g, "??????", BOOK_X + 20, y + 5, Kit.DISABLED, 0.75f);
                continue;
            }
            int can = makeable(id);
            Kit.blit(g, selected ? "list/row_selected" : over ? "list/row_hover" : "list/row", BOOK_X, y, BOOK_W, 16);
            g.renderItem(c.result(), BOOK_X + 1, y);
            String name = font.plainSubstrByWidth(c.result().getHoverName().getString(), (int) ((BOOK_W - 32) / 0.75f));
            small(g, name, BOOK_X + 19, y + 5, can > 0 ? (over || selected ? Kit.GOLD_LIGHT : Kit.BONE) : Kit.ASH, 0.75f);
            if (can > 0) {
                Kit.blit(g, "icon/hud/gather_check", BOOK_X + BOOK_W - 11, y + 3, 9, 9);
            }
            if (over) {
                tip = c.result();
                hoverRecipe = id;
            }
        }
        g.disableScissor();
        if (max > 0) {
            int span = bottom - BOOK_TOP;
            int barH = Math.max(10, (int) (span * (double) span / (span + max)));
            Kit.blit(g, "scroll/handle", BOOK_X + BOOK_W + 1, BOOK_TOP + (int) ((span - barH) * bookScroll / max), 3, barH);
        }

        // Selected recipe: name, what it is and its gate; then the grid, arrow and result.
        String sel = shownHover != null ? shownHover : ghostRecipe;
        var recipe = recipeFor(sel);
        Kit.blit(g, "panel/label", 98, 22, 100, 9);
        if (recipe != null) {
            ItemStack out = recipe.getResultItem(minecraft.level.registryAccess());
            small(g, out.getHoverName().getString().toUpperCase(Locale.ROOT), 101, 23.5f, Kit.GOLD_LIGHT, Math.min(0.75f, 94f / font.width(out.getHoverName().getString())));
            boolean big = recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe sh && (sh.getWidth() > 2 || sh.getHeight() > 2)
                    || !(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe) && recipe.getIngredients().size() > 4;
            small(g, out.getCount() > 1 ? "Makes " + out.getCount() : "Makes 1", 98, 34, Kit.ASH, 0.75f);
            small(g, big ? "3x3: crafting table or hideout" : "2x2: anywhere", 98, 42, Kit.ASH, 0.75f);
        } else {
            small(g, "PICK A RECIPE", 101, 23.5f, Kit.ASH, 0.75f);
            small(g, "Its pattern shows in the grid;", 98, 34, Kit.ASH, 0.75f);
            small(g, "place the items by hand.", 98, 42, Kit.ASH, 0.75f);
        }
        if (!craft.result().isEmpty()) {
            ghostRecipe = null;
        }
        ItemStack[] ghostAt = ghostCells();
        for (int c = 0; c < 9; c++) {
            int x = GX + (c % 3) * GC;
            int y = GRID_Y + (c / 3) * GC;
            boolean over = cellAt(mx, my) == c || dragging(Loc.craft(c));
            boolean locked = !craft.table() && (c % 3 == 2 || c / 3 == 2);
            ItemStack in = c < craft.grid().size() ? craft.grid().get(c) : ItemStack.EMPTY;
            if (in.isEmpty() && ghostAt[c] != null && !ghostAt[c].isEmpty()) {
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
        Kit.blit(g, craft.result().isEmpty() ? "progress/arrow_empty" : "progress/arrow_full", 156 - 2, 76, 10, 15);
        boolean resultHover = overResult(mx, my);
        Kit.blit(g, craft.result().isEmpty() ? "slot/result" : "slot/result_ready", RESULT_X, RESULT_Y, 26, 26);
        if (resultHover && !craft.result().isEmpty()) {
            g.renderOutline(RESULT_X, RESULT_Y, 26, 26, Kit.GOLD_LIGHT);
        }
        if (!craft.result().isEmpty()) {
            g.renderItem(craft.result(), RESULT_X + 5, RESULT_Y + 5);
            g.renderItemDecorations(font, craft.result(), RESULT_X + 5, RESULT_Y + 5);
            if (resultHover) {
                tip = craft.result();
            }
        }
        boolean clearHover = in(mx, my, RESULT_X, RESULT_Y + 29, 26, 10);
        Kit.smallButton(g, font, "CLEAR", RESULT_X, RESULT_Y + 29, 26, 10, true, clearHover, false, 0.625f);
        if (clearHover) {
            uiTip = "Put everything in the grid back";
        }

        // Ingredients: have / need and where each one is.
        Kit.blit(g, "panel/label", 98, 116, 100, 9);
        small(g, "INGREDIENTS", 101, 117.5f, Kit.ASH, 0.75f);
        if (recipe != null) {
            int y = 128;
            for (IngredientLine line : ingredientsOf(recipe)) {
                if (y > 166) {
                    break;
                }
                Kit.slot(g, 98, y, C, Kit.SlotState.NORMAL);
                g.renderItem(line.display(), 99, y + 1);
                int have = haveFor(line.ingredient());
                boolean enough = have >= line.need();
                small(g, font.plainSubstrByWidth(line.display().getHoverName().getString().toUpperCase(Locale.ROOT), 90), 119, y + 2, Kit.BONE, 0.75f);
                small(g, where(line.ingredient()), 119, y + 10, Kit.ASH, 0.625f);
                String hn = have + " / " + line.need();
                int hw = (int) (font.width(hn) * 0.75f);
                if (!enough) {
                    Kit.blit(g, "icon/hud/missing", 196 - hw - 11, y + 2, 9, 9);
                }
                small(g, hn, 196 - hw, y + 3, enough ? Kit.MOSS : Kit.BLOOD_LIGHT, 0.75f);
                y += 18;
            }
        }
        small(g, "CLICK RESULT: 1   SHIFT: ALL", 98, 184, Kit.ASH, 0.625f);
        boolean table = craft.table();
        Kit.blit(g, table ? "icon/hud/gather_check" : "icon/hud/missing", 98, 194, 9, 9);
        small(g, table ? (craft.inHideout() ? "HIDEOUT: 3X3" : "CRAFTING TABLE: 3X3") : "2X2 HERE: 3X3 AT A TABLE", 109, 195.5f, table ? Kit.MOSS : Kit.ASH, 0.625f);
        if (ghostRecipe != null) {
            boolean pinHover = in(mx, my, 98, 206, 58, 10);
            Kit.smallButton(g, font, "PIN TO HUD", 98, 206, 58, 10, true, pinHover, false, 0.625f);
            if (pinHover) {
                uiTip = "Put this recipe's ingredients on your gather list";
            }
        }
        if (!craft.inHideout()) {
            small(g, "No hideout here: the Hideout Bench", 98, 222, Kit.GOLD_LIGHT, 0.625f);
            small(g, "is in the book under its category.", 98, 229, Kit.GOLD_LIGHT, 0.625f);
        }
        return tip;
    }

    private net.minecraft.world.item.crafting.CraftingRecipe recipeFor(String id) {
        var key = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = key == null ? null : minecraft.level.getRecipeManager().byKey(key).orElse(null);
        return holder != null && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe r ? r : null;
    }

    /** Where an ingredient is: the first place you have it. */
    private String where(net.minecraft.world.item.crafting.Ingredient ing) {
        TetrisNet.Sync s = ClientTetris.sync;
        for (int i = 0; i < 9; i++) {
            if (ing.test(minecraft.player.getInventory().items.get(i))) {
                return "IN QUICK USE";
            }
        }
        String[] names = {"IN POCKETS", "IN RIG", "IN BACKPACK", "IN POUCH"};
        List<List<Placed>> lists = List.of(s.pockets(), s.rigItems(), s.backpackItems(), s.pouch());
        for (int k = 0; k < lists.size(); k++) {
            for (Placed p : lists.get(k)) {
                if (ing.test(p.stack)) {
                    return names[k];
                }
            }
        }
        for (TetrisNet.ContainerView c : s.containers()) {
            for (Placed p : c.items()) {
                if (ing.test(p.stack)) {
                    return "IN STORAGE";
                }
            }
        }
        return "NOT CARRIED";
    }

    // ------------------------------------------------------------------ the whole tab

    private ItemStack renderInventory(GuiGraphics g, int mx, int my) {
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

        ItemStack left = craftMode ? renderCraftZone(g, mx, my) : renderEquipment(g, mx, my, hover);
        if (!left.isEmpty()) {
            tip = left;
        }

        // Carried zone: rig, pockets, backpack, each with its own item beside its grid.
        int cxx = cx();
        zone(g, cxx, ZONE_Y, 158, ZONE_H);
        colScroll = Math.max(0, Math.min(Math.max(0, colContent() - (COL_BOTTOM - COL_TOP)), colScroll));
        g.enableScissor((int) (ox + cxx * scale), (int) (oy + COL_TOP * scale), (int) (ox + (cxx + 158) * scale), (int) (oy + COL_BOTTOM * scale));
        boolean sortShown = false;
        for (ColSec sec : colSections()) {
            int y = sec.y - (int) colScroll;
            Kit.blit(g, "panel/label", secX(), y, 150, 9);
            small(g, sec.title, secX() + 3, y + 1.5f, sec.kind == 0 ? Kit.BLOOD_LIGHT : Kit.ASH, 0.75f);
            switch (sec.kind) {
                case 0 -> {
                    boolean over = in(mx, my, secX() + 150 - 28, y, 28, 9) && inCol(my);
                    Kit.smallButton(g, font, "DROP", secX() + 150 - 28, y, 28, 9, true, over, false, 0.625f);
                    if (over) {
                        uiTip = "Drop everything that has no room";
                    }
                    List<ItemStack> list = s.unsorted();
                    for (int i = 0; i < list.size(); i++) {
                        Loc loc = Loc.unsorted(i);
                        slot(g, secX() + (i % 8) * C, y + 11 + (i / 8) * C, C, list.get(i), hover != null && hover.equals(loc), null);
                    }
                }
                case 1, 3 -> {
                    equipSlot(g, sec.kind == 1 ? 4 : 5, hover);
                    boolean none = sec.kind == 1 ? s.rig().isEmpty() : s.backpack().isEmpty();
                    if (none) {
                        small(g, sec.kind == 1 ? "no rig" : "no backpack", secGridX(), y + 16, Kit.DISABLED, 0.75f);
                    }
                }
                default -> { }
            }
            if (sec.kind != 0 && !sortShown) {
                sortShown = true;
                iconButton(g, "sort", secX() + 150 - 10, y, 9, true, inCol(my) ? mx : -1, my, "Sort your grids");
            }
        }
        for (Box b : boxes()) {
            if (b.id() != GridId.POUCH) {
                renderGrid(g, b);
            }
        }
        g.disableScissor();
        double cmax = colContent() - (COL_BOTTOM - COL_TOP);
        if (cmax > 0) {
            int span = COL_BOTTOM - COL_TOP;
            int barH = Math.max(10, (int) (span * (double) span / colContent()));
            Kit.blit(g, "scroll/handle", cxx + 158 - 4, COL_TOP + (int) ((span - barH) * colScroll / cmax), 3, barH);
        }

        renderStorage(g, mx, my);

        // Quick-use bar, with the auto-refill toggles after it.
        small(g, "QUICK USE", quX() - 40, 250, Kit.ASH, 0.75f);
        int selected = minecraft.player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = quX() + i * QU_STEP;
            boolean over = hover != null && hover.equals(Loc.hand(i)) || dragging(Loc.hand(i));
            slot(g, x, QU_Y, C, stackAt(Loc.hand(i)), over || i == selected, null);
            Kit.blit(g, "slot/key_badge", x, QU_Y, 6, 7);
            small(g, String.valueOf(i + 1), x + 1.5f, QU_Y + 1.5f, i == selected ? Kit.GOLD_LIGHT : Kit.BONE, 0.5f);
        }
        int tx = quX() + 9 * QU_STEP + 4;
        iconButton(g, "refill", tx, QU_Y + 3, 12, true, mx, my, "Auto-refill the quick-use bar: " + (s.autoRefill() ? "ON" : "OFF"));
        if (s.autoRefill()) {
            g.renderOutline(tx - 1, QU_Y + 2, 14, 14, Kit.GOLD);
        }
        boolean overStash = in(mx, my, tx + 16, QU_Y + 3, 12, 12);
        Kit.buttonBg(g, tx + 16, QU_Y + 3, 12, 12, true, overStash, s.refillFromStash());
        Kit.blit(g, "icon/tab/stash", tx + 17, QU_Y + 4, 9, 9);
        if (overStash) {
            uiTip = "Refill from the stash too: " + (s.refillFromStash() ? "ON" : "OFF");
        }

        renderFilter(g, mx, my);
        renderHeld(g, mx, my, hover);
        return tip;
    }

    /** True if a point is on one of the zones or the quick-use bar (a click anywhere else with something held drops it). */
    private boolean onPanel(double x, double y) {
        return in(x, y, 4, ZONE_Y, craftMode ? 200 : 114, ZONE_H) || in(x, y, cx(), ZONE_Y, 158, ZONE_H) || in(x, y, sx(), ZONE_Y, sw(), ZONE_H)
                || in(x, y, 0, 240, VW, 30) || y < 18;
    }

    /** Clicks on the small controls: book, CAN MAKE NOW, CLEAR, PIN, storage header, tabs, search, toggles. */
    private boolean clickControls(double x, double y, int button) {
        TetrisNet.Sync s = ClientTetris.sync;
        List<TetrisNet.ContainerView> cs = s.containers();
        // Storage tabs (right-click renames).
        for (int[] t : storageTabs()) {
            if (in(x, y, t[1], ZONE_Y + 33, t[2], 14)) {
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
        storageSearchFocused = anyStorage() && in(x, y, sx() + 5, ZONE_Y + 18, sw() - 10, 12);
        if (storageSearchFocused) {
            searchFocused = false;
            return true;
        }
        if (craftMode) {
            searchFocused = in(x, y, BOOK_X, 22, BOOK_W, 12);
            if (searchFocused) {
                return true;
            }
            if (in(x, y, BOOK_X, 37, BOOK_W, 10)) {
                canMakeOnly = !canMakeOnly;
                bookScroll = 0;
                return true;
            }
            if (in(x, y, RESULT_X, RESULT_Y + 29, 26, 10)) {
                dropHeld();
                ClientPlayNetworking.send(new TetrisNet.Craft("#clear", 0));
                return true;
            }
            if (ghostRecipe != null && in(x, y, 98, 206, 58, 10)) {
                pinRecipe(ghostRecipe);
                return true;
            }
            int bottom = ZONE_Y + ZONE_H - 3;
            if (held.isEmpty() && in(x, y, BOOK_X, BOOK_TOP, BOOK_W, bottom - BOOK_TOP)) {
                double at = y - BOOK_TOP + bookScroll;
                for (BookRow r : bookRows()) {
                    if (at >= r.y && at < r.y + r.h) {
                        if (r.header != null) {
                            if (!COLLAPSED.remove(r.header)) {
                                COLLAPSED.add(r.header);
                            }
                        } else if (r.cell.known()) {
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
        int tx = quX() + 9 * QU_STEP + 4;
        if (in(x, y, tx, QU_Y + 3, 12, 12)) {
            ClientPlayNetworking.send(new TetrisNet.Action(6));
            return true;
        }
        if (in(x, y, tx + 16, QU_Y + 3, 12, 12)) {
            ClientPlayNetworking.send(new TetrisNet.Action(7));
            return true;
        }
        if (inCol(y)) {
            boolean sortDone = false;
            for (ColSec sec : colSections()) {
                int sy = sec.y - (int) colScroll;
                if (sec.kind == 0 && in(x, y, secX() + 150 - 28, sy, 28, 9)) {
                    ClientPlayNetworking.send(new TetrisNet.Action(2));
                    return true;
                }
                if (sec.kind != 0 && !sortDone) {
                    sortDone = true;
                    if (in(x, y, secX() + 150 - 10, sy, 9, 9)) {
                        ClientPlayNetworking.send(new TetrisNet.Action(0));
                        return true;
                    }
                }
            }
        }
        if (anyStorage()) {
            boolean hideout = s.stash();
            boolean chestTab = storageTab >= 0 || !hideout;
            int chest = storageTab >= 0 ? storageTab : 0;
            int bx = sx() + sw() - 16;
            if (in(x, y, bx, ZONE_Y + 3, 12, 12)) {
                if (chestTab && chest < cs.size()) {
                    filterOpen = filterOpen == chest ? -1 : chest;
                    filterX = sx() + sw() - FILTER_W - 4;
                    filterY = ZONE_Y + 17;
                }
                return true;
            }
            if (in(x, y, bx - 14, ZONE_Y + 3, 12, 12)) {
                if (chestTab && chest < cs.size()) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(chest, 0, 0));
                } else {
                    ClientPlayNetworking.send(new TetrisNet.Action(8));
                }
                return true;
            }
            if (!craftMode && in(x, y, bx - 28, ZONE_Y + 3, 12, 12)) {
                if (hideout) {
                    ClientPlayNetworking.send(new TetrisNet.Action(1));
                }
                return true;
            }
            if (!craftMode && in(x, y, bx - 42, ZONE_Y + 3, 12, 12)) {
                if (chestTab && chest < cs.size()) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(chest, 1, 0));
                } else {
                    for (int i = 0; i < cs.size(); i++) {
                        ClientPlayNetworking.send(new TetrisNet.ContainerAction(i, 1, 0));
                    }
                }
                return true;
            }
        }
        return false;
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

    /** The station row (top right, inside a hideout): kind, x, width. */
    private List<Object[]> stationButtons() {
        List<Object[]> out = new ArrayList<>();
        if (ClientTarkov.raid.state() != 1) {
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
            case "module_intel" -> "icon/station/intel_center";
            default -> kind.startsWith("module_") ? "icon/station/" + kind.substring(7) : "icon/tab/hideout";
        };
    }

    // ================================================================== crafting

    private List<TetrisNet.CraftEntry> filtered() {
        String q = search.toLowerCase(Locale.ROOT);
        List<TetrisNet.CraftEntry> out = new ArrayList<>();
        for (TetrisNet.CraftEntry e : ClientTetris.craft.entries()) {
            if (q.isEmpty() || e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    /** A line in the CRAFT list: a category header (entry null) or a recipe. */
    private record CraftRow(StorageCategory header, int count, TetrisNet.CraftEntry entry, int y, int h) {}

    private static final int HEAD = 13;
    /** Categories folded shut in the CRAFT list; kept while the game runs. */
    private static final java.util.Set<StorageCategory> COLLAPSED = java.util.EnumSet.noneOf(StorageCategory.class);

    /**
     * The CRAFT list grouped by category, with each row's offset from the top. Outside a hideout the Hideout Bench
     * stays first, above the groups.
     */
    private List<CraftRow> craftRows() {
        List<TetrisNet.CraftEntry> list = filtered();
        List<CraftRow> rows = new ArrayList<>();
        int y = 0;
        String bench = com.cavmkii.seancraft.SeanCraft.id("hideout_bench").toString();
        if (!ClientTetris.craft.inHideout() && !list.isEmpty() && list.get(0).id().equals(bench)) {
            rows.add(new CraftRow(null, 0, list.remove(0), y, ROW));
            y += ROW + 2;
        }
        java.util.Map<StorageCategory, List<TetrisNet.CraftEntry>> groups = new java.util.EnumMap<>(StorageCategory.class);
        for (TetrisNet.CraftEntry e : list) {
            groups.computeIfAbsent(StorageCategory.of(e.result()), k -> new ArrayList<>()).add(e);
        }
        for (var group : groups.entrySet()) {
            rows.add(new CraftRow(group.getKey(), group.getValue().size(), null, y, HEAD));
            y += HEAD;
            // A search shows matches even in folded categories.
            if (COLLAPSED.contains(group.getKey()) && search.isEmpty()) {
                y += 2;
                continue;
            }
            for (TetrisNet.CraftEntry e : group.getValue()) {
                rows.add(new CraftRow(null, 0, e, y, ROW));
                y += ROW;
            }
            y += 2;
        }
        return rows;
    }

    /** Puts a recipe's ingredients on the gather list. */
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

    private static final int LIST_W = 250;
    private static final int SEARCH_X = 100;
    /** INVENTORY sub-view: 0 the inventory with its crafting grid, 1 the recipe book (every recipe). */
    private int craftView;
    /** The recipe book view's own layout. */
    private static final int PX = 262;
    /** The crafting book (what you can make now) starts shut, like vanilla's recipe book. */
    private static boolean listFolded = true;

    private int cellAt(double x, double y) {
        if (x < GX || x >= GX + 3 * GC || y < GRID_Y || y >= GRID_Y + 3 * GC) {
            return -1;
        }
        return (int) ((y - GRID_Y) / GC) * 3 + (int) ((x - GX) / GC);
    }

    private boolean overResult(double x, double y) {
        return x >= RESULT_X && x < RESULT_X + 26 && y >= RESULT_Y && y < RESULT_Y + 26;
    }

    private void subTabs(GuiGraphics g, int x0, int mx, int my) {
        for (int v = 0; v < 2; v++) {
            int bx = x0 + v * 48;
            boolean active = craftView == v;
            boolean over = mx >= bx && mx < bx + 44 && my >= 0 && my < 14;
            Kit.button(g, font, v == 0 ? "CRAFT" : "RECIPES", bx, 0, 44, 14, true, over && !active, active);
        }
    }

    private int subTabAt(int x0, double x, double y) {
        for (int v = 0; v < 2; v++) {
            if (x >= x0 + v * 48 && x < x0 + v * 48 + 44 && y >= 0 && y < 14) {
                return v;
            }
        }
        return -1;
    }

    private void searchBox(GuiGraphics g, int x, int y, int w, int h) {
        Kit.blit(g, searchFocused ? "text_field/focused" : "text_field/normal", x, y, w, h);
        String shown = search.isEmpty() && !searchFocused ? "Search..." : search + (searchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        g.drawString(font, font.plainSubstrByWidth(shown, w - 6, true), x + 4, y + (h - 8) / 2,
                search.isEmpty() && !searchFocused ? Kit.ASH : Kit.BONE, false);
    }


    private ItemStack renderCrafting(GuiGraphics g, int mx, int my) {
        if (craftView == 1) {
            subTabs(g, 0, mx, my);
            searchBox(g, SEARCH_X, 0, LIST_W - SEARCH_X, 14);
            return renderRecipes(g, mx, my);
        }
        return renderInventory(g, mx, my);
    }

    // ================================================================== recipe book

    private ItemStack hoverStack = ItemStack.EMPTY;
    private double recipeScroll;
    private net.minecraft.resources.ResourceLocation selectedRecipe;
    private List<RecipeSection> recipeCache;
    private int recipeCacheTick = -1000;
    private static final int RCELL = 18;
    private static final int RCOLS = LIST_W / RCELL;

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

    private List<RecipeSection> visibleSections() {
        String q = search.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return recipeSections();
        }
        List<RecipeSection> out = new ArrayList<>();
        for (RecipeSection sec : recipeSections()) {
            List<RecipeCell> cells = new ArrayList<>();
            for (RecipeCell c : sec.cells) {
                if (c.known && c.result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                    cells.add(c);
                }
            }
            if (!cells.isEmpty()) {
                out.add(new RecipeSection(sec.category, cells));
            }
        }
        return out;
    }

    private RecipeCell recipeAt(double x, double y) {
        int top = 20 - (int) recipeScroll;
        if (x < 0 || x >= RCOLS * RCELL || y < 20 || y >= H) {
            return null;
        }
        for (RecipeSection sec : visibleSections()) {
            int rows = (sec.cells.size() + RCOLS - 1) / RCOLS;
            int gridTop = top + 11;
            if (y >= gridTop && y < gridTop + rows * RCELL) {
                int i = (int) ((y - gridTop) / RCELL) * RCOLS + (int) (x / RCELL);
                return i < sec.cells.size() ? sec.cells.get(i) : null;
            }
            top = gridTop + rows * RCELL + 4;
        }
        return null;
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

    private static final int REC_BTN_Y = 146;
    private static final int TRACK_Y = 166;

    private ItemStack renderRecipes(GuiGraphics g, int mx, int my) {
        ItemStack tip = ItemStack.EMPTY;
        List<RecipeSection> sections = visibleSections();
        int total = 0;
        for (RecipeSection sec : sections) {
            total += 11 + ((sec.cells.size() + RCOLS - 1) / RCOLS) * RCELL + 4;
        }
        recipeScroll = Math.max(0, Math.min(Math.max(0, total - (H - 20)), recipeScroll));
        RecipeCell hovered = recipeAt(mx, my);
        g.enableScissor((int) ox, (int) (oy + 20 * scale), (int) (ox + LIST_W * scale), (int) (oy + H * scale));
        int top = 20 - (int) recipeScroll;
        for (RecipeSection sec : sections) {
            int known = 0;
            for (RecipeCell c : sec.cells) {
                known += c.known ? 1 : 0;
            }
            g.drawString(font, sec.category.label.toUpperCase(Locale.ROOT) + "  " + known + "/" + sec.cells.size(), 0, top + 1, Kit.ASH, false);
            int gridTop = top + 11;
            for (int i = 0; i < sec.cells.size(); i++) {
                RecipeCell c = sec.cells.get(i);
                int x = (i % RCOLS) * RCELL;
                int y = gridTop + (i / RCOLS) * RCELL;
                if (y + RCELL < 20 || y > H) {
                    continue;
                }
                boolean sel = c.known && c.holder.id().equals(selectedRecipe);
                Kit.slot(g, x, y, RCELL, sel ? Kit.SlotState.SELECTED : c == hovered ? Kit.SlotState.HOVER : !c.known ? Kit.SlotState.LOCKED : Kit.SlotState.NORMAL);
                if (c.known) {
                    g.renderItem(c.result, x + 1, y + 1);
                    if (c == hovered) {
                        tip = c.result;
                        hoverRecipe = c.holder.id().toString();
                    }
                } else {
                    g.drawString(font, "?", x + 6, y + 5, Kit.DISABLED, false);
                }
            }
            top = gridTop + ((sec.cells.size() + RCOLS - 1) / RCOLS) * RCELL + 4;
        }
        g.disableScissor();
        if (hovered != null && !hovered.known) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            Kit.hudPanel(g, mx + 6, my - 13, 44, 14);
            g.drawString(font, "??????", mx + 9, my - 10, Kit.ASH, false);
            g.pose().popPose();
        }

        // Selected recipe.
        g.drawString(font, "RECIPE", PX, 2, Kit.ASH, false);
        RecipeCell sel = null;
        for (RecipeSection sec : recipeSections()) {
            for (RecipeCell c : sec.cells) {
                if (c.holder.id().equals(selectedRecipe)) {
                    sel = c;
                }
            }
        }
        if (sel == null) {
            g.drawWordWrap(font, Component.literal("Click a discovered recipe. You discover recipes by carrying one of their ingredients."),
                    PX, 16, W - PX, Kit.ASH);
        } else {
            g.renderItem(sel.result, PX, 14);
            g.renderItemDecorations(font, sel.result, PX, 14);
            g.drawString(font, font.plainSubstrByWidth(sel.result.getHoverName().getString(), W - PX - 22), PX + 20, 18, Kit.BONE, false);
            int ly = 36;
            for (IngredientLine line : ingredientsOf(sel.holder.value())) {
                if (ly > REC_BTN_Y - 12) {
                    break;
                }
                int have = haveFor(line.ingredient);
                g.pose().pushPose();
                g.pose().translate(PX, ly - 1, 0);
                g.pose().scale(0.625f, 0.625f, 1);
                g.renderItem(line.display, 0, 0);
                g.pose().popPose();
                g.drawString(font, font.plainSubstrByWidth(line.display.getHoverName().getString(), W - PX - 50), PX + 13, ly, Kit.BONE, false);
                String c = have + "/" + line.need;
                g.drawString(font, c, W - font.width(c), ly, have >= line.need ? Kit.MOSS : Kit.BLOOD_LIGHT, false);
                ly += 11;
            }
            boolean over = mx >= PX && mx < PX + 80 && my >= REC_BTN_Y && my < REC_BTN_Y + 14;
            Kit.button(g, font, "PIN TO HUD", PX, REC_BTN_Y, 80, 14, true, over, false);
        }

        // Tracker.
        g.drawString(font, "TRACKING", PX, TRACK_Y, Kit.ASH, false);
        g.pose().pushPose();
        g.pose().translate(PX + 52, TRACK_Y + 1, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, "press T on any item to add", 0, 0, Kit.ASH, false);
        g.pose().popPose();
        List<ClientTracker.Entry> entries = ClientTracker.entries();
        for (int i = 0; i < entries.size(); i++) {
            ClientTracker.Entry e = entries.get(i);
            int y = TRACK_Y + 11 + i * 9;
            if (y > H - 8) {
                break;
            }
            int have = ClientTracker.have(e);
            g.pose().pushPose();
            g.pose().translate(PX, y - 1, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.renderItem(new ItemStack(e.item()), 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(PX + 10, y, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 80), 0, 0, Kit.BONE, false);
            String c = have + "/" + e.goal();
            g.drawString(font, c, 112, 0, have >= e.goal() ? Kit.MOSS : Kit.BONE, false);
            g.pose().popPose();
            String[] marks = {"-", "+", "x"};
            for (int b = 0; b < 3; b++) {
                int bx = W - 30 + b * 10;
                boolean over = mx >= bx && mx < bx + 9 && my >= y - 1 && my < y + 7;
                Kit.buttonBg(g, bx, y - 1, 9, 8, true, over, false);
                g.drawString(font, marks[b], bx + 2, y - 1, over ? Kit.GOLD_LIGHT : Kit.BONE, false);
            }
        }
        return tip;
    }

    private boolean clickRecipes(double x, double y, int button) {
        RecipeCell cell = recipeAt(x, y);
        if (cell != null) {
            if (cell.known) {
                selectedRecipe = cell.holder.id();
                // Discovered recipes show their ghost pattern in the grid.
                ghostRecipe = selectedRecipe.toString();
            }
            return true;
        }
        if (selectedRecipe != null && y >= REC_BTN_Y && y < REC_BTN_Y + 14) {
            if (x >= PX && x < PX + 80) {
                pinRecipe(selectedRecipe.toString());
                return true;
            }
        }
        List<ClientTracker.Entry> entries = ClientTracker.entries();
        for (int i = 0; i < entries.size(); i++) {
            int ty = TRACK_Y + 11 + i * 9;
            for (int b = 0; b < 3; b++) {
                int bx = W - 30 + b * 10;
                if (x >= bx && x < bx + 9 && y >= ty - 1 && y < ty + 7) {
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
        return true;
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
        if (tab == INVENTORY) {
            int[] cr = craftToggle();
            if (in(mouseX, mouseY, cr[0], cr[1], cr[2], cr[3])) {
                dropHeld();
                craftMode = !craftMode;
                colScroll = 0;
                storageScroll = 0;
                searchFocused = false;
                ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
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
        heldFrom = p == null ? loc : loc.kind() == Loc.GRID ? Loc.grid(GridId.values()[loc.a()], p.x, p.y) : Loc.container(loc.a(), p.x, p.y);
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
                        && !ItemStack.isSameItemSameComponents(there, held) && heldFrom.kind() != Loc.UNSORTED
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
        if (craftMode && overResult(x, y)) {
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
            if (craftMode && in(x, y, BOOK_X, BOOK_TOP, BOOK_W + 4, ZONE_Y + ZONE_H - BOOK_TOP)) {
                bookScroll -= scrollY * BOOK_ROW;
            } else if (in(x, y, cx(), ZONE_Y, 158, ZONE_H)) {
                colScroll -= scrollY * 18;
            } else if (in(x, y, sx(), ZONE_Y, sw(), ZONE_H)) {
                storageScroll -= scrollY * 18;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T && renameIndex < 0 && !searchFocused && !storageSearchFocused && !hoverStack.isEmpty()) {
            // T on any item: track it (a stack's worth, or one if it doesn't stack).
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
