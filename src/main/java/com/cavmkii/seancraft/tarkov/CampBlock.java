package com.cavmkii.seancraft.tarkov;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A camp (2.5): a fire with an awning and a bedroll. Use it to set your respawn here; sneak and use it to pack it back
 * into its kit. Placing a second camp packs up your first.
 */
public class CampBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public CampBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, 2, 16);
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Camps camps = Camps.get(server);
        if (placer instanceof ServerPlayer player && !player.isCreative()) {
            // One camp per player: the old one packs itself up and comes back.
            for (ServerLevel other : server.getServer().getAllLevels()) {
                BlockPos old = Camps.get(other).of(player.getUUID());
                if (old != null && !(other == server && old.equals(pos))) {
                    Camps.get(other).remove(old);
                    if (other.getBlockState(old).is(this)) {
                        other.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
                    }
                    if (!player.getInventory().add(new ItemStack(TarkovContent.CAMP_KIT))) {
                        player.drop(new ItemStack(TarkovContent.CAMP_KIT), false);
                    }
                    player.displayClientMessage(Component.literal("Your old camp is packed up and back in your inventory.").withStyle(ChatFormatting.GRAY), true);
                }
            }
            camps.put(pos, player.getUUID());
            setRespawn(player, server, pos);
        } else {
            camps.put(pos, null);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            Camps.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (sp.isSecondaryUseActive()) {
                // Pack it up.
                server.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                server.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8f, 1f);
                if (!sp.getInventory().add(new ItemStack(TarkovContent.CAMP_KIT))) {
                    sp.drop(new ItemStack(TarkovContent.CAMP_KIT), false);
                }
            } else {
                setRespawn(sp, server, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void setRespawn(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (Tarkov.nearPedestal(level, pos)) {
            player.displayClientMessage(Component.literal("Too close to a boss dungeon: this camp doesn't set your spawn.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.setRespawnPosition(level.dimension(), pos, player.getYRot(), true, false);
        player.displayClientMessage(Component.literal("Camp set: you'll respawn here.").withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.05, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
        if (random.nextInt(20) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6f, 1f, false);
        }
    }

    /** The camp kit: won't go down near a Summoning Pedestal. */
    public static class Kit extends BlockItem {
        public Kit(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        protected boolean canPlace(BlockPlaceContext context, BlockState state) {
            if (context.getLevel() instanceof ServerLevel level && Tarkov.nearPedestal(level, context.getClickedPos())
                    && context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getPlayer().displayClientMessage(Component.literal("Too close to a boss dungeon for a camp.").withStyle(ChatFormatting.RED), true);
                context.getPlayer().containerMenu.sendAllDataToRemote();
                return false;
            }
            return super.canPlace(context, state);
        }
    }
}
