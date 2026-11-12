package com.cavmkii.seancraft.world;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.compat.Dungeons;
import com.cavmkii.seancraft.tarkov.Distance;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Readable danger in the world itself (design doc 18.2): cairns and skull totems along each distance band's edge,
 * about every 200 blocks and wherever the edge crosses a river, and abandoned camps that get more common further
 * out. Both are world generation, so only new chunks get them.
 */
public final class Landmarks {
    public static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE, SeanCraft.id("chests/abandoned_camp"));

    static final Feature<NoneFeatureConfiguration> BAND_MARKER = Registry.register(BuiltInRegistries.FEATURE, SeanCraft.id("band_marker"),
            new BandMarker());
    static final Feature<NoneFeatureConfiguration> ABANDONED_CAMP = Registry.register(BuiltInRegistries.FEATURE, SeanCraft.id("abandoned_camp"),
            new AbandonedCamp());
    private static final ResourceKey<PlacedFeature> BAND_MARKER_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, SeanCraft.id("band_marker"));
    private static final ResourceKey<PlacedFeature> ABANDONED_CAMP_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, SeanCraft.id("abandoned_camp"));

    /** Spacing of markers along a band edge, in blocks. */
    static final double SPACING = 200;

    private Landmarks() {}

    public static void init() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, BAND_MARKER_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, ABANDONED_CAMP_PLACED);
    }

    /** Solid, dry ground at a column, or null (water, leaves on top). */
    static BlockPos ground(WorldGenLevel level, int x, int z) {
        // Under the canopy too: the highest block that isn't leaves (these heightmaps exist by the features step).
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        BlockPos below = top.below();
        BlockState s = level.getBlockState(below);
        if (!level.getFluidState(below).isEmpty() || !level.getFluidState(top).isEmpty() || !s.isFaceSturdy(level, below, Direction.UP)
                || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)) {
            return null;
        }
        return top;
    }

    // ================================================================== band edges

    /** Called for every chunk: places this chunk's share of the markers along each band edge. */
    static final class BandMarker extends Feature<NoneFeatureConfiguration> {
        BandMarker() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!Cfg.bandMarkers || !Cfg.distanceScaling) {
                return false;
            }
            WorldGenLevel level = ctx.level();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            BlockPos spawn = Dungeons.spawn();
            double cx = x0 + 8 - spawn.getX(), cz = z0 + 8 - spawn.getZ();
            double centre = Math.sqrt(cx * cx + cz * cz);
            boolean placed = false;
            for (int edge = 0; edge < Cfg.distanceBands.length; edge++) {
                double r = Cfg.distanceBands[edge];
                if (Math.abs(centre - r) > 12) {
                    continue;
                }
                int n = Math.max(8, (int) Math.round(2 * Math.PI * r / SPACING));
                double step = 2 * Math.PI / n;
                double theta = Math.atan2(cz, cx);
                long k = Math.round(theta / step);
                for (long kk = k - 1; kk <= k + 1; kk++) {
                    int px = (int) Math.floor(spawn.getX() + r * Math.cos(kk * step));
                    int pz = (int) Math.floor(spawn.getZ() + r * Math.sin(kk * step));
                    if (px >> 4 == x0 >> 4 && pz >> 4 == z0 >> 4) {
                        placed |= marker(level, px, pz, edge, ctx.random());
                    }
                }
                // Where the edge crosses a river, always: on the first dry bank along the edge in this chunk.
                if (level.getBiome(new BlockPos(x0 + 8, 64, z0 + 8)).is(ConventionalBiomeTags.IS_RIVER)) {
                    for (int i = -16; i <= 16 && !placed; i += 2) {
                        double a = theta + i / r;
                        int px = (int) Math.floor(spawn.getX() + r * Math.cos(a));
                        int pz = (int) Math.floor(spawn.getZ() + r * Math.sin(a));
                        if (px >> 4 == x0 >> 4 && pz >> 4 == z0 >> 4) {
                            placed = marker(level, px, pz, edge, ctx.random());
                        }
                    }
                }
            }
            return placed;
        }

        /** A cairn at the Wilds' edge, a skull totem at the Frontier's, a wither-skull totem at the Far lands'. */
        private static boolean marker(WorldGenLevel level, int x, int z, int edge, RandomSource random) {
            BlockPos at = ground(level, x, z);
            if (at == null) {
                return false;
            }
            BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
            BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            // A low cairn under every marker.
            level.setBlock(at, random.nextBoolean() ? stone : mossy, 2);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = at.relative(d);
                if (random.nextFloat() < 0.7f && level.getBlockState(side).canBeReplaced() && level.getFluidState(side).isEmpty()) {
                    level.setBlock(side, (random.nextBoolean() ? Blocks.COBBLESTONE_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB).defaultBlockState(), 2);
                }
            }
            switch (edge) {
                case 0 -> {
                    level.setBlock(at.above(), random.nextBoolean() ? stone : mossy, 2);
                    level.setBlock(at.above(2), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
                }
                case 1 -> {
                    level.setBlock(at.above(), Blocks.OAK_FENCE.defaultBlockState(), 2);
                    level.setBlock(at.above(2), Blocks.OAK_FENCE.defaultBlockState(), 2);
                    level.setBlock(at.above(3), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), 2);
                }
                default -> {
                    level.setBlock(at.above(), Blocks.BLACKSTONE_WALL.defaultBlockState(), 2);
                    level.setBlock(at.above(2), Blocks.BLACKSTONE_WALL.defaultBlockState(), 2);
                    level.setBlock(at.above(3), Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), 2);
                    BlockPos web = at.above().relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                    if (level.getBlockState(web).isAir()) {
                        level.setBlock(web, Blocks.COBWEB.defaultBlockState(), 2);
                    }
                }
            }
            return true;
        }
    }

    // ================================================================== abandoned camps

    /** Notes left behind: each hints at what got its owner. */
    private static final List<String[]> NOTES = List.of(
            new String[] {"Something with a red name came out of the dark. Bigger than the rest. I ran and dropped half my kit.",
                    "If you find this, don't sleep in the open."},
            new String[] {"Heard a horn about midnight. Then the howling, all around, getting closer.",
                    "Pick your ground before they come. I didn't."},
            new String[] {"Fell off the ridge chasing a goat. Leg's broken. Splint didn't hold.",
                    "Waiting for morning. Fire's out."},
            new String[] {"Third night of no sleep. They come every night now, more each time.",
                    "Going back to the Heartland at first light. Leaving what I can't carry."},
            new String[] {"Ate the last of the bread two days ago. Nothing grows here and the game's gone.",
                    "Should have brought a proper meal."});

    /** Testing: an abandoned camp right here, at this band's loot. */
    public static boolean camp(net.minecraft.server.level.ServerLevel level, BlockPos at) {
        int band = Distance.at(level, net.minecraft.world.phys.Vec3.atCenterOf(at)).ordinal();
        return AbandonedCamp.build(level, at.getX(), at.getZ(), Math.max(1, band), level.random);
    }

    static final class AbandonedCamp extends Feature<NoneFeatureConfiguration> {
        AbandonedCamp() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!Cfg.abandonedCamps) {
                return false;
            }
            WorldGenLevel level = ctx.level();
            RandomSource random = ctx.random();
            int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
            BlockPos spawn = Dungeons.spawn();
            double dx = x0 + 8 - spawn.getX(), dz = z0 + 8 - spawn.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            int band = 0;
            for (int i = 0; i < Cfg.distanceBands.length; i++) {
                if (d >= Cfg.distanceBands[i]) {
                    band = i + 1;
                }
            }
            // None in the Heartland; then about one per 300, 150 and 80 chunks.
            int[] oneIn = {0, 300, 150, 80};
            if (band == 0 || random.nextInt(oneIn[Math.min(band, 3)]) != 0) {
                return false;
            }
            int x = x0 + 4 + random.nextInt(8), z = z0 + 4 + random.nextInt(8);
            return build(level, x, z, band, random);
        }

        /** Builds a camp at a column if the ground there is dry and flat enough. */
        static boolean build(WorldGenLevel level, int x, int z, int band, RandomSource random) {
            BlockPos at = ground(level, x, z);
            if (at == null || !flat(level, at)) {
                return false;
            }
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            Direction side = facing.getClockWise();
            // A torn tent: a wool ridge over two rows, with holes; fence poles at the ends.
            BlockState wool = (random.nextBoolean() ? Blocks.WHITE_WOOL : Blocks.LIGHT_GRAY_WOOL).defaultBlockState();
            for (int i = 0; i < 3; i++) {
                BlockPos row = at.relative(facing.getOpposite(), i);
                setIfFree(level, row.relative(side).above(0), random.nextFloat() < 0.7f ? wool : null);
                setIfFree(level, row.relative(side.getOpposite()), random.nextFloat() < 0.7f ? wool : null);
                setIfFree(level, row.above(), random.nextFloat() < 0.6f ? wool : null);
            }
            setIfFree(level, at.relative(facing.getOpposite(), 3), Blocks.OAK_FENCE.defaultBlockState());
            // The chest at the back, a cold fire out front, and the broken weapon and note in the chest.
            BlockPos chestPos = at.relative(facing.getOpposite(), 2);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setItem(4, note(random, band));
                chest.setItem(13, brokenWeapon(random));
            }
            RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, CAMP_LOOT);
            BlockPos fire = at.relative(facing, 2);
            if (ground(level, fire.getX(), fire.getZ()) != null) {
                BlockPos f = ground(level, fire.getX(), fire.getZ());
                level.setBlock(f, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
            }
            return true;
        }

        private static boolean flat(WorldGenLevel level, BlockPos at) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos g = ground(level, at.getX() + dx, at.getZ() + dz);
                    if (g == null || Math.abs(g.getY() - at.getY()) > 1) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static void setIfFree(WorldGenLevel level, BlockPos pos, BlockState state) {
            if (state != null && level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()) {
                level.setBlock(pos, state, 2);
            }
        }

        private static ItemStack note(RandomSource random, int band) {
            String[] lines = NOTES.get(random.nextInt(NOTES.size()));
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            String where = Distance.values()[Math.min(band, Distance.values().length - 1)].label;
            book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Last entry"), "a traveller", 0,
                    List.of(Filterable.passThrough(Component.literal(where + ".\n\n" + lines[0])), Filterable.passThrough(Component.literal(lines[1]))),
                    true));
            return book;
        }

        private static ItemStack brokenWeapon(RandomSource random) {
            ItemStack weapon = new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.STONE_AXE);
            weapon.setDamageValue(weapon.getMaxDamage() - 1 - random.nextInt(4));
            return weapon;
        }
    }
}
