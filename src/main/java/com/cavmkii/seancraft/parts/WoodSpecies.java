package com.cavmkii.seancraft.parts;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * The tree a plank or log part was made from. Each species pulls a wood part a different way: damage, draw (or swing)
 * speed and durability. Bow and crossbow limbs feel the full effect; handles, hafts, stocks and other wood parts a third
 * of it. Bone, blaze and breeze parts have no species.
 */
public enum WoodSpecies {
    OAK("Oak", 0.00f, 0.00f, 0.10f, "all-round, sturdy"),
    SPRUCE("Spruce", 0.10f, -0.05f, 0.05f, "springy and strong"),
    BIRCH("Birch", -0.05f, 0.10f, 0.00f, "light and quick"),
    JUNGLE("Jungle", 0.00f, 0.05f, 0.15f, "dense, lasts"),
    ACACIA("Acacia", 0.15f, -0.10f, -0.05f, "hard and stiff"),
    DARK_OAK("Dark oak", 0.12f, -0.12f, 0.10f, "heavy and strong"),
    MANGROVE("Mangrove", 0.05f, 0.00f, 0.20f, "won't rot"),
    CHERRY("Cherry", -0.05f, 0.15f, -0.10f, "whippy"),
    BAMBOO("Bamboo", -0.12f, 0.20f, -0.15f, "very light, fragile"),
    CRIMSON("Crimson", 0.08f, 0.05f, -0.05f, "nether-grown, snappy"),
    WARPED("Warped", 0.03f, 0.08f, 0.05f, "nether-grown, even");

    /** Share of the full effect a non-limb wood part gets. */
    public static final float GRIP_SHARE = 0.33f;

    public final String label;
    public final float damage;
    public final float draw;
    public final float durability;
    public final String feel;

    WoodSpecies(String label, float damage, float draw, float durability, String feel) {
        this.label = label;
        this.damage = damage;
        this.draw = draw;
        this.durability = durability;
        this.feel = feel;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WoodSpecies byId(String id) {
        if (id == null) {
            return null;
        }
        for (WoodSpecies s : values()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The species of a plank, log, wood, stem or bamboo block stack, or null (modded or not wood). */
    public static WoodSpecies of(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : new String[] {"_planks", "_log", "_wood", "_stem", "_hyphae", "_block"}) {
            if (path.endsWith(suffix)) {
                return byId(path.substring(0, path.length() - suffix.length()));
            }
        }
        return null;
    }

    /** "+10% damage, -5% draw, +5% durability", scaled by {@code share}. */
    public String effects(float share) {
        StringBuilder out = new StringBuilder();
        add(out, damage * share, "damage");
        add(out, draw * share, "draw/swing speed");
        add(out, durability * share, "durability");
        return out.length() == 0 ? "no change" : out.toString();
    }

    private static void add(StringBuilder out, float v, String what) {
        int pct = Math.round(v * 100);
        if (pct != 0) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(pct > 0 ? "+" : "").append(pct).append("% ").append(what);
        }
    }
}
