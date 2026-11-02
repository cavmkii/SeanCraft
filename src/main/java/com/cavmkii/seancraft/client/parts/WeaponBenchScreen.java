package com.cavmkii.seancraft.client.parts;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.client.tarkov.StationTabs;
import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.PartDef;
import com.cavmkii.seancraft.parts.PartItem;
import com.cavmkii.seancraft.parts.PartItems;
import com.cavmkii.seancraft.parts.PartKind;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import com.cavmkii.seancraft.parts.WeaponBenchMenu;
import com.cavmkii.seancraft.tarkov.Cost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Weapon Bench screen. BUILD: the item slot on the left, and the outline of what you're building (or modifying) with
 * each part's slot sitting where that part goes; empty slots show a faded example part. PARTS: every part by kind and
 * material, made from what you carry and the stash.
 */
public class WeaponBenchScreen extends AbstractContainerScreen<WeaponBenchMenu> {
    private static final int AREA_X0 = 62;
    private static final int AREA_Y0 = 16;
    private static final int AREA_X1 = 172;
    private static final int AREA_Y1 = 116;
    private static final int PICK_X = 4;
    private static final int PICK_Y = 44;
    private static final int ASSEMBLE_X = 4;
    private static final int ASSEMBLE_Y = 100;
    private static final int TAB_Y = 3;
    /** One icon per buildable family: its iron item (or its only item), in the menu's order. */
    private static final Item[] PICK_ICONS = java.util.Arrays.stream(WeaponBenchMenu.BUILDABLE)
            .map(f -> f == Family.BOW ? Items.BOW : f == Family.CROSSBOW ? Items.CROSSBOW : f == Family.SHIELD ? Items.SHIELD
                    : f == Family.BUCKLER ? com.cavmkii.seancraft.parts.WeaponItems.BUCKLER
                    : f == Family.TOWER_SHIELD ? com.cavmkii.seancraft.parts.WeaponItems.TOWER_SHIELD
                    : f == Family.BOOMERANG ? com.cavmkii.seancraft.parts.WeaponItems.BOOMERANG : Family.itemFor(f, 2)).toArray(Item[]::new);
    private static final String[] PICK_NAMES = java.util.Arrays.stream(WeaponBenchMenu.BUILDABLE)
            .map(f -> f == Family.CHEST ? "Chestplate" : f == Family.LEGS ? "Leggings" : f.name().charAt(0) + f.name().substring(1).toLowerCase())
            .toArray(String[]::new);
    private static final int PICK_PITCH = 12;

    // PARTS tab: the book of part patterns down the left (4.8).
    private static final int BOOK_X = 4;
    private static final int BOOK_Y = 18;
    private static final int BOOK_W = 52;
    private static final int BOOK_ROW = 8;
    private static final int BOOK_ROWS = 12;
    private static final String[] TABS = {"BUILD", "PARTS", "REPAIR"};

    private int tab;
    private int bookScroll;
    /** The pattern picked in the book, drawn as a ghost in the grid until it's complete. */
    private com.cavmkii.seancraft.pattern.Pattern ghost;

    public WeaponBenchScreen(WeaponBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 212;
        inventoryLabelY = 119;
    }

    private int pickX(int i) {
        return leftPos + PICK_X + (i % 4) * PICK_PITCH;
    }

    private int pickY(int i) {
        return topPos + PICK_Y + (i / 4) * PICK_PITCH;
    }

    private int tabX(int t) {
        return leftPos + 78 + t * 32;
    }

