package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.tetris.GearSpec;
import com.cavmkii.seancraft.tetris.Tetris;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Things only the Hideout Bench makes, some gated behind module levels. Secure containers, meds, rigs and packs. */
public record HideoutRecipe(String id, Supplier<ItemStack> result, List<Cost> costs, Module gate, int gateLevel) {
    public static final List<HideoutRecipe> ALL = new ArrayList<>();

    static {
        ALL.add(new HideoutRecipe("secure_beta", () -> new ItemStack(TarkovContent.SECURE_BETA),
                List.of(Cost.of(Items.COPPER_INGOT, 16), Cost.of(Items.IRON_INGOT, 4)), Module.WORKSHOP, 1));
        ALL.add(new HideoutRecipe("secure_gamma", () -> new ItemStack(TarkovContent.SECURE_GAMMA),
                List.of(Cost.of(Items.IRON_INGOT, 16), Cost.of(Items.GOLD_INGOT, 10)), Module.WORKSHOP, 2));
        ALL.add(new HideoutRecipe("secure_kappa", () -> new ItemStack(TarkovContent.SECURE_KAPPA),
                List.of(Cost.of(Items.DIAMOND, 8), Cost.of(Items.GOLD_INGOT, 20)), Module.WORKSHOP, 3));
        ALL.add(new HideoutRecipe("fire_arrow", () -> new ItemStack(com.cavmkii.seancraft.item.ModItems.FIRE_ARROW, 4),
                List.of(Cost.of(Items.ARROW, 4), Cost.tag(net.minecraft.tags.ItemTags.COALS, 1, "Coal or charcoal"), Cost.of(Items.STRING, 1)), null, 0));
        // Arrow types (9.4.4).
        ALL.add(new HideoutRecipe("arrow_broadhead", () -> new ItemStack(com.cavmkii.seancraft.item.ModItems.BROADHEAD_ARROW, 4),
                List.of(Cost.of(Items.ARROW, 4), Cost.of(Items.IRON_NUGGET, 4), Cost.of(Items.FLINT, 1)), null, 0));
        ALL.add(new HideoutRecipe("arrow_bodkin", () -> new ItemStack(com.cavmkii.seancraft.item.ModItems.BODKIN_ARROW, 4),
                List.of(Cost.of(Items.ARROW, 4), Cost.of(Items.IRON_INGOT, 1)), Module.WORKSHOP, 1));
        ALL.add(new HideoutRecipe("arrow_blunt", () -> new ItemStack(com.cavmkii.seancraft.item.ModItems.BLUNT_ARROW, 4),
                List.of(Cost.of(Items.ARROW, 4), Cost.tag(net.minecraft.tags.ItemTags.PLANKS, 1, "Planks"), Cost.of(Items.LEATHER, 1)), null, 0));
        ALL.add(new HideoutRecipe("mark_totem", () -> new ItemStack(com.cavmkii.seancraft.boss.BossContent.MARK_TOTEM),
                List.of(Cost.of(Items.COPPER_INGOT, 4), Cost.of(Items.FISHING_ROD, 1), Cost.of(Items.COD, 1), Cost.of(Items.WRITABLE_BOOK, 1)), null, 0));
        ALL.add(new HideoutRecipe("daylan_totem", () -> new ItemStack(com.cavmkii.seancraft.boss.BossContent.DAYLAN_TOTEM),
                List.of(Cost.of(Items.COPPER_INGOT, 4), Cost.of(Items.EMERALD, 1), Cost.of(Items.GLOW_INK_SAC, 1), Cost.of(Items.CHERRY_SAPLING, 1)), null, 0));
        ALL.add(new HideoutRecipe("bo_daddy_totem", () -> new ItemStack(com.cavmkii.seancraft.boss.BossContent.BO_DADDY_TOTEM),
                List.of(Cost.of(Items.COPPER_INGOT, 4), Cost.of(Items.NOTE_BLOCK, 1), Cost.of(Items.LEATHER, 1), Cost.of(Items.POPPY, 1)), null, 0));
        // The module stations (2.5): placed in the hideout, then built and upgraded from their own screens.
        ALL.add(new HideoutRecipe("workshop", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.WORKSHOP)),
                List.of(Cost.of(Items.SMITHING_TABLE, 1), Cost.planks(8)), null, 0));
        ALL.add(new HideoutRecipe("medstation", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.MEDSTATION)),
                List.of(Cost.of(Items.BREWING_STAND, 1), Cost.planks(8)), null, 0));
        ALL.add(new HideoutRecipe("perimeter", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.PERIMETER)),
                List.of(Cost.of(Items.STONE_BRICKS, 8), Cost.planks(4)), null, 0));
        ALL.add(new HideoutRecipe("intel", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.INTEL)),
                List.of(Cost.of(Items.LECTERN, 1), Cost.of(Items.COMPASS, 1)), null, 0));
        ALL.add(new HideoutRecipe("trophy_rack", () -> new ItemStack(com.cavmkii.seancraft.boss.BossContent.TROPHY_RACK_ITEM),
                List.of(Cost.planks(6), Cost.of(Items.ITEM_FRAME, 1), Cost.of(Items.IRON_INGOT, 2)), null, 0));
        ALL.add(new HideoutRecipe("fob_table", () -> new ItemStack(TarkovContent.FOB_TABLE_ITEM),
                List.of(Cost.planks(8), Cost.of(Items.STICK, 4), Cost.of(Items.CAMPFIRE, 1)), null, 0));
        // Rigs and backpacks (3.1). The scav vest and sling bag are the starting kit; the rest are made here.
        gear(GearSpec.RIG_CHEST, null, 0, Cost.of(Items.LEATHER, 6), Cost.of(Items.STRING, 4));
        gear(GearSpec.RIG_CARRIER, Module.WORKSHOP, 1, Cost.of(Items.LEATHER, 10), Cost.of(Items.STRING, 6), Cost.of(Items.IRON_NUGGET, 4));
        gear(GearSpec.RIG_ASSAULT, Module.WORKSHOP, 2, Cost.of(Items.LEATHER, 12), Cost.of(Items.IRON_INGOT, 8), Cost.of(Items.RABBIT_HIDE, 2));
        gear(GearSpec.PACK_SCAV, null, 0, Cost.of(Items.LEATHER, 8), Cost.of(Items.STRING, 4));
        gear(GearSpec.PACK_PILGRIM, Module.WORKSHOP, 1, Cost.of(Items.LEATHER, 14), Cost.of(Items.STRING, 8));
        gear(GearSpec.PACK_EXPEDITION, Module.WORKSHOP, 3, Cost.of(Items.LEATHER, 16), Cost.of(Items.RABBIT_HIDE, 6), Cost.of(Items.PHANTOM_MEMBRANE, 4));
        ALL.add(new HideoutRecipe("antidote", () -> new ItemStack(TarkovContent.ANTIDOTE),
                List.of(Cost.of(Items.HONEY_BOTTLE, 1), Cost.of(Items.SUGAR, 2), Cost.of(Items.BROWN_MUSHROOM, 1)), Module.MEDSTATION, 1));
        for (Med med : Med.values()) {
            ALL.add(new HideoutRecipe(med.id, () -> new ItemStack(TarkovContent.MEDS.get(med)), med.costs, med.gate, med.gateLevel));
        }
    }

    /** Makes a rig or backpack, and the upgrade to it from the one before: trade that in, empty, plus half the materials. */
    private static void gear(GearSpec spec, Module gate, int gateLevel, Cost... costs) {
        ALL.add(new HideoutRecipe(spec.id, () -> new ItemStack(Tetris.GEAR.get(spec)), List.of(costs), gate, gateLevel));
        GearSpec from = spec.previous();
        if (from != null) {
            List<Cost> half = new ArrayList<>();
            half.add(Cost.of(Tetris.GEAR.get(from), 1));
            for (Cost c : costs) {
                half.add(c.times(0.5f));
            }
            ALL.add(new HideoutRecipe(UPGRADE + spec.id, () -> new ItemStack(Tetris.GEAR.get(spec)), half, gate, gateLevel));
        }
    }

    public static final String UPGRADE = "upgrade_";

    public static HideoutRecipe byId(String id) {
        for (HideoutRecipe r : ALL) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }
}
