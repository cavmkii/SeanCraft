package com.cavmkii.seancraft.world;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.ecology.Ecology;
import com.cavmkii.seancraft.ecology.Patch;
import com.cavmkii.seancraft.ecology.Params;
import com.cavmkii.seancraft.ecology.Seasons;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wildfire (design doc 18.4): in a dry summer (no rain for Events.WildfireDryDays), grassland, savanna and prairie
 * whose fire is overdue (17.16.1) can catch. A front then crosses grass at about walking pace with a smoke column,
 * animals running ahead of it. It burns the plants and leaves dirt that grass takes back within days. It never
 * enters a home base, burns at most Events.WildfireBlocksPerTick blocks a tick, stops at anything that isn't grass,
 * and goes out in rain. It counts as fire for the ecology, which is the disturbance prairie needs.
 *
 * <p>The flames are their own block: vanilla fire spreads by itself (to houses), this doesn't.
 */
public final class Wildfire {
    /** Burning grass: looks like fire, hurts like fire, burns out in a few seconds, never spreads by itself. */
    public static final class FlameBlock extends BaseFireBlock {
        static final MapCodec<FlameBlock> CODEC = simpleCodec(FlameBlock::new);

        FlameBlock(Properties properties) {
            super(properties, 1.0f);
        }

        @Override
        protected MapCodec<FlameBlock> codec() {
            return CODEC;
        }

