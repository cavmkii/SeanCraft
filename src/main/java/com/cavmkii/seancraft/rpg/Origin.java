package com.cavmkii.seancraft.rpg;

/**
 * Where a character starts (design doc 6.2.1): one per attribute, picked once and never changed. It sets the starting
 * attribute points, the starter web in the home region, and which three specializations the character can take.
 */
public enum Origin {
    VANGUARD("Vanguard", Attr.STR, Archetype.WARRIOR, "Wooden club"),
    SCOUT("Scout", Attr.AGI, Archetype.RANGER, "Bow"),
    ARCANIST("Arcanist", Attr.INT, Archetype.MAGE, "Apprentice wand");

    /** Attribute points an origin starts with in its own attribute (6.4). */
    public static final int START_POINTS = 10;

    public final String label;
    public final Attr attr;
    public final Archetype home;
    public final String weapon;

    Origin(String label, Attr attr, Archetype home, String weapon) {
        this.label = label;
        this.attr = attr;
        this.home = home;
        this.weapon = weapon;
    }

    /** The origin whose home region this is, or null for a hybrid region. */
    public static Origin homeOf(Archetype region) {
        for (Origin o : values()) {
            if (o.home == region) {
                return o;
            }
        }
        return null;
    }
}
