package com.cavmkii.seancraft.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Where you modify weapons and armor: swap parts in and out, and level parts up. */
public class WeaponBenchBlock extends com.cavmkii.seancraft.FacingBlock {
    public WeaponBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WeaponBenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.literal("Weapon Bench")));
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.cavmkii.seancraft.tarkov.Stations.send(sp);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
