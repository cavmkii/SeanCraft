package com.cavmkii.seancraft.rpg;

/**
 * Specializations (design doc 6.2.4): three per origin, one in its home region and one in each neighbouring hybrid
 * region. Chosen at a gate on the web once the level-20 milestone and a tier 1 trophy are in hand (6.2.5).
 */
public enum Specialization {
    WARRIOR("Warrior", Origin.VANGUARD, Archetype.WARRIOR, "Damage with heavy weapons",
            "Rage-fuelled heavy attacks; can't be staggered while Rage is above half"),
    KNIGHT("Knight", Origin.VANGUARD, Archetype.DUELIST, "Protector with sword and shield",
            "Blocks shield allies behind you; Challenge pulls nearby enemies onto you"),
    PALADIN("Paladin", Origin.VANGUARD, Archetype.TEMPLAR, "Holy melee",
            "Smites deal spirit damage to what's weak to it; an aura slows allies' bleeding"),
    ARCHER("Archer", Origin.SCOUT, Archetype.RANGER, "Ranged damage and control",
            "Pinning shots that slow or root; faster draw; a stronger Hunter's Mark"),
    BLADEMASTER("Blademaster", Origin.SCOUT, Archetype.DUELIST, "Fast sword damage",
            "Ripostes after a well-timed block; chained quick strikes"),
    ROGUE("Rogue", Origin.SCOUT, Archetype.SHADOW, "Stealth and burglary",
            "Silent, faster looting while sneaking; stronger backstabs; shorter detection range"),
    SORCERER("Sorcerer", Origin.ARCANIST, Archetype.MAGE, "Area damage at range",
            "Larger spell areas; spells apply their element's injury more strongly"),
    WARLOCK("Warlock", Origin.ARCANIST, Archetype.SHADOW, "Curses and damage over time",
            "Hexes that make a target take more from its weaknesses; stronger poison"),
    CLERIC("Cleric", Origin.ARCANIST, Archetype.TEMPLAR, "Medic and support",
            "Treats other players' injuries faster and better; revives an ally's destroyed part in the field");

    /** Character level a specialization needs (6.2.5), with any tier 1 trophy. */
    public static final int LEVEL = 20;

    public final String label;
    public final Origin origin;
    public final Archetype region;
    public final String role;
    public final String signature;

    Specialization(String label, Origin origin, Archetype region, String role, String signature) {
        this.label = label;
        this.origin = origin;
        this.region = region;
        this.role = role;
        this.signature = signature;
    }

    /** The specialization an origin takes in a region, or null if that origin has none there. */
    public static Specialization of(Origin origin, Archetype region) {
        for (Specialization s : values()) {
            if (s.origin == origin && s.region == region) {
                return s;
            }
        }
        return null;
    }
}
