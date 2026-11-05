package com.cavmkii.seancraft.ecology;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ecology, phase 1 (design doc 17.1): the grassland loop. The world is split into patches of Ecology.PatchChunks
 * chunks a side, each holding populations, soil and pest pressure as numbers; the animals players see are samples of
 * them (17.3). Loaded patches step forward within Ecology.TickBudgetMs a tick, and a patch that was away catches up
 * when it loads again (17.2).
 */
public final class Ecology {
    public static final String SAMPLE_TAG = "seancraft_eco";
    private static final int SAMPLE_COLUMNS = 64;
    /** Ticks between re-samples of a loaded patch after its blocks change, and at most. */
    private static final int RESAMPLE_AFTER_CHANGE = 600;
    private static final int RESAMPLE_EVERY = 24000;

    /** Per dimension: loaded chunks per patch, patches due a sample, and the round-robin queue. */
    private static final class LevelState {
        final Map<Long, Integer> loaded = new HashMap<>();
        final Set<Long> toSample = new java.util.LinkedHashSet<>();
        final ArrayDeque<Long> queue = new ArrayDeque<>();
    }

    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private Ecology() {}

    public static boolean enabled() {
        return Cfg.ecoEnabled;
    }

    public static int patchChunks() {
        return Math.max(1, Cfg.ecoPatchChunks);
    }

    /** Hectares in a patch (64 x 64 blocks is 0.41 ha). */
    public static double hectares() {
        double side = patchChunks() * 16;
        return side * side / 10000;
    }

    static int sampleSize(Patch p) {
        int n = 0;
        for (int c : p.plants.values()) {
            n += c;
        }
        return n;
    }