        @Override
        protected boolean canBurn(BlockState state) {
            return true;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state, level, pos, old, moved);
            level.scheduleTick(pos, this, 40 + level.random.nextInt(60));
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.removeBlock(pos, false);
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
        }
    }

    public static final Block FLAME = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("wildfire"), new FlameBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).replaceable().noCollission().instabreak().lightLevel(s -> 15)
                    .sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY).noLootTable()));

    /** One fire: the cells burning at its edge and every cell it has burned. */
    private static final class Fire {
        final ArrayDeque<BlockPos> front = new ArrayDeque<>();
        final LongOpenHashSet burned = new LongOpenHashSet();
        final LongOpenHashSet patches = new LongOpenHashSet();
    }

    /** Most cells a single fire burns: a big fire, not a world fire. */
    static final int MAX_CELLS = 4000;

    private static final Map<Level, List<Fire>> FIRES = new HashMap<>();
    private static long lastRain = Long.MIN_VALUE;

    private Wildfire() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Wildfire::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (level.isRaining() || lastRain == Long.MIN_VALUE) {
            lastRain = now;
        }
        List<Fire> fires = FIRES.computeIfAbsent(level, k -> new ArrayList<>());
        if (level.isRaining()) {
            fires.clear();
        }
        if (now % 10 == 0 && !fires.isEmpty()) {
            spread(level, fires);
        }
        if (now % 200 == 0 && Cfg.wildfire && fires.size() < 2 && dry(level)) {
            maybeStart(level);
        }
    }

    /** Summer and no rain for WildfireDryDays. */
    private static boolean dry(ServerLevel level) {
        return Seasons.of(level).name().equals("summer") && level.getGameTime() - lastRain >= Cfg.wildfireDryDays * 24000L;
    }

    /** About once every 15 minutes of a dry summer, near someone standing in grassland with fuel built up. */
    private static void maybeStart(ServerLevel level) {
        RandomSource random = level.random;
        if (random.nextInt(90) != 0 || level.players().isEmpty()) {
            return;
        }
        ServerPlayer player = level.players().get(random.nextInt(level.players().size()));
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 32 + random.nextDouble() * 64;
        BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * dist, 0, player.getZ() + Math.sin(angle) * dist);
        if (!Ecology.ready(level, at.getX() >> 4, at.getZ() >> 4)) {
            return;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
        if (!grassland(level.getBiome(ground)) || !fuel(level, ground)) {
            return;
        }
        Patch p = Ecology.enabled() ? Ecology.update(level, ground) : null;
        if (p != null && (p.grass < 0.4 || p.daysUndisturbed() < Params.disturbanceMax)) {
            return;
        }
        ignite(level, ground, true);
    }

    static boolean grassland(Holder<Biome> biome) {
        return biome.is(ConventionalBiomeTags.IS_PLAINS) || biome.is(ConventionalBiomeTags.IS_SAVANNA)
                || biome.unwrapKey().map(k -> k.location().getPath().contains("meadow") || k.location().getPath().contains("prairie")).orElse(false);
    }

    /** Starts a fire on the ground at {@code ground} (a lightning strike, or a test). */
    public static boolean ignite(ServerLevel level, BlockPos ground, boolean lightning) {
        if (!fuel(level, ground) || Hideouts.at(level, Vec3.atCenterOf(ground)) != null) {
            return false;
        }
        if (lightning) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(ground.above()));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        Fire fire = new Fire();
        burn(level, fire, ground);
        FIRES.computeIfAbsent(level, k -> new ArrayList<>()).add(fire);
        SeanCraft.LOG.info("Wildfire started at {}", ground.toShortString());
        return true;
    }

    /** Grass that can burn: a grass block, bare or with plants on it. */
    static boolean fuel(ServerLevel level, BlockPos ground) {
        if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
            return false;
        }
        BlockState above = level.getBlockState(ground.above());
        return above.isAir() || above.getBlock() instanceof BushBlock && !above.is(BlockTags.SAPLINGS) || above.is(BlockTags.REPLACEABLE_BY_TREES)
                || above.is(BlockTags.FLOWERS);
    }

    private static void burn(ServerLevel level, Fire fire, BlockPos ground) {
        fire.burned.add(ground.asLong());
        fire.front.add(ground);
        BlockPos top = ground.above();
        level.setBlock(top, FLAME.defaultBlockState(), 3);
        level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
        long patch = Ecology.keyOf(ground);
        if (fire.patches.add(patch)) {
            Ecology.burned(level, ground);
        }
    }

    private static void spread(ServerLevel level, List<Fire> fires) {
        RandomSource random = level.random;
        int budget = Math.max(1, Cfg.wildfireBlocksPerTick * 10);
        for (Iterator<Fire> it = fires.iterator(); it.hasNext(); ) {
            Fire fire = it.next();
            int n = fire.front.size();
            List<BlockPos> next = new ArrayList<>();
            for (int i = 0; i < n && budget > 0; i++) {
                BlockPos cell = fire.front.poll();
                boolean spread = false;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (budget <= 0 || fire.burned.size() >= MAX_CELLS || random.nextFloat() > 0.7f) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos to = cell.relative(dir).above(dy);
                        if (fire.burned.contains(to.asLong()) || !Ecology.ready(level, to.getX() >> 4, to.getZ() >> 4)) {
                            continue;
                        }
                        if (fuel(level, to) && Hideouts.at(level, Vec3.atCenterOf(to)) == null) {
                            burn(level, fire, to);
                            next.add(to);
                            budget--;
                            spread = true;
                            break;
                        }
                    }
                }
                if (!spread && random.nextFloat() < 0.5f && fire.burned.size() < MAX_CELLS) {
                    // Not done with this cell yet: it can still catch next time.
                    next.add(cell);
                }
            }
            fire.front.clear();
            fire.front.addAll(next);
            if (fire.front.isEmpty()) {
                it.remove();
                continue;
            }
            effects(level, fire, random);
        }
    }

    /** A smoke column over the front, the crackle, and animals running ahead of it. */
    private static void effects(ServerLevel level, Fire fire, RandomSource random) {
        BlockPos any = fire.front.peek();
        if (any == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            BlockPos c = random.nextInt(2) == 0 ? any : fire.front.stream().skip(random.nextInt(fire.front.size())).findFirst().orElse(any);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5, 2, 0.4, 0.5, 0.4, 0.01);
        }
        if (random.nextInt(4) == 0) {
            level.playSound(null, any.above(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.5f, 0.8f);
        }
        if (level.getGameTime() % 40 == 0) {
            for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(any).inflate(16))) {
                Vec3 away = animal.position().subtract(Vec3.atCenterOf(any)).normalize().scale(16).add(animal.position());
                animal.getNavigation().moveTo(away.x, away.y, away.z, 1.6);
            }
        }
    }
}
