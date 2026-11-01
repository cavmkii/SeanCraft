package com.cavmkii.seancraft.tarkov;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A med. Hold use to apply it to the part that needs it most, or pick the part on the STATUS screen (H).
 * It isn't used up if there's nothing for it to do.
 */
public class MedItem extends Item {
    public final Med med;

    public MedItem(Med med, Properties properties) {
        super(properties);
        this.med = med;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && Health.target(serverPlayer, med) == null) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(med.label + ": nothing to treat."), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (user instanceof ServerPlayer player && Health.treat(player, med, Health.target(player, med))) {
            stack.consume(1, player);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        // AGI applies meds faster (6.3). The server's count decides when it's done.
        if (user instanceof net.minecraft.server.level.ServerPlayer player) {
            return Math.max(1, (int) Math.round(med.useTicks / com.cavmkii.seancraft.rpg.Rpg.stats(player).medSpeed()));
        }
        return med.useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return med == Med.PAINKILLERS ? UseAnim.EAT : UseAnim.BOW;
    }
}
