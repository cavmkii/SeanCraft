package com.cavmkii.seancraft.tarkov;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A hideout module as its own block (design doc 2.5): crafted at the Hideout Bench and placed anywhere inside the
 * hideout, one of each kind. Using it opens its screen, where it's built and upgraded. Broken, it drops with its level.
 */
public class ModuleBlock extends com.cavmkii.seancraft.FacingBlock {
    public final Module module;

    public ModuleBlock(Module module, Properties properties) {
        super(properties);
        this.module = module;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            Hideouts.Hideout h = Hideouts.at(server, Vec3.atCenterOf(pos));
            if (h != null && h.modules[module.ordinal()] == null) {
                h.place(module, pos, stack.getOrDefault(TarkovContent.MODULE_LEVEL, 0));
                Hideouts.get(server).setDirty();
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            Hideouts.Hideout h = Hideouts.at(server, Vec3.atCenterOf(pos));
            int kept = 0;
            if (h != null && pos.equals(h.modules[module.ordinal()])) {
                kept = h.pickUp(module);
                Hideouts.get(server).setDirty();
            }
            ItemStack drop = new ItemStack(asItem());
            if (kept > 0) {
                drop.set(TarkovContent.MODULE_LEVEL, kept);
            }
            popResource(level, pos, drop);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Hideouts.Hideout h = Hideouts.at(serverPlayer.serverLevel(), Vec3.atCenterOf(pos));
            if (h == null || !pos.equals(h.modules[module.ordinal()])) {
                serverPlayer.displayClientMessage(Component.literal("This " + module.label + " isn't part of a hideout.").withStyle(ChatFormatting.GRAY), true);
            } else {
                Tarkov.useModule(serverPlayer, h, module);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Only inside a hideout, and only one of each module per hideout. */
    public static class Item extends BlockItem {
        public Item(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public InteractionResult place(BlockPlaceContext context) {
            if (context.getLevel() instanceof ServerLevel server && getBlock() instanceof ModuleBlock block) {
                Hideouts.Hideout h = Hideouts.at(server, Vec3.atCenterOf(context.getClickedPos()));
                String problem = h == null ? "A " + block.module.label + " has to go inside a hideout."
                        : h.modules[block.module.ordinal()] != null ? "This hideout already has a " + block.module.label + "." : null;
                if (problem != null) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.RED), true);
                        if (context.getPlayer() instanceof ServerPlayer sp) {
                            sp.containerMenu.sendAllDataToRemote();
                        }
                    }
                    return InteractionResult.FAIL;
                }
            }
            return super.place(context);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            int level = stack.getOrDefault(TarkovContent.MODULE_LEVEL, 0);
            lines.add(Component.literal(level > 0 ? "Level " + level + ", kept when you move it." : "Not built yet. Place it in a hideout, then use it to build.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