    private static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new LevelState());
    }

    private static EcoData data(ServerLevel level) {
        EcoData d = EcoData.get(level);
        if (d.patchChunks != patchChunks()) {
            // A new patch size can't reuse the old records.
            d.patches.clear();
            d.patchChunks = patchChunks();
            d.setDirty();
        }
        return d;
    }

    static void markDirty(ServerLevel level) {
        EcoData.get(level).setDirty();
    }

    public static long key(int px, int pz) {
        return ChunkPos.asLong(px, pz);
    }

    static long keyAt(BlockPos pos) {
        int size = patchChunks() * 16;
        return key(Math.floorDiv(pos.getX(), size), Math.floorDiv(pos.getZ(), size));
    }

    /** Ecological day now: in-game days times Ecology.DayScale. */
    static double today(ServerLevel level) {
        return level.getGameTime() / 24000.0 * Cfg.ecoDayScale;
    }

    /** The patch at a position, created if new and caught up to today. */
    public static Patch patchAt(ServerLevel level, BlockPos pos) {
        return patch(level, keyAt(pos));
    }

    static Patch patch(ServerLevel level, long key) {
        EcoData d = data(level);
        Patch p = d.patches.get(key);
        if (p == null) {
            p = new Patch();
            p.day = today(level);
            p.lastDisturbance = p.day;
            d.patches.put(key, p);
            d.setDirty();
            state(level).toSample.add(key);
        }
        catchUp(level, key, p);
        return p;
    }

    /** Patch at an offset from another, only if it exists already (for the threat layer and neighbours). */
    static Patch existing(ServerLevel level, long key, int dx, int dz) {
        return data(level).patches.get(key(ChunkPos.getX(key) + dx, ChunkPos.getZ(key) + dz));
    }

    /** Steps a patch from its last day to today, at most Ecology.CatchUpMaxDays at once (17.2). */
    static void catchUp(ServerLevel level, long key, Patch p) {
        double now = today(level);
        if (Double.isNaN(p.day)) {
            p.day = now;
        }
        double days = now - p.day;
        if (days < 0.02) {
            return;
        }
        if (days > Cfg.ecoCatchUpMaxDays) {
            p.day = now - Cfg.ecoCatchUpMaxDays;
            days = Cfg.ecoCatchUpMaxDays;
        }
        stepPatch(level, key, p, days);
    }

    static void stepPatch(ServerLevel level, long key, Patch p, double days) {
        double best = bestQualityNear(level, key, 2);
        // Good habitat holds more game (17.16.2); natural enemies from good habitat nearby slow pests.
        Model.step(p, days, Seasons.of(level), p.quality >= 60 ? 0.25 : 0, best >= 60);
        Quality.update(p, (dx, dz) -> existing(level, key, dx, dz));
        markDirty(level);
    }

    /** Highest quality among the patches within {@code r} (inclusive of this one). */
    static double bestQualityNear(ServerLevel level, long key, int r) {
        double best = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Patch n = existing(level, key, dx, dz);
                if (n != null && n.sampled) {
                    best = Math.max(best, n.quality);
                }
            }
        }
        return best;
    }

    // ================================================================== setup

    public static void init() {
        EcoContent.init();
        Params.init();
        YieldFunction.init();
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!enabled()) {
                return;
            }
            LevelState s = state(level);
            long key = key(Math.floorDiv(chunk.getPos().x, patchChunks()), Math.floorDiv(chunk.getPos().z, patchChunks()));
            if (s.loaded.merge(key, 1, Integer::sum) == 1) {
                s.queue.add(key);
                Patch p = data(level).patches.get(key);
                if (p == null || !p.sampled) {
                    s.toSample.add(key);
                }
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            LevelState s = state(level);
            long key = key(Math.floorDiv(chunk.getPos().x, patchChunks()), Math.floorDiv(chunk.getPos().z, patchChunks()));
            s.loaded.computeIfPresent(key, (k, n) -> n <= 1 ? null : n - 1);
        });
        ServerTickEvents.END_WORLD_TICK.register(Ecology::tick);
        // Feedback (17.3): a rabbit or fox killed is one fewer in its patch.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (enabled() && entity.level() instanceof ServerLevel level && (entity instanceof Rabbit || entity instanceof Fox)) {
                Patch p = patchAt(level, entity.blockPosition());
                if (entity instanceof Rabbit) {
                    p.rabbits = Math.max(0.05, p.rabbits - 1 / hectares());
                } else {
                    p.foxes = Math.max(0.02, p.foxes - 1 / hectares());
                }
                markDirty(level);
            }
        });
        // Our samples don't outlive the player leaving.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(SAMPLE_TAG) && level.getNearestPlayer(entity, 96) == null) {
                entity.discard();
            }
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl) {
                changed(sl, pos);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ServerLevel sl && enabled()) {
                ItemStack held = player.getItemInHand(hand);
                BlockState at = level.getBlockState(hit.getBlockPos());
                if (held.is(Items.BONE_MEAL) && (at.getBlock() instanceof CropBlock || at.getBlock() instanceof FarmBlock)) {
                    // Compost and bone meal go through the decomposer pool (17.6).
                    patchAt(sl, hit.getBlockPos()).organicFast += Params.boneMealOrganic;
                    markDirty(sl);
                }
                if (held.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    changed(sl, hit.getBlockPos());
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seancraft").then(Commands.literal("eco").requires(src -> src.hasPermission(2))
                .then(Commands.literal("patch").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    long key = keyAt(player.blockPosition());
                    Patch p = patch(level, key);
                    sample(level, key, p);
                    for (String line : describe(level, key, p)) {
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("set").then(Commands.argument("field", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> {
                            // Testing: set one number on the patch you're in (rabbits, foxes, nitrogen, pest...).
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Patch p = patchAt(player.serverLevel(), player.blockPosition());
                            String field = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "field");
                            try {
                                Patch.class.getField(field).setDouble(p, DoubleArgumentType.getDouble(ctx, "value"));
                            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                                ctx.getSource().sendFailure(Component.literal("No number field " + field + " on a patch."));
                                return 0;
                            }
                            markDirty(player.serverLevel());
                            ctx.getSource().sendSuccess(() -> Component.literal("Set " + field), false);
                            return 1;
                        }))))
                .then(Commands.literal("step").then(Commands.argument("days", DoubleArgumentType.doubleArg(0.01, 365)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    long key = keyAt(player.blockPosition());
                    Patch p = patch(level, key);
                    double days = DoubleArgumentType.getDouble(ctx, "days");
                    stepPatch(level, key, p, days);
                    // The patch is now ahead of the clock; the clock catches up before it steps again.
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "Stepped this patch %.1f days: rabbits %.1f, foxes %.2f per ha, N %.0f, quality %.0f", days, p.rabbits, p.foxes,
                            p.nitrogen, p.quality)), false);
                    return 1;
                })))));
    }

    static List<String> describe(ServerLevel level, long key, Patch p) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "Patch %d, %d (day %.1f, season %s)", ChunkPos.getX(key), ChunkPos.getZ(key), p.day, Seasons.of(level).name()));
        out.add(String.format(Locale.ROOT, "Cover: grass %.0f%% crop %.0f%% (wheat %.0f%%) shrub %.0f%% forest %.0f%% water %.0f%% built %.0f%% path %.0f%% bare %.0f%%",
                p.grass * 100, p.crop * 100, p.wheat * 100, p.shrub * 100, p.forest * 100, p.water * 100, p.built * 100, p.path * 100, p.bare * 100));
        out.add(String.format(Locale.ROOT, "Per ha: grass %.2f, rabbits %.1f, mice %.1f, foxes %.2f, bats %.1f (roost %.1f), aphids %.0f (resistance %.0f%%)",
                p.grassBiomass, p.rabbits, p.mice, p.foxes, p.bats, p.roost, p.pest, p.resistance * 100));
        out.add(String.format(Locale.ROOT, "Soil: N %.1f, P %.1f, OM fast %.1f slow %.1f, moisture %.0f%%; crops limited by %s",
                p.nitrogen, p.phosphorus, p.organicFast, p.organicSlow, p.moisture * 100, Model.limiting(p, p.wheat > 0)));
        out.add(String.format(Locale.ROOT, "Habitat H %.2f, threats D %.2f (most from %s), Q %.2f; layers %s%s%s%s", Quality.habitat(p), p.threat, p.worstThreat, p.habitatQ,
                p.layerGround ? "ground " : "", p.layerHerbs ? "herbs " : "", p.layerShrubs ? "shrubs " : "", p.layerCanopy ? "canopy" : ""));
        out.add(String.format(Locale.ROOT, "Quality %.0f %s, FQI %.1f from %d species%s; %.0f days since fire or grazing; grazers %d, villagers %d",
                p.quality, Quality.band(p.quality), Quality.fqi(p), p.plants.size(), p.limitedBy.isEmpty() ? "" : ", held back by " + p.limitedBy,
                p.daysUndisturbed(), p.grazers, p.villagers));
        if (!p.builtSeen.isEmpty()) {
            out.add("Counted as built: " + String.join(", ", p.builtSeen));
        }
        return out;
    }

    // ================================================================== ticking

    private static void tick(ServerLevel level) {
        if (!enabled()) {
            return;
        }
        LevelState s = state(level);
        long deadline = System.nanoTime() + (long) (Cfg.ecoTickBudgetMs * 1_000_000);
        // Samples first: a new or changed patch reads its blocks (cheap, 64 columns).
        // One key at a time: sampling a patch can queue its neighbours.
        while (!s.toSample.isEmpty() && System.nanoTime() < deadline) {
            long key = s.toSample.iterator().next();
            s.toSample.remove(key);
            if (s.loaded.containsKey(key)) {
                sample(level, key, patch(level, key));
            }
        }
        // Then step loaded patches round-robin until the budget runs out.
        int n = s.queue.size();
        while (n-- > 0 && System.nanoTime() < deadline) {
            long key = s.queue.poll();
            if (!s.loaded.containsKey(key)) {
                continue;
            }
            s.queue.add(key);
            Patch p = patch(level, key);
            if (p.sampled && level.getGameTime() - p.sampledAt > RESAMPLE_EVERY) {
                s.toSample.add(key);
            }
        }
        if (level.getGameTime() % 400 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    wildlife(level, player);
                }
            }
        }
    }

    /** A block changed: the patch reads its land cover again soon. */
    static void changed(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        long key = keyAt(pos);
        Patch p = data(level).patches.get(key);
        if (p != null && level.getGameTime() - p.sampledAt > RESAMPLE_AFTER_CHANGE) {
            state(level).toSample.add(key);
        }
    }

    /** Fire in a patch counts as a disturbance (17.16.1). Called from the fire block's tick. */
    public static void burned(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        Patch p = patchAt(level, pos);
        p.lastDisturbance = p.day;
    }

    // ================================================================== sampling (17.2, 17.16.4)

    static void sample(ServerLevel level, long key, Patch p) {
        int size = patchChunks() * 16;
        int x0 = ChunkPos.getX(key) * size;
        int z0 = ChunkPos.getZ(key) * size;
        RandomSource random = RandomSource.create(key ^ level.getGameTime());
        int cols = 0;
        double grass = 0, crop = 0, wheat = 0, shrub = 0, forest = 0, water = 0, built = 0, bare = 0, path = 0;
        int caves = 0;
        Map<String, Integer> plants = new HashMap<>();
        Set<String> builtSeen = new java.util.TreeSet<>();
        boolean ground = false, herbs = false;
        for (int i = 0; i < SAMPLE_COLUMNS; i++) {
            int x = x0 + random.nextInt(size);
            int z = z0 + random.nextInt(size);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            cols++;
            // Leaves over the ground: MOTION_BLOCKING counts leaves but not grass or flowers.
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos g = new BlockPos(x, gy, z);
            BlockState gs = level.getBlockState(g);
            BlockState above = level.getBlockState(g.above());
            int canopy = top - gy;
            if (gs.getFluidState().is(Fluids.WATER) || gs.is(Blocks.WATER)) {
                water++;
            } else if (gs.getBlock() instanceof FarmBlock || gs.getBlock() instanceof CropBlock || above.getBlock() instanceof CropBlock) {
                crop++;
                if (above.is(Blocks.WHEAT) || gs.is(Blocks.WHEAT)) {
                    wheat++;
                }
            } else if (gs.is(BlockTags.LOGS) || canopy >= 4) {
                forest++;
            } else if (canopy >= 1 || gs.is(BlockTags.LEAVES) || above.is(Blocks.SWEET_BERRY_BUSH) || gs.is(Blocks.AZALEA)
                    || gs.is(Blocks.FLOWERING_AZALEA)) {
                shrub++;
            } else if (gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.PODZOL) || gs.is(Blocks.MOSS_BLOCK) || gs.is(Blocks.MYCELIUM)) {
                grass++;
                ground = true;
            } else if (gs.is(Blocks.DIRT_PATH)) {
                path++;
            } else if (natural(gs)) {
                bare++;
            } else {
                built++;
                builtSeen.add(BuiltInRegistries.BLOCK.getKey(gs.getBlock()).getPath());
            }
            // Plants in a 3 x 3 around the column (17.16).
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = new BlockPos(x + dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz), z + dz);
                    String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).toString();
                    if (Params.PLANTS.containsKey(id)) {
                        plants.merge(id, 1, Integer::sum);
                        herbs = true;
                    }
                }
            }
            // Caves under the column hold bat roosts.
            for (int y = gy - 4; y > Math.max(level.getMinBuildHeight(), gy - 44); y -= 2) {
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && level.getBrightness(LightLayer.SKY, at) == 0 && level.getBrightness(LightLayer.BLOCK, at) == 0) {
                    caves++;
                    break;
                }
            }
        }
        if (cols < 16) {
            // Too few chunks loaded to say; try again later.
            return;
        }
        boolean first = !p.sampled;
        p.grass = grass / cols;
        p.crop = crop / cols;
        p.wheat = wheat / cols;
        p.shrub = shrub / cols;
        p.forest = forest / cols;
        p.water = water / cols;
        p.built = built / cols;
        p.bare = bare / cols;
        p.path = path / cols;
        p.plants = plants;
        p.builtSeen = builtSeen;
        p.layerGround = ground;
        p.layerHerbs = herbs;
        p.layerShrubs = p.shrub > 0.03;
        p.layerCanopy = p.forest > 0.08;
        p.roost = p.forest * 4 + (double) caves / cols * 15;
        p.moisture = Model.clamp(0.35 + p.water * 1.5 + (level.isRainingAt(new BlockPos(x0 + size / 2, level.getSeaLevel() + 1, z0 + size / 2)) ? 0.15 : 0), 0, 1);
        AABB box = new AABB(x0, level.getMinBuildHeight(), z0, x0 + size, level.getMaxBuildHeight(), z0 + size);
        // Wild herds count as grazing; a pen (animals plus fences or buildings) also counts as a threat (17.16.1).
        p.grazers = level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Sheep || e instanceof Cow || e instanceof Goat).size();
        p.villagers = level.getEntitiesOfClass(Villager.class, box).size();
        if (p.grazers >= 2) {
            p.lastDisturbance = p.day;
        }
        if (first) {
            // Fresh ground: carbon from trees and prairie roots, animals near what the land can hold.
            int bluestem = plants.getOrDefault(EcoContent.id(EcoContent.Prairie.BIG_BLUESTEM), 0);
            p.organicSlow = 40 + 30 * p.forest + Math.min(40, bluestem * 4);
            p.grassBiomass = Math.max(0.05, p.grass);
            p.rabbits = Math.max(0.5, Params.rabbitK * p.grassBiomass * 0.4);
            p.mice = Math.max(1, Params.mouseK * 0.5 * Math.max(0.1, p.grass * 0.4 + p.crop + p.shrub * 0.5));
            p.foxes = Math.max(0.05, p.rabbits / 8);
            p.bats = p.roost;
            p.sampled = true;
        }
        p.sampledAt = level.getGameTime();
        Quality.update(p, (dx, dz) -> existing(level, key, dx, dz));
        markDirty(level);
    }

    /** Natural ground: dirt, sand, gravel, stone and the like (not built). */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE)
                || s.is(Blocks.LAVA) || s.getBlock() instanceof BushBlock;
    }

    // ================================================================== crops (17.6, 17.7)

    /** A crop's random tick: whether it may grow, given the soil (poor soil slows growth). */
    public static boolean cropMayGrow(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!enabled()) {
            return true;
        }
        Patch p = patchAt(level, pos);
        double f = Model.soilFactor(p);
        return random.nextDouble() < 0.5 + 0.5 * f;
    }

    /** A crop grew a stage: it takes nitrogen and phosphorus from the patch. */
    public static void cropGrew(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        Patch p = patchAt(level, pos);
        p.nitrogen = Math.max(0, p.nitrogen - Params.uptakeN / hectares());
        p.phosphorus = Math.max(0, p.phosphorus - Params.uptakeP / hectares());
        markDirty(level);
    }

    /** Harvest yield multiplier for a crop here: soil, pests on wheat, and pollinators from good habitat nearby. */
    public static double yieldMultiplier(ServerLevel level, BlockPos pos, Block crop) {
        if (!enabled()) {
            return 1;
        }
        Patch p = patchAt(level, pos);
        double best = bestQualityNear(level, keyAt(pos), 2);
        double pollinators = best >= 80 ? 0.25 : best >= 60 ? 0.15 : 0;
        double pests = crop == Blocks.WHEAT ? 1 - Model.pestLoss(p) : 1;
        return (0.4 + 0.6 * Model.soilFactor(p)) * pests * (1 + pollinators);
    }

    /** Insecticide on a patch (17.7): pests and bats both drop; the pest builds resistance. */
    static void spray(Patch p) {
        p.pest *= 1 - Params.insecticideKill * (1 - p.resistance);
        p.bats *= 1 - Params.insecticideBatKill;
        p.resistance = Math.min(Params.resistanceMax, p.resistance + Params.resistanceStep);
    }

    // ================================================================== hideouts

    /** Built ground inside a hideout never changes through succession or grazing (17.8). */
    static boolean protectedGround(ServerLevel level, BlockPos pos) {
        return Hideouts.at(level, Vec3.atCenterOf(pos)) != null;
    }

    /** Comfort from nature (12, 17.16.2): +1 beside a Good patch, +2 beside a Remnant. */
    public static int comfortBonus(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return 0;
        }
        double best = bestQualityNear(level, keyAt(pos), 1);
        return best >= 80 ? 2 : best >= 60 ? 1 : 0;
    }

    // ================================================================== what players see (17.3, 17.11)

    /** Every 20 seconds per player: rabbits and foxes to match the patch, guano under roosting bats, pest signs. */
    private static void wildlife(ServerLevel level, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        long key = keyAt(at);
        if (!state(level).loaded.containsKey(key)) {
            return;
        }
        Patch p = patch(level, key);
        if (!p.sampled) {
            return;
        }
        RandomSource random = level.getRandom();
        double area = hectares();
        sampleAnimals(level, player, EntityType.RABBIT, Rabbit.class,
                Math.min(Params.rabbitCap, (int) Math.round(p.rabbits * area * Params.rabbitsShown)));
        sampleAnimals(level, player, EntityType.FOX, Fox.class, Math.min(Params.foxCap, (int) Math.round(p.foxes * area * Params.foxesShown)));
        // Guano under resting bats (17.7).
        for (Bat bat : level.getEntitiesOfClass(Bat.class, player.getBoundingBox().inflate(48))) {
            if (bat.isResting() && random.nextInt(8) == 0) {
                level.addFreshEntity(new ItemEntity(level, bat.getX(), bat.getY() - 0.2, bat.getZ(), new ItemStack(EcoContent.BAT_GUANO)));
            }
        }
        // Aphids on wheat show as specks over the crop.
        if (p.wheat > 0 && p.pest > 120) {
            for (int i = 0; i < 20; i++) {
                BlockPos c = at.offset(random.nextInt(25) - 12, random.nextInt(7) - 3, random.nextInt(25) - 12);
                if (level.getBlockState(c).is(Blocks.WHEAT)) {
                    level.sendParticles(ParticleTypes.MYCELIUM, c.getX() + 0.5, c.getY() + 0.6, c.getZ() + 0.5,
                            (int) Math.min(12, p.pest / 60), 0.3, 0.2, 0.3, 0);
                }
            }
        }
        // Overgrazed grass turns to dirt (17.11), never inside a hideout.
        if (p.grassBiomass < 0.15) {
            BlockPos c = at.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c).below();
            if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) && !protectedGround(level, top)) {
                level.setBlockAndUpdate(top, Blocks.DIRT.defaultBlockState());
            }
        }
    }

    /** Keeps roughly {@code want} of an animal near a player: spawns ours out of sight, removes ours when too many. */
    private static <T extends Mob> void sampleAnimals(ServerLevel level, ServerPlayer player, EntityType<T> type, Class<T> cls, int want) {
        List<T> near = level.getEntitiesOfClass(cls, player.getBoundingBox().inflate(48, 24, 48));
        for (T e : level.getEntitiesOfClass(cls, player.getBoundingBox().inflate(128, 48, 128), e -> e.getTags().contains(SAMPLE_TAG))) {
            if (level.getNearestPlayer(e, 80) == null) {
                e.discard();
            }
        }
        if (near.size() > want + 2) {
            for (T e : near) {
                if (e.getTags().contains(SAMPLE_TAG) && e.distanceTo(player) > 24 && !e.hasCustomName() && !e.isLeashed()) {
                    e.discard();
                    return;
                }
            }
            return;
        }
        if (near.size() >= want) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 20 + random.nextInt(20);
            BlockPos c = player.blockPosition().offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
            if (!level.hasChunkAt(c)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c);
            if (!level.getBlockState(top.below()).is(Blocks.GRASS_BLOCK) || keyAt(top) != keyAt(player.blockPosition()) && random.nextBoolean()) {
                continue;
            }
            // Out of the player's view.
            Vec3 to = Vec3.atCenterOf(top).subtract(player.getEyePosition()).normalize();
            if (to.dot(player.getViewVector(1)) > 0.3) {
                continue;
            }
            T e = type.create(level);
            if (e == null) {
                return;
            }
            e.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, random.nextFloat() * 360, 0);
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.NATURAL, null);
            e.addTag(SAMPLE_TAG);
            level.addFreshEntity(e);
            return;
        }
    }

    /** One rabbit born in a patch (breeding feedback, 17.3). */
    public static void born(ServerLevel level, Entity child) {
        if (enabled() && child instanceof Rabbit) {
            Patch p = patchAt(level, child.blockPosition());
            p.rabbits += 1 / hectares();
            markDirty(level);
        }
    }
}
