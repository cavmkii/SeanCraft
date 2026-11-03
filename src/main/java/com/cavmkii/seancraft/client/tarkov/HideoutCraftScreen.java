package com.cavmkii.seancraft.client.tarkov;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.pattern.Pattern;
import com.cavmkii.seancraft.pattern.Patterns;
import com.cavmkii.seancraft.tarkov.HideoutCraftMenu;
import com.cavmkii.seancraft.tarkov.Module;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Hideout Bench's grid and recipe book (4.8): picking a recipe draws its pattern as a ghost; locked ones show the module level. */
public class HideoutCraftScreen extends AbstractContainerScreen<HideoutCraftMenu> {
    private static final int BOOK_X = 4;
    private static final int BOOK_Y = 18;
    private static final int BOOK_W = 62;
    private static final int ROW = 10;
    private static final int ROWS = 10;
    private static final List<String> ORDER = List.of("Rigs", "Backpacks", "Pouches", "Meds", "Arrows", "Stations", "Modules", "Totems");

    private int scroll;
    private Pattern ghost;

    public HideoutCraftScreen(HideoutCraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 212;
        inventoryLabelY = 119;
    }

    /** This station's recipes: the Hideout Bench's own, or one module's. */
    private List<Pattern> book() {
        List<Pattern> out = new ArrayList<>();
        for (Pattern p : Patterns.station("hideout")) {
            if (HideoutCraftMenu.belongs(p, menu.station())) {
                out.add(p);
            }
        }
        out.sort(Comparator.<Pattern>comparingInt(p -> {
            int i = ORDER.indexOf(p.category);
            return i < 0 ? 99 : i;
        }).thenComparing(p -> p.id));
        return out;
    }

    private String stationKind() {
        return menu.station() == null ? StationTabs.HIDEOUT : com.cavmkii.seancraft.tarkov.Stations.moduleKind(menu.station());
    }

    /** On a module's station: UPGRADE goes to its build-and-upgrade screen. */
    private boolean overUpgrade(double mx, double my) {
        int bx = leftPos + imageWidth - 56;
        int by = topPos + 5;
        return menu.station() != null && mx >= bx && mx < bx + 50 && my >= by && my < by + 12;
    }

    private void upgradeButton(GuiGraphics g, int mx, int my) {
        if (menu.station() != null) {
            Kit.button(g, font, "UPGRADE", leftPos + imageWidth - 56, topPos + 5, 50, 12, true, overUpgrade(mx, my), false);
        }
    }

    private String gateText(Pattern p) {
        try {
            return Module.valueOf(p.gateModule).label + " " + p.gateLevel;
        } catch (Exception e) {
            return p.gateModule + " " + p.gateLevel;
        }
    }

