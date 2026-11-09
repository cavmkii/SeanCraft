package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.SeanCraft;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Loot for the raid loop. Vanilla chests often hold meds (village chests nearly always, plus some supplies),
 * and small supply caches (a barrel) are scattered over the overworld surface.
 */
public final class TarkovLoot {
    public static final ResourceKey<LootTable> SUPPLY_CACHE = ResourceKey.create(Registries.LOOT_TABLE, SeanCraft.id("chests/supply_cache"));
    public static final ResourceKey<LootTable> SPAWN_CACHE = ResourceKey.create(Registries.LOOT_TABLE, SeanCraft.id("chests/spawn_cache"));

    public static final Feature<NoneFeatureConfiguration> SUPPLY_CACHE_FEATURE = Registry.register(BuiltInRegistries.FEATURE,
            SeanCraft.id("supply_cache"), new SupplyCache());
    private static final ResourceKey<PlacedFeature> SUPPLY_CACHE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, SeanCraft.id("supply_cache"));

    private TarkovLoot() {}

    static void init() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, SUPPLY_CACHE_PLACED);
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            String path = key.location().getPath();
            if (!source.isBuiltin() || !key.location().getNamespace().equals("minecraft") || !path.startsWith("chests/")) {
                return;
            }
            boolean village = path.startsWith("chests/village/");
            builder.withPool(meds(village ? 0.9f : 0.5f));
            if (village) {
                builder.withPool(supplies());
            }
        });
    }

    private static LootPool.Builder meds(float chance) {
        return LootPool.lootPool()
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(entry(TarkovContent.MEDS.get(Med.BANDAGE), 10, 1, 3))
                .add(entry(TarkovContent.MEDS.get(Med.TOURNIQUET), 4, 1, 1))
                .add(entry(TarkovContent.MEDS.get(Med.SPLINT), 4, 1, 1))
                .add(entry(TarkovContent.MEDS.get(Med.PAINKILLERS), 4, 1, 2))
                .add(entry(TarkovContent.ANTIDOTE, 3, 1, 1))
                .add(entry(TarkovContent.MEDS.get(Med.FIELD_KIT), 2, 1, 1))
                .add(entry(TarkovContent.MEDS.get(Med.SURGERY_CASE), 1, 1, 1));
    }

    private static LootPool.Builder supplies() {
        return LootPool.lootPool()
                .setRolls(UniformGenerator.between(1, 3))
                .add(entry(Items.BREAD, 8, 1, 4))
                .add(entry(Items.LEATHER, 5, 1, 3))
                .add(entry(Items.STRING, 5, 1, 4))
                .add(entry(Items.ARROW, 4, 4, 10))
                .add(entry(Items.IRON_INGOT, 3, 1, 3))
                .add(entry(Items.HONEY_BOTTLE, 2, 1, 1));
    }

    private static LootItem.Builder<?> entry(Item item, int weight, int min, int max) {
        var e = LootItem.lootTableItem(item).setWeight(weight);
        if (max > 1) {
            e.apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
        }
        return e;
    }

    /** A barrel of supplies on open ground. */
    static final class SupplyCache extends Feature<NoneFeatureConfiguration> {
        SupplyCache() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            BlockPos pos = ctx.origin();
            if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                return false;
            }
            level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 2);
            RandomizableContainer.setBlockEntityLootTable(level, ctx.random(), pos, SUPPLY_CACHE);
            BlockPos side = pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(ctx.random()));
            if (level.getBlockState(side).canBeReplaced() && level.getFluidState(side).isEmpty()
                    && level.getBlockState(side.below()).isFaceSturdy(level, side.below(), Direction.UP)) {
                level.setBlock(side, Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), 2);
            }
            return true;
        }
    }
}
