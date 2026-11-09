package com.cavmkii.seancraft.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an item entity's age, so a sign item (a dead fish) lasts a minute and then goes. */
@Mixin(ItemEntity.class)
public interface ItemEntityAgeAccessor {
    @Accessor("age")
    void seancraft$setAge(int age);
}
