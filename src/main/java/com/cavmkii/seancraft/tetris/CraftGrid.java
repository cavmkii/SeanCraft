package com.cavmkii.seancraft.tetris;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * The CRAFTING tab's own 3x3 grid. Items placed in it are taken out of your hotbar, grids or stash (the
 * "materials"), and go back when the screen closes. Anything a crafting table can make works here, including
 * custom recipes; 3x3 shapes need a crafting table within reach or the hideout.
 */
final class CraftGrid {
    private CraftGrid() {}

    static NonNullList<ItemStack> cells(ServerPlayer player) {
        return Tetris.rt(player).craft;
    }

    /** Everything usable as a material, merged by item, in a fixed order. Copies with total counts. */
    static List<ItemStack> materials(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : Tetris.pool(player, new ArrayList<>())) {
            if (s.isEmpty()) {
                continue;
            }
            ItemStack same = null;
            for (ItemStack m : out) {
                if (ItemStack.isSameItemSameComponents(m, s)) {
                    same = m;
                    break;
                }
            }
            if (same != null) {
                same.grow(s.getCount());
            } else {
                out.add(s.copy());
            }
        }
        out.sort((a, b) -> {
            int c = BuiltInRegistries.ITEM.getKey(a.getItem()).compareTo(BuiltInRegistries.ITEM.getKey(b.getItem()));
            return c != 0 ? c : Integer.compare(b.getCount(), a.getCount());
        });
        return out;
    }

    /** Takes up to {@code count} of the material at an index out of hotbar, grids and stash. */
    static ItemStack takeMaterial(ServerPlayer player, int index, int count) {
        List<ItemStack> mats = materials(player);
        if (index < 0 || index >= mats.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack template = mats.get(index);
        int want = Math.min(count <= 0 ? template.getMaxStackSize() : count, template.getMaxStackSize());
        List<Container> touched = new ArrayList<>();
        ItemStack out = template.copyWithCount(0);
        for (ItemStack s : Tetris.pool(player, touched)) {
            if (want <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(s, template)) {
                int n = Math.min(want, s.getCount());
                s.shrink(n);
                out.grow(n);
                want -= n;
            }
        }
        touched.forEach(Container::setChanged);
        return out;
    }

    record Preview(ItemStack result, boolean needsTable, RecipeHolder<CraftingRecipe> recipe, CraftingInput.Positioned input) {}

    static Preview preview(ServerPlayer player) {
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(3, 3, cells(player));
        CraftingInput input = positioned.input();
        if (input.isEmpty()) {
            return new Preview(ItemStack.EMPTY, false, null, positioned);
        }
        var holder = player.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level()).orElse(null);
        if (holder == null) {
            return new Preview(ItemStack.EMPTY, false, null, positioned);
        }
        if (!com.cavmkii.seancraft.rpg.Ladders.canCraft(player, holder.value().getResultItem(player.registryAccess()))) {
            return new Preview(ItemStack.EMPTY, false, null, positioned);
        }
        boolean big = input.width() > 2 || input.height() > 2;
        if (big && !Tetris.nearTable(player)) {
            return new Preview(ItemStack.EMPTY, true, holder, positioned);
        }
        return new Preview(holder.value().assemble(input, player.registryAccess()), false, holder, positioned);
    }

    /** Crafts from the grid up to {@code times} times. With a remembered recipe, refills between crafts. */
    static void craft(ServerPlayer player, int times) {
        NonNullList<ItemStack> cells = cells(player);
        int made = 0;
        ItemStack first = ItemStack.EMPTY;
        for (int t = 0; t < Math.max(1, Math.min(64, times)); t++) {
            if (t > 0 && Tetris.rt(player).lastRecipe != null && cells.stream().allMatch(ItemStack::isEmpty)) {
                fill(player, Tetris.rt(player).lastRecipe, false);
            }
            Preview p = preview(player);
            if (p.result.isEmpty()) {
                break;
            }
            NonNullList<ItemStack> remaining = p.recipe.value().getRemainingItems(p.input.input());
            int w = p.input.input().width();
            for (int i = 0; i < p.input.input().size(); i++) {
                int cell = (p.input.top() + i / w) * 3 + p.input.left() + i % w;
                cells.get(cell).shrink(1);
                ItemStack rest = remaining.get(i);
                if (!rest.isEmpty()) {
                    if (cells.get(cell).isEmpty()) {
                        cells.set(cell, rest.copy());
                    } else {
                        Tetris.give(player, rest.copy());
                    }
                }
            }
            for (int i = 0; i < 9; i++) {
                if (cells.get(i).isEmpty()) {
                    cells.set(i, ItemStack.EMPTY);
                }
            }
            ItemStack out = p.result.copy();
            out.onCraftedBy(player.level(), player, out.getCount());
            if (first.isEmpty()) {
                first = out.copy();
            }
            Tetris.give(player, out);
            made++;
        }
        // The recipe stays selected: lay it out again so the next click crafts another.
        if (made > 0 && Tetris.rt(player).lastRecipe != null && cells.stream().allMatch(ItemStack::isEmpty)) {
            fill(player, Tetris.rt(player).lastRecipe, false);
        }
        if (made > 0) {
            player.awardStat(net.minecraft.stats.Stats.ITEM_CRAFTED.get(first.getItem()), made * first.getCount());
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }

    /**
     * Empties the grid back into your inventory, then lays a recipe out in it with what you have. Missing
     * ingredients leave their cells empty so you can see what's short.
     */
    static void fill(ServerPlayer player, String id, boolean returnFirst) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var holder = key == null ? null : player.server.getRecipeManager().byKey(key).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) {
            return;
        }
        if (returnFirst) {
            returnAll(player);
        }
        Tetris.rt(player).lastRecipe = id;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        List<Container> touched = new ArrayList<>();
        List<ItemStack> pool = Tetris.pool(player, touched);
        Map<ItemStack, Integer> used = new IdentityHashMap<>();
        NonNullList<ItemStack> cells = cells(player);
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            Ingredient ing = ingredients.get(i);
            if (ing.isEmpty()) {
                continue;
            }
            int cell = (i / width) * 3 + i % width;
            if (!cells.get(cell).isEmpty()) {
                continue;
            }
            for (ItemStack s : pool) {
                if (!s.isEmpty() && s.getCount() > used.getOrDefault(s, 0) && ing.test(s)) {
                    used.merge(s, 1, Integer::sum);
                    cells.set(cell, s.copyWithCount(1));
                    break;
                }
            }
        }
        used.forEach(ItemStack::shrink);
        touched.forEach(Container::setChanged);
    }

    static void returnAll(ServerPlayer player) {
        NonNullList<ItemStack> cells = cells(player);
        for (int i = 0; i < 9; i++) {
            if (!cells.get(i).isEmpty()) {
                Tetris.give(player, cells.get(i));
                cells.set(i, ItemStack.EMPTY);
            }
        }
    }
}
