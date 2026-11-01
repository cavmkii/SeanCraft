package com.cavmkii.seancraft.client.parts;

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
    private static final Item[] PICK_ICONS = {Items.IRON_SWORD, Items.IRON_AXE, Items.CROSSBOW, Items.SHIELD, Items.IRON_HELMET,
            Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final String[] PICK_NAMES = {"Sword", "Axe", "Crossbow", "Shield", "Helmet", "Chestplate", "Leggings", "Boots"};

    // PARTS tab: kinds across, materials (tiers) down.
    private static final int GRID_X = 6;
    private static final int GRID_Y = 28;
    private static final int COL = 21;
    private static final int ROW = 18;
    private static final String[] KIND_SHORT = {"Blade", "Axe hd", "Limbs", "Guard", "Plates", "Handle", "Wrap", "Pad"};

    private boolean partsTab;

    public WeaponBenchScreen(WeaponBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 212;
        inventoryLabelY = 119;
    }

    private int pickX(int i) {
        return leftPos + PICK_X + (i % 3) * 18;
    }

    private int pickY(int i) {
        return topPos + PICK_Y + (i / 3) * 18;
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
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFC6C6C6);
        for (int t = 0; t < 2; t++) {
            boolean active = (t == 1) == partsTab;
            boolean over = mouseX >= tabX(t) && mouseX < tabX(t) + 30 && mouseY >= y + TAB_Y && mouseY < y + TAB_Y + 11;
            g.fill(tabX(t), y + TAB_Y, tabX(t) + 30, y + TAB_Y + 11, active ? 0xFF4A4A55 : over ? 0xFF9A9AA6 : 0xFFADADB6);
            String label = t == 0 ? "BUILD" : "PARTS";
            g.drawString(font, label, tabX(t) + (30 - font.width(label)) / 2, y + TAB_Y + 2, active ? 0xFFFFFFFF : 0xFF404040, false);
        }
        if (partsTab) {
            return;
        }
        // The outline area.
        g.fill(x + AREA_X0 - 1, y + AREA_Y0 - 1, x + AREA_X1 + 1, y + AREA_Y1 + 1, 0xFF55555E);
        g.fill(x + AREA_X0, y + AREA_Y0, x + AREA_X1, y + AREA_Y1, 0xFF2E2E36);
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
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1A20);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, slot.index == 0 ? 0xFF7A7A88 : 0xFF4A4A58);
        }
        // Faded example parts in empty slots, so you can see what goes where.
        if (family != null) {
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                PartSlot kind = menu.slotFor(i);
                if (slot == null || kind == null || slot.hasItem()) {
                    continue;
                }
                PartDef example = PartDef.of(exampleKind(family, kind), 2);
                if (example != null) {
                    g.renderItem(new ItemStack(PartItems.get(example)), x + slot.x, y + slot.y);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xB04A4A58);
                    g.pose().popPose();
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
            case HANDLE -> PartKind.HANDLE;
            case GUARD -> PartKind.GUARD;
            case WRAP -> PartKind.WRAP;
            case PLATE -> PartKind.PLATE;
            case PADDING -> PartKind.PADDING;
        };
    }

    private void shape(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(leftPos + x0 - 1, topPos + y0 - 1, leftPos + x1 + 1, topPos + y1 + 1, 0xFF8A8A98);
    }

    private void fillShape(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, 0xFF5C5C6A);
    }

    /** Draws a family's outline: a light edge pass, then the body, so touching pieces read as one shape. */
    private void outline(GuiGraphics g, Family family) {
        int[][] rects = switch (family) {
            case SWORD -> new int[][] {{115, 16, 121, 20}, {112, 20, 124, 58}, {86, 59, 150, 71}, {113, 71, 123, 96}, {108, 96, 128, 116}};
            case AXE -> new int[][] {{113, 18, 123, 114}, {80, 20, 113, 52}, {76, 25, 80, 47}};
            case BOW -> new int[][] {{116, 16, 126, 22}, {124, 20, 134, 34}, {132, 32, 140, 50}, {134, 50, 144, 74}, {132, 74, 140, 92},
                    {124, 90, 134, 104}, {116, 102, 126, 110}, {100, 20, 102, 108}, {101, 18, 118, 20}, {101, 106, 118, 108}};
            case CROSSBOW -> new int[][] {{70, 30, 164, 38}, {66, 34, 72, 44}, {162, 34, 168, 44}, {112, 38, 124, 114}, {72, 44, 110, 46},
                    {126, 44, 162, 46}};
            case SHIELD -> new int[][] {{84, 18, 150, 92}, {90, 92, 144, 104}, {100, 104, 134, 112}};
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
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
        if (partsTab) {
            return;
        }
        ItemStack weapon = menu.weapon();
        Family family = menu.family();
        if (Parts.modular(weapon)) {
            Parts.Totals t = Parts.totals(weapon);
            List<String> lines = new ArrayList<>();
            if (family.armor()) {
                lines.add(pct("Armor", t.armor));
            } else {
                lines.add(pct("Damage", t.damage));
                if (family != Family.BOW && family != Family.CROSSBOW && family != Family.SHIELD) {
                    lines.add(pct("Speed", -t.stamina));
                }
            }
            lines.add(pct("Durab.", t.durability));
            int ly = 42;
            for (String line : lines) {
                small(g, line, 6, ly, 0xFF303030);
                ly += 8;
            }
        } else if (family == null) {
            small(g, "Pick what to build,", AREA_X0 + 20, 58, 0xFFB0B0BC);
            small(g, "or put an item in", AREA_X0 + 20, 66, 0xFFB0B0BC);
            small(g, "the slot to modify it.", AREA_X0 + 20, 74, 0xFFB0B0BC);
        }
        if (family != null && menu.buildMode()) {
            // How many plates the armor takes, on the plate slot.
            for (int i = 0; i < WeaponBenchMenu.PARTS; i++) {
                Slot slot = menu.partSlot(i);
                if (slot != null && menu.slotFor(i) == PartSlot.PLATE) {
                    String n = "x" + WeaponBenchMenu.platesFor(family);
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.drawString(font, n, slot.x + 17 - font.width(n), slot.y - 8, 0xFFE0C060, true);
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
                small(g, "L" + level, slot.x + 18, slot.y + 10, 0xFFE0C060);
                if (level < PartDef.MAX_LEVEL) {
                    int bx = slot.x + 17;
                    int by = slot.y;
                    boolean hover = mouseX - leftPos >= bx && mouseX - leftPos < bx + 9 && mouseY - topPos >= by && mouseY - topPos < by + 9;
                    g.fill(bx, by, bx + 9, by + 9, hover ? 0xFF4C8C4C : 0xFF3A6A3A);
                    g.drawString(font, "▲", bx + 2, by + 1, 0xFFFFFFFF, false);
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

    private void renderBuild(GuiGraphics g, int mouseX, int mouseY) {
        if (!menu.buildMode()) {
            return;
        }
        for (int i = 0; i < PICK_ICONS.length; i++) {
            int px = pickX(i);
            int py = pickY(i);
            boolean selected = menu.buildingIndex() == i;
            boolean hover = mouseX >= px && mouseX < px + 17 && mouseY >= py && mouseY < py + 17;
            g.fill(px, py, px + 17, py + 17, selected ? 0xFF3A6A3A : hover ? 0xFF9A9AAA : 0xFF7A7A88);
            g.renderItem(new ItemStack(PICK_ICONS[i]), px, py);
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
            g.fill(ax, ay, ax + 54, ay + 14, !ready ? 0xFF6A6A70 : hover ? 0xFF4C8C4C : 0xFF3A6A3A);
            g.drawString(font, "ASSEMBLE", ax + (54 - font.width("ASSEMBLE")) / 2, ay + 3, ready ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            String problem = menu.assemblyProblem();
            if (!ready && problem != null && mouseX >= ax && mouseX < ax + 54 && mouseY >= ay && mouseY < ay + 14) {
                g.renderTooltip(font, Component.literal(problem), mouseX, mouseY);
            }
        }
    }

    private int partAt(double mouseX, double mouseY) {
        int kinds = PartKind.values().length;
        double lx = mouseX - leftPos - GRID_X;
        double ly = mouseY - topPos - GRID_Y;
        if (lx < 0 || ly < 0) {
            return -1;
        }
        int k = (int) (lx / COL);
        int t = (int) (ly / ROW);
        if (k >= kinds || t >= 5 || lx - k * COL > 18 || ly - t * ROW > 17) {
            return -1;
        }
        return t * kinds + k;
    }

    private void renderParts(GuiGraphics g, int mouseX, int mouseY) {
        int kinds = PartKind.values().length;
        for (int k = 0; k < kinds; k++) {
            g.pose().pushPose();
            g.pose().translate(leftPos + GRID_X + k * COL, topPos + GRID_Y - 6, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.drawString(font, KIND_SHORT[k], 0, 0, 0xFF404040, false);
            g.pose().popPose();
        }
        int hovered = partAt(mouseX, mouseY);
        for (int i = 0; i < PartDef.ALL.size(); i++) {
            int k = i % kinds;
            int t = i / kinds;
            int px = leftPos + GRID_X + k * COL;
            int py = topPos + GRID_Y + t * ROW;
            boolean can = menu.canMake(i);
            g.fill(px, py, px + 18, py + 17, hovered == i ? 0xFF9A9AAA : can ? 0xFF4C7C4C : 0xFF6A6A74);
            g.renderItem(new ItemStack(PartItems.get(PartDef.ALL.get(i))), px + 1, py);
        }
        if (hovered >= 0) {
            PartDef def = PartDef.ALL.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(def.label()));
            for (Cost c : def.costs()) {
                lines.add(Component.literal("  " + c.count() + " " + c.label()).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            lines.add(Component.literal(menu.canMake(hovered) ? "Click: make one. Shift: make 5." : "Not enough materials")
                    .withStyle(menu.canMake(hovered) ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
            g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        StationTabs.render(g, font, leftPos, topPos - 14, imageWidth, StationTabs.WEAPON_BENCH, mouseX, mouseY);
        if (partsTab) {
            renderParts(g, mouseX, mouseY);
            return;
        }
        renderBuild(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        if (menu.buildMode()) {
            for (int i = 0; i < PICK_ICONS.length; i++) {
                if (mouseX >= pickX(i) && mouseX < pickX(i) + 17 && mouseY >= pickY(i) && mouseY < pickY(i) + 17) {
                    g.renderTooltip(font, Component.literal("Build a " + PICK_NAMES[i].toLowerCase()), mouseX, mouseY);
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
                if (mouseX >= pickX(i) && mouseX < pickX(i) + 17 && mouseY >= pickY(i) && mouseY < pickY(i) + 17) {
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
