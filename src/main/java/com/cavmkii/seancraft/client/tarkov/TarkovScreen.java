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
    private static final String[] TABS = {"INVENTORY", "STATUS", "CHARACTER"};

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
        this.tab = tab;
    }

    void setTab(int tab) {
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
            int bottom = StationTabs.visible() ? 16 : 2;
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
        out.add(new Box(GridId.POUCH, POUCH_GRID_X, POUCH_Y, s.pouchW(), s.pouchH(), s.pouch()));
        for (ColSec sec : colSections()) {
            int gy = sec.y() + 11 - (int) colScroll;
            if (sec.kind() == 1 && s.rig().getItem() instanceof GearItem g) {
                out.add(new Box(GridId.RIG, SEC_GRID_X, gy, g.spec.gridWidth, g.spec.gridHeight, s.rigItems()));
            } else if (sec.kind() == 2) {
                out.add(new Box(GridId.POCKETS, SEC_GRID_X, gy, InvData.POCKET_W, InvData.POCKET_H, s.pockets()));
            } else if (sec.kind() == 3 && s.backpack().getItem() instanceof GearItem g) {
                out.add(new Box(GridId.BACKPACK, SEC_GRID_X, gy, g.spec.gridWidth, g.spec.gridHeight, s.backpackItems()));
            }
        }
        return out;
    }



    /** x, y, size of an equipment slot: in its box, or (rig, backpack) beside its grid; null if scrolled away. */
    private int[] equipRect(int slot) {
        if (EQUIP_BOX[slot] != null) {
            return new int[] {EQUIP_BOX[slot][0] + 11, EQUIP_BOX[slot][1] + 11, C};
        }
        for (ColSec sec : colSections()) {
            if (slot == 4 && sec.kind() == 1 || slot == 5 && sec.kind() == 3) {
                return new int[] {SEC_X, sec.y() + 11 - (int) colScroll, C};
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
        if (loc.kind() == Loc.CONTAINER && loc.b() >= 0 && loc.a() < ClientTetris.sync.containers().size() && !bookOpen()) {
            TetrisNet.ContainerView c = ClientTetris.sync.containers().get(loc.a());
            for (CPlace p : containerPlaces()) {
                if (p.index() == loc.a()) {
                    return new GridRef(p.x(), p.y(), c.width(), c.height(), c.items());
                }
            }
        }
        return null;
    }

    private static int rows(int n) {
        return Math.max(1, (n + 8) / 9);
    }


    /** What's under a point (virtual coords) on the INVENTORY tab. For grids it's the cell. */
    private Loc locAt(double x, double y) {
        if (tab == INVENTORY && craftView == 0) {
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
            if (in(x, y, QU_X + i * QU_STEP, QU_Y, C, C)) {
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
                if (x >= SEC_X && x < SEC_X + 8 * C && y >= top) {
                    int i = (int) ((y - top) / C) * 8 + (int) ((x - SEC_X) / C);
                    if (i >= 0 && i < n && y < top + ((n + 7) / 8) * C) {
                        return Loc.unsorted(i);
                    }
                }
            }
        }
        if (!bookOpen()) {
            for (CPlace p : containerPlaces()) {
                if (y < p.clipTop() || y >= p.clipBottom()) {
                    continue;
                }
                TetrisNet.ContainerView c = ClientTetris.sync.containers().get(p.index());
                if (x >= p.x() && x < p.x() + c.width() * C && y >= p.y() && y < p.y() + c.height() * C) {
                    return Loc.container(p.index(), (int) ((x - p.x()) / C), (int) ((y - p.y()) / C));
                }
                int otop = p.y() + c.height() * C + 10;
                if (!c.overflow().isEmpty() && x >= p.x() && x < p.x() + 9 * C && y >= otop && y < otop + rows(c.overflow().size()) * C) {
                    int i = (int) ((y - otop) / C) * 9 + (int) ((x - p.x()) / C);
                    if (i >= 0 && i < c.overflow().size()) {
                        return Loc.containerOverflow(p.index(), c.overflowSlots().get(i));
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
        if (tab == CRAFTING) {
            StationTabs.render(g, font, 0, height - 15, width, StationTabs.CRAFTING_TABLE, mouseX, mouseY);
        }
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

    private static final String[] TAB_ICONS = {"gear", "status", "web"};

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
        if (ClientTarkov.raid.state() == 1) {
            String t = "HIDEOUT";
            int rx = width - 4 - font.width(t);
            Kit.blit(g, "icon/tab/hideout", rx - 11, 4, 9, 9);
            Kit.text(g, font, t, rx, 5, Kit.MOSS);
            return;
        }
        // The raid state, always in view (C.4); shorter forms when the bar leaves too little room.
        for (String raid : RaidHud.stateLines()) {
            int rx = width - 4 - font.width(raid);
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
        int y = Math.min(filterY, R_BOTTOM - (cats.length + 2) * FILTER_ROW - 6);
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
        int y = Math.min(filterY, R_BOTTOM - (cats.length + 2) * FILTER_ROW - 6);
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

    // ================================================================== INVENTORY layout (GEAR-V2.md: 480 x 270 GUI pixels)

    private static final int VW = 480;
    private static final int VH = 270;
    /** Equipment boxes (40 x 30) for head, chest, legs, feet and off-hand; the rig and backpack sit by their grids. */
    private static final int[][] EQUIP_BOX = {{10, 24}, {10, 58}, {10, 92}, {10, 126}, null, null, {72, 126}};
    private static final String[] EQUIP_LABEL = {"HEAD", "CHEST", "LEGS", "FEET", "RIG", "BACKPACK", "OFF-HAND"};
    private static final int POUCH_Y = 176;
    private static final int POUCH_GRID_X = 34;
    private static final int COL_X = 122;
    private static final int COL_Y = 18;
    private static final int COL_W = 158;
    private static final int COL_H = 221;
    private static final int COL_TOP = 21;
    private static final int COL_BOTTOM = 237;
    private static final int SEC_X = 126;
    private static final int SEC_W = 150;
    private static final int SEC_GRID_X = 148;
    private static final int CP_X = 284;
    private static final int CP_Y = 18;
    private static final int CP_W = 192;
    private static final int CP_H = 66;
    private static final int GC = 18;
    private static final int GX = 294;
    private static final int GRID_Y = 33;
    private static final int RESULT_X = 382;
    private static final int RESULT_Y = 47;
    private static final int R_X = 284;
    private static final int R_Y = 88;
    private static final int R_W = 192;
    private static final int R_BOTTOM = 239;
    private static final int QU_X = 160;
    private static final int QU_Y = 245;
    private static final int QU_STEP = 19;
    private static final int CLEAR_X = 412;
    private static final int CLEAR_Y = 70;
    private static final int CLEAR_W = 26;
    private static final int PIN_X = 442;
    private static final int PIN_W = 32;
    /** The crafting book's list covers the stash and chest panels while it's open. */
    private static final int LIST_X = R_X + 4;
    private static final int LIST_W2 = R_W - 10;
    private static final int LIST_HEAD_Y = R_Y + 4;

    private double colScroll;
    private double stashScroll;
    private double chestScroll;
    /** Tooltip for whatever small control is under the mouse this frame. */
    private String uiTip;

    private static boolean in(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x < rx + w && y >= ry && y < ry + h;
    }

    private boolean bookOpen() {
        return !listFolded;
    }

    /** One of the containers column's sections: 0 unsorted, 1 rig, 2 pockets, 3 backpack. y is before scrolling. */
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

    private static boolean inCol(double y) {
        return y >= COL_TOP && y < COL_BOTTOM;
    }

    private boolean opened() {
        return ClientTetris.sync.opened() && !ClientTetris.sync.containers().isEmpty();
    }

    /** The open chest's panel: x, y, w, h; null if no chest is open. */
    private int[] chestArea() {
        if (!opened()) {
            return null;
        }
        return ClientTetris.sync.stash() ? new int[] {R_X, 168, R_W, 71} : new int[] {R_X, R_Y, R_W, R_BOTTOM - R_Y};
    }

    /** The stash panel (inside a hideout): x, y, w, h; null outside one. */
    private int[] stashArea() {
        if (!ClientTetris.sync.stash()) {
            return null;
        }
        return opened() ? new int[] {R_X, R_Y, R_W, 76} : new int[] {R_X, R_Y, R_W, R_BOTTOM - R_Y};
    }

    /** Where a shown container's grid sits, the clip of its panel, and its header row in the stash (-1 in the chest panel). */
    private record CPlace(int index, int x, int y, int clipTop, int clipBottom, int headerY) {}

    private static int overflowH(TetrisNet.ContainerView c) {
        return c.overflow().isEmpty() ? 0 : 10 + rows(c.overflow().size()) * C;
    }

    private List<CPlace> containerPlaces() {
        List<CPlace> out = new ArrayList<>();
        List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
        int start = 0;
        int[] ch = chestArea();
        if (ch != null) {
            int top = ch[1] + 18;
            out.add(new CPlace(0, ch[0] + 6, top - (int) chestScroll, top, ch[1] + ch[3] - 3, -1));
            start = 1;
        }
        int[] st = stashArea();
        if (st != null) {
            int top = st[1] + 18;
            int y = top - (int) stashScroll;
            for (int i = start; i < cs.size(); i++) {
                TetrisNet.ContainerView c = cs.get(i);
                out.add(new CPlace(i, st[0] + 6, y + 11, top, st[1] + st[3] - 3, y));
                y += 11 + c.height() * C + overflowH(c) + 3;
            }
        }
        return out;
    }

    private int stashContent() {
        int h = 0;
        List<TetrisNet.ContainerView> cs = ClientTetris.sync.containers();
        for (int i = opened() ? 1 : 0; i < cs.size(); i++) {
            h += 11 + cs.get(i).height() * C + overflowH(cs.get(i)) + 3;
        }
        return h;
    }

    private int chestContent() {
        return opened() ? ClientTetris.sync.containers().get(0).height() * C + overflowH(ClientTetris.sync.containers().get(0)) : 0;
    }

    /** True if a point is on one of the screen's panels (a click anywhere else with something held drops it, like vanilla). */
    private boolean onPanel(double x, double y) {
        return in(x, y, 4, 18, 114, 221) || in(x, y, COL_X, COL_Y, COL_W, COL_H) || in(x, y, CP_X, CP_Y, CP_W, CP_H)
                || in(x, y, R_X, R_Y, R_W, R_BOTTOM - R_Y) || in(x, y, 0, 240, VW, 30) || y < 18;
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

    private ItemStack renderInventory(GuiGraphics g, int mx, int my) {
        lastMx = mx;
        lastMy = my;
        TetrisNet.Sync s = ClientTetris.sync;
        Loc hover = locAt(mx, my);
        ItemStack tip = stackAt(hover);

        // Equipment column: the body, the worn slots around it, and the secure pouch.
        Kit.blit(g, "panel/column", 4, 18, 114, 221);
        Kit.blit(g, "gear/silhouette", 54, 30, 56, 118);
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
        Kit.blit(g, "panel/label", 10, 164, 104, 9);
        String tier = pouchItem().isEmpty() ? "SECURE POUCH" : pouchItem().getHoverName().getString().toUpperCase(Locale.ROOT);
        small(g, tier, 13, 165.5f, Kit.GOLD, Math.min(0.75f, 96f / font.width(tier)));
        Kit.slot(g, 10, POUCH_Y, C, Kit.SlotState.NORMAL);
        if (!pouchItem().isEmpty()) {
            g.renderItem(pouchItem(), 11, POUCH_Y + 1);
            if (in(mx, my, 10, POUCH_Y, C, C)) {
                tip = pouchItem();
            }
        }
        Box pouch = box(GridId.POUCH);
        if (pouch != null) {
            renderGrid(g, pouch);
            int kx = POUCH_GRID_X + pouch.w() * C + 4;
            // Gold marks what survives death.
            if (kx + 30 < 116) {
                small(g, "KEPT", kx, POUCH_Y + 2, Kit.GOLD, 0.75f);
                small(g, "ON DEATH", kx, POUCH_Y + 10, Kit.GOLD, 0.75f);
            } else {
                small(g, "KEPT ON DEATH", POUCH_GRID_X, POUCH_Y + pouch.h() * C + 2, Kit.GOLD, 0.625f);
            }
        }

        // Containers column: rig, pockets, backpack, each with its own item beside its grid.
        Kit.blit(g, "panel/column", COL_X, COL_Y, COL_W, COL_H);
        colScroll = Math.max(0, Math.min(Math.max(0, colContent() - (COL_BOTTOM - COL_TOP)), colScroll));
        g.enableScissor((int) (ox + COL_X * scale), (int) (oy + COL_TOP * scale), (int) (ox + (COL_X + COL_W) * scale),
                (int) (oy + COL_BOTTOM * scale));
        boolean sortShown = false;
        for (ColSec sec : colSections()) {
            int y = sec.y - (int) colScroll;
            Kit.blit(g, "panel/label", SEC_X, y, SEC_W, 9);
            small(g, sec.title, SEC_X + 3, y + 1.5f, sec.kind == 0 ? Kit.BLOOD_LIGHT : Kit.ASH, 0.75f);
            switch (sec.kind) {
                case 0 -> {
                    boolean over = in(mx, my, SEC_X + SEC_W - 28, y, 28, 9) && inCol(my);
                    Kit.smallButton(g, font, "DROP", SEC_X + SEC_W - 28, y, 28, 9, true, over, false, 0.625f);
                    if (over) {
                        uiTip = "Drop everything that has no room";
                    }
                    List<ItemStack> list = s.unsorted();
                    for (int i = 0; i < list.size(); i++) {
                        Loc loc = Loc.unsorted(i);
                        slot(g, SEC_X + (i % 8) * C, y + 11 + (i / 8) * C, C, list.get(i), hover != null && hover.equals(loc), null);
                    }
                }
                case 1, 3 -> {
                    equipSlot(g, sec.kind == 1 ? 4 : 5, hover);
                    boolean none = sec.kind == 1 ? s.rig().isEmpty() : s.backpack().isEmpty();
                    if (none) {
                        small(g, sec.kind == 1 ? "no rig" : "no backpack", SEC_GRID_X, y + 16, Kit.DISABLED, 0.75f);
                    }
                }
                default -> { }
            }
            if (sec.kind != 0 && !sortShown) {
                sortShown = true;
                if (inCol(my)) {
                    iconButton(g, "sort", SEC_X + SEC_W - 10, y, 9, true, mx, my, "Sort your grids");
                } else {
                    iconButton(g, "sort", SEC_X + SEC_W - 10, y, 9, true, -1, -1, null);
                }
            }
        }
        for (Box b : boxes()) {
            if (b.id() != GridId.POUCH) {
                renderGrid(g, b);
            }
        }
        g.disableScissor();
        scrollBar(g, COL_X + COL_W - 4, COL_TOP, COL_BOTTOM - COL_TOP, colContent(), colScroll);

        // Crafting, then the stash and the open chest (or the crafting book's list over them).
        ItemStack craftTip = renderCraftPanel(g, mx, my);
        if (!craftTip.isEmpty()) {
            tip = craftTip;
        }
        if (bookOpen()) {
            Kit.blit(g, "panel/column", R_X, R_Y, R_W, R_BOTTOM - R_Y);
            small(g, "CAN MAKE", LIST_X, LIST_HEAD_Y + 3, Kit.ASH, 0.75f);
            searchBox(g, LIST_X + 50, LIST_HEAD_Y, LIST_W2 - 50, 11);
            ItemStack listTip = renderCraftList(g, craftRows(), mx, my);
            if (!listTip.isEmpty()) {
                tip = listTip;
            }
        } else {
            renderStashPanel(g, mx, my, hover);
            renderChestPanel(g, mx, my, hover);
            if (stashArea() == null && chestArea() == null) {
                Kit.blit(g, "panel/column", R_X, R_Y, R_W, R_BOTTOM - R_Y);
                g.drawWordWrap(font, Component.literal("Open a chest or barrel to see it here. Inside your hideout, the stash shows here."),
                        R_X + 8, R_Y + 8, R_W - 16, Kit.ASH);
                if (!ClientTetris.craft.inHideout()) {
                    g.drawWordWrap(font, Component.literal("No hideout here: open the crafting book for the Hideout Bench."),
                            R_X + 8, R_Y + 40, R_W - 16, Kit.GOLD_LIGHT);
                }
            }
        }

        // Quick-use bar, with the auto-refill toggles after it.
        small(g, "QUICK USE", 120, 250, Kit.ASH, 0.75f);
        int selected = minecraft.player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = QU_X + i * QU_STEP;
            boolean over = hover != null && hover.equals(Loc.hand(i)) || dragging(Loc.hand(i));
            slot(g, x, QU_Y, C, stackAt(Loc.hand(i)), over || i == selected, null);
            Kit.blit(g, "slot/key_badge", x, QU_Y, 6, 7);
            small(g, String.valueOf(i + 1), x + 1.5f, QU_Y + 1.5f, i == selected ? Kit.GOLD_LIGHT : Kit.BONE, 0.5f);
        }
        int tx = QU_X + 9 * QU_STEP + 4;
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

        renderStats(g);
        renderFilter(g, mx, my);
        renderHeld(g, mx, my, hover);
        return tip;
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
            // Where the held item can be worn.
            int c = hover != null && hover.equals(Loc.equip(slot)) ? Kit.GOLD_LIGHT : Kit.GOLD;
            g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[2] + 2, c);
        }
    }

    private void scrollBar(GuiGraphics g, int x, int top, int span, int content, double scroll) {
        double max = content - span;
        if (max <= 0) {
            return;
        }
        int barH = Math.max(10, (int) (span * (double) span / content));
        int barY = top + (int) ((span - barH) * scroll / max);
        Kit.blit(g, "scroll/handle", x, barY, 3, barH);
    }

    /** Bottom left: load, then health, stamina, and Rage or mana if your skills use them. */
    private void renderStats(GuiGraphics g) {
        com.cavmkii.seancraft.combat.Load.State load = com.cavmkii.seancraft.client.combat.ClientCombat.load();
        com.cavmkii.seancraft.combat.Load.Tier tier = load.tier();
        Kit.blit(g, "icon/tab/load", 6, 244, 9, 9);
        int lc = tier == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED ? Kit.BLOOD_LIGHT
                : tier == com.cavmkii.seancraft.combat.Load.Tier.HEAVY ? Kit.EMBER : Kit.BONE;
        String loadText = "LOAD " + Math.round(load.fraction() * 100) + "%  " + tier.label;
        small(g, loadText, 18, 245.5f, lc, Math.min(1f, 98f / font.width(loadText)));
        float x = 6;
        Kit.blit(g, "hud/heart_full", 6, 256, 9, 9);
        x += 11;
        String hp = Math.round(minecraft.player.getHealth()) + "/" + Math.round(minecraft.player.getMaxHealth());
        small(g, hp, x, 257.5f, Kit.BLOOD_LIGHT, 0.75f);
        x += font.width(hp) * 0.75f + 5;
        com.cavmkii.seancraft.rpg.Stats st = com.cavmkii.seancraft.client.rpg.ClientRpg.stats;
        x = stat(g, x, "stamina", com.cavmkii.seancraft.client.rpg.ClientRpg.stamina,
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
        if (used.contains(com.cavmkii.seancraft.rpg.Resource.RAGE) && x < 108) {
            x = stat(g, x, "rage", com.cavmkii.seancraft.client.rpg.ClientRpg.rage, st.maxRage(), Kit.EMBER);
        }
        if (used.contains(com.cavmkii.seancraft.rpg.Resource.MANA) && x < 108) {
            stat(g, x, "mana", com.cavmkii.seancraft.client.rpg.ClientRpg.mana, st.maxMana(), Kit.ARCANE);
        }
    }

    private float stat(GuiGraphics g, float x, String icon, float value, float max, int color) {
        g.pose().pushPose();
        g.pose().translate(x, 256, 0);
        Kit.blit(g, "icon/resource/" + icon, 0, 0, 9, 9);
        g.pose().popPose();
        String t = Math.round(value) + "/" + Math.round(max);
        small(g, t, x + 10, 257.5f, color, 0.75f);
        return x + 10 + font.width(t) * 0.75f + 5;
    }

    /** Crafting panel: label and the two books, the grid (2x2, 3x3 near a crafting table or in the hideout), arrow, result. */
    private ItemStack renderCraftPanel(GuiGraphics g, int mx, int my) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        ItemStack tip = ItemStack.EMPTY;
        Kit.blit(g, "panel/column", CP_X, CP_Y, CP_W, CP_H);
        Kit.blit(g, "panel/label", CP_X + 4, CP_Y + 3, CP_W - 40, 9);
        small(g, "CRAFTING", CP_X + 7, CP_Y + 4.5f, Kit.ASH, 0.75f);
        books(g, mx, my);
        // The picked (or hovered) recipe's pattern as a ghost (4.1): faded icons in the empty squares.
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
        Kit.blit(g, craft.result().isEmpty() ? "progress/arrow_empty" : "progress/arrow_full", 354, 53, 22, 15);
        boolean resultHover = overResult(mx, my);
        Kit.slot(g, RESULT_X, RESULT_Y, 26, resultHover && !craft.result().isEmpty() ? Kit.SlotState.HOVER : Kit.SlotState.RESULT);
        if (!craft.result().isEmpty()) {
            g.renderItem(craft.result(), RESULT_X + 5, RESULT_Y + 5);
            g.renderItemDecorations(font, craft.result(), RESULT_X + 5, RESULT_Y + 5);
            if (resultHover) {
                tip = craft.result();
            }
        }
        String help = craft.needsTable() ? "NEEDS A TABLE" : !craft.result().isEmpty() ? "SHIFT: ALL" : ghostRecipe != null ? "GHOST: PLACE" : !craft.table() ? "2X2 HERE" : "";
        String help2 = ghostRecipe != null && craft.result().isEmpty() ? "ITEMS BY HAND" : !craft.table() && craft.result().isEmpty() ? "3X3 AT A TABLE" : "";
        small(g, help, 412, 49, craft.needsTable() ? Kit.BLOOD_LIGHT : Kit.ASH, 0.625f);
        small(g, help2, 412, 56, Kit.ASH, 0.625f);
        boolean clearHover = in(mx, my, CLEAR_X, CLEAR_Y, CLEAR_W, 10);
        Kit.smallButton(g, font, "CLEAR", CLEAR_X, CLEAR_Y, CLEAR_W, 10, true, clearHover, false, 0.625f);
        if (ghostRecipe != null) {
            boolean pinHover = in(mx, my, PIN_X, CLEAR_Y, PIN_W, 10);
            Kit.smallButton(g, font, "PIN HUD", PIN_X, CLEAR_Y, PIN_W, 10, true, pinHover, false, 0.625f);
            if (pinHover) {
                uiTip = "Put this recipe's ingredients on your gather list";
            }
        }
        return tip;
    }

    /** Two books at the crafting panel's top right: what you can make now (its list covers the stash), and every recipe. */
    private void books(GuiGraphics g, int mx, int my) {
        int bx = CP_X + CP_W - 32;
        boolean over0 = in(mx, my, bx, CP_Y + 2, 12, 12);
        Kit.blit(g, over0 || bookOpen() ? "button_small/recipe_book_hover" : "button_small/recipe_book_normal", bx, CP_Y + 2, 12, 12);
        if (bookOpen()) {
            g.renderOutline(bx - 1, CP_Y + 1, 14, 14, Kit.GOLD);
        }
        if (over0) {
            uiTip = bookOpen() ? "Close the crafting book" : "Crafting book: what you can make now";
        }
        boolean over1 = in(mx, my, bx + 15, CP_Y + 2, 12, 12);
        Kit.buttonBg(g, bx + 15, CP_Y + 2, 12, 12, true, over1, false);
        g.pose().pushPose();
        g.pose().translate(bx + 16, CP_Y + 3, 0);
        g.pose().scale(0.625f, 0.625f, 1);
        g.renderItem(new ItemStack(net.minecraft.world.item.Items.KNOWLEDGE_BOOK), 0, 0);
        g.pose().popPose();
        if (over1) {
            uiTip = "Recipe book: every recipe, found or not";
        }
    }

    private int bookAt(double x, double y) {
        int bx = CP_X + CP_W - 32;
        return in(x, y, bx, CP_Y + 2, 12, 12) ? 0 : in(x, y, bx + 15, CP_Y + 2, 12, 12) ? 1 : -1;
    }

    /** The name of a container, or the rename field while it's being renamed. */
    private void containerName(GuiGraphics g, int index, int x, int y, int w, int color) {
        TetrisNet.ContainerView c = ClientTetris.sync.containers().get(index);
        boolean editing = renameIndex == index;
        if (editing) {
            Kit.blit(g, "text_field/focused", x - 2, y - 2, w + 2, 10);
        }
        String text = editing ? renameText + (ticks / 6 % 2 == 0 ? "_" : "") : c.name().toUpperCase(Locale.ROOT);
        float k = 0.75f;
        String fit = editing ? font.plainSubstrByWidth(text, (int) (w / k), true) : font.plainSubstrByWidth(text, (int) (w / k));
        small(g, fit, x, y, editing ? Kit.BONE : color, k);
    }

    private void renderStashPanel(GuiGraphics g, int mx, int my, Loc hover) {
        int[] a = stashArea();
        if (a == null) {
            return;
        }
        Kit.blit(g, "panel/column", a[0], a[1], a[2], a[3]);
        Kit.blit(g, "icon/tab/stash", a[0] + 6, a[1] + 4, 9, 9);
        Kit.text(g, font, "STASH", a[0] + 18, a[1] + 5, Kit.BONE);
        iconButton(g, "stash_all", a[0] + a[2] - 32, a[1] + 3, 12, !ClientTetris.sync.containers().isEmpty(), mx, my,
                "Stash all: your pockets, rig and backpack into the stash");
        iconButton(g, "sort", a[0] + a[2] - 18, a[1] + 3, 12, true, mx, my, "Organize the stash: merge and sort across its chests");
        int top = a[1] + 18;
        int bottom = a[1] + a[3] - 3;
        stashScroll = Math.max(0, Math.min(Math.max(0, stashContent() - (bottom - top)), stashScroll));
        List<CPlace> places = containerPlaces();
        boolean any = false;
        g.enableScissor((int) (ox + a[0] * scale), (int) (oy + top * scale), (int) (ox + (a[0] + a[2]) * scale), (int) (oy + bottom * scale));
        for (CPlace p : places) {
            if (p.headerY() < 0) {
                continue;
            }
            any = true;
            TetrisNet.ContainerView c = ClientTetris.sync.containers().get(p.index());
            int hy = p.headerY();
            boolean live = hy >= top && hy + 10 <= bottom && in(mx, my, a[0], top, a[2], bottom - top);
            g.pose().pushPose();
            g.pose().translate(p.x(), hy, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.renderItem(c.icon(), 0, 0);
            g.pose().popPose();
            boolean overName = live && in(mx, my, p.x() + 10, hy, a[2] - 62, 9);
            containerName(g, p.index(), p.x() + 10, hy + 1, a[2] - 62, overName ? Kit.GOLD_LIGHT : Kit.ASH);
            if (overName) {
                uiTip = "Click to rename";
            }
            int bx = a[0] + a[2] - 40;
            iconButton(g, "filter", bx, hy, 10, true, live ? mx : -1, my, "Filter: what this chest takes");
            if (c.filter() != com.cavmkii.seancraft.tetris.StorageCategory.ALL) {
                g.renderOutline(bx - 1, hy - 1, 12, 12, Kit.GOLD);
            }
            iconButton(g, "sort", bx + 12, hy, 10, true, live ? mx : -1, my, "Sort this chest");
            iconButton(g, "take_all", bx + 24, hy, 10, true, live ? mx : -1, my, "Take all from this chest");
            renderGrid(g, new GridRef(p.x(), p.y(), c.width(), c.height(), c.items()));
            renderOverflow(g, p, c, hover);
        }
        g.disableScissor();
        if (!any) {
            small(g, "Your stash is empty: place chests or barrels in the hideout.", a[0] + 6, top + 2, Kit.ASH, 0.75f);
        }
        scrollBar(g, a[0] + a[2] - 4, top, bottom - top, stashContent(), stashScroll);
    }

    private void renderChestPanel(GuiGraphics g, int mx, int my, Loc hover) {
        int[] a = chestArea();
        if (a == null) {
            return;
        }
        TetrisNet.ContainerView c = ClientTetris.sync.containers().get(0);
        Kit.blit(g, "panel/column", a[0], a[1], a[2], a[3]);
        Kit.blit(g, "icon/tab/chest", a[0] + 6, a[1] + 4, 9, 9);
        int nameW = Math.min(a[2] - 110, (int) (font.width(c.name()) * 0.75f) + 2);
        containerName(g, 0, a[0] + 18, a[1] + 5, renameIndex == 0 ? a[2] - 80 : nameW, Kit.BONE);
        if (renameIndex != 0) {
            boolean overRename = in(mx, my, a[0] + 22 + nameW, a[1] + 4, 32, 9);
            small(g, "RENAME", a[0] + 22 + nameW, a[1] + 5.5f, overRename ? Kit.GOLD_LIGHT : Kit.GOLD, 0.75f);
        }
        iconButton(g, "sort", a[0] + a[2] - 46, a[1] + 3, 12, true, mx, my, "Sort this chest");
        iconButton(g, "take_all", a[0] + a[2] - 32, a[1] + 3, 12, true, mx, my, "Take all");
        iconButton(g, "refill", a[0] + a[2] - 18, a[1] + 3, 12, true, mx, my, "Refill stacks: put in what you carry of what's already here");
        int top = a[1] + 18;
        int bottom = a[1] + a[3] - 3;
        chestScroll = Math.max(0, Math.min(Math.max(0, chestContent() - (bottom - top)), chestScroll));
        for (CPlace p : containerPlaces()) {
            if (p.index() == 0 && p.headerY() < 0) {
                g.enableScissor((int) (ox + a[0] * scale), (int) (oy + top * scale), (int) (ox + (a[0] + a[2]) * scale), (int) (oy + bottom * scale));
                renderGrid(g, new GridRef(p.x(), p.y(), c.width(), c.height(), c.items()));
                renderOverflow(g, p, c, hover);
                g.disableScissor();
            }
        }
        scrollBar(g, a[0] + a[2] - 4, top, bottom - top, chestContent(), chestScroll);
    }

    private void renderOverflow(GuiGraphics g, CPlace p, TetrisNet.ContainerView c, Loc hover) {
        if (c.overflow().isEmpty()) {
            return;
        }
        int oy0 = p.y() + c.height() * C;
        small(g, "NO ROOM IN GRID", p.x(), oy0 + 2, Kit.BLOOD_LIGHT, 0.75f);
        for (int i = 0; i < c.overflow().size(); i++) {
            Loc loc = Loc.containerOverflow(p.index(), c.overflowSlots().get(i));
            slot(g, p.x() + (i % 9) * C, oy0 + 10 + (i / 9) * C, C, c.overflow().get(i), hover != null && hover.equals(loc), null);
        }
    }

    /** Clicks on the small controls: books, CLEAR, PIN, panel buttons, renames, toggles. True if one was hit. */
    private boolean clickControls(double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        TetrisNet.Sync s = ClientTetris.sync;
        int book = bookAt(x, y);
        if (book == 0) {
            listFolded = !listFolded;
            return true;
        }
        if (book == 1) {
            craftView = 1;
            dropHeld();
            layout();
            return true;
        }
        if (in(x, y, CLEAR_X, CLEAR_Y, CLEAR_W, 10)) {
            dropHeld();
            ClientPlayNetworking.send(new TetrisNet.Craft("#clear", 0));
            return true;
        }
        if (ghostRecipe != null && in(x, y, PIN_X, CLEAR_Y, PIN_W, 10)) {
            pinRecipe(ghostRecipe);
            return true;
        }
        int tx = QU_X + 9 * QU_STEP + 4;
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
                if (sec.kind == 0 && in(x, y, SEC_X + SEC_W - 28, sy, 28, 9)) {
                    ClientPlayNetworking.send(new TetrisNet.Action(2));
                    return true;
                }
                if (sec.kind != 0 && !sortDone) {
                    sortDone = true;
                    if (in(x, y, SEC_X + SEC_W - 10, sy, 9, 9)) {
                        ClientPlayNetworking.send(new TetrisNet.Action(0));
                        return true;
                    }
                }
            }
        }
        if (bookOpen()) {
            return false;
        }
        int[] st = stashArea();
        if (st != null) {
            if (in(x, y, st[0] + st[2] - 32, st[1] + 3, 12, 12)) {
                ClientPlayNetworking.send(new TetrisNet.Action(1));
                return true;
            }
            if (in(x, y, st[0] + st[2] - 18, st[1] + 3, 12, 12)) {
                ClientPlayNetworking.send(new TetrisNet.Action(8));
                return true;
            }
            int top = st[1] + 18;
            int bottom = st[1] + st[3] - 3;
            for (CPlace p : containerPlaces()) {
                int hy = p.headerY();
                if (hy < top || hy + 10 > bottom) {
                    continue;
                }
                int bx = st[0] + st[2] - 40;
                if (in(x, y, bx, hy, 10, 10)) {
                    int was = filterOpen;
                    filterOpen = was == p.index() ? -1 : p.index();
                    filterX = st[0] + st[2] - FILTER_W - 2;
                    filterY = hy + 11;
                    return true;
                }
                if (in(x, y, bx + 12, hy, 10, 10)) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(p.index(), 0, 0));
                    return true;
                }
                if (in(x, y, bx + 24, hy, 10, 10)) {
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(p.index(), 1, 0));
                    return true;
                }
                if (held.isEmpty() && in(x, y, p.x() + 10, hy, st[2] - 62, 9)) {
                    renameIndex = p.index();
                    renameText = s.containers().get(p.index()).name();
                    return true;
                }
            }
        }
        int[] ch = chestArea();
        if (ch != null) {
            TetrisNet.ContainerView c = s.containers().get(0);
            int nameW = Math.min(ch[2] - 110, (int) (font.width(c.name()) * 0.75f) + 2);
            if (held.isEmpty() && (in(x, y, ch[0] + 22 + nameW, ch[1] + 4, 32, 9) || in(x, y, ch[0] + 18, ch[1] + 4, nameW, 9))) {
                renameIndex = 0;
                renameText = c.name();
                return true;
            }
            for (int b = 0; b < 3; b++) {
                if (in(x, y, ch[0] + ch[2] - 46 + b * 14, ch[1] + 3, 12, 12)) {
                    // Sort (0), take all (1), refill stacks (3).
                    ClientPlayNetworking.send(new TetrisNet.ContainerAction(0, b == 2 ? 3 : b, 0));
                    return true;
                }
            }
        }
        return false;
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

    private int listTop() {
        return LIST_HEAD_Y + 14 + (ClientTetris.craft.inHideout() ? 0 : 12);
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

    private ItemStack renderCraftList(GuiGraphics g, List<CraftRow> rows, int mx, int my) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        ItemStack tip = ItemStack.EMPTY;
        if (!craft.inHideout()) {
            // The Hideout Bench is pinned first in the list; its ghost shows the pattern.
            int y0 = LIST_HEAD_Y + 14;
            g.fill(LIST_X, y0, LIST_X + 1, y0 + 10, Kit.GOLD);
            small(g, "No hideout here: make a bench (first below)", LIST_X + 4, y0 + 2, Kit.GOLD_LIGHT, 0.75f);
        }
        int top = listTop();
        int height = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).h;
        double max = Math.max(0, height - (R_BOTTOM - 3 - top));
        craftScroll = Math.max(0, Math.min(max, craftScroll));
        if (rows.isEmpty()) {
            g.drawString(font, craft.entries().isEmpty() ? "Nothing to make yet." : "No matches.", LIST_X, top + 4, Kit.ASH, false);
        }
        g.enableScissor((int) (ox + LIST_X * scale), (int) (oy + top * scale), (int) (ox + (LIST_X + LIST_W2) * scale), (int) (oy + (R_BOTTOM - 3) * scale));
        for (CraftRow row : rows) {
            int y = top + row.y - (int) craftScroll;
            if (y + row.h < top || y > R_BOTTOM) {
                continue;
            }
            if (row.entry == null) {
                boolean folded = COLLAPSED.contains(row.header) && search.isEmpty();
                boolean overHead = mx >= LIST_X && mx < LIST_X + LIST_W2 && my >= y && my < y + HEAD && my >= top;
                if (overHead) {
                    Kit.blit(g, "list/row_hover", LIST_X, y, LIST_W2, HEAD - 1);
                }
                g.drawString(font, (folded ? "▸ " : "▾ ") + row.header.label.toUpperCase(Locale.ROOT), LIST_X + 1, y + 3,
                        overHead ? Kit.GOLD_LIGHT : Kit.ASH, false);
                String n = String.valueOf(row.count);
                g.drawString(font, n, LIST_X + LIST_W2 - 2 - font.width(n), y + 3, Kit.ASH, false);
                continue;
            }
            TetrisNet.CraftEntry e = row.entry;
            boolean hover = mx >= LIST_X && mx < LIST_X + LIST_W2 && my >= y && my < y + ROW - 2 && my >= top;
            boolean selected = e.id().equals(ghostRecipe);
            Kit.blit(g, selected ? "list/row_selected" : hover ? "list/row_hover" : "list/row", LIST_X, y, LIST_W2, ROW - 2);
            g.renderItem(e.result(), LIST_X + 3, y + 2);
            g.renderItemDecorations(font, e.result(), LIST_X + 3, y + 2);
            String can = e.times() > 0 ? "x" + e.times() : e.times() < 0 ? "table" : "missing";
            int canW = font.width(can);
            g.drawString(font, font.plainSubstrByWidth(e.result().getHoverName().getString(), LIST_W2 - 32 - canW), LIST_X + 23, y + 6,
                    hover ? Kit.GOLD_LIGHT : Kit.BONE, false);
            g.drawString(font, can, LIST_X + LIST_W2 - 4 - canW, y + 6, e.times() > 0 ? Kit.ASH : e.times() < 0 ? Kit.GOLD : Kit.BLOOD_LIGHT, false);
            if (hover) {
                tip = e.result();
                hoverRecipe = e.id();
            }
        }
        g.disableScissor();
        if (max > 0) {
            int span = R_BOTTOM - 3 - top;
            int barH = Math.max(10, (int) (span * span / (span + max)));
            int barY = top + (int) ((span - barH) * craftScroll / max);
            Kit.blit(g, "scroll/handle", LIST_X + LIST_W2 + 1, barY, 4, barH);
        }
        return tip;
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
        if (tab == CRAFTING && StationTabs.click(0, height - 15, width, StationTabs.CRAFTING_TABLE, mouseX, mouseY)) {
            return true;
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
        if (craftView == 1) {
            searchFocused = x >= SEARCH_X && x < LIST_W && y >= 0 && y < 14;
            int v = subTabAt(0, x, y);
            if (v >= 0) {
                craftView = v;
                dropHeld();
                layout();
                return true;
            }
            return clickRecipes(x, y, button);
        }
        TetrisNet.CraftList craft = ClientTetris.craft;
        searchFocused = bookOpen() && in(x, y, LIST_X + 50, LIST_HEAD_Y, LIST_W2 - 50, 11);
        if (searchFocused) {
            return true;
        }
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
        if (bookOpen() && held.isEmpty() && in(x, y, LIST_X, listTop(), LIST_W2, R_BOTTOM - 3 - listTop())) {
            double at = y - listTop() + craftScroll;
            for (CraftRow row : craftRows()) {
                if (row.entry == null && row.header != null && at >= row.y && at < row.y + HEAD) {
                    if (!COLLAPSED.remove(row.header)) {
                        COLLAPSED.add(row.header);
                    }
                    return true;
                }
                if (row.entry != null && at >= row.y && at < row.y + ROW - 2) {
                    // Shows the pattern; nothing places items for the player (4.1).
                    ghostRecipe = row.entry.id().equals(ghostRecipe) ? null : row.entry.id();
                    return true;
                }
            }
            return true;
        }
        if (button != 0 && button != 1) {
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
        if (tab == CRAFTING && craftView == 1) {
            if (vx(mouseX) < LIST_W) {
                recipeScroll -= scrollY * 20;
            }
        } else if (tab == INVENTORY) {
            double x = vx(mouseX);
            double y = vy(mouseY);
            int[] st = stashArea();
            int[] ch = chestArea();
            if (in(x, y, COL_X, COL_Y, COL_W, COL_H)) {
                colScroll -= scrollY * 18;
            } else if (bookOpen() && in(x, y, R_X, R_Y, R_W, R_BOTTOM - R_Y)) {
                craftScroll -= scrollY * 20;
            } else if (st != null && in(x, y, st[0], st[1], st[2], st[3])) {
                stashScroll -= scrollY * 18;
            } else if (ch != null && in(x, y, ch[0], ch[1], ch[2], ch[3])) {
                chestScroll -= scrollY * 18;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T && renameIndex < 0 && !searchFocused && !hoverStack.isEmpty()) {
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
        if (tab == CRAFTING && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                craftScroll = 0;
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
        if (tab == CRAFTING && searchFocused && c >= ' ' && search.length() < 40) {
            search += c;
            craftScroll = 0;
            return true;
        }
        return super.charTyped(c, modifiers);
    }
}
