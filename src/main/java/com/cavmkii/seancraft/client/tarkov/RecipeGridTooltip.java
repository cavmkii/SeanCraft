package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** A recipe's 3x3 layout drawn inside its tooltip, so you can see the pattern before picking it. */
public final class RecipeGridTooltip {
    static final int CELL = 18;

    /** The nine cells, top-left first; null or empty for a blank square. */
    public record Data(ItemStack[] cells) implements TooltipComponent {
        /** A pattern's ghost letters, each drawn as an example of its material. */
        public static Data of(char[] letters, java.util.function.Function<Character, ItemStack> example) {
            ItemStack[] cells = new ItemStack[9];
            for (int i = 0; i < 9 && i < letters.length; i++) {
                cells[i] = letters[i] == 0 ? null : example.apply(letters[i]);
            }
            return new Data(cells);
        }
    }

    private RecipeGridTooltip() {}

    static void init() {
        TooltipComponentCallback.EVENT.register(data -> data instanceof Data d ? new Component(d) : null);
    }

    private record Component(Data data) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 3 * CELL + 4;
        }

        @Override
        public int getWidth(Font font) {
            return 3 * CELL;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            for (int c = 0; c < 9; c++) {
                int cx = x + (c % 3) * CELL;
                int cy = y + 2 + (c / 3) * CELL;
                Kit.slot(g, cx, cy, CELL, Kit.SlotState.NORMAL);
                ItemStack s = data.cells[c];
                if (s != null && !s.isEmpty()) {
                    g.renderItem(s, cx + 1, cy + 1);
                }
            }
        }
    }
}
