package com.cavmkii.seancraft.tarkov;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Home Bench (2.4): founds the home base. Hostile mobs don't spawn within its radius. Use it for modules and home crafting. */
public class HideoutBlock extends com.cavmkii.seancraft.FacingBlock {
    public HideoutBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            Hideouts.get(server).add(pos);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof ServerPlayer player && !player.isCreative()) {
            // One Home Bench per player (Home.MaxBenches): the oldest past the limit is picked back up (2.4).
            java.util.List<Object[]> mine = Hideouts.benchesOf(server.getServer(), player.getUUID());
            for (int i = 0; i <= mine.size() - com.cavmkii.seancraft.Cfg.maxBenches; i++) {
                ServerLevel where = (ServerLevel) mine.get(i)[0];
                BlockPos old = (BlockPos) mine.get(i)[1];
                where.setBlock(old, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                net.minecraft.world.item.ItemStack bench = new net.minecraft.world.item.ItemStack(TarkovContent.HIDEOUT_BENCH_ITEM);
                if (!player.getInventory().add(bench)) {
                    player.drop(bench, false);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Your old Home Bench is picked up: this is your home base now.").withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
            Hideouts.get(server).setOwner(pos, player.getUUID());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            Hideouts.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Tarkov.openBench(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
