package com.cavmkii.seancraft.tarkov;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Home base modules (2.4.1), each its own block, built and upgraded in place. Each home base keeps its own levels.
 * Valheim's materials are mapped to Minecraft tiers: bronze to copper, iron to iron, nails to nuggets.
 */
public enum Module {
    WORKSHOP("Workshop", Items.SMITHING_TABLE, List.of(
            new Level("Furnaces in the home base smelt 25% faster.", 20,
                    List.of(Cost.planks(32), Cost.of(Items.COBBLESTONE, 16), Cost.of(Items.FLINT, 8))),
            new Level("Furnaces 50% faster.", 40,
                    List.of(Cost.logs(16), Cost.of(Items.COPPER_INGOT, 12), Cost.of(Items.IRON_NUGGET, 16))),
            new Level("Furnaces 75% faster; Weapon Bench repairs cost half.", 60,
                    List.of(Cost.logs(16), Cost.of(Items.IRON_INGOT, 16), Cost.of(Items.IRON_NUGGET, 32))))),
    MEDSTATION("Medstation", Items.BREWING_STAND, List.of(
            new Level("Unlocks basic meds. Bleeding stops in the home base.", 20,
                    List.of(Cost.planks(16), Cost.of(Items.LEATHER, 8), Cost.of(Items.STRING, 8))),
            new Level("Unlocks painkillers and field kits. Fractures mend after a minute in the home base.", 40,
                    List.of(Cost.logs(8), Cost.of(Items.COPPER_INGOT, 6), Cost.of(Items.HONEY_BOTTLE, 6)),
                    WORKSHOP, 1),
            new Level("Unlocks surgical kits. Faster health regeneration in the home base.", 60,
                    List.of(Cost.of(Items.IRON_INGOT, 10), Cost.of(Items.SLIME_BALL, 8), Cost.of(Items.GLISTERING_MELON_SLICE, 8)),
                    WORKSHOP, 2))),
    PERIMETER("Perimeter", Items.STONE_BRICK_WALL, List.of(
            new Level("Home base radius 48 blocks.", 20,
                    List.of(Cost.planks(32), Cost.of(Items.COBBLESTONE, 32))),
            new Level("Home base radius 72 blocks.", 40,
                    List.of(Cost.of(Items.STONE_BRICKS, 32), Cost.of(Items.COPPER_INGOT, 8)), WORKSHOP, 1),
            new Level("Home base radius 96 blocks.", 60,
                    List.of(Cost.of(Items.STONE_BRICKS, 64), Cost.of(Items.IRON_INGOT, 16)), WORKSHOP, 2))),
    MAP_ROOM("Map room", Items.CARTOGRAPHY_TABLE, List.of(
            new Level("Shared map (needs JourneyMap's waypoint sharing, not built yet).", 20,
                    List.of(Cost.planks(16), Cost.of(Items.MAP, 2), Cost.of(Items.COMPASS, 1))),
            new Level("Season and weather forecast when you come home; warns everyone when a hunting party is tracking a friend.", 40,
                    List.of(Cost.of(Items.COPPER_INGOT, 8), Cost.of(Items.SPYGLASS, 1), Cost.of(Items.REDSTONE, 16)), WORKSHOP, 2))),
    STOREHOUSE("Storehouse", Items.CHEST, List.of(
            new Level("Every chest and barrel in the radius joins one shared stash, listed in INVENTORY.", 20,
                    List.of(Cost.planks(32), Cost.of(Items.CHEST, 4))),
            new Level("Chest filters and ORGANIZE STASH; REFILL STACKS and auto-refill draw from the stash.", 40,
                    List.of(Cost.logs(16), Cost.of(Items.COPPER_INGOT, 8), Cost.of(Items.BARREL, 4)), WORKSHOP, 1),
            new Level("The stash at every station: crafting and furnaces draw straight from it.", 60,
                    List.of(Cost.of(Items.IRON_INGOT, 8), Cost.of(Items.HOPPER, 4), Cost.of(Items.REDSTONE, 16)), WORKSHOP, 2))),
    KITCHEN("Kitchen", Items.SMOKER, List.of(
            new Level("Hearth and cooking pot: pot dishes, any order.", 20,
                    List.of(Cost.planks(16), Cost.of(Items.COBBLESTONE, 16), Cost.of(Items.CAMPFIRE, 1), Cost.of(Items.CAULDRON, 1))),
            new Level("Fermenter: sake, mead, tofu, soy sauce and Koji (about 10 minutes a batch).", 40,
                    List.of(Cost.logs(8), Cost.of(Items.COPPER_INGOT, 6), Cost.of(Items.GLASS_BOTTLE, 4), Cost.of(Items.BARREL, 2)), WORKSHOP, 1),
            new Level("Oven: pies, tarts and roasts.", 60,
                    List.of(Cost.of(Items.BRICKS, 32), Cost.of(Items.IRON_INGOT, 8), Cost.of(Items.FURNACE, 2)), WORKSHOP, 2))),
    LIVING_QUARTERS("Living quarters", Items.RED_BED, List.of(
            new Level("+2 comfort at home, on top of the +1 every module gives.", 20,
                    List.of(Cost.planks(32), Cost.tag(net.minecraft.tags.ItemTags.WOOL, 8, "Any wool"), Cost.tag(net.minecraft.tags.ItemTags.BEDS, 1, "Any bed"))),
            new Level("+4 comfort at home.", 40,
                    List.of(Cost.logs(16), Cost.tag(net.minecraft.tags.ItemTags.WOOL_CARPETS, 8, "Any carpet"), Cost.of(Items.LANTERN, 4)), WORKSHOP, 1),
            new Level("+6 comfort at home, and you wake up Rested after a night's sleep here.", 60,
                    List.of(Cost.of(Items.BOOKSHELF, 4), Cost.of(Items.PAINTING, 2), Cost.of(Items.FLOWER_POT, 4)), WORKSHOP, 2)));

    /** One level of a module: what it does, how long it takes, what it costs, and what it needs first. */
    public record Level(String effect, int buildSeconds, List<Cost> costs, Module needs, int needsLevel) {
        Level(String effect, int buildSeconds, List<Cost> costs) {
            this(effect, buildSeconds, costs, null, 0);
        }
    }

    /** Home base radius by Perimeter level beyond the base Home.Radius (2.4.1). */
    public static final double[] PERIMETER_RADIUS = {48, 72, 96};

    public final String label;
    public final Item icon;
    public final List<Level> levels;

    Module(String label, Item icon, List<Level> levels) {
        this.label = label;
        this.icon = icon;
        this.levels = levels;
    }

    public int max() {
        return levels.size();
    }
}
