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
            case DAGGER, BOW, CROSSBOW -> new Attr[] {Attr.AGI, null};
            case SWORD, SPEAR, PIKE -> new Attr[] {Attr.STR, Attr.AGI};
            default -> new Attr[] {Attr.STR, null};
        };
    }

    /** How much of the weapon's scaling is AGI, for stamina efficiency (6.5.1). */
    public static double agiShare(ItemStack weapon) {
        Attr[] s = of(weapon);
        return (s[0] == Attr.AGI ? 1 : 0) * (s[1] == null ? 1 : 0.5) + (s[1] == Attr.AGI ? 0.5 : 0);
    }

    /** How much of an active skill's region is AGI. */
    public static double agiShare(Archetype archetype) {
        return ((archetype.first == Attr.AGI ? 1 : 0) + (archetype.second == Attr.AGI ? 1 : 0)) / 2.0;
    }
}
