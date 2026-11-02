package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.parts.Parts;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Part-built items wear down to zero and stop there instead of breaking (9.4.8): parts are never destroyed by wear. */
@Mixin(ItemStack.class)
public abstract class ItemStackWearMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void seancraft$wearNotBreak(int amount, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.isDamageableItem() || !Parts.modular(self)) {
            return;
        }
        if (player != null && player.hasInfiniteMaterials()) {
            ci.cancel();
            return;
        }
        int dealt = amount > 0 ? EnchantmentHelper.processDurabilityChange(level, self, amount) : amount;
        if (dealt != 0) {
            self.setDamageValue(Math.max(0, Math.min(self.getMaxDamage() - 1, self.getDamageValue() + dealt)));
        }
        ci.cancel();
    }
}
