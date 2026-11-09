package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.WeaponItems;
import net.minecraft.world.item.ItemStack;

/** Which attribute a weapon scales with (design doc 6.3.1): one, or two split 50/50. */
public final class Scaling {
    private Scaling() {}

    /** {first, second}; second is null for single-attribute weapons. Bare hands and tools count as STR. */
    public static Attr[] of(ItemStack weapon) {
        if (weapon.is(WeaponItems.WOODEN_CLUB)) {
            return new Attr[] {Attr.STR, null};
        }
        Family.Stock stock = Family.stock(weapon);
        if (stock == null) {
            return new Attr[] {Attr.STR, null};
        }
        return switch (stock.family()) {
            case DAGGER, BOW, CROSSBOW, THROWING_KNIFE, JAVELIN, BOOMERANG -> new Attr[] {Attr.AGI, null};
            case WAND, STAFF -> new Attr[] {Attr.INT, null};
            case SWORD, SPEAR, PIKE, LONGBOW -> new Attr[] {Attr.STR, Attr.AGI};
            default -> new Attr[] {Attr.STR, null};
        };
    }

    /** How much of the weapon's scaling is AGI, for stamina efficiency (6.5.1). */
    public static double agiShare(ItemStack weapon) {
        return weights(weapon).getOrDefault(Attr.AGI, 0.0);
    }

    /** Share of each attribute in the weapon's scaling, after its wrap's infusion (9.4.3) shifts 25% toward one. */
    public static java.util.Map<Attr, Double> weights(ItemStack weapon) {
        java.util.Map<Attr, Double> w = new java.util.EnumMap<>(Attr.class);
        Attr[] s = of(weapon);
        if (s[1] == null) {
            w.put(s[0], 1.0);
        } else {
            w.put(s[0], 0.5);
            w.merge(s[1], 0.5, Double::sum);
        }
        Attr toward = infusion(weapon);
        if (toward != null) {
            w.replaceAll((a, v) -> v * 0.75);
            w.merge(toward, 0.25, Double::sum);
        }
        return w;
    }

    /** The attribute a weapon's infused wrap leans toward, or null. */
    public static Attr infusion(ItemStack weapon) {
        com.cavmkii.seancraft.parts.Parts.Installed wrap = com.cavmkii.seancraft.parts.Parts.installed(weapon)
                .get(com.cavmkii.seancraft.parts.PartSlot.WRAP);
        if (wrap == null || wrap.infusion() == null) {
            return null;
        }
        return switch (wrap.infusion()) {
            case "heavy" -> Attr.STR;
            case "keen" -> Attr.AGI;
            case "arcane" -> Attr.INT;
            default -> null;
        };
    }

    /**
     * What a weapon asks for (9.4.2): nothing at rung 1, 15 in its scaling attribute at rung 2, 30 at rung 3; split
     * weapons need half of each. Uses the family's own scaling, not the infused one.
     */
    public static java.util.Map<Attr, Integer> requirement(ItemStack weapon) {
        java.util.Map<Attr, Integer> out = new java.util.EnumMap<>(Attr.class);
        String key = Ladders.key(weapon);
        int rung = key == null ? 1 : Ladders.rungOf(key);
        int need = rung >= 3 ? 30 : rung == 2 ? 15 : 0;
        if (need == 0) {
            return out;
        }
        Attr[] s = of(weapon);
        if (s[1] == null) {
            out.put(s[0], need);
        } else {
            out.put(s[0], need / 2);
            out.put(s[1], need / 2);
        }
        return out;
    }

    public static boolean meets(ItemStack weapon, Stats stats) {
        for (java.util.Map.Entry<Attr, Integer> e : requirement(weapon).entrySet()) {
            if (stats.attr(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Below the requirement: x0.7 damage, a "more" multiplier (9.5). */
    public static final float UNDER_REQUIREMENT_DAMAGE = 0.7f;
    /** Below the requirement: swings cost 30% more stamina. */
    public static final float UNDER_REQUIREMENT_STAMINA = 1.3f;

    /** How much of an active skill's region is AGI. */
    public static double agiShare(Archetype archetype) {
        return ((archetype.first == Attr.AGI ? 1 : 0) + (archetype.second == Attr.AGI ? 1 : 0)) / 2.0;
    }
}
