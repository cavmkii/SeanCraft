package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.Parts;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Bows draw at their parts' pace: the string and the limbs' wood set how fast a full draw comes, and the longbow is
 * slower to draw but throws its arrows harder.
 */
@Mixin(BowItem.class)
abstract class BowDrawMixin {
    @ModifyArg(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private int seancraft$draw(int ticks, @Local(argsOnly = true) ItemStack stack) {
        return Math.round(ticks * Parts.drawFactor(stack));
    }

    @ModifyArg(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;shoot("
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V"), index = 5)
    private float seancraft$velocity(float velocity, @Local(argsOnly = true) ItemStack stack) {
        Family.Stock stock = Family.stock(stack);
        return stock != null && stock.family() == Family.LONGBOW ? velocity * 1.3f : velocity;
    }
}