    private void setTab(int t) {
        tab = t;
        menu.setTab(minecraft.player, t);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.TAB_BUTTON + t);
    }

    /** Part patterns in kind order. */
    private static List<com.cavmkii.seancraft.pattern.Pattern> book() {
        List<com.cavmkii.seancraft.pattern.Pattern> out = new ArrayList<>(com.cavmkii.seancraft.pattern.Patterns.station("part"));
        out.sort(java.util.Comparator.comparingInt(p -> {
            try {
                return PartKind.valueOf(p.part).ordinal();
            } catch (Exception e) {
                return 99;
            }
        }));
        return out;
    }

    private static String bookName(com.cavmkii.seancraft.pattern.Pattern p) {
        try {
            return PartKind.valueOf(p.part).label;
        } catch (Exception e) {
            return p.id;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Kit.panel(g, x, y, imageWidth, imageHeight);
        for (int t = 0; t < TABS.length; t++) {
            boolean active = t == tab;
            boolean over = mouseX >= tabX(t) && mouseX < tabX(t) + 30 && mouseY >= y + TAB_Y && mouseY < y + TAB_Y + 11;
            Kit.smallButton(g, font, TABS[t], tabX(t), y + TAB_Y, 30, 11, true, over && !active, active, 0.75f);
        }
        if (tab == WeaponBenchMenu.TAB_PARTS) {
            partsBg(g, mouseX, mouseY);
            return;
        }
        if (tab == WeaponBenchMenu.TAB_REPAIR) {
            repairBg(g);
            return;
        }
        // The outline area.
        Kit.inset(g, x + AREA_X0 - 1, y + AREA_Y0 - 1, AREA_X1 - AREA_X0 + 2, AREA_Y1 - AREA_Y0 + 2);
        Family family = menu.family();
        if (family != null) {
            outline(g, family);
        }
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || slot.index >= firstInventory()) {
                continue;
            }
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            boolean over = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
            Kit.slot(g, sx, sy, 18, over ? Kit.SlotState.HOVER : slot.index == 0 ? Kit.SlotState.RESULT : Kit.SlotState.NORMAL);
        }
        // Faded example parts in empty slots, so you can see what goes where.
        if (family != null) {
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                PartSlot kind = menu.slotFor(i);
                if (slot == null || kind == null || slot.hasItem()) {
                    continue;
                }
                // The kit's part outline (ghost/part_*) in the empty slot.
                PartKind ex = exampleKind(family, kind);
                if (ex != null) {
                    String ghost = ex == PartKind.PLATE ? "plates" : ex.name().toLowerCase(java.util.Locale.ROOT);
                    Kit.blit(g, "ghost/part_" + ghost, x + slot.x, y + slot.y, 16, 16);
                }
            }
        }
    }

    /** PARTS: book, grid, arrow, result; ghost icons and the levelling ring under the slot items. */
    private void partsBg(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Kit.inset(g, x + BOOK_X - 1, y + BOOK_Y - 1, BOOK_W + 2, BOOK_ROWS * BOOK_ROW + 2);
        List<com.cavmkii.seancraft.pattern.Pattern> book = book();
        bookScroll = Math.max(0, Math.min(Math.max(0, book.size() - BOOK_ROWS), bookScroll));
        for (int r = 0; r < BOOK_ROWS && r + bookScroll < book.size(); r++) {
            var p = book.get(r + bookScroll);
            int ry = y + BOOK_Y + r * BOOK_ROW;
            boolean over = mouseX >= x + BOOK_X && mouseX < x + BOOK_X + BOOK_W && mouseY >= ry && mouseY < ry + BOOK_ROW;
            if (p == ghost) {
                Kit.blit(g, "list/row_selected", x + BOOK_X, ry, BOOK_W, BOOK_ROW);
            } else if (over) {
                Kit.blit(g, "list/row_hover", x + BOOK_X, ry, BOOK_W, BOOK_ROW);
            }
            small(g, bookName(p), BOOK_X + 2 + leftPos, ry + 2, p == ghost || over ? Kit.GOLD_LIGHT : Kit.BONE);
        }
        Slot center = menu.slots.get(gridSlot(4));
        int ringLeft = 0;
        PartItem ringPart = center.getItem().getItem() instanceof PartItem cp ? cp : null;
        if (ringPart != null) {
            int have = 0;
            for (int i = 0; i < 9; i++) {
                if (i != 4 && menu.slots.get(gridSlot(i)).hasItem()) {
                    have++;
                }
            }
            ringLeft = Math.max(0, com.cavmkii.seancraft.parts.PartCrafting.ringNeeded(ringPart.def, Parts.level(center.getItem())) - have);
        }
        boolean ready = menu.slots.get(gridSlot(9)).hasItem();
        if (ready) {
            ghost = null;
        }
        char[] ghostAt = ghost == null ? null : ghost.ghost();
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.slots.get(gridSlot(i));
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            boolean empty = !slot.hasItem();
            if (empty && ringLeft > 0 && i != 4) {
                // MODIFY levelling: ring squares that still need the part's material (4.6).
                Kit.slot(g, sx, sy, 18, Kit.SlotState.NORMAL);
                Kit.blit(g, "slot/ring_target", sx, sy, 18, 18);
                ringLeft--;
            } else if (empty && ghostAt != null && ghostAt[i] != 0) {
                Kit.blit(g, "slot/ghost", sx, sy, 18, 18);
                g.renderItem(com.cavmkii.seancraft.parts.PartCrafting.example(ghostAt[i]), sx + 1, sy + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                Kit.blit(g, "slot/ghost_overlay", sx + 1, sy + 1, 16, 16);
                g.pose().popPose();
            } else {
                Kit.slot(g, sx, sy, 18, Kit.SlotState.NORMAL);
            }
        }
        Kit.blit(g, ready ? "progress/arrow_full" : "progress/arrow_empty", x + WeaponBenchMenu.GRID_X + 56, y + WeaponBenchMenu.GRID_Y + 20, 22, 15);
        Kit.blit(g, ready ? "slot/result_ready" : "slot/result", x + WeaponBenchMenu.GRID_RESULT_X - 5, y + WeaponBenchMenu.GRID_RESULT_Y - 5, 26, 26);
        if (menu.gridLocked()) {
            Kit.blit(g, "list/lock", x + WeaponBenchMenu.GRID_RESULT_X + 4, y + WeaponBenchMenu.GRID_RESULT_Y + 4, 9, 9);
        }
    }

    /** REPAIR: item, material, arrow, repaired item (9.4.8). */
    private void repairBg(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        Kit.slot(g, x + WeaponBenchMenu.REPAIR_ITEM_X - 1, y + WeaponBenchMenu.REPAIR_Y - 1, 18, Kit.SlotState.NORMAL);
        Kit.slot(g, x + WeaponBenchMenu.REPAIR_MAT_X - 1, y + WeaponBenchMenu.REPAIR_Y - 1, 18, Kit.SlotState.NORMAL);
        g.drawString(font, "+", x + WeaponBenchMenu.REPAIR_ITEM_X + 23, y + WeaponBenchMenu.REPAIR_Y + 4, Kit.ASH, false);
        boolean ready = menu.slots.get(gridSlot(12)).hasItem();
        Kit.blit(g, ready ? "progress/arrow_full" : "progress/arrow_empty", x + WeaponBenchMenu.REPAIR_MAT_X + 26, y + WeaponBenchMenu.REPAIR_Y + 1, 22, 15);
        Kit.blit(g, ready ? "slot/result_ready" : "slot/result", x + WeaponBenchMenu.REPAIR_RESULT_X - 5, y + WeaponBenchMenu.REPAIR_Y - 5, 26, 26);
    }

    /** Menu index of the station slots: 0-8 grid, 9 grid result, 10-11 repair inputs, 12 repair result, 13 build result. */
    private int gridSlot(int n) {
        int at = 1;
        for (Family f : Family.values()) {
            at += f.slots.size();
        }
        return at + n;
    }

    private int firstInventory() {
        return menu.slots.size() - 36;
    }

    private static PartKind exampleKind(Family family, PartSlot slot) {
        return switch (slot) {
            case HEAD -> family.head;
            case HANDLE -> family.grip;
            case GUARD -> PartKind.GUARD;
            case WRAP -> PartKind.WRAP;
            case PLATE -> PartKind.PLATE;
            case PADDING -> PartKind.PADDING;
        };
    }

    private void shape(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(leftPos + x0 - 1, topPos + y0 - 1, leftPos + x1 + 1, topPos + y1 + 1, Kit.IRON_LIGHT);
    }

    private void fillShape(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, Kit.IRON);
    }

    /** Draws a family's outline: a light edge pass, then the body, so touching pieces read as one shape. */
    private void outline(GuiGraphics g, Family family) {
        int[][] rects = switch (family) {
            case SWORD -> new int[][] {{115, 16, 121, 20}, {112, 20, 124, 58}, {86, 59, 150, 71}, {113, 71, 123, 96}, {108, 96, 128, 116}};
            case AXE -> new int[][] {{113, 18, 123, 114}, {80, 20, 113, 52}, {76, 25, 80, 47}};
            case DAGGER -> new int[][] {{115, 24, 119, 30}, {112, 30, 122, 70}, {100, 70, 134, 76}, {113, 76, 121, 100}, {110, 100, 124, 106}};
            case MACE -> new int[][] {{100, 20, 134, 50}, {96, 28, 100, 42}, {134, 28, 138, 42}, {113, 16, 121, 20}, {113, 50, 121, 112}};
            case WARHAMMER -> new int[][] {{84, 20, 150, 46}, {80, 24, 84, 42}, {113, 46, 121, 116}};
            case GREATSWORD -> new int[][] {{115, 16, 121, 20}, {111, 20, 125, 56}, {80, 56, 156, 64}, {113, 64, 123, 96}, {108, 96, 128, 116}};
            case HALBERD -> new int[][] {{114, 16, 120, 26}, {113, 26, 121, 116}, {121, 22, 152, 52}, {148, 18, 154, 56}, {104, 30, 113, 38}};
            case SPEAR -> new int[][] {{115, 16, 121, 20}, {111, 20, 125, 40}, {114, 40, 122, 116}};
            case PIKE -> new int[][] {{116, 16, 120, 18}, {113, 18, 123, 40}, {115, 40, 121, 116}, {111, 40, 125, 42}};
            case BOW -> new int[][] {{116, 16, 126, 22}, {124, 20, 134, 34}, {132, 32, 140, 50}, {134, 50, 144, 74}, {132, 74, 140, 92},
                    {124, 90, 134, 104}, {116, 102, 126, 110}, {100, 20, 102, 108}, {101, 18, 118, 20}, {101, 106, 118, 108}};
            case CROSSBOW -> new int[][] {{70, 30, 164, 38}, {66, 34, 72, 44}, {162, 34, 168, 44}, {112, 38, 124, 114}, {72, 44, 110, 46},
                    {126, 44, 162, 46}};
            case SHIELD -> new int[][] {{84, 18, 150, 92}, {90, 92, 144, 104}, {100, 104, 134, 112}};
            case BUCKLER -> new int[][] {{94, 28, 140, 84}};
            case TOWER_SHIELD -> new int[][] {{86, 14, 148, 110}};
            case THROWING_KNIFE -> new int[][] {{113, 28, 121, 70}, {111, 70, 123, 100}};
            case JAVELIN -> new int[][] {{113, 16, 121, 40}, {115, 40, 119, 116}};
            case BOOMERANG -> new int[][] {{90, 40, 140, 52}, {128, 52, 140, 90}};
            case WAND -> new int[][] {{111, 22, 123, 42}, {109, 42, 125, 50}, {115, 50, 119, 104}};
            case STAFF -> new int[][] {{111, 14, 123, 34}, {109, 34, 125, 42}, {115, 42, 119, 116}};
            case HELMET -> new int[][] {{92, 22, 142, 30}, {84, 30, 150, 72}, {84, 72, 98, 86}, {136, 72, 150, 86}, {104, 108, 130, 112}};
            case CHEST -> new int[][] {{86, 22, 148, 112}, {72, 22, 86, 52}, {148, 22, 162, 52}};
            case LEGS -> new int[][] {{84, 16, 150, 38}, {84, 38, 113, 114}, {121, 38, 150, 114}};
            case BOOTS -> new int[][] {{80, 44, 104, 92}, {72, 92, 108, 108}, {130, 44, 154, 92}, {126, 92, 162, 108}, {104, 40, 130, 50}};
        };
        for (int[] r : rects) {
            shape(g, r[0], r[1], r[2], r[3]);
        }
        for (int[] r : rects) {
            fillShape(g, r[0], r[1], r[2], r[3]);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Kit.text(g, font, Kit.heading(title.getString()), titleLabelX, titleLabelY - 2, Kit.GOLD_LIGHT);
        if (tab == WeaponBenchMenu.TAB_PARTS) {
            small(g, "Part in the centre + its", 60, 88, Kit.ASH);
            small(g, "material around: level up", 60, 96, Kit.ASH);
            return;
        }
        if (tab == WeaponBenchMenu.TAB_REPAIR) {
            small(g, "1 of the striking part's material", 30, 76, Kit.ASH);
            small(g, "restores 25%", 30, 84, Kit.ASH);
            return;
        }
        Kit.label(g, font, playerInventoryTitle.getString().toUpperCase(java.util.Locale.ROOT), inventoryLabelX, inventoryLabelY);
        ItemStack weapon = menu.weapon();
        Family family = menu.family();
        if (Parts.modular(weapon)) {
            Parts.Totals t = Parts.totals(weapon);
            List<String> lines = new ArrayList<>();
            if (family.armor()) {
                lines.add(pct("Armor", t.armor));
            } else {
                lines.add(pct("Damage", t.damage));
                if (family != Family.BOW && family != Family.CROSSBOW && !family.shield()) {
                    lines.add(pct("Speed", -t.stamina));
                }
            }
            lines.add(pct("Durab.", t.durability));
            int ly = 42;
            for (String line : lines) {
                small(g, line, 6, ly, Kit.BONE);
                ly += 8;
            }
        } else if (family == null) {
            small(g, "Pick what to build,", AREA_X0 + 20, 58, Kit.ASH);
            small(g, "or put an item in", AREA_X0 + 20, 66, Kit.ASH);
            small(g, "the slot to modify it.", AREA_X0 + 20, 74, Kit.ASH);
        }
        if (family != null && menu.buildMode()) {
            // How many plates the armor takes, on the plate slot.
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                if (slot != null && menu.slotFor(i) == PartSlot.PLATE) {
                    String n = "x" + WeaponBenchMenu.platesFor(family);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    Kit.text(g, font, n, slot.x + 17 - font.width(n), slot.y - 8, Kit.GOLD_LIGHT);
                    g.pose().popPose();
                }
            }
        }
        // Levels and level-up buttons when modifying.
        if (!menu.buildMode()) {
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                if (slot == null || !(slot.getItem().getItem() instanceof PartItem)) {
                    continue;
                }
                int level = Parts.level(slot.getItem());
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                small(g, "L" + level, slot.x + 18, slot.y + 10, Kit.GOLD_LIGHT);
                g.pose().popPose();
            }
        }
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static String pct(String label, float value) {
        return String.format("%s %+d%%", label, Math.round(value * 100));
    }

    private void renderBuild(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.buildMode()) {
            return;
        }
        for (int i = 0; i < PICK_ICONS.length; i++) {
            int px = pickX(i);
            int py = pickY(i);
            boolean selected = menu.buildingIndex() == i;
            boolean hover = mouseX >= px && mouseX < px + PICK_PITCH - 1 && mouseY >= py && mouseY < py + PICK_PITCH - 1;
            Kit.slot(g, px, py, PICK_PITCH - 1, selected ? Kit.SlotState.SELECTED : hover ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
            g.pose().pushPose();
            g.pose().translate(px + 0.5f, py + 0.5f, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(new ItemStack(PICK_ICONS[i]), 0, 0);
            g.pose().popPose();
            if (menu.lockReason(WeaponBenchMenu.BUILDABLE[i]) != null) {
                // Not on the player's ladders yet (4.7).
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(px + 1, py + 1, px + PICK_PITCH - 2, py + PICK_PITCH - 2, 0xB00E0C0B);
                g.pose().popPose();
            }
        }
        ItemStack preview = menu.assemblyResult();
        boolean ready = !preview.isEmpty();
        if (ready) {
            // Ghost of the result in the item slot.
            g.renderItem(preview, leftPos + WeaponBenchMenu.ITEM_X, topPos + WeaponBenchMenu.ITEM_Y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(leftPos + WeaponBenchMenu.ITEM_X, topPos + WeaponBenchMenu.ITEM_Y, leftPos + WeaponBenchMenu.ITEM_X + 16,
                    topPos + WeaponBenchMenu.ITEM_Y + 16, 0x80C6C6C6);
            g.pose().popPose();
        }
        if (menu.family() != null) {
            // The finished weapon comes out of the result slot (4.5).
            int ax = leftPos + WeaponBenchMenu.BUILD_RESULT_X;
            int ay = topPos + WeaponBenchMenu.BUILD_RESULT_Y;
            g.pose().pushPose();
            g.pose().translate(0, 0, -50);
            Kit.blit(g, ready ? "slot/result_ready" : "slot/result", ax - 5, ay - 5, 26, 26);
            g.pose().popPose();
            String problem = menu.assemblyProblem();
            if (!ready && problem != null && mouseX >= ax - 5 && mouseX < ax + 21 && mouseY >= ay - 5 && mouseY < ay + 21) {
                g.renderTooltip(font, Component.literal(problem), mouseX, mouseY);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        menu.viewer = com.cavmkii.seancraft.client.rpg.ClientRpg.data;
        super.render(g, mouseX, mouseY, partialTick);
        StationTabs.render(g, font, leftPos, topPos - 14, imageWidth, StationTabs.WEAPON_BENCH, mouseX, mouseY);
        if (tab != WeaponBenchMenu.TAB_BUILD) {
            renderTooltip(g, mouseX, mouseY);
            if (tab == WeaponBenchMenu.TAB_PARTS && menu.gridLocked() && mouseX >= leftPos + WeaponBenchMenu.GRID_RESULT_X - 5
                    && mouseX < leftPos + WeaponBenchMenu.GRID_RESULT_X + 21 && mouseY >= topPos + WeaponBenchMenu.GRID_RESULT_Y - 5
                    && mouseY < topPos + WeaponBenchMenu.GRID_RESULT_Y + 21) {
                g.renderTooltip(font, List.of(Component.literal("Locked"),
                        Component.literal("No weapon you've unlocked uses this part (recipe ladders)").withStyle(net.minecraft.ChatFormatting.GRAY)),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
            if (tab == WeaponBenchMenu.TAB_PARTS) {
                List<com.cavmkii.seancraft.pattern.Pattern> book = book();
                int r = (int) Math.floor((mouseY - topPos - BOOK_Y) / (double) BOOK_ROW);
                if (mouseX >= leftPos + BOOK_X && mouseX < leftPos + BOOK_X + BOOK_W && r >= 0 && r < BOOK_ROWS && r + bookScroll < book.size()) {
                    var p = book.get(r + bookScroll);
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(bookName(p)));
                    for (String row : p.rows) {
                        lines.add(Component.literal(row.replace('.', '·').replace("", " ").trim()).withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    lines.add(Component.literal("M metal, W wood, H hide, G gem, C catalyst, S stick").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    lines.add(Component.literal("Click to show it in the grid").withStyle(net.minecraft.ChatFormatting.GRAY));
                    g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
                }
            }
            return;
        }
        renderBuild(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        if (menu.buildMode()) {
            for (int i = 0; i < PICK_ICONS.length; i++) {
                if (mouseX >= pickX(i) && mouseX < pickX(i) + PICK_PITCH - 1 && mouseY >= pickY(i) && mouseY < pickY(i) + PICK_PITCH - 1) {
                    String locked = menu.lockReason(WeaponBenchMenu.BUILDABLE[i]);
                    if (locked != null) {
                        g.renderTooltip(font, List.of(Component.literal(PICK_NAMES[i] + " (locked)"),
                                Component.literal(locked).withStyle(net.minecraft.ChatFormatting.GRAY)), java.util.Optional.empty(), mouseX, mouseY);
                    } else {
                        g.renderTooltip(font, Component.literal("Build a " + PICK_NAMES[i].toLowerCase()), mouseX, mouseY);
                    }
                }
            }
        }
        Family family = menu.family();
        for (int i = 0; i < WeaponBenchMenu.PARTS && family != null; i++) {
            Slot slot = menu.partSlot(i);
            PartSlot kind = menu.slotFor(i);
            if (slot == null || kind == null) {
                continue;
            }
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (!slot.hasItem() && menu.getCarried().isEmpty() && mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                String name = family.slotName(kind) + (menu.buildMode() && kind == PartSlot.PLATE ? " x" + WeaponBenchMenu.platesFor(family) : "");
                g.renderTooltip(font, List.of(Component.literal(name), Component.literal("Make parts on the PARTS tab, by pattern")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)), java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == WeaponBenchMenu.TAB_PARTS && mouseX < leftPos + BOOK_X + BOOK_W) {
            bookScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (StationTabs.click(leftPos, topPos - 14, imageWidth, StationTabs.WEAPON_BENCH, mouseX, mouseY)) {
            return true;
        }
        for (int t = 0; t < TABS.length; t++) {
            if (mouseX >= tabX(t) && mouseX < tabX(t) + 30 && mouseY >= topPos + TAB_Y && mouseY < topPos + TAB_Y + 11) {
                setTab(t);
                return true;
            }
        }
        if (tab == WeaponBenchMenu.TAB_PARTS) {
            List<com.cavmkii.seancraft.pattern.Pattern> book = book();
            int r = (int) Math.floor((mouseY - topPos - BOOK_Y) / (double) BOOK_ROW);
            if (mouseX >= leftPos + BOOK_X && mouseX < leftPos + BOOK_X + BOOK_W && r >= 0 && r < BOOK_ROWS && r + bookScroll < book.size()) {
                var p = book.get(r + bookScroll);
                ghost = ghost == p ? null : p;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (tab == WeaponBenchMenu.TAB_BUILD && menu.buildMode()) {
            for (int i = 0; i < PICK_ICONS.length; i++) {
                if (mouseX >= pickX(i) && mouseX < pickX(i) + PICK_PITCH - 1 && mouseY >= pickY(i) && mouseY < pickY(i) + PICK_PITCH - 1) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.PICK_BUTTON + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
