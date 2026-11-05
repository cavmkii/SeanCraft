package com.cavmkii.seancraft.ecology;

import com.cavmkii.seancraft.SeanCraft;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Harvest yield from the patch (11.3, 17.6): a ripe crop's produce is scaled by soil, pests and pollinators. Added
 * to the vanilla crop loot tables; seeds are left alone.
 */
public final class YieldFunction implements LootItemFunction {
    public static final YieldFunction INSTANCE = new YieldFunction();
    public static final MapCodec<YieldFunction> CODEC = MapCodec.unit(INSTANCE);
    public static final LootItemFunctionType<YieldFunction> TYPE = Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE,
            SeanCraft.id("eco_yield"), new LootItemFunctionType<>(CODEC));
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};

    private YieldFunction() {}

    static void init() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            for (Block crop : CROPS) {
                if (key.equals(crop.getLootTable())) {
                    builder.apply(() -> INSTANCE);
                }
            }
        });
    }

    @Override
    public LootItemFunctionType<YieldFunction> getType() {
        return TYPE;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext ctx) {
        BlockState state = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (state == null || origin == null || !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                || !(stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT))) {
            return stack;
        }
        double m = Ecology.yieldMultiplier(ctx.getLevel(), BlockPos.containing(origin), state.getBlock());
        double want = stack.getCount() * m;
        int count = (int) Math.floor(want);
        if (ctx.getRandom().nextDouble() < want - count) {
            count++;
        }
        stack.setCount(count);
        return stack;
    }
}
