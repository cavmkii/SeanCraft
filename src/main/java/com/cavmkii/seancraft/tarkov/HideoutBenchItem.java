package com.cavmkii.seancraft.tarkov;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The Home Bench as an item: refuses to go inside someone else's home base or near a boss dungeon (2.4). */
public class HideoutBenchItem extends BlockItem {
    public HideoutBenchItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (context.getLevel() instanceof ServerLevel level) {
            String problem = Hideouts.placementProblem(level, context.getClickedPos(), context.getPlayer());
            if (problem != null) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.RED), true);
                    // The client already took the item out of the stack when it guessed the placement would work.
                    context.getPlayer().containerMenu.sendAllDataToRemote();
                }
                return false;
            }
        }
        return super.canPlace(context, state);
    }
}
