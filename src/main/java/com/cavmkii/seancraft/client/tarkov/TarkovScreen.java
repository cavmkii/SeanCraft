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
 * The Tarkov screen. GEAR: equipment, hotbar, the Tetris grids, and any open container or stash on the right.
 * CRAFTING: recipes you can make from everything you carry. SKILLS: character level, attributes and use-skills.
 * STATUS: body health and meds.
 *
 * <p>GEAR controls, mostly vanilla's: click to pick up and put down, right-click to pick up half or put down one,
 * Shift+click (or Ctrl) to transfer, R to rotate, 1-9 to send the hovered item to a hotbar slot, Q to drop one
 * (Ctrl+Q the stack), Esc to let go.
 */
public class TarkovScreen extends Screen {
    public static final int GEAR = 0;
    public static final int CRAFTING = 1;
    public static final int SKILLS = 2;
    public static final int STATUS = 3;
    public static final int WEB = 4;
    private static final String[] TABS = {"GEAR", "CRAFTING", "SKILLS", "STATUS", "WEB"};

    private static final int C = 18;
    private static final int W = 426;
    private static final int H = 220;
    private static final int TOP = 24;
    private static final int COL4 = 264;
    private static final int COL4_W = 9 * C;
    private static final int ROW = 22;

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

    private double containerScroll;
    private double craftScroll;
    private double matScroll;
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
        if (tab == CRAFTING) {
            ClientPlayNetworking.send(new TetrisNet.Craft("", 0));
        }
    }

    @Override
    protected void init() {
        // Room at the bottom for the station tabs (hideout, Weapon Bench...).
        int bottom = 16;
        scale = Math.min(1f, Math.min((width - 8) / (float) W, (height - TOP - 4 - bottom) / (float) H));
        ox = (width - W * scale) / 2f;
        oy = TOP + Math.max(0, (height - TOP - bottom - H * scale) / 2f - 4);
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
        out.add(new Box(GridId.POCKETS, 66, 10, InvData.POCKET_W, InvData.POCKET_H, s.pockets()));
        if (s.rig().getItem() instanceof GearItem g) {
            out.add(new Box(GridId.RIG, 66, 60, g.spec.gridWidth, g.spec.gridHeight, s.rigItems()));
        }
        out.add(new Box(GridId.POUCH, 66, 128, s.pouchW(), s.pouchH(), s.pouch()));
        if (s.backpack().getItem() instanceof GearItem g) {
            out.add(new Box(GridId.BACKPACK, 164, 10, g.spec.gridWidth, g.spec.gridHeight, s.backpackItems()));
        }
        return out;
    }

    /** x, y, size of an equipment place. */
    private static int[] equipRect(int slot) {
        return switch (slot) {
            case 4 -> new int[] {25, 12, 36};
            case 5 -> new int[] {25, 62, 36};
            case 6 -> new int[] {0, 108, 22};
            default -> new int[] {0, 12 + slot * 24, 22};
        };
    }

    /** Hotbar auto-refill toggles beside the HOTBAR label: x, width. */
    private static final int[][] TOGGLES = {{44, 52}, {100, 62}};

    private static final String[] EQUIP_NAMES = {"Head", "Chest", "Legs", "Feet", "Rig", "Backpack", "Off-hand"};
    /** Outlines shown in empty equipment slots (ghost/*). */
    private static final String[] EQUIP_GHOSTS = {"helmet", "chestplate", "leggings", "boots", "rig", "backpack", "offhand"};

    private static final int BTN_X = 164;
    private static final int BTN_W = 90;
    private static final int[] BTN_Y = {146, 159, 172, 185};
    private static final String[] BTN = {"SORT", "STASH ALL", "ORGANIZE STASH", "DROP UNSORTED"};
    private static final int[] BTN_ACTION = {0, 1, 8, 2};
    private static final int BTN_H = 12;
    private static final int DISCARD_Y = 199;

    /** Rows on the right: unsorted first, then each container. y is before scrolling. */
    private record Section(int kind, int index, String title, int y, int height) {}

    private List<Section> sections() {
        TetrisNet.Sync s = ClientTetris.sync;
        List<Section> out = new ArrayList<>();
        int y = 0;
        if (!s.unsorted().isEmpty()) {
            int h = 10 + rows(s.unsorted().size()) * C;
            out.add(new Section(Loc.UNSORTED, 0, "UNSORTED (no room)", y, h));
            y += h + 6;
        }
        for (int i = 0; i < s.containers().size(); i++) {
            TetrisNet.ContainerView c = s.containers().get(i);
            int h = 10 + c.height() * C + (c.overflow().isEmpty() ? 0 : 10 + rows(c.overflow().size()) * C);
            out.add(new Section(Loc.CONTAINER, i, c.name().toUpperCase(Locale.ROOT), y, h));
            y += h + 6;
        }
        return out;
    }

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
        if (loc.kind() == Loc.CONTAINER && loc.b() >= 0 && loc.a() < ClientTetris.sync.containers().size()) {
            TetrisNet.ContainerView c = ClientTetris.sync.containers().get(loc.a());
            for (Section sec : sections()) {
                if (sec.kind == Loc.CONTAINER && sec.index == loc.a()) {
                    return new GridRef(COL4, sec.y - (int) containerScroll + 10, c.width(), c.height(), c.items());
                }
            }
        }
        return null;
    }

    private static int rows(int n) {
        return Math.max(1, (n + 8) / 9);
    }

    private int sectionsHeight(List<Section> list) {
        if (list.isEmpty()) {
            return 0;
        }
        Section last = list.get(list.size() - 1);
        return last.y + last.height;
    }

    /** What's under a point (virtual coords) on the GEAR tab. For grids it's the cell. */
    private Loc locAt(double x, double y) {
        for (int slot = 0; slot < 7; slot++) {
            int[] r = equipRect(slot);
            if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[2]) {
                return Loc.equip(slot);
            }
        }
        if (y >= 190 && y < 190 + C && x >= 0 && x < 9 * C) {
            return Loc.hand((int) (x / C));
        }
        for (Box b : boxes()) {
            if (x >= b.x && x < b.x + b.w * C && y >= b.y && y < b.y + b.h * C) {
                return Loc.grid(b.id, (int) ((x - b.x) / C), (int) ((y - b.y) / C));
            }
        }
        if (x >= BTN_X && x < BTN_X + BTN_W && y >= DISCARD_Y && y < DISCARD_Y + 19) {
            return Loc.discard();
        }
        if (x >= COL4 && x < COL4 + COL4_W && y >= 0 && y < H) {
            for (Section sec : sections()) {
                int top = sec.y - (int) containerScroll + 10;
                int col = (int) ((x - COL4) / C);
                if (sec.kind == Loc.UNSORTED) {
                    int n = ClientTetris.sync.unsorted().size();
                    if (y >= top && y < top + rows(n) * C) {
                        int i = (int) ((y - top) / C) * 9 + col;
                        if (i >= 0 && i < n) {
                            return Loc.unsorted(i);
                        }
                    }
                    continue;
                }
                TetrisNet.ContainerView c = ClientTetris.sync.containers().get(sec.index);
                if (y >= top && y < top + c.height() * C && col < c.width()) {
                    return Loc.container(sec.index, col, (int) ((y - top) / C));
                }
                int otop = top + c.height() * C + 10;
                if (!c.overflow().isEmpty() && y >= otop && y < otop + rows(c.overflow().size()) * C) {
                    int i = (int) ((y - otop) / C) * 9 + col;
                    if (i >= 0 && i < c.overflow().size()) {
                        return Loc.containerOverflow(sec.index, c.overflowSlots().get(i));
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
            default -> ItemStack.EMPTY;
        };
    }

    // ================================================================== render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        settleSwap();
        g.fill(0, 0, width, height, Kit.BACKDROP);
        int[] pr = panelRect();
        Kit.panel(g, pr[0], pr[1], pr[2], pr[3]);
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
            ItemStack tip = switch (tab) {
                case GEAR -> renderGear(g, mx, my);
                case CRAFTING -> renderCrafting(g, mx, my);
                default -> {
                    renderSkills(g);
                    yield ItemStack.EMPTY;
                }
            };
            g.pose().popPose();
            hoverStack = tip;
            if (!tip.isEmpty() && held.isEmpty()) {
                g.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
        renderTabs(g, mouseX, mouseY);
        if (tab == CRAFTING) {
            StationTabs.render(g, font, 0, height - 15, width, StationTabs.CRAFTING_TABLE, mouseX, mouseY);
        }
    }

    /** Top of the panel; the tab row sits on its edge (Appendix C, spacing rules). */
    private static final int PANEL_TOP = TOP - 10;

    /** The window frame, in screen coordinates: around the scaled content, or the full width for STATUS and WEB. */
    private int[] panelRect() {
        if (tab == STATUS || tab == WEB) {
            return new int[] {2, PANEL_TOP, width - 4, height - PANEL_TOP - 2};
        }
        int x = Math.max(2, (int) Math.floor(ox - 8 * scale) - 2);
        int right = Math.min(width - 2, (int) Math.ceil(ox + W * scale + 8 * scale) + 2);
        int bottom = Math.min(height - (tab == CRAFTING ? 17 : 2), (int) Math.ceil(oy + H * scale + 8 * scale));
        return new int[] {x, PANEL_TOP, right - x, bottom - PANEL_TOP};
    }

    private int tabX(int t) {
        int x = panelRect()[0] + 6;
        for (int i = 0; i < t; i++) {
            x += Kit.tabWidth(font, TABS[i]) + 1;
        }
        return x;
    }

    private boolean overTab(int t, double mouseX, double mouseY) {
        int x = tabX(t);
        return mouseX >= x && mouseX < x + Kit.tabWidth(font, TABS[t]) && mouseY >= PANEL_TOP - 12 && mouseY < PANEL_TOP + 5;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int t = 0; t < TABS.length; t++) {
            if (t != tab) {
                Kit.tab(g, font, TABS[t], tabX(t), PANEL_TOP, Kit.tabWidth(font, TABS[t]), false, overTab(t, mouseX, mouseY));
            }
        }
        Kit.tab(g, font, TABS[tab], tabX(tab), PANEL_TOP, Kit.tabWidth(font, TABS[tab]), true, false);
        // The raid state, always in view while the inventory is open (C.4).
        // Shorter forms when the tab row leaves too little room.
        int[] pr = panelRect();
        int tabsEnd = tabX(TABS.length - 1) + Kit.tabWidth(font, TABS[TABS.length - 1]) + 8;
        for (String raid : RaidHud.stateLines()) {
            int rx = pr[0] + pr[2] - 10 - font.width(raid);
            if (rx >= tabsEnd) {
                Kit.text(g, font, raid, rx, PANEL_TOP - 9, RaidHud.stateColor());
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

    private ItemStack renderGear(GuiGraphics g, int mx, int my) {
        lastMx = mx;
        lastMy = my;
        Loc hover = locAt(mx, my);
        ItemStack tip = stackAt(hover);
        TetrisNet.Sync s = ClientTetris.sync;

        label(g, "EQUIPMENT", 0, 0);
        for (int slot = 0; slot < 7; slot++) {
            int[] r = equipRect(slot);
            slot(g, r[0], r[1], r[2], stackAt(Loc.equip(slot)), hover != null && hover.equals(Loc.equip(slot)), null);
            if (stackAt(Loc.equip(slot)).isEmpty()) {
                Kit.blit(g, "ghost/" + EQUIP_GHOSTS[slot], r[0] + (r[2] - 16) / 2, r[1] + (r[2] - 16) / 2, 16, 16);
            }
            if (!held.isEmpty() && equipAccepts(slot, held)) {
                // Where the held item can be worn.
                int c = hover != null && hover.equals(Loc.equip(slot)) ? Kit.GOLD_LIGHT : Kit.GOLD;
                g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 1, r[2] + 1, c);
            }
        }
        label(g, "HOTBAR", 0, 180);
        for (int t = 0; t < 2; t++) {
            int[] r = TOGGLES[t];
            boolean on = t == 0 ? s.autoRefill() : s.refillFromStash();
            boolean over = mx >= r[0] && mx < r[0] + r[1] && my >= 179 && my < 188;
            Kit.smallButton(g, font, (t == 0 ? "REFILL " : "FROM STASH ") + (on ? "ON" : "OFF"), r[0], 179, r[1], 9, true, over, on, 0.75f);
        }
        for (int i = 0; i < 9; i++) {
            slot(g, i * C, 190, C, stackAt(Loc.hand(i)), hover != null && hover.equals(Loc.hand(i)), null);
        }
        // Load and roll under the hotbar, so packing shows what it does to the dodge (C.4, 9.4.7).
        com.cavmkii.seancraft.combat.Load.State load = com.cavmkii.seancraft.client.combat.ClientCombat.load();
        com.cavmkii.seancraft.combat.Load.Tier tier = load.tier();
        String loadText = String.format("LOAD %d%%   %s   %s", Math.round(load.fraction() * 100), tier.label,
                tier.rollTicks == 0 ? "NO ROLL" : String.format("ROLL %.1f S", tier.rollTicks / 20f));
        g.pose().pushPose();
        g.pose().translate(0, 211, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, loadText, 0, 0, tier == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED ? Kit.BLOOD_LIGHT
                : tier == com.cavmkii.seancraft.combat.Load.Tier.HEAVY ? Kit.EMBER : Kit.ASH, false);
        g.pose().popPose();
        Kit.bar(g, "load", 9 * C - 40, 210, 40, (float) Math.min(1, load.fraction()));

        for (Box b : boxes()) {
            // Every grid is labelled with what it is and its size (C.4).
            String title = switch (b.id) {
                case POCKETS -> "POCKETS";
                case RIG -> ClientTetris.sync.rig().isEmpty() ? "RIG" : ClientTetris.sync.rig().getHoverName().getString().toUpperCase(Locale.ROOT);
                case BACKPACK -> ClientTetris.sync.backpack().isEmpty() ? "BACKPACK"
                        : ClientTetris.sync.backpack().getHoverName().getString().toUpperCase(Locale.ROOT);
                case POUCH -> "SECURE POUCH";
            } + "  " + b.w + "X" + b.h;
            // The secure pouch label is gold: gold marks what survives death.
            Kit.label(g, font, title, b.x, b.y - 10, b.id == GridId.POUCH ? Kit.GOLD : Kit.ASH);
            renderGrid(g, b);
        }
        if (s.rig().isEmpty()) {
            g.drawString(font, "no rig", 66, 64, Kit.DISABLED, false);
        }
        if (s.backpack().isEmpty()) {
            g.drawString(font, "no backpack", 164, 14, Kit.DISABLED, false);
        }

        for (int i = 0; i < BTN.length; i++) {
            boolean h = mx >= BTN_X && mx < BTN_X + BTN_W && my >= BTN_Y[i] && my < BTN_Y[i] + BTN_H;
            boolean enabled = i != 1 || !s.containers().isEmpty();
            enabled &= i != 2 || s.stash();
            enabled &= i != 3 || !s.unsorted().isEmpty();
            Kit.smallButton(g, font, BTN[i], BTN_X, BTN_Y[i], BTN_W, BTN_H, enabled, h, false, 0.85f);
        }
        boolean overDiscard = hover != null && hover.kind() == Loc.DISCARD;
        // Blood marks danger: discarding destroys the item.
        Kit.buttonBg(g, BTN_X, DISCARD_Y, BTN_W, 19, true, overDiscard && !held.isEmpty(), false);
        g.renderOutline(BTN_X, DISCARD_Y, BTN_W, 19, overDiscard && !held.isEmpty() ? Kit.BLOOD_LIGHT : Kit.BLOOD);
        Kit.text(g, font, "DISCARD", BTN_X + (BTN_W - font.width("DISCARD")) / 2, DISCARD_Y + 6, Kit.BLOOD_LIGHT);

        renderRight(g, hover);
        renderFilter(g, mx, my);
        renderHeld(g, mx, my, hover);
        return tip;
    }

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
        int pw = w * C;
        int ph = h * C;
        if (bg != 0) {
            g.fill(px, py, px + pw - 1, py + ph - 1, bg);
        }
        if (w > 1 || h > 1) {
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

    private void renderRight(GuiGraphics g, Loc hover) {
        List<Section> secs = sections();
        double max = Math.max(0, sectionsHeight(secs) - H);
        containerScroll = Math.max(0, Math.min(max, containerScroll));
        if (secs.isEmpty()) {
            g.drawWordWrap(font, Component.literal(ClientTetris.sync.stash()
                    ? "Your stash is empty: place chests or barrels inside the hideout."
                    : "Open a chest or barrel to see it here. Inside your hideout, the stash shows here."), COL4, 4, COL4_W, Kit.ASH);
            return;
        }
        int sx0 = (int) (ox + COL4 * scale);
        int sy0 = (int) oy;
        g.enableScissor(sx0, sy0, (int) (ox + (COL4 + COL4_W) * scale) + 1, (int) (oy + H * scale));
        for (Section sec : secs) {
            int y = sec.y - (int) containerScroll;
            if (sec.kind == Loc.UNSORTED) {
                g.drawString(font, sec.title, COL4, y, Kit.BLOOD_LIGHT, false);
            } else {
                renderContainerTitle(g, sec, y);
            }
            if (sec.kind == Loc.UNSORTED) {
                List<ItemStack> list = ClientTetris.sync.unsorted();
                for (int i = 0; i < list.size(); i++) {
                    Loc loc = Loc.unsorted(i);
                    slot(g, COL4 + (i % 9) * C, y + 10 + (i / 9) * C, C, list.get(i), hover != null && hover.equals(loc), null);
                }
                continue;
            }
            TetrisNet.ContainerView c = ClientTetris.sync.containers().get(sec.index);
            for (int b = 0; b < 3; b++) {
                int[] r = containerButton(y, b);
                boolean hb = lastMx >= r[0] && lastMx < r[0] + r[2] && lastMy >= r[1] && lastMy < r[1] + 9;
                boolean filtered = b == 2 && c.filter() != com.cavmkii.seancraft.tetris.StorageCategory.ALL;
                Kit.smallButton(g, font, b == 0 ? "SORT" : b == 1 ? "TAKE ALL" : "FILTER", r[0], r[1], r[2], 9, true, hb, filtered, 0.75f);
            }
            renderGrid(g, new GridRef(COL4, y + 10, c.width(), c.height(), c.items()));
            if (!c.overflow().isEmpty()) {
                int oy0 = y + 10 + c.height() * C;
                g.drawString(font, "NO ROOM IN GRID", COL4, oy0 + 1, Kit.BLOOD_LIGHT, false);
                for (int i = 0; i < c.overflow().size(); i++) {
                    Loc loc = Loc.containerOverflow(sec.index, c.overflowSlots().get(i));
                    slot(g, COL4 + (i % 9) * C, oy0 + 10 + (i / 9) * C, C, c.overflow().get(i), hover != null && hover.equals(loc), null);
                }
            }
        }
        g.disableScissor();
        if (max > 0) {
            int barH = Math.max(10, (int) (H * H / (H + max)));
            int barY = (int) ((H - barH) * containerScroll / max);
            Kit.blit(g, "scroll/handle", COL4 + COL4_W + 1, barY, 6, barH);
        }
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
    private static final int TITLE_W = COL4_W - 102;

    /** Block icon, then the name (smaller, so it fits beside the buttons). Click it to rename. */
    private void renderContainerTitle(GuiGraphics g, Section sec, int y) {
        TetrisNet.ContainerView c = ClientTetris.sync.containers().get(sec.index);
        g.pose().pushPose();
        g.pose().translate(COL4, y - 1, 0);
        g.pose().scale(0.625f, 0.625f, 1);
        g.renderItem(c.icon(), 0, 0);
        g.pose().popPose();
        boolean editing = renameIndex == sec.index;
        boolean hover = lastMx >= COL4 && lastMx < COL4 + TITLE_W && lastMy >= y - 1 && lastMy < y + 9;
        if (editing) {
            Kit.blit(g, "text_field/focused", COL4 + 11, y - 1, TITLE_W - 11, 9);
        }
        String text = editing ? renameText + (ticks / 6 % 2 == 0 ? "_" : "") : c.name();
        g.pose().pushPose();
        // A name a little too long drops to a smaller size before it gets cut.
        float k = font.width(text) * 0.75f <= TITLE_W - 15 ? 0.75f : 0.625f;
        g.pose().translate(COL4 + 13, y + (k < 0.75f ? 2 : 1), 0);
        g.pose().scale(k, k, 1);
        String fit = editing ? font.plainSubstrByWidth(text, (int) ((TITLE_W - 15) / k), true)
                : font.plainSubstrByWidth(text, (int) ((TITLE_W - 15) / k));
        g.drawString(font, fit, 0, 0, editing ? Kit.BONE : hover ? Kit.GOLD_LIGHT : Kit.ASH, false);
        g.pose().popPose();
    }

    private int filterOpen = -1;
    private int filterY;
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
        int x = COL4 + COL4_W - FILTER_W;
        int y = Math.min(filterY, H - (cats.length + 2) * FILTER_ROW - 6);
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
        int x = COL4 + COL4_W - FILTER_W;
        int y = Math.min(filterY, H - (cats.length + 2) * FILTER_ROW - 6);
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

    /** x, y, width of a container section's SORT (0) or TAKE ALL (1) button; {@code y} is the section's on-screen top. */
    private static int[] containerButton(int y, int which) {
        return switch (which) {
            case 0 -> new int[] {COL4 + COL4_W - 66, y - 1, 24};
            case 1 -> new int[] {COL4 + COL4_W - 40, y - 1, 40};
            default -> new int[] {COL4 + COL4_W - 99, y - 1, 31};
        };
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
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        drawPlaced(g, shown, mx - 9, my - 9, w, h, heldRot && size[0] != size[1], 0);
        g.pose().popPose();
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
    private void pinRecipe(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        var holder = key == null ? null : minecraft.level.getRecipeManager().byKey(key).orElse(null);
        if (holder != null && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe) {
            for (IngredientLine line : ingredientsOf(recipe)) {
                ClientTracker.add(line.display.getItem(), line.need);
            }
        }
    }

    // Layout of the CRAFTING tab: recipe list on the left, grid, result and materials on the right.
    private static final int LIST_W = 250;
    private static final int SEARCH_X = 100;
    /** CRAFTING sub-tab: 0 the grid, 1 the recipe book. */
    private int craftView;
    private static final int PX = 262;
    private static final int CELL = 22;
    private static final int GRID_Y = 14;
    private static final int RESULT_X = PX + 3 * CELL + 26;
    private static final int RESULT_Y = GRID_Y + CELL - 4;
    private static final int CLEAR_X = W - 40;
    private static final int PIN_X = RESULT_X - 2;
    private static final int PIN_Y = RESULT_Y + 50;
    private static final int PIN_W = 62;
    private static final int MAT_Y = GRID_Y + 3 * CELL + 18;
    /** The hotbar, under the materials. */
    private static final int CRAFT_HOTBAR_Y = H - C;
    private static final int MAT_BOTTOM = CRAFT_HOTBAR_Y - 12;

    private int listTop() {
        return ClientTetris.craft.inHideout() ? 20 : 58;
    }

    private int cellAt(double x, double y) {
        if (x < PX || x >= PX + 3 * CELL || y < GRID_Y || y >= GRID_Y + 3 * CELL) {
            return -1;
        }
        return (int) ((y - GRID_Y) / CELL) * 3 + (int) ((x - PX) / CELL);
    }

    private int craftHotbarAt(double x, double y) {
        if (x < PX || x >= PX + 9 * C || y < CRAFT_HOTBAR_Y || y >= CRAFT_HOTBAR_Y + C) {
            return -1;
        }
        return (int) ((x - PX) / C);
    }

    private boolean overResult(double x, double y) {
        return x >= RESULT_X && x < RESULT_X + 26 && y >= RESULT_Y && y < RESULT_Y + 26;
    }

    private int materialAt(double x, double y) {
        int top = MAT_Y + 10 - (int) matScroll;
        if (x < PX || x >= PX + 9 * C || y < Math.max(top, MAT_Y + 10) || y >= MAT_BOTTOM) {
            return -2;
        }
        int i = (int) ((y - top) / C) * 9 + (int) ((x - PX) / C);
        return i >= 0 && i < ClientTetris.craft.materials().size() ? i : -1;
    }

    private ItemStack renderCrafting(GuiGraphics g, int mx, int my) {
        TetrisNet.CraftList craft = ClientTetris.craft;
        for (int v = 0; v < 2; v++) {
            int bx = v * 48;
            boolean active = craftView == v;
            boolean over = mx >= bx && mx < bx + 44 && my >= 0 && my < 14;
            Kit.button(g, font, v == 0 ? "CRAFT" : "RECIPES", bx, 0, 44, 14, true, over && !active, active);
        }
        Kit.blit(g, searchFocused ? "text_field/focused" : "text_field/normal", SEARCH_X, 0, LIST_W - SEARCH_X, 14);
        String shown = search.isEmpty() && !searchFocused ? "Search..." : search + (searchFocused && ticks / 6 % 2 == 0 ? "_" : "");
        g.drawString(font, font.plainSubstrByWidth(shown, LIST_W - SEARCH_X - 6, true), SEARCH_X + 4, 3,
                search.isEmpty() && !searchFocused ? Kit.ASH : Kit.BONE, false);
        if (craftView == 1) {
            return renderRecipes(g, mx, my);
        }
        ItemStack tip = ItemStack.EMPTY;

        if (!craft.inHideout()) {
            Kit.inset(g, 0, 18, LIST_W, 36);
            g.fill(1, 19, 2, 53, Kit.GOLD);
            g.drawString(font, "No hideout here. Start one:", 6, 21, Kit.GOLD_LIGHT, false);
            g.pose().pushPose();
            g.pose().translate(6, 32, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, "Hideout Bench: 3 cobblestone on top, then plank,", 0, 0, Kit.BONE, false);
            g.drawString(font, "crafting table, plank, then 3 planks. Place it and", 0, 9, Kit.BONE, false);
            g.drawString(font, "everything within 30 blocks is your hideout.", 0, 18, Kit.BONE, false);
            g.pose().popPose();
        }

        // Recipe list, grouped by category.
        List<CraftRow> rows = craftRows();
        int top = listTop();
        int height = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).h;
        double max = Math.max(0, height - (H - top));
        craftScroll = Math.max(0, Math.min(max, craftScroll));
        if (rows.isEmpty()) {
            g.drawString(font, craft.entries().isEmpty() ? "Nothing you can make right now." : "No matches.", 0, top + 4, Kit.ASH, false);
        }
        g.enableScissor((int) ox, (int) (oy + top * scale), (int) (ox + LIST_W * scale), (int) (oy + H * scale));
        for (CraftRow row : rows) {
            int y = top + row.y - (int) craftScroll;
            if (y + row.h < top || y > H) {
                continue;
            }
            if (row.entry == null) {
                boolean folded = COLLAPSED.contains(row.header) && search.isEmpty();
                boolean overHead = mx >= 0 && mx < LIST_W && my >= y && my < y + HEAD && my >= top;
                if (overHead) {
                    Kit.blit(g, "list/row_hover", 0, y, LIST_W, HEAD - 1);
                }
                g.drawString(font, (folded ? "▸ " : "▾ ") + row.header.label.toUpperCase(Locale.ROOT), 1, y + 3,
                        overHead ? Kit.GOLD_LIGHT : Kit.ASH, false);
                String n = String.valueOf(row.count);
                g.drawString(font, n, LIST_W - 2 - font.width(n), y + 3, Kit.ASH, false);
                continue;
            }
            TetrisNet.CraftEntry e = row.entry;
            boolean hover = mx >= 0 && mx < LIST_W && my >= y && my < y + ROW - 2 && my >= top;
            boolean selected = e.id().equals(craft.selected());
            Kit.blit(g, selected ? "list/row_selected" : hover ? "list/row_hover" : "list/row", 0, y, LIST_W, ROW - 2);
            g.renderItem(e.result(), 3, y + 2);
            g.renderItemDecorations(font, e.result(), 3, y + 2);
            g.drawString(font, font.plainSubstrByWidth(e.result().getHoverName().getString(), LIST_W - 90), 24, y + 6, hover ? Kit.GOLD_LIGHT : Kit.BONE, false);
            String can = e.times() > 0 ? "x" + e.times() : e.times() < 0 ? "needs crafting table" : "missing items";
            g.drawString(font, can, LIST_W - 6 - font.width(can), y + 6, e.times() > 0 ? Kit.ASH : e.times() < 0 ? Kit.GOLD : Kit.BLOOD_LIGHT, false);
            if (hover) {
                tip = e.result();
            }
        }
        g.disableScissor();

        // Grid, result, clear.
        g.drawString(font, "CRAFTING GRID", PX, 2, Kit.ASH, false);
        boolean clearHover = mx >= CLEAR_X && mx < W && my >= 0 && my < 12;
        Kit.button(g, font, "CLEAR", CLEAR_X, 0, 40, 12, true, clearHover, false);
        if (craft.selected() != null && !craft.selected().isEmpty()) {
            boolean pinHover = mx >= PIN_X && mx < PIN_X + PIN_W && my >= PIN_Y && my < PIN_Y + 12;
            Kit.button(g, font, "PIN TO HUD", PIN_X, PIN_Y, PIN_W, 12, true, pinHover, false);
        }
        for (int c = 0; c < 9; c++) {
            int x = PX + (c % 3) * CELL;
            int y = GRID_Y + (c / 3) * CELL;
            boolean hover = cellAt(mx, my) == c;
            boolean small = !craft.table() && (c % 3 == 2 || c / 3 == 2);
            Kit.slot(g, x, y, CELL, small ? Kit.SlotState.LOCKED : hover ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            ItemStack in = c < craft.grid().size() ? craft.grid().get(c) : ItemStack.EMPTY;
            boolean lifted = heldFrom != null && heldFrom.kind() == Loc.CRAFT && heldFrom.a() == c && heldCount == 0;
            if (!in.isEmpty() && !lifted) {
                g.renderItem(in, x + 3, y + 3);
                g.renderItemDecorations(font, in, x + 3, y + 3);
                if (hover) {
                    tip = in;
                }
            }
        }
        Kit.blit(g, craft.result().isEmpty() ? "progress/arrow_empty" : "progress/arrow_full", PX + 3 * CELL + 2, RESULT_Y + 6, 22, 15);
        boolean resultHover = overResult(mx, my);
        Kit.slot(g, RESULT_X, RESULT_Y, 26, resultHover && !craft.result().isEmpty() ? Kit.SlotState.HOVER : Kit.SlotState.RESULT);
        if (!craft.result().isEmpty()) {
            g.renderItem(craft.result(), RESULT_X + 5, RESULT_Y + 5);
            g.renderItemDecorations(font, craft.result(), RESULT_X + 5, RESULT_Y + 5);
            if (resultHover) {
                tip = craft.result();
            }
        }
        g.pose().pushPose();
        g.pose().translate(RESULT_X - 2, RESULT_Y + 30, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        if (craft.needsTable()) {
            g.drawString(font, "Needs a crafting table", 0, 0, Kit.BLOOD_LIGHT, false);
            g.drawString(font, "or the hideout", 0, 9, Kit.BLOOD_LIGHT, false);
        } else if (!craft.result().isEmpty()) {
            g.drawString(font, "Click: craft", 0, 0, Kit.ASH, false);
            g.drawString(font, "Shift: craft all", 0, 9, Kit.ASH, false);
        } else if (!craft.table()) {
            g.drawString(font, "2x2 only here.", 0, 0, Kit.ASH, false);
            g.drawString(font, "3x3: near a table", 0, 9, Kit.ASH, false);
        }
        g.pose().popPose();

        // Materials.
        List<ItemStack> mats = craft.materials();
        g.drawString(font, "MATERIALS" + (craft.inHideout() ? " (incl. stash)" : ""), PX, MAT_Y, Kit.ASH, false);
        double mmax = Math.max(0, ((mats.size() + 8) / 9) * C - (MAT_BOTTOM - MAT_Y - 10));
        matScroll = Math.max(0, Math.min(mmax, matScroll));
        g.enableScissor((int) (ox + PX * scale), (int) (oy + (MAT_Y + 10) * scale), (int) (ox + W * scale), (int) (oy + MAT_BOTTOM * scale));
        for (int i = 0; i < mats.size(); i++) {
            int x = PX + (i % 9) * C;
            int y = MAT_Y + 10 + (i / 9) * C - (int) matScroll;
            ItemStack m = mats.get(i);
            boolean hover = materialAt(mx, my) == i;
            Kit.slot(g, x, y, C, hover ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            g.renderItem(m, x + 1, y + 1);
            g.renderItemDecorations(font, m, x + 1, y + 1, m.getCount() > 999 ? "999+" : null);
            if (hover) {
                tip = m;
            }
        }
        g.disableScissor();
        if (mats.isEmpty()) {
            g.drawString(font, "Nothing to craft with.", PX, MAT_Y + 14, Kit.ASH, false);
        }

        // Hotbar.
        g.drawString(font, "HOTBAR", PX, CRAFT_HOTBAR_Y - 10, Kit.ASH, false);
        for (int i = 0; i < 9; i++) {
            int x = PX + i * C;
            ItemStack h = minecraft.player.getInventory().items.get(i);
            boolean over = craftHotbarAt(mx, my) == i;
            slot(g, x, CRAFT_HOTBAR_Y, C, h, over, null);
            if (over && !h.isEmpty()) {
                tip = h;
            }
        }

        if (!held.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            ItemStack shownHeld = heldCount > 0 ? held.copyWithCount(heldCount) : held;
            g.renderItem(shownHeld, mx - 8, my - 8);
            g.renderItemDecorations(font, shownHeld, mx - 8, my - 8);
            g.pose().popPose();
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
            for (int b = 0; b < 2; b++) {
                int bx = PX + b * 84;
                boolean over = mx >= bx && mx < bx + 80 && my >= REC_BTN_Y && my < REC_BTN_Y + 14;
                Kit.button(g, font, b == 0 ? "LAY OUT" : "PIN TO HUD", bx, REC_BTN_Y, 80, 14, true, over, false);
            }
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
            }
            return true;
        }
        if (selectedRecipe != null && y >= REC_BTN_Y && y < REC_BTN_Y + 14) {
            if (x >= PX && x < PX + 80) {
                ClientPlayNetworking.send(new TetrisNet.Craft(selectedRecipe.toString(), 0));
                craftView = 0;
                return true;
            }
            if (x >= PX + 84 && x < PX + 164) {
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

    private void renderSkills(GuiGraphics g) {
        PlayerData data = ClientRpg.data;
        Stats stats = ClientRpg.stats;
        String head = "Level " + data.level + (data.level < PlayerData.MAX_LEVEL ? "  (" + data.xp + " / " + PlayerData.xpToNext(data.level) + " xp)" : "")
                + "   Free points " + data.freePoints()
                + (data.origin != null ? "   " + data.origin.label + (data.spec != null ? ", " + data.spec.label : "") : "   No origin yet (K)");
        g.drawString(font, head, 0, 0, Kit.GOLD_LIGHT, false);
        int ax = 0;
        for (Attr attr : Attr.values()) {
            String t = attr.label + " " + String.format("%.1f", stats.attr(attr));
            Kit.blit(g, "icon/attribute/" + attr.name().toLowerCase(Locale.ROOT), ax, 11, 9, 9);
            g.drawString(font, t, ax + 12, 12, Kit.BONE, false);
            ax += font.width(t) + 26;
        }
        g.pose().pushPose();
        g.pose().translate(0, 21, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, String.format("Crit chance %.0f%%, crit damage +%.0f%%   Attack speed +%.0f%%   Move speed +%.0f%%",
                stats.critChance(), stats.critDamage(), stats.attackSpeedPct(), stats.moveSpeedPct()), 0, 0, Kit.ASH, false);
        g.pose().popPose();
        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++) {
            Skill skill = skills[i];
            int x = (i % 2) * 214;
            int y = 28 + (i / 2) * 32;
            int level = data.skillLevel(skill);
            double xp = data.skillXp.getOrDefault(skill, 0.0);
            double from = Skill.xpForLevel(level);
            double to = Skill.xpForLevel(Math.min(Skill.MAX_LEVEL, level + 1));
            Kit.inset(g, x, y, 206, 29);
            g.drawString(font, skill.label, x + 6, y + 4, Kit.BONE, false);
            String lv = String.valueOf(level);
            g.drawString(font, lv, x + 200 - font.width(lv), y + 4, Kit.BONE, false);
            Kit.bar(g, "xp", x + 6, y + 13, 194, level >= Skill.MAX_LEVEL || to <= from ? 1f : (float) Math.min(1, (xp - from) / (to - from)));
            g.pose().pushPose();
            g.pose().translate(x + 6, y + 19, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, skill.describe(level), 0, 0, Kit.ASH, false);
            g.pose().popPose();
        }
    }

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
        if (tab == CRAFTING) {
            return clickCrafting(x, y, button);
        }
        if (tab != GEAR) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0 && button != 1) {
            return false;
        }
        int wasOpen = filterOpen;
        if (filterOpen >= 0 && clickFilter(x, y)) {
            return true;
        }
        for (int i = 0; i < BTN.length && button == 0; i++) {
            if (x >= BTN_X && x < BTN_X + BTN_W && y >= BTN_Y[i] && y < BTN_Y[i] + BTN_H) {
                ClientPlayNetworking.send(new TetrisNet.Action(BTN_ACTION[i]));
                return true;
            }
        }
        for (int t = 0; t < 2 && button == 0; t++) {
            int[] r = TOGGLES[t];
            if (x >= r[0] && x < r[0] + r[1] && y >= 179 && y < 188) {
                ClientPlayNetworking.send(new TetrisNet.Action(6 + t));
                return true;
            }
        }
        if (held.isEmpty() && x >= COL4 && x < COL4 + COL4_W) {
            for (Section sec : sections()) {
                if (sec.kind != Loc.CONTAINER) {
                    continue;
                }
                int top = sec.y - (int) containerScroll;
                if (button == 0 && x < COL4 + TITLE_W && y >= top - 1 && y < top + 9 && top >= 0) {
                    renameIndex = sec.index;
                    renameText = ClientTetris.sync.containers().get(sec.index).name();
                    return true;
                }
                for (int b = 0; b < 3; b++) {
                    int[] r = containerButton(top, b);
                    if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + 9 && top >= 0) {
                        if (b == 2) {
                            filterOpen = wasOpen == sec.index ? -1 : sec.index;
                            filterY = top + 9;
                        } else {
                            ClientPlayNetworking.send(new TetrisNet.ContainerAction(sec.index, b, 0));
                        }
                        return true;
                    }
                }
            }
        }
        Loc loc = locAt(x, y);
        if (!held.isEmpty()) {
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
                boolean swap = heldCount == 0 && (loc.kind() == Loc.HAND || loc.kind() == Loc.EQUIP) && !there.isEmpty()
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

    private boolean rotationChanged() {
        Placed p = placedAt(heldFrom);
        return p != null && p.rotated != heldRot;
    }

    private boolean clickCrafting(double x, double y, int button) {
        searchFocused = x >= SEARCH_X && x < LIST_W && y >= 0 && y < 14;
        for (int v = 0; v < 2; v++) {
            if (x >= v * 48 && x < v * 48 + 44 && y >= 0 && y < 14) {
                craftView = v;
                dropHeld();
                return true;
            }
        }
        if (craftView == 1) {
            return clickRecipes(x, y, button);
        }
        TetrisNet.CraftList craft = ClientTetris.craft;
        if (x >= CLEAR_X && x < W && y >= 0 && y < 12) {
            dropHeld();
            ClientPlayNetworking.send(new TetrisNet.Craft("#clear", 0));
            return true;
        }
        if (craft.selected() != null && !craft.selected().isEmpty() && x >= PIN_X && x < PIN_X + PIN_W && y >= PIN_Y && y < PIN_Y + 12) {
            pinRecipe(craft.selected());
            return true;
        }
        if (overResult(x, y)) {
            if (!craft.result().isEmpty()) {
                ClientPlayNetworking.send(new TetrisNet.Craft("#grid", hasShiftDown() ? 64 : 1));
            }
            return true;
        }
        int cell = cellAt(x, y);
        if (cell >= 0) {
            if (!held.isEmpty()) {
                if (heldFrom.kind() == Loc.CRAFT && heldFrom.a() == cell) {
                    dropHeld();
                    return true;
                }
                if (button == 1) {
                    int carrying = heldCount > 0 ? heldCount : held.getCount();
                    ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.craft(cell), false, 1));
                    if (carrying - 1 <= 0) {
                        dropHeld();
                    } else {
                        heldCount = carrying - 1;
                    }
                    return true;
                }
                ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.craft(cell), false, heldCount));
                dropHeld();
                return true;
            }
            ItemStack in = cell < craft.grid().size() ? craft.grid().get(cell) : ItemStack.EMPTY;
            if (!in.isEmpty()) {
                if (button == 1) {
                    // Right-click a cell: put it back.
                    ClientPlayNetworking.send(new TetrisNet.Move(Loc.craft(cell), Loc.material(0), false, 0));
                } else {
                    heldFrom = Loc.craft(cell);
                    held = in;
                    heldCount = hasShiftDown() && in.getCount() > 1 ? in.getCount() / 2 : 0;
                }
            }
            return true;
        }
        int hot = craftHotbarAt(x, y);
        if (hot >= 0) {
            ItemStack in = minecraft.player.getInventory().items.get(hot);
            if (!held.isEmpty()) {
                if (!heldFrom.equals(Loc.hand(hot))) {
                    ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.hand(hot), false, button == 1 ? 1 : heldCount));
                }
                dropHeld();
            } else if (!in.isEmpty()) {
                heldFrom = Loc.hand(hot);
                held = in;
                heldCount = button == 1 && in.getCount() > 1 ? (in.getCount() + 1) / 2 : 0;
            }
            return true;
        }
        int mat = materialAt(x, y);
        if (mat != -2) {
            if (!held.isEmpty()) {
                if (heldFrom.kind() == Loc.CRAFT) {
                    ClientPlayNetworking.send(new TetrisNet.Move(heldFrom, Loc.material(0), false, heldCount));
                } else if (heldFrom.kind() == Loc.HAND) {
                    // From the hotbar into your grids.
                    ClientPlayNetworking.send(new TetrisNet.QuickMove(heldFrom));
                }
                dropHeld();
                return true;
            }
            if (mat >= 0) {
                ItemStack m = craft.materials().get(mat);
                int full = Math.min(m.getCount(), m.getMaxStackSize());
                heldFrom = Loc.material(mat);
                held = m.copyWithCount(full);
                heldCount = button == 1 ? 1 : hasShiftDown() ? Math.max(1, full / 2) : full;
            }
            return true;
        }
        if (!held.isEmpty()) {
            dropHeld();
            return true;
        }
        int top = listTop();
        if (x < 0 || x >= LIST_W || y < top) {
            return true;
        }
        double at = y - top + craftScroll;
        for (CraftRow row : craftRows()) {
            if (row.entry == null && row.header != null && at >= row.y && at < row.y + HEAD) {
                if (!COLLAPSED.remove(row.header)) {
                    COLLAPSED.add(row.header);
                }
                return true;
            }
            if (row.entry != null && at >= row.y && at < row.y + ROW - 2) {
                ClientPlayNetworking.send(new TetrisNet.Craft(row.entry.id(), 0));
                break;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return tab == WEB ? web.mouseDragged(mouseX, mouseY - TOP, button, dragX, dragY) : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
        } else if (tab == CRAFTING && vx(mouseX) >= PX) {
            matScroll -= scrollY * 18;
        } else if (tab == CRAFTING) {
            craftScroll -= scrollY * 20;
        } else if (tab == GEAR && vx(mouseX) >= COL4) {
            containerScroll -= scrollY * 20;
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
        if (tab == GEAR && keyCode == GLFW.GLFW_KEY_R && !held.isEmpty()) {
            int[] s = ItemSize.of(held);
            if (s[0] != s[1]) {
                heldRot = !heldRot;
            }
            return true;
        }
        if (tab == GEAR && held.isEmpty() && minecraft.options.keyDrop.matches(keyCode, scanCode)) {
            // Q drops one of the hovered item, Ctrl+Q the whole stack.
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            Loc loc = locAt(vx(mx), vy(my));
            if (loc != null && !stackAt(loc).isEmpty()) {
                ClientPlayNetworking.send(new TetrisNet.Move(loc, Loc.discard(), false, hasControlDown() ? 0 : 1));
            }
            return true;
        }
        if (tab == GEAR && held.isEmpty() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
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
