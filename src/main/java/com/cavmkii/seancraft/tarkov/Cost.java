package com.cavmkii.seancraft.tarkov;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** An ingredient: an item or an item tag, and how many. */
public record Cost(Item item, TagKey<Item> tag, int count, String label) {
    public static Cost of(Item item, int count) {
        return new Cost(item, null, count, new ItemStack(item).getHoverName().getString());
    }

    public static Cost tag(TagKey<Item> tag, int count, String label) {
        return new Cost(null, tag, count, label);
    }

    public static Cost planks(int count) {
        return tag(ItemTags.PLANKS, count, "Planks");
    }

    public static Cost logs(int count) {
        return tag(ItemTags.LOGS, count, "Logs");
    }

    /** The same ingredient, that share of the amount, rounded up. */
    public Cost times(float share) {
        return new Cost(item, tag, Math.max(1, (int) Math.ceil(count * share)), label);
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && (item != null ? stack.is(item) : stack.is(tag));
    }

    /** An item to show for this cost. */
    public Item icon() {
        if (item != null) {
            return item;
        }
        return tag == ItemTags.LOGS ? Items.OAK_LOG : tag == ItemTags.PLANKS ? Items.OAK_PLANKS : tag == ItemTags.COALS ? Items.COAL : Items.BARRIER;
    }
}
