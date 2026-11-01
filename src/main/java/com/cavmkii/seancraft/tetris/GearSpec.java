package com.cavmkii.seancraft.tetris;

/**
 * Rigs and backpacks: the grid they give you, and their own size in a grid. Each kind is a line of four, from the
 * starting kit up (design doc 3.1); an empty one upgrades to the next at the Hideout Bench.
 */
public enum GearSpec {
    RIG_SCAV("rig_scav", "Scav vest", Kind.RIG, 4, 2, 2, 2),
    RIG_CHEST("rig_chest", "Chest rig", Kind.RIG, 4, 3, 2, 3),
    RIG_CARRIER("rig_carrier", "Carrier rig", Kind.RIG, 5, 3, 3, 3),
    RIG_ASSAULT("rig_assault", "Assault rig", Kind.RIG, 6, 3, 3, 3),
    PACK_SLING("pack_sling", "Sling bag", Kind.BACKPACK, 3, 3, 2, 3),
    PACK_SCAV("pack_scav", "Scav backpack", Kind.BACKPACK, 4, 5, 4, 4),
    PACK_PILGRIM("pack_pilgrim", "Pilgrim backpack", Kind.BACKPACK, 5, 7, 5, 5),
    PACK_EXPEDITION("pack_expedition", "Expedition pack", Kind.BACKPACK, 6, 8, 5, 6);

    public enum Kind { RIG, BACKPACK }

    public final String id;
    public final String label;
    public final Kind kind;
    public final int gridWidth;
    public final int gridHeight;
    public final int itemWidth;
    public final int itemHeight;

    /** The one before this in its line, or null for the starting kit. */
    public GearSpec previous() {
        int i = ordinal() - 1;
        return i >= 0 && values()[i].kind == kind ? values()[i] : null;
    }

    GearSpec(String id, String label, Kind kind, int gridWidth, int gridHeight, int itemWidth, int itemHeight) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.itemWidth = itemWidth;
        this.itemHeight = itemHeight;
    }
}
