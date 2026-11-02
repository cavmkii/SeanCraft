package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.parts.Parts;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla swords, axes, bows, crossbows and shields become part-built items with default parts on pickup (9.4.10). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityConvertMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void seancraft$convert(Player player, CallbackInfo ci) {
        ItemStack stack = ((ItemEntity) (Object) this).getItem();
        if (!player.level().isClientSide && Parts.modular(stack) && stack.get(Parts.INSTALLED) == null) {
            Parts.setInstalled(stack, Parts.installed(stack));
        }
    }
}
