package com.cavmkii.seancraft.ecology;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A prairie plant (17.16). Placed by hand or at world generation in remnant prairie. It spreads into nearby grass
 * while its patch is disturbed often enough but not too often, and dies back when shrubs would move in (no fire or
 * grazing for too long) or when nitrogen runs too high (17.16.1). The most conservative ones also die out slowly in a
 * small patch cut off from other good grassland (the extinction debt, 17.5).
 */
public class PrairiePlantBlock extends BushBlock {
    public static final MapCodec<PrairiePlantBlock> CODEC = simpleCodec(PrairiePlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public PrairiePlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.move(state.getOffset(level, pos).x, 0, state.getOffset(level, pos).z);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Ecology.enabled()) {
            return;
        }
        Patch p = Ecology.patchAt(level, pos);
        if (p == null || Double.isNaN(p.day)) {
            return;
        }
        boolean protectedGround = Ecology.protectedGround(level, pos);
        double undisturbed = p.daysUndisturbed();
        if (!protectedGround && (undisturbed > Params.disturbanceMax || p.nitrogen > Params.excessNitrogen)) {
            // Shrubs and weeds crowd it out.
            if (random.nextInt(8) == 0) {
                level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 3);
            }
            return;
        }
        double c = Params.PLANTS.getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).toString(), 5.0);
        // Extinction debt (17.5, 17.16.1): a conservative plant in a small, cut-off patch slowly dies out. Corridors
        // and good grassland next door raise connectivity and stop it.
        if (!protectedGround && c >= 7) {
            double risk = (1 - p.connectivity) * (1 - Math.min(1, p.grass / 0.5));
            if (random.nextDouble() < Params.isolationLoss * risk) {
                level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), 3);
                return;
            }
        }
        // Slow, and slower for the most conservative plants: a restored prairie takes in-game weeks (17.16.1).
        if (undisturbed < Params.disturbanceMin || !Seasons.of(level).spreads() || random.nextInt(20 + (int) (4 * c)) != 0) {
            return;
        }
        BlockPos to = pos.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
        BlockState there = level.getBlockState(to);
        if ((there.isAir() || there.is(Blocks.SHORT_GRASS)) && level.getBlockState(to.below()).is(Blocks.GRASS_BLOCK)) {
            level.setBlock(to, state, 3);
        }
    }
}
