package com.cavmkii.seancraft.tarkov;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Where recovered striking parts wait (9.4.9). Use it to take yours. */
public class RecoveryCrateBlock extends com.cavmkii.seancraft.FacingBlock {
    public RecoveryCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            Recovery.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
