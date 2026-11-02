package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.rpg.Ladders;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recipe ladders (4.7): a weapon the player hasn't unlocked doesn't come out of a crafting grid. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void seancraft$ladderLock(AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots,
            ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && !Ladders.canCraft(sp, resultSlots.getItem(0))) {
            resultSlots.setItem(0, ItemStack.EMPTY);
            sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
        }
    }
}
