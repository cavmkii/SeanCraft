package com.cavmkii.seancraft.rpg;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Ultimates (design doc 6.5.4): one per specialization, on V. Unlocked with the specialization and ranked up at mastery
 * and capstone. Costs, cooldowns and what each rank adds follow the 6.5.4 table.
 */
public enum Ultimate {
    RAMPAGE(Specialization.WARRIOR, "Rampage", "For 8 seconds your skills cost no Rage, you can't be staggered, and you hit 20% harder.",
            Resource.RAGE, 50, Items.NETHERITE_AXE, "Lasts 10 s", "Each kill adds 1 s"),
    BULWARK(Specialization.KNIGHT, "Bulwark", "Plant your shield: for 6 seconds players behind you take 50% less damage, and you 30% less.",
            Resource.RAGE, 40, Items.SHIELD, "60% less behind you", "Players behind you can't be staggered"),
    HALLOWED_GROUND(Specialization.PALADIN, "Hallowed Ground",
            "Consecrate a 6-block circle for 8 seconds: spirit damage to the undead in it, and players in it bleed 30% slower.",
            Resource.RAGE, 60, Items.GLOWSTONE, "8-block circle", "Players' bleeds stop inside"),
    DEADEYE(Specialization.ARCHER, "Deadeye", "Your next three arrows always crit and pin what they hit.",
            Resource.STAMINA, 40, Items.SPECTRAL_ARROW, "Four arrows", "The arrows pierce"),
    THOUSAND_CUTS(Specialization.BLADEMASTER, "Thousand Cuts", "Dash through up to five enemies ahead, three quick cuts on each.",
            Resource.STAMINA, 50, Items.NETHERITE_SWORD, "Seven targets", "Every cut bleeds"),
    VANISH(Specialization.ROGUE, "Vanish", "Turn invisible for 5 seconds and lose anything hunting you; your first hit out of it is a backstab.",
            Resource.STAMINA, 40, Items.ENDER_EYE, "Lasts 7 s", "Monsters lose you entirely"),
    CATACLYSM(Specialization.SORCERER, "Cataclysm", "A storm around you: lightning strikes the monsters nearby for 5 seconds.",
            Resource.MANA, 70, Items.LIGHTNING_ROD, "Lasts 7 s", "Lightning and frost at once"),
    DOOM(Specialization.WARLOCK, "Doom", "For 8 seconds your target takes double from its weaknesses; if it dies, it bursts into poison.",
            Resource.MANA, 60, Items.WITHER_SKELETON_SKULL, "Lasts 10 s", "The burst spreads Doom to one nearby enemy"),
    MIRACLE(Specialization.CLERIC, "Miracle", "Revive every destroyed body part on players near you, with lasting damage.",
            Resource.MANA, 80, Items.TOTEM_OF_UNDYING, "Lasting damage halved", "Also clears fractures");

    /** Cooldown in seconds at ranks 1, 2 (mastery) and 3 (capstone). */
    static final int[] COOLDOWN_SECONDS = {90, 75, 60};

    public final Specialization spec;
    public final String label;
    public final String description;
    public final Resource resource;
    public final int cost;
    public final Item icon;
    /** What ranks 2 and 3 add. */
    public final String[] ranks;

    Ultimate(Specialization spec, String label, String description, Resource resource, int cost, Item icon, String rank2, String rank3) {
        this.spec = spec;
        this.label = label;
        this.description = description;
        this.resource = resource;
        this.cost = cost;
        this.icon = icon;
        this.ranks = new String[] {rank2, rank3};
    }

    public static Ultimate of(Specialization spec) {
        for (Ultimate u : values()) {
            if (u.spec == spec) {
                return u;
            }
        }
        return null;
    }

    public int cooldownTicks(int rank) {
        return COOLDOWN_SECONDS[Math.max(0, Math.min(2, rank - 1))] * 20;
    }
}
