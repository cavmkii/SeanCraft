package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.ecology.Ecology;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Fishing draws on the patch's fish (17.3): a catch takes one, and in thin water more bites get away. */
@Mixin(FishingHook.class)
abstract class EcoFishingMixin {
    @Shadow
    private int nibble;

    @Shadow
    private Entity hookedIn;

    @Inject(method = "retrieve", at = @At("HEAD"))
    private void seancraft$stock(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook self = (FishingHook) (Object) this;
        if (hookedIn == null && nibble > 0 && self.level() instanceof ServerLevel level
                && !Ecology.fishBite(level, self.blockPosition(), level.getRandom())) {
            nibble = 0;
            Player owner = self.getPlayerOwner();
            if (owner != null) {
                owner.displayClientMessage(Component.literal("It got away."), true);
            }
        }
    }

    /** In the frozen far lands, most fish caught are ice fish (11.2). */
    @ModifyExpressionValue(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> seancraft$iceFish(ObjectArrayList<ItemStack> items) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            com.cavmkii.seancraft.food.Kitchen.iceFish(level, self.blockPosition(), items);
        }
        return items;
    }
}
