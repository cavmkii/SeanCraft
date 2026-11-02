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
    private static final int PICK_Y = 40;
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
    private static final int PICK_PITCH = 14;

    // PARTS tab: kinds across, materials (tiers) down.
    private static final int GRID_X = 6;
    private static final int GRID_Y = 28;
    private static final int COL = 21;
    private static final int ROW = 18;
    private static final String[] KIND_SHORT = {"Blade", "Long", "Knife", "Axe", "Mace", "Spear", "Halb.", "Limbs", "Guard", "Plates",
            "Handle", "Haft", "Wrap", "Pad", "Crystal", "Focus"};
    /** The PARTS tab's two pages: striking parts, then everything else. Each is a list of kind ordinals. */
    private static final PartKind[][] PAGES = {
            {PartKind.BLADE, PartKind.LONG_BLADE, PartKind.KNIFE_BLADE, PartKind.AXE_HEAD, PartKind.MACE_HEAD, PartKind.SPEARHEAD,
                    PartKind.HALBERD_HEAD, PartKind.LIMBS},
            {PartKind.GUARD, PartKind.PLATE, PartKind.HANDLE, PartKind.HAFT, PartKind.WRAP, PartKind.PADDING},
            {PartKind.CRYSTAL, PartKind.FOCUS}};
    private static final String[] PAGE_NAMES = {"Heads", "Other parts", "Magic"};

    private boolean partsTab;
    private int page;

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
        return leftPos + 110 + t * 32;
    }

    private void setTab(boolean parts) {
        partsTab = parts;
        menu.partsTab = parts;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Kit.panel(g, x, y, imageWidth, imageHeight);
        for (int t = 0; t < 2; t++) {
            boolean active = (t == 1) == partsTab;
            boolean over = mouseX >= tabX(t) && mouseX < tabX(t) + 30 && mouseY >= y + TAB_Y && mouseY < y + TAB_Y + 11;
            Kit.smallButton(g, font, t == 0 ? "BUILD" : "PARTS", tabX(t), y + TAB_Y, 30, 11, true, over && !active, active, 0.85f);
        }
        if (partsTab) {
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
        if (partsTab) {
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
                if (level < PartDef.MAX_LEVEL) {
                    int bx = slot.x + 17;
                    int by = slot.y;
                    boolean hover = mouseX - leftPos >= bx && mouseX - leftPos < bx + 9 && mouseY - topPos >= by && mouseY - topPos < by + 9;
                    Kit.blit(g, hover ? "button_small/level_up_hover" : "button_small/level_up_normal", bx, by, 9, 9);
                }
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

    /** Modify mode: REPAIR for a worn item, 1 of its striking part's material per 25% (9.4.8). */
    private void renderRepair(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack w = menu.weapon();
        int steps = com.cavmkii.seancraft.parts.Wear.stepsToFull(w);
        net.minecraft.world.item.Item mat = com.cavmkii.seancraft.parts.Wear.material(w);
        if (w.isEmpty() || mat == null || !w.isDamageableItem()) {
            return;
        }
        int ax = leftPos + ASSEMBLE_X;
        int ay = topPos + ASSEMBLE_Y;
        boolean hover = steps > 0 && mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14;
        Kit.button(g, font, "REPAIR", ax, ay, 54, 14, steps > 0, hover, false);
        if (mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14) {
            String matName = new ItemStack(mat).getHoverName().getString();
            g.renderTooltip(font, steps == 0 ? List.of(Component.literal("Not worn")) : List.of(
                    Component.literal("Click: 1 " + matName + " for 25%"),
                    Component.literal("Shift: all the way (" + steps + " " + matName + ")").withStyle(net.minecraft.ChatFormatting.GRAY)),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
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
            int ax = leftPos + ASSEMBLE_X;
            int ay = topPos + ASSEMBLE_Y;
            boolean hover = ready && mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14;
            Kit.button(g, font, "ASSEMBLE", ax, ay, 54, 14, ready, hover, false);
            String problem = menu.assemblyProblem();
            if (!ready && problem != null && mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14) {
                g.renderTooltip(font, Component.literal(problem), mouseX, mouseY);
            }
        }
    }

    /** Index into {@link PartDef#ALL} of the part kind {@code kind} at tier {@code t} (0-4). */
    private static int partIndex(PartKind kind, int t) {
        return t * PartKind.values().length + kind.ordinal();
    }

    private int partAt(double mouseX, double mouseY) {
        PartKind[] kinds = PAGES[page];
        double lx = mouseX - leftPos - GRID_X;
        double ly = mouseY - topPos - GRID_Y;
        if (lx < 0 || ly < 0) {
            return -1;
        }
        int k = (int) (lx / COL);
        int t = (int) (ly / ROW);
        if (k >= kinds.length || t >= 5 || lx - k * COL > 18 || ly - t * ROW > 17) {
            return -1;
        }
        return partIndex(kinds[k], t);
    }

    private int pageButtonX() {
        return leftPos + GRID_X;
    }

    private String catalystLabel() {
        return "CATALYST: " + com.cavmkii.seancraft.rpg.Magic.ELEMENT_LABELS[menu.catalystIndex()].toUpperCase();
    }

    private String infusionLabel() {
        return "INFUSION: " + WeaponBenchMenu.INFUSION_LABELS[menu.infusionIndex()].toUpperCase();
    }

    private int pageButtonY() {
        return topPos + GRID_Y + 5 * ROW + 2;
    }

    private void renderParts(GuiGraphics g, int mouseX, int mouseY) {
        PartKind[] kinds = PAGES[page];
        for (int k = 0; k < kinds.length; k++) {
            g.pose().pushPose();
            g.pose().translate(leftPos + GRID_X + k * COL, topPos + GRID_Y - 6, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.drawString(font, KIND_SHORT[kinds[k].ordinal()], 0, 0, Kit.ASH, false);
            g.pose().popPose();
        }
        // Page switch: heads, or everything else.
        String label = "< " + PAGE_NAMES[page] + " (" + (page + 1) + "/" + PAGES.length + ") >";
        int bx = pageButtonX();
        int by = pageButtonY();
        boolean over = mouseX >= bx && mouseX < bx + font.width(label) + 6 && mouseY >= by && mouseY < by + 11;
        Kit.button(g, font, label, bx, by, font.width(label) + 6, 11, true, over, false);
        if (page == 2) {
            // Crystals made here get this element (9.4.5).
            String cat = catalystLabel();
            int iy = by + 13;
            boolean overCat = mouseX >= bx && mouseX < bx + font.width(cat) + 6 && mouseY >= iy && mouseY < iy + 11;
            Kit.button(g, font, cat, bx, iy, font.width(cat) + 6, 11, true, overCat, true);
            if (overCat) {
                g.renderTooltip(font, List.of(Component.literal("Crystals you make get this element"),
                        Component.literal("Fire: blaze powder. Frost: packed ice. Lightning: lightning rod.").withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.literal("Poison: fermented spider eye. Spirit: golden carrot.").withStyle(net.minecraft.ChatFormatting.GRAY)),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
        }
        if (page == 1) {
            // Wraps made here carry this infusion (9.4.3).
            String inf = infusionLabel();
            int iy = by + 13;
            boolean overInf = mouseX >= bx && mouseX < bx + font.width(inf) + 6 && mouseY >= iy && mouseY < iy + 11;
            Kit.button(g, font, inf, bx, iy, font.width(inf) + 6, 11, true, overInf, menu.infusionIndex() > 0);
            if (overInf) {
                g.renderTooltip(font, List.of(Component.literal("Wraps you make get this infusion"),
                        Component.literal("Heavy: +2 iron nuggets, 25% of scaling to STR").withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.literal("Keen: +1 flint, 25% to AGI").withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.literal("Arcane: +1 lapis, 25% to INT").withStyle(net.minecraft.ChatFormatting.GRAY)),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
        }
        int hovered = partAt(mouseX, mouseY);
        for (int k = 0; k < kinds.length; k++) {
            for (int t = 0; t < 5; t++) {
                int i = partIndex(kinds[k], t);
                int px = leftPos + GRID_X + k * COL;
                int py = topPos + GRID_Y + t * ROW;
                boolean can = menu.canMake(i);
                // Moss rim: you have enough to make it (C.2). Hatched: a head no unlocked weapon uses (4.7).
                Kit.slot(g, px, py, 18, menu.headLocked(PartDef.ALL.get(i)) ? Kit.SlotState.LOCKED : hovered == i ? Kit.SlotState.HOVER : Kit.SlotState.NORMAL);
                if (can && hovered != i) {
                    g.renderOutline(px, py, 18, 18, Kit.MOSS);
                }
                g.renderItem(new ItemStack(PartItems.get(PartDef.ALL.get(i))), px + 1, py);
            }
        }
        if (hovered >= 0) {
            PartDef def = PartDef.ALL.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(def.label()));
            List<Cost> all = new ArrayList<>(def.costs());
            if (def.kind() == PartKind.CRYSTAL) {
                all.addAll(WeaponBenchMenu.catalystCost(menu.catalystIndex()));
                lines.set(0, Component.literal(com.cavmkii.seancraft.rpg.Magic.ELEMENT_LABELS[menu.catalystIndex()] + " " + def.label().toLowerCase()));
            }
            if (def.kind() == PartKind.WRAP) {
                all.addAll(WeaponBenchMenu.infusionCost(menu.infusionIndex()));
                if (menu.infusionIndex() > 0) {
                    lines.set(0, Component.literal(WeaponBenchMenu.INFUSION_LABELS[menu.infusionIndex()] + " " + def.label().toLowerCase()));
                }
            }
            for (Cost c : all) {
                lines.add(Component.literal("  " + c.count() + " " + c.label()).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            boolean headLocked = menu.headLocked(PartDef.ALL.get(hovered));
            lines.add(Component.literal(headLocked ? "Locked: unlock a weapon that uses it first" : menu.canMake(hovered) ? "Click: make one. Shift: make 5." : "Not enough materials")
                    .withStyle(menu.canMake(hovered) ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
            g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        menu.viewer = com.cavmkii.seancraft.client.rpg.ClientRpg.data;
        super.render(g, mouseX, mouseY, partialTick);
        StationTabs.render(g, font, leftPos, topPos - 14, imageWidth, StationTabs.WEAPON_BENCH, mouseX, mouseY);
        if (partsTab) {
            renderParts(g, mouseX, mouseY);
            return;
        }
        renderBuild(g, mouseX, mouseY);
        if (!partsTab && !menu.buildMode()) {
            renderRepair(g, mouseX, mouseY);
        }
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
                g.renderTooltip(font, List.of(Component.literal(name), Component.literal("Make parts on the PARTS tab")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)), java.util.Optional.empty(), mouseX, mouseY);
            }
            if (slot.getItem().getItem() instanceof PartItem p && !menu.buildMode() && Parts.level(slot.getItem()) < PartDef.MAX_LEVEL
                    && mouseX >= sx + 17 && mouseX < sx + 26 && mouseY >= sy && mouseY < sy + 9) {
                int level = Parts.level(slot.getItem());
                g.renderTooltip(font, List.of(Component.literal("Level up to " + (level + 1)),
                        Component.literal("Costs " + p.def.upgradeCost(level) + " " + new ItemStack(p.def.material()).getHoverName().getString())),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (StationTabs.click(leftPos, topPos - 14, imageWidth, StationTabs.WEAPON_BENCH, mouseX, mouseY)) {
            return true;
        }
        for (int t = 0; t < 2; t++) {
            if (mouseX >= tabX(t) && mouseX < tabX(t) + 30 && mouseY >= topPos + TAB_Y && mouseY < topPos + TAB_Y + 11) {
                setTab(t == 1);
                return true;
            }
        }
        if (partsTab) {
            String label = "< " + PAGE_NAMES[page] + " (" + (page + 1) + "/" + PAGES.length + ") >";
            if (mouseX >= pageButtonX() && mouseX < pageButtonX() + font.width(label) + 6 && mouseY >= pageButtonY() && mouseY < pageButtonY() + 11) {
                page = (page + 1) % PAGES.length;
                return true;
            }
            int iy = pageButtonY() + 13;
            if (page == 2 && mouseX >= pageButtonX() && mouseX < pageButtonX() + font.width(catalystLabel()) + 6 && mouseY >= iy && mouseY < iy + 11) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.CATALYST_BUTTON);
                return true;
            }
            if (page == 1 && mouseX >= pageButtonX() && mouseX < pageButtonX() + font.width(infusionLabel()) + 6 && mouseY >= iy && mouseY < iy + 11) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.INFUSE_BUTTON);
                return true;
            }
            int i = partAt(mouseX, mouseY);
            if (i >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        (hasShiftDown() ? WeaponBenchMenu.MAKE_FIVE_BUTTON : WeaponBenchMenu.MAKE_BUTTON) + i);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (menu.buildMode()) {
            for (int i = 0; i < PICK_ICONS.length; i++) {
                if (mouseX >= pickX(i) && mouseX < pickX(i) + PICK_PITCH - 1 && mouseY >= pickY(i) && mouseY < pickY(i) + PICK_PITCH - 1) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.PICK_BUTTON + i);
                    return true;
                }
            }
            int ax = leftPos + ASSEMBLE_X;
            int ay = topPos + ASSEMBLE_Y;
            if (mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14 && !menu.assemblyResult().isEmpty()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponBenchMenu.ASSEMBLE_BUTTON);
                return true;
            }
        } else if (!partsTab && mouseX >= leftPos + ASSEMBLE_X && mouseX < leftPos + ASSEMBLE_X + 54 && mouseY >= topPos + ASSEMBLE_Y
                && mouseY < topPos + ASSEMBLE_Y + 14 && com.cavmkii.seancraft.parts.Wear.stepsToFull(menu.weapon()) > 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, hasShiftDown() ? WeaponBenchMenu.REPAIR_ALL_BUTTON : WeaponBenchMenu.REPAIR_BUTTON);
            return true;
        } else {
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                if (slot == null || !(slot.getItem().getItem() instanceof PartItem) || Parts.level(slot.getItem()) >= PartDef.MAX_LEVEL) {
                    continue;
                }
                int bx = leftPos + slot.x + 17;
                int by = topPos + slot.y;
                if (mouseX >= bx && mouseX < bx + 9 && mouseY >= by && mouseY < by + 9) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
