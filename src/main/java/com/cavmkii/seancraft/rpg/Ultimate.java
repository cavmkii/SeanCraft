package com.cavmkii.seancraft.rpg;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Ultimates (design doc 6.5.4): one per specialization, on V. Unlocked with the specialization and ranked up at mastery
 * and capstone; a hybrid specialization spends the resource of the side it leans toward (6.5).
 */
public enum Ultimate {
    RAMPAGE(Specialization.WARRIOR, "Rampage", "For 8 seconds your skills cost no Rage, you can't be staggered, and you hit 20% harder.",
            Resource.RAGE, Items.NETHERITE_AXE),
    BULWARK(Specialization.KNIGHT, "Bulwark", "Plant your shield: for 6 seconds players behind you take 50% less damage, and you 30% less.",
            Resource.RAGE, Items.SHIELD),
    HALLOWED_GROUND(Specialization.PALADIN, "Hallowed Ground",
            "Consecrate the ground around you for 8 seconds: spirit damage to the undead in it, and allies in it bleed slower.",
            Resource.RAGE, Items.GLOWSTONE),
    DEADEYE(Specialization.ARCHER, "Deadeye", "Your next three arrows always crit and pin what they hit.", Resource.STAMINA, Items.SPECTRAL_ARROW),
    THOUSAND_CUTS(Specialization.BLADEMASTER, "Thousand Cuts", "Dash through up to five enemies ahead, hitting each with a flurry.",
            Resource.STAMINA, Items.NETHERITE_SWORD),
    VANISH(Specialization.ROGUE, "Vanish", "Turn invisible for 5 seconds and lose anything hunting you; your first hit out of it is a backstab.",
            Resource.STAMINA, Items.ENDER_EYE),
    CATACLYSM(Specialization.SORCERER, "Cataclysm", "A storm around you: lightning strikes the monsters nearby for 5 seconds.",
            Resource.MANA, Items.LIGHTNING_ROD),
    DOOM(Specialization.WARLOCK, "Doom", "For 8 seconds your target takes double from its weaknesses; if it dies, it bursts into poison.",
            Resource.MANA, Items.WITHER_SKELETON_SKULL),
    MIRACLE(Specialization.CLERIC, "Miracle", "Revive every destroyed body part on players near you, with lasting damage.", Resource.MANA, Items.TOTEM_OF_UNDYING);

    public static final int COST = 40;
    /** Cooldown in seconds at ranks 1, 2 and 3. */
    static final int[] COOLDOWN_SECONDS = {120, 100, 80};

    public final Specialization spec;
    public final String label;
    public final String description;
    public final Resource resource;
    public final Item icon;

    Ultimate(Specialization spec, String label, String description, Resource resource, Item icon) {
        this.spec = spec;
        this.label = label;
        this.description = description;
        this.resource = resource;
        this.icon = icon;
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
