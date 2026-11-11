package com.cavmkii.seancraft.tarkov;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Things only the Home Bench and its modules make, some gated behind module levels (4.6). */
public record HideoutRecipe(String id, Supplier<ItemStack> result, List<Cost> costs, Module gate, int gateLevel) {
    public static final List<HideoutRecipe> ALL = new ArrayList<>();

    static {
        ALL.add(new HideoutRecipe("fire_arrow", () -> new ItemStack(com.cavmkii.seancraft.item.ModItems.FIRE_ARROW, 4),
                List.of(Cost.of(Items.ARROW, 4), Cost.tag(net.minecraft.tags.ItemTags.COALS, 1, "Coal or charcoal"), Cost.of(Items.STRING, 1)), null, 0));
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
        ALL.add(new HideoutRecipe("map_room", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.MAP_ROOM)),
                List.of(Cost.of(Items.LECTERN, 1), Cost.of(Items.COMPASS, 1)), null, 0));
        ALL.add(new HideoutRecipe("trophy_rack", () -> new ItemStack(com.cavmkii.seancraft.boss.BossContent.TROPHY_RACK_ITEM),
                List.of(Cost.planks(6), Cost.of(Items.ITEM_FRAME, 1), Cost.of(Items.IRON_INGOT, 2)), null, 0));
        ALL.add(new HideoutRecipe("storehouse", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.STOREHOUSE)),
                List.of(Cost.of(Items.CHEST, 1), Cost.planks(8)), null, 0));
        ALL.add(new HideoutRecipe("kitchen", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.KITCHEN)),
                List.of(Cost.planks(6), Cost.of(Items.CAMPFIRE, 1), Cost.of(Items.COBBLESTONE, 3)), null, 0));
        ALL.add(new HideoutRecipe("living_quarters", () -> new ItemStack(TarkovContent.MODULE_ITEMS.get(Module.LIVING_QUARTERS)),
                List.of(Cost.planks(6), Cost.tag(net.minecraft.tags.ItemTags.BEDS, 1, "Any bed"), Cost.of(Items.COBBLESTONE, 3)), null, 0));
        ALL.add(new HideoutRecipe("camp_kit", () -> new ItemStack(TarkovContent.CAMP_KIT),
                List.of(Cost.planks(8), Cost.of(Items.STICK, 4), Cost.of(Items.CAMPFIRE, 1)), null, 0));
        ALL.add(new HideoutRecipe("antidote", () -> new ItemStack(TarkovContent.ANTIDOTE),
                List.of(Cost.of(Items.HONEY_BOTTLE, 1), Cost.of(Items.SUGAR, 2), Cost.of(Items.BROWN_MUSHROOM, 1)), Module.MEDSTATION, 1));
        for (Med med : Med.values()) {
            ALL.add(new HideoutRecipe(med.id, () -> new ItemStack(TarkovContent.MEDS.get(med)), med.costs, med.gate, med.gateLevel));
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
