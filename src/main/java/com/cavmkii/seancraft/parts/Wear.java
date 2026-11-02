package com.cavmkii.seancraft.parts;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Durability for part-built items (design doc 9.4.8): at zero a weapon doesn't break, it deals half damage (armor and
 * shields hold half as well) until it's repaired at the Weapon Bench, 1 of the striking part's material per 25%.
 */
public final class Wear {
    public static final float WORN = 0.5f;
    public static final float STEP = 0.25f;

    private Wear() {}

    public static boolean isWorn(ItemStack stack) {
        return stack != null && Parts.modular(stack) && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Multiplier for what a worn-out item does. */
    public static float factor(ItemStack stack) {
        return isWorn(stack) ? WORN : 1f;
    }

    /** The striking part's material: the head, the plates, or a shield's rim. */
    public static Item material(ItemStack stack) {
        var parts = Parts.installed(stack);
        Parts.Installed core = parts.get(PartSlot.HEAD);
        if (core == null) {
            core = parts.get(PartSlot.PLATE);
        }
        if (core == null) {
            core = parts.get(PartSlot.GUARD);
        }
        if (core == null) {
            core = parts.get(PartSlot.HANDLE);
        }
        return core == null ? null : core.part().material();
    }

    /** Materials to repair it fully. */
    public static int stepsToFull(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() <= 0) {
            return 0;
        }
        return (int) Math.ceil(stack.getDamageValue() / (stack.getMaxDamage() * STEP));
    }

    /** One step: 25% of max durability back. */
    public static void repairStep(ItemStack stack) {
        int back = Math.max(1, Math.round(stack.getMaxDamage() * STEP));
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - back));
    }
}
