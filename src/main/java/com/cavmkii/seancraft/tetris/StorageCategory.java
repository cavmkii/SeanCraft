package com.cavmkii.seancraft.tetris;

import com.cavmkii.seancraft.parts.PartItem;
import com.cavmkii.seancraft.tarkov.AntidoteItem;
import com.cavmkii.seancraft.tarkov.MedItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** What kind of thing an item is, for storage filters on chests (allow/disallow per kind). */
public enum StorageCategory {
    WEAPONS("Weapons"),
    AMMO("Ammo"),
    ARMOR("Armor & gear"),
    TOOLS("Tools"),
    FOOD("Food"),
    MEDS("Meds"),
    PARTS("Parts"),
    BLOCKS("Blocks"),
    MATERIALS("Materials");

    /** A filter mask that allows everything. */
    public static final int ALL = (1 << values().length) - 1;

    public final String label;

    StorageCategory(String label) {
        this.label = label;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static boolean allows(int mask, ItemStack stack) {
        return (mask & of(stack).bit()) != 0;
    }

    public static StorageCategory of(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof MedItem || item instanceof AntidoteItem || item instanceof PotionItem || stack.is(Items.MILK_BUCKET)
                || stack.is(Items.HONEY_BOTTLE)) {
            return MEDS;
        }
        if (item instanceof PartItem) {
            return PARTS;
        }
        if (item instanceof ArrowItem) {
            return AMMO;
        }
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                || item instanceof MaceItem || item instanceof ShieldItem || item instanceof com.cavmkii.seancraft.item.GlockItem) {
            return WEAPONS;
        }
        if (item instanceof ArmorItem || item instanceof ElytraItem) {
            return ARMOR;
        }
        if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FlintAndSteelItem || item instanceof FishingRodItem) {
            return TOOLS;
        }
        if (stack.has(DataComponents.FOOD)) {
            return FOOD;
        }
        if (item instanceof BlockItem) {
            return BLOCKS;
        }
        return MATERIALS;
    }
}
