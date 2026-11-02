package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.rpg.RpgHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the window in which a drunk potion applies its effects, for the Brewing bonus. */
@Mixin(PotionItem.class)
public abstract class PotionItemMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void seancraft$startDrinking(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        RpgHooks.drinking = entity;
        com.cavmkii.seancraft.combat.Potions.drank(entity, stack);
    }

    /** One of each type at a time: 5 minutes before the same potion again (9.7.2). */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void seancraft$potionCooldown(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            CallbackInfoReturnable<net.minecraft.world.InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        long left = com.cavmkii.seancraft.combat.Potions.cooldown(player, stack);
        if (left > 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format("You can drink that again in %d:%02d",
                        left / 1200, left / 20 % 60)).withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            cir.setReturnValue(net.minecraft.world.InteractionResultHolder.fail(stack));
        }
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void seancraft$stopDrinking(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        RpgHooks.drinking = null;
    }
}
