package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.pattern.Pattern;
import com.cavmkii.seancraft.pattern.Patterns;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Weapon Bench's 3x3 grid (design doc 4.6, 4.8). In order:
 * <ol>
 *   <li>Levelling: a part in the centre with its own material in that many ring squares (L+1 for metal and gems,
 *       double that for wood and hide, at most eight) gives the part one level up.</li>
 *   <li>Netherite: netherite scrap, a diamond part, netherite scrap in a row gives the netherite part.</li>
 *   <li>Part patterns from data: the material placed decides the part's material. A crystal's catalyst sets its
 *       element; a wrap with one iron nugget, flint or lapis beside it gets an infusion (9.4.3).</li>
 * </ol>
 */
public final class PartCrafting {
    private static final Item[] METAL = {Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
    private static final Item[] HIDE = {Items.STRING, Items.LEATHER, Items.RABBIT_HIDE, Items.PHANTOM_MEMBRANE, Items.ARMADILLO_SCUTE};
    private static final Item[] GEM = {Items.QUARTZ, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD, Items.PRISMARINE_SHARD, Items.ECHO_SHARD};
    private static final Item[] CATALYST = {Items.BLAZE_POWDER, Items.PACKED_ICE, Items.LIGHTNING_ROD, Items.FERMENTED_SPIDER_EYE, Items.GOLDEN_CARROT};

    /** What the grid makes and how many of each square it uses up. */
    public record Result(ItemStack output, int[] use) {
        static final Result NONE = new Result(ItemStack.EMPTY, new int[9]);
    }

    private PartCrafting() {}

    static int index(Item[] list, ItemStack stack) {
        for (int i = 0; i < list.length; i++) {
            if (stack.is(list[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Tier (1-5) of a stack as the given letter's material, 0 if it isn't one. */
    static int tier(char letter, ItemStack s) {
        if (s.isEmpty()) {
            return 0;
        }
        return switch (letter) {
            case 'M' -> index(METAL, s);
            case 'W' -> s.is(ItemTags.PLANKS) ? 1 : s.is(ItemTags.LOGS) ? 2 : s.is(Items.BONE) ? 3 : s.is(Items.BLAZE_ROD) ? 4 : s.is(Items.BREEZE_ROD) ? 5 : 0;
            case 'H' -> index(HIDE, s);
            case 'G' -> index(GEM, s);
            case 'C' -> index(CATALYST, s);
            case 'S' -> s.is(Items.STICK) ? 1 : 0;
            default -> 0;
        };
    }

    static char letterOf(PartKind.Material m) {
        return switch (m) {
            case METAL -> 'M';
            case WOOD -> 'W';
            case HIDE -> 'H';
            case GEM -> 'G';
        };
    }

    /** True if the stack is the part's own material (any planks or logs for the first two woods). */
    static boolean isMaterial(PartDef def, ItemStack s) {
        if (def.kind().material == PartKind.Material.METAL && def.tier() == 5) {
            return s.is(Items.NETHERITE_SCRAP);
        }
        return tier(letterOf(def.kind().material), s) == def.tier();
    }

    /** Ring squares of material a part needs to go from {@code level} to the next (4.6). */
    public static int ringNeeded(PartDef def, int level) {
        boolean cheap = def.kind().material == PartKind.Material.METAL || def.kind().material == PartKind.Material.GEM;
        return Math.min(8, (level + 1) * (cheap ? 1 : 2));
    }

    public static Result match(ItemStack[] grid) {
        Result r = level(grid);
        if (r != null) {
            return r;
        }
        r = netherite(grid);
        if (r != null) {
            return r;
        }
        r = pattern(grid);
        if (r != null) {
            return r;
        }
        // A wrap with one infusion ingredient beside it.
        for (int i = 0; i < 9; i++) {
            String inf = infusion(grid[i]);
            if (inf == null || grid[i].getCount() < 1) {
                continue;
            }
            ItemStack[] rest = grid.clone();
            rest[i] = ItemStack.EMPTY;
            Result wrap = pattern(rest);
            if (wrap != null && wrap.output.getItem() instanceof PartItem p && p.def.kind() == PartKind.WRAP) {
                ItemStack out = wrap.output.copy();
                out.set(Parts.INFUSION, inf);
                int[] use = wrap.use.clone();
                use[i] = 1;
                return new Result(out, use);
            }
        }
        return Result.NONE;
    }

    static String infusion(ItemStack s) {
        return s.is(Items.IRON_NUGGET) ? "heavy" : s.is(Items.FLINT) ? "keen" : s.is(Items.LAPIS_LAZULI) ? "arcane" : null;
    }

    private static Result level(ItemStack[] grid) {
        if (!(grid[4].getItem() instanceof PartItem part)) {
            return null;
        }
        int level = Parts.level(grid[4]);
        int needed = ringNeeded(part.def, level);
        int have = 0;
        int[] use = new int[9];
        use[4] = 1;
        for (int i = 0; i < 9; i++) {
            if (i == 4 || grid[i].isEmpty()) {
                continue;
            }
            if (!isMaterial(part.def, grid[i])) {
                return Result.NONE;
            }
            have++;
            use[i] = 1;
        }
        if (level >= PartDef.MAX_LEVEL || have != needed) {
            return Result.NONE;
        }
        ItemStack out = grid[4].copyWithCount(1);
        out.set(Parts.LEVEL, level + 1);
        return new Result(out, use);
    }

    private static Result netherite(ItemStack[] grid) {
        for (int row = 0; row < 3; row++) {
            int a = row * 3;
            if (!grid[a].is(Items.NETHERITE_SCRAP) || !grid[a + 2].is(Items.NETHERITE_SCRAP)
                    || !(grid[a + 1].getItem() instanceof PartItem p) || p.def.kind().material != PartKind.Material.METAL || p.def.tier() != 4) {
                continue;
            }
            for (int i = 0; i < 9; i++) {
                if (i / 3 != row && !grid[i].isEmpty()) {
                    return null;
                }
            }
            PartDef up = PartDef.of(p.def.kind(), 5);
            ItemStack out = new ItemStack(PartItems.get(up));
            int lv = Parts.level(grid[a + 1]);
            if (lv > 1) {
                out.set(Parts.LEVEL, lv);
            }
            int[] use = new int[9];
            use[a] = use[a + 1] = use[a + 2] = 1;
            return new Result(out, use);
        }
        return null;
    }

    private static Result pattern(ItemStack[] grid) {
        for (Pattern p : Patterns.station("part")) {
            PartKind kind;
            try {
                kind = PartKind.valueOf(p.part);
            } catch (Exception e) {
                continue;
            }
            char main = letterOf(kind.material);
            char[] at = p.place(grid, (c, s) -> s.getItem() instanceof PartItem ? false : tier(c, s) > 0);
            if (at == null) {
                continue;
            }
            // Every square of a letter holds the same material.
            int tier = 0;
            int catalyst = 0;
            boolean same = true;
            int[] use = new int[9];
            int[] seen = new int[128];
            for (int i = 0; i < 9 && same; i++) {
                if (at[i] == 0) {
                    continue;
                }
                use[i] = 1;
                int t = tier(at[i], grid[i]);
                if (seen[at[i]] != 0 && seen[at[i]] != t) {
                    same = false;
                }
                seen[at[i]] = t;
                if (at[i] == main) {
                    tier = t;
                }
                if (at[i] == 'C') {
                    catalyst = t;
                }
            }
            if (!same || tier == 0) {
                continue;
            }
            PartDef def = PartDef.of(kind, tier);
            if (def == null) {
                continue;
            }
            ItemStack out = new ItemStack(PartItems.get(def));
            if (kind == PartKind.CRYSTAL && catalyst > 0) {
                out.set(Parts.INFUSION, com.cavmkii.seancraft.rpg.Magic.ELEMENTS[catalyst - 1]);
            }
            return new Result(out, use);
        }
        return null;
    }

    /** A material stack for a letter, for the book's ghost: iron, logs, leather, amethyst, blaze powder, stick. */
    public static ItemStack example(char letter) {
        return new ItemStack(switch (letter) {
            case 'M' -> Items.IRON_INGOT;
            case 'W' -> Items.OAK_LOG;
            case 'H' -> Items.LEATHER;
            case 'G' -> Items.AMETHYST_SHARD;
            case 'C' -> Items.BLAZE_POWDER;
            case 'S' -> Items.STICK;
            default -> Items.AIR;
        });
    }
}
