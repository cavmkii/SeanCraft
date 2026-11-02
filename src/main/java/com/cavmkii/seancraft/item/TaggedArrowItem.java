package com.cavmkii.seancraft.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Broadhead, bodkin and blunt arrows (design doc 9.4.4): a vanilla arrow with a tag the damage pipeline reads. */
public class TaggedArrowItem extends ArrowItem {
    private final String tag;

    public TaggedArrowItem(String tag, Properties properties) {
        super(properties);
        this.tag = tag;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
        arrow.addTag(tag);
        return arrow;
    }
}