    private int rowAt(double mx, double my) {
        int r = (int) Math.floor((my - topPos - BOOK_Y) / ROW);
        if (mx < leftPos + BOOK_X || mx >= leftPos + BOOK_X + BOOK_W || r < 0 || r >= ROWS) {
            return -1;
        }
        return r + scroll;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Kit.panel(g, x, y, imageWidth, imageHeight);
        // The hotbar row under the stash box: framed so it reads as usable slots.
        for (int col = 0; col < 9; col++) {
            Kit.slot(g, x + 7 + col * 18, y + 187, 18, Kit.SlotState.NORMAL);
        }
        Kit.inset(g, x + BOOK_X - 1, y + BOOK_Y - 1, BOOK_W + 2, ROWS * ROW + 2);
        List<Pattern> book = book();
        scroll = Math.max(0, Math.min(Math.max(0, book.size() - ROWS), scroll));
        int hovered = rowAt(mouseX, mouseY);
        for (int r = 0; r < ROWS && r + scroll < book.size(); r++) {
            Pattern p = book.get(r + scroll);
            int ry = y + BOOK_Y + r * ROW;
            boolean ok = menu.allowed(p);
            String row = !ok ? "list/row_locked" : p == ghost ? "list/row_selected" : hovered == r + scroll ? "list/row_hover" : null;
            if (row != null) {
                Kit.blit(g, row, x + BOOK_X, ry, BOOK_W, ROW);
            }
            g.pose().pushPose();
            g.pose().translate(x + BOOK_X + 1, ry + 1, 0);
            g.pose().scale(0.5f, 0.5f, 1);
            g.renderItem(p.resultStack(), 0, 0);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(x + BOOK_X + 11, ry + 2, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            String name = font.plainSubstrByWidth(p.resultStack().getHoverName().getString(), (int) ((BOOK_W - 20) / 0.625f));
            g.drawString(font, name, 0, 0, !ok ? Kit.DISABLED : p == ghost ? Kit.GOLD_LIGHT : Kit.BONE, false);
            g.pose().popPose();
            if (!ok) {
                Kit.blit(g, "list/lock", x + BOOK_X + BOOK_W - 9, ry + 1, 7, 8);
            }
        }
        boolean ready = menu.slots.get(9).hasItem();
        if (ready) {
            ghost = null;
        }
        // Hovering a book entry previews its pattern; otherwise the picked one.
        int hr = rowAt(mouseX, mouseY);
        Pattern shown = hr >= 0 && hr < book.size() && !ready ? book.get(hr) : ghost;
        char[] at = shown == null ? null : shown.ghost();
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            if (!slot.hasItem() && at != null && at[i] != 0) {
                Kit.blit(g, "slot/ghost", sx, sy, 18, 18);
                g.renderItem(shown.example(at[i]), sx + 1, sy + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                Kit.blit(g, "slot/ghost_overlay", sx + 1, sy + 1, 16, 16);
                g.pose().popPose();
            } else {
                Kit.slot(g, sx, sy, 18, Kit.SlotState.NORMAL);
            }
        }
        Kit.blit(g, ready ? "progress/arrow_full" : "progress/arrow_empty", x + HideoutCraftMenu.GRID_X + 60, y + HideoutCraftMenu.GRID_Y + 20, 22, 15);
        Kit.blit(g, ready ? "slot/result_ready" : "slot/result", x + HideoutCraftMenu.RESULT_X - 5, y + HideoutCraftMenu.RESULT_Y - 5, 26, 26);
        if (menu.gated()) {
            Kit.blit(g, "list/lock", x + HideoutCraftMenu.RESULT_X + 4, y + HideoutCraftMenu.RESULT_Y + 4, 7, 8);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Kit.text(g, font, Kit.heading(title.getString()), titleLabelX, titleLabelY - 2, Kit.GOLD_LIGHT);
        // Only where some recipe here is an upgrade (rigs, packs, pouches).
        if (book().stream().anyMatch(p -> p.previous != 0)) {
            g.pose().pushPose();
            g.pose().translate(70, 88, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.drawString(font, "Upgrades: put the empty old item", 0, 0, Kit.ASH, false);
            g.drawString(font, "in the centre of the pattern.", 0, 10, Kit.ASH, false);
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        StationTabs.render(g, font, leftPos, topPos - 14, imageWidth, stationKind(), mouseX, mouseY);
        upgradeButton(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
        int r = rowAt(mouseX, mouseY);
        List<Pattern> book = book();
        if (r >= 0 && r < book.size()) {
            Pattern p = book.get(r);
            List<Component> lines = new ArrayList<>();
            ItemStack result = p.resultStack();
            lines.add(Component.literal(result.getHoverName().getString() + (result.getCount() > 1 ? " x" + result.getCount() : "")));
            if (p.previous != 0) {
                lines.add(Component.literal("Upgrade: the empty " + p.example(p.previous).getHoverName().getString() + " goes in the centre")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!menu.allowed(p)) {
                lines.add(Component.literal("Needs " + gateText(p) + " in this hideout").withStyle(ChatFormatting.RED));
            }
            lines.add(Component.literal("Click: keep in grid").withStyle(ChatFormatting.DARK_GRAY));
            g.renderTooltip(font, lines, java.util.Optional.of(RecipeGridTooltip.Data.of(p.ghost(), p::example)), mouseX, mouseY);
        }
        if (menu.gated() && mouseX >= leftPos + HideoutCraftMenu.RESULT_X - 5 && mouseX < leftPos + HideoutCraftMenu.RESULT_X + 21
                && mouseY >= topPos + HideoutCraftMenu.RESULT_Y - 5 && mouseY < topPos + HideoutCraftMenu.RESULT_Y + 21) {
            g.renderTooltip(font, Component.literal("This hideout's module isn't high enough yet"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overUpgrade(mouseX, mouseY)) {
            ClientPlayNetworking.send(new com.cavmkii.seancraft.tarkov.TarkovNet.HideoutAction(net.minecraft.core.BlockPos.ZERO, -2, menu.station().name()));
            return true;
        }
        if (StationTabs.click(leftPos, topPos - 14, imageWidth, stationKind(), mouseX, mouseY)) {
            return true;
        }
        int r = rowAt(mouseX, mouseY);
        List<Pattern> book = book();
        if (r >= 0 && r < book.size()) {
            ghost = ghost == book.get(r) ? null : book.get(r);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < leftPos + BOOK_X + BOOK_W) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
