package com.cavmkii.seancraft.tetris;

import com.cavmkii.seancraft.parts.PartItem;
import com.cavmkii.seancraft.parts.PartKind;
import com.cavmkii.seancraft.tarkov.Med;
import com.cavmkii.seancraft.tarkov.MedItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** How many grid cells an item takes, as width x height before rotation. Most things are 1x1. */
public final class ItemSize {
    private ItemSize() {}

    public static int[] of(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof GearItem gear) {
            return new int[] {gear.spec.itemWidth, gear.spec.itemHeight};
        }
        if (item instanceof TridentItem) {
            return new int[] {1, 4};
        }
        if (item instanceof com.cavmkii.seancraft.parts.WeaponItems.PartWeapon weapon) {
            // Daggers are short, the polearms and greatswords long.
            return switch (weapon.family) {
                case DAGGER -> new int[] {1, 2};
                case GREATSWORD, HALBERD, SPEAR, PIKE, WARHAMMER -> new int[] {1, 4};
                default -> new int[] {1, 3};
            };
        }
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem) {
            return new int[] {1, 3};
        }
        if (item instanceof CrossbowItem) {
            return new int[] {2, 3};
        }
        if (item instanceof BowItem || item instanceof FishingRodItem) {
            return new int[] {1, 3};
        }
        if (item instanceof DiggerItem) {
            return new int[] {1, 2};
        }
        if (item instanceof ShieldItem) {
            return new int[] {2, 2};
        }
        if (item instanceof ElytraItem) {
            return new int[] {2, 3};
        }
        if (item instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case CHESTPLATE, BODY -> new int[] {3, 3};
                case LEGGINGS -> new int[] {2, 3};
                default -> new int[] {2, 2};
            };
        }
        if (item instanceof MedItem med) {
            return switch (med.med) {
                case SPLINT -> new int[] {1, 2};
                case SURGICAL_KIT -> new int[] {2, 1};
                case SURGERY_CASE -> new int[] {2, 2};
                default -> new int[] {1, 1};
            };
        }
        if (item instanceof PartItem part) {
            return part.def.kind() == PartKind.PLATE ? new int[] {2, 2}
                    : part.def.kind() == PartKind.BLADE || part.def.kind() == PartKind.HANDLE || part.def.kind() == PartKind.LIMBS
                    ? new int[] {1, 2} : new int[] {1, 1};
        }
        return new int[] {1, 1};
    }
}
