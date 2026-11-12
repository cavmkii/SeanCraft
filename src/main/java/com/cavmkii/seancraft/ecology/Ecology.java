package com.cavmkii.seancraft.ecology;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ecology (design doc 17), phases 1 and 2, kept to what players can see within a session or two (17.1). The world is
 * split into patches of Ecology.PatchChunks chunks a side, each holding a few numbers (Patch). Loaded patches update
 * once per in-game day, round-robin within Ecology.TickBudgetMs, and at once on the player actions that change them:
 * harvesting, hunting, fertilizing, spraying and burning (17.2). A patch that was away jumps to where it was heading in
 * one calculation. Animals, fish, green water, dead fish, chewed wheat and bare dirt are the model showing itself.
 */
public final class Ecology {
    public static final String SAMPLE_TAG = "seancraft_eco";
    /** Columns read when a patch is first seen (17.16.4). */
    private static final int SAMPLE_COLUMNS = 16;
    /** Ticks between land cover re-reads of a patch after its blocks change. */
    private static final int RESAMPLE_AFTER_CHANGE = 600;

    /** Per dimension: loaded chunks per patch, patches due a sample, and the round-robin queue. */
    private static final class LevelState {
        final Map<Long, Integer> loaded = new HashMap<>();
        final Set<Long> toSample = new java.util.LinkedHashSet<>();
        final ArrayDeque<Long> queue = new ArrayDeque<>();
    }

    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();
    /** The patch each player was last in, for the quality banner (17.11). */
    private static final Map<UUID, Long> LAST_PATCH = new HashMap<>();

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

    /** The key of the patch a block is in. */
    public static long keyOf(BlockPos pos) {
        return keyAt(pos);
    }

    static long keyAt(BlockPos pos) {
        int size = patchChunks() * 16;
        return key(Math.floorDiv(pos.getX(), size), Math.floorDiv(pos.getZ(), size));
    }

    /** Ecological day now: in-game days times Ecology.DayScale. */
    static double today(ServerLevel level) {
        return level.getGameTime() / 24000.0 * Cfg.ecoDayScale;
    }

    /** The patch at a position, created if new. It isn't stepped here: see {@link #update}. */
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
        if (Double.isNaN(p.day)) {
            p.day = today(level);
        }
        return p;
    }

    /** Patch at an offset from another, only if it exists already. */
    static Patch existing(ServerLevel level, long key, int dx, int dz) {
        return data(level).patches.get(key(ChunkPos.getX(key) + dx, ChunkPos.getZ(key) + dz));
    }

    /** Brings a patch up to today, at most Ecology.CatchUpMaxDays at once (17.2). Used before any player action on it. */
    public static Patch update(ServerLevel level, BlockPos pos) {
        long key = keyAt(pos);
        Patch p = patch(level, key);
        update(level, key, p, 0);
        return p;
    }

    /** Steps the patch if at least {@code minDays} have passed since it was last stepped. */
    static void update(ServerLevel level, long key, Patch p, double minDays) {
        double now = today(level);
        double days = now - p.day;
        if (days < Math.max(minDays, 1.0e-3)) {
            return;
        }
        if (days > Cfg.ecoCatchUpMaxDays) {
            p.day = now - Cfg.ecoCatchUpMaxDays;
            days = Cfg.ecoCatchUpMaxDays;
        }
        stepPatch(level, key, p, days);
    }

    static void stepPatch(ServerLevel level, long key, Patch p, double days) {
        Model.Outflow out = Model.step(p, days, Seasons.of(level), enemiesNear(level, key));
        if (p.sampled) {
            if (out.leached() > 0) {
                leach(level, key, p, out.leached());
            }
            if (out.cleared() > 0) {
                // Half of what clears flows on to the next water downhill (17.6).
                Patch next = downstream(level, key, p);
                if (next != null && next != p && next.holdsWater()) {
                    next.pollution += out.cleared() * 0.5 * Math.max(Params.waterMin, p.water) / Math.max(Params.waterMin, next.water);
                }
            }
            neighbours(level, key, p, days);
        }
        quality(level, key, p);
        markDirty(level);
    }

    // ================================================================== water and space (17.5, 17.6)

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * A chunk that is fully loaded right now. Level.hasChunk only says one is on its way; reading a chunk that isn't
     * finished makes the server thread stop and finish it (or generate it) on the spot, which is a lag spike.
     */
    public static boolean ready(ServerLevel level, int cx, int cz) {
        return level.getChunkSource().getChunkNow(cx, cz) != null;
    }

    public static boolean holdsWater(Patch p) {
        return p != null && p.holdsWater();
    }

    /**
     * Where this patch's runoff goes: itself if it holds water, else the first water patch downhill (lowest of the 8
     * neighbours each step, up to 8 steps). Worked out once and stored (17.6); patches not seen yet are skipped, so it
     * tries again later if it found nothing.
     */
    static Patch downstream(ServerLevel level, long key, Patch p) {
        if (p.holdsWater()) {
            return p;
        }
        if (p.downhillKnown) {
            return p.downhill == Long.MIN_VALUE ? null : data(level).patches.get(p.downhill);
        }
        long at = key;
        Patch cur = p;
        for (int hop = 0; hop < 8; hop++) {
            long next = Long.MIN_VALUE;
            double low = cur.elevation - 0.5;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Patch n = (dx == 0 && dz == 0) ? null : existing(level, at, dx, dz);
                    if (n != null && n.sampled && !Double.isNaN(n.elevation) && n.elevation < low) {
                        low = n.elevation;
                        next = key(ChunkPos.getX(at) + dx, ChunkPos.getZ(at) + dz);
                    }
                }
            }
            if (next == Long.MIN_VALUE) {
                break;
            }
            Patch n = data(level).patches.get(next);
            if (n.holdsWater()) {
                p.downhill = next;
                p.downhillKnown = true;
                return n;
            }
            at = next;
            cur = n;
        }
        return null;
    }

    /** Fertility that washed out lands in the water downhill as pollution; a grass strip in a Fair patch stops most. */
    static void leach(ServerLevel level, long key, Patch p, double amount) {
        if (p.quality >= 40) {
            amount *= 1 - Params.stripIntercept * p.strip;
        }
        Patch water = downstream(level, key, p);
        if (water != null) {
            water.pollution += Model.pollutionFrom(water, amount);
        }
    }

    /**
     * Once a day, small game moves a step toward the average of the open neighbours (17.5); Good and Remnant ones push
     * harder. An edge is closed by walls, roads and open farmland unless a corridor (hedges, trees) crosses it.
     */
    static void neighbours(ServerLevel level, long key, Patch p, double days) {
        double sum = 0;
        double weight = 0;
        int open = 0;
        for (int[] d : SIDES) {
            Patch n = existing(level, key, d[0], d[1]);
            if (n == null || !n.sampled) {
                open++; // unexplored ground counts as open
                continue;
            }
            boolean corridor = p.shrub + p.forest >= Params.corridor || n.shrub + n.forest >= Params.corridor;
            if (!corridor && (Model.permeability(p) < Params.openEdge || Model.permeability(n) < Params.openEdge)) {
                continue;
            }
            open++;
            double w = n.quality >= 60 ? Params.neighbourStepGood / Params.neighbourStep : 1;
            sum += n.game * w;
            weight += w;
        }
        p.openEdges = open;
        if (weight > 0) {
            double step = 1 - Math.exp(-Params.neighbourStep * Math.min(weight, 2) * days);
            p.game += (sum / weight - p.game) * step;
        }
    }

    /** A cave, roost, hedgerow or tree cover in this patch or one next to it (17.7). */
    static boolean enemiesNear(ServerLevel level, long key) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Patch n = existing(level, key, dx, dz);
                if (n != null && n.enemies) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A fish bit: whether it lands, given the fish here, and if it does, a little fewer in the patch (17.3). */
    public static boolean fishBite(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!enabled()) {
            return true;
        }
        Patch p = update(level, pos);
        if (!p.holdsWater()) {
            return true;
        }
        if (random.nextDouble() >= Model.fishAvailability(p)) {
            return false;
        }
        p.fish = Math.max(0, p.fish - Params.fishPerCatch);
        markDirty(level);
        return true;
    }

    /** Fish spawn to match the fish left (17.3). */
    public static boolean fishSpawnAllowed(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!enabled()) {
            return true;
        }
        Patch p = data(level).patches.get(keyAt(pos));
        return !holdsWater(p) || random.nextDouble() < Model.fishAvailability(p);
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
        // Hunting (17.3, 17.4): a rabbit killed takes a step off its patch's small game.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (enabled() && entity.level() instanceof ServerLevel level && entity instanceof Rabbit) {
                Patch p = update(level, entity.blockPosition());
                p.game = Math.max(0.02, p.game - Params.gamePerKill);
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
                plantChanged(sl, pos, state, -1);
                changed(sl, pos);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ServerLevel sl && enabled()) {
                ItemStack held = player.getItemInHand(hand);
                BlockState at = level.getBlockState(hit.getBlockPos());
                boolean field = at.getBlock() instanceof CropBlock || at.getBlock() instanceof FarmBlock;
                if (field && (held.is(Items.BONE_MEAL) || held.is(Items.ROTTEN_FLESH))) {
                    // Fertilizer works over the next day or two, not at once (17.6).
                    Patch p = update(sl, hit.getBlockPos());
                    p.pendingFast += held.is(Items.BONE_MEAL) ? Params.boneMeal : Params.rottenFlesh;
                    markDirty(sl);
                    if (held.is(Items.ROTTEN_FLESH)) {
                        held.consume(1, player);
                        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, hit.getBlockPos().getX() + 0.5, hit.getBlockPos().getY() + 1,
                                hit.getBlockPos().getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0);
                        return InteractionResult.SUCCESS;
                    }
                }
                if (held.getItem() instanceof BlockItem bi) {
                    // Placing a plant counts it in the patch's plant sample (17.16.4).
                    plantChanged(sl, hit.getBlockPos().relative(hit.getDirection()), bi.getBlock().defaultBlockState(), 1);
                    changed(sl, hit.getBlockPos());
                }
            }
            return InteractionResult.PASS;
        });
    }

    /** A plant placed (+1) or broken (-1) by a player, or spreading or dying back: the plant sample follows. */
    static void plantChanged(ServerLevel level, BlockPos pos, BlockState state, int delta) {
        if (!enabled()) {
            return;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!Params.PLANTS.containsKey(id)) {
            return;
        }
        Patch p = data(level).patches.get(keyAt(pos));
        if (p == null || !p.sampled) {
            return;
        }
        int n = p.plants.getOrDefault(id, 0) + delta;
        if (n <= 0) {
            p.plants.remove(id);
        } else {
            p.plants.put(id, n);
        }
        markDirty(level);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seancraft").then(Commands.literal("eco").requires(src -> src.hasPermission(2))
                .then(Commands.literal("patch").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    long key = keyAt(player.blockPosition());
                    Patch p = patch(level, key);
                    if (!p.sampled) {
                        sample(level, key, p);
                    }
                    update(level, key, p, 0);
                    for (String line : describe(level, key, p)) {
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("set").then(Commands.argument("field", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> {
                            // Testing: set one number on the patch you're in (game, fertility, pest, pollution, fish...).
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Patch p = update(player.serverLevel(), player.blockPosition());
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
                    update(level, key, p, 0);
                    double days = DoubleArgumentType.getDouble(ctx, "days");
                    stepPatch(level, key, p, days);
                    // The patch is now ahead of the clock; the clock catches up before it steps again.
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "Stepped this patch %.1f days: game %.2f, fertility %.0f, pests %.2f, pollution %.2f, quality %.0f", days, p.game,
                            p.fertility, p.pest, p.pollution, p.quality)), false);
                    return 1;
                })))));
    }

    static List<String> describe(ServerLevel level, long key, Patch p) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "Patch %d, %d (day %.1f, season %s)", ChunkPos.getX(key), ChunkPos.getZ(key), p.day, Seasons.of(level).name()));
        out.add(String.format(Locale.ROOT, "Cover: grass %.0f%% crop %.0f%% (wheat %.0f%%) shrub %.0f%% forest %.0f%% water %.0f%% built %.0f%% path %.0f%% bare %.0f%%",
                p.grass * 100, p.crop * 100, p.wheat * 100, p.shrub * 100, p.forest * 100, p.water * 100, p.built * 100, p.path * 100, p.bare * 100));
        out.add(String.format(Locale.ROOT, "Game %.2f (cap %.2f, foxes follow %.2f), forage %.2f; open edges %d; natural enemies %s",
                p.game, Model.gameCap(p), p.gameLag, p.forage, p.openEdges, enemiesNear(level, key) ? (p.day < p.enemiesOffUntil ? "sprayed off" : "yes") : "no"));
        out.add(String.format(Locale.ROOT, "Soil: fertility %.0f (+%.1f breaking down), moisture %.0f%%; pests %.2f (resistance %.0f%%); crops held back by %s",
                p.fertility, p.pendingFast, p.moisture * 100, p.pest, p.resistance * 100, Model.limiting(p, p.wheat > 0)));
        Patch water = downstream(level, key, p);
        out.add(String.format(Locale.ROOT, "Runoff goes to %s; crops near water %d, %.0f%% behind a grass strip",
                water == null ? "nowhere yet" : water == p ? "this patch's water" : "water downhill", p.cropNearWater, p.strip * 100));
        if (p.holdsWater()) {
            out.add(String.format(Locale.ROOT, "Water: pollution %.2f (green at %.1f, fish die at %.1f), fish %.2f (%.0f%% of bites land)",
                    p.pollution, Params.greenAt, Params.deadAt, p.fish, Model.fishAvailability(p) * 100));
        }
        out.add(String.format(Locale.ROOT, "Quality %.0f %s: variety %.0f/%.0f, threats %.2f%s; %.0f days since fire or grazing%s",
                p.quality, Quality.band(p.quality), Quality.variety(p), Params.varietyCap, p.threat,
                p.worstThreat.isEmpty() ? "" : " (most from " + p.worstThreat + ")", p.daysUndisturbed(),
                p.limitedBy.isEmpty() ? "" : "; held back by " + p.limitedBy));
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
        // Samples first: a new or changed patch reads its blocks. One key at a time: sampling can queue neighbours.
        while (!s.toSample.isEmpty() && System.nanoTime() < deadline) {
            long key = s.toSample.iterator().next();
            s.toSample.remove(key);
            if (s.loaded.containsKey(key)) {
                sample(level, key, patch(level, key));
            }
        }
        // Then the daily pass: each loaded patch steps once a day has gone by since its last update (17.2).
        int n = s.queue.size();
        while (n-- > 0 && System.nanoTime() < deadline) {
            long key = s.queue.poll();
            if (!s.loaded.containsKey(key)) {
                continue;
            }
            s.queue.add(key);
            Patch p = patch(level, key);
            if (p.sampled) {
                update(level, key, p, 1);
            }
        }
        long time = level.getGameTime();
        if (time % 40 == 0) {
            for (ServerPlayer player : level.players()) {
                signs(level, player);
            }
        }
        if (time % 400 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    wildlife(level, player);
                }
            }
        }
    }

    /** A block changed: the patch reads its land cover again soon (not its plant sample, 17.16.4). */
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
        Patch p = update(level, pos);
        p.disturb();
        markDirty(level);
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
        double height = 0;
        BlockPos lastLoaded = null;
        Map<String, Integer> plants = new HashMap<>();
        Set<String> builtSeen = new java.util.TreeSet<>();
        int tries = 0;
        while (cols < SAMPLE_COLUMNS && tries++ < SAMPLE_COLUMNS * 3) {
            int x = x0 + random.nextInt(size);
            int z = z0 + random.nextInt(size);
            if (!ready(level, x >> 4, z >> 4)) {
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
            height += gy;
            lastLoaded = new BlockPos(x, top + 1, z);
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
            } else if (gs.is(Blocks.DIRT_PATH)) {
                path++;
            } else if (natural(gs)) {
                bare++;
            } else {
                built++;
                builtSeen.add(BuiltInRegistries.BLOCK.getKey(gs.getBlock()).getPath());
            }
            if (!p.sampled) {
                // The plant sample is read once, when the patch is first seen; after that it follows plants placed,
                // broken, spreading and dying (17.16.4). A 3 x 3 around each column, in loaded chunks only.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!ready(level, (x + dx) >> 4, (z + dz) >> 4)) {
                            continue;
                        }
                        BlockPos at = new BlockPos(x + dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz), z + dz);
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).toString();
                        if (Params.PLANTS.containsKey(id)) {
                            plants.merge(id, 1, Integer::sum);
                        }
                    }
                }
            }
            // Caves under the column: bats, a natural enemy of crop pests (17.7).
            for (int y = gy - 4; y > Math.max(level.getMinBuildHeight(), gy - 44); y -= 2) {
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && level.getBrightness(LightLayer.SKY, at) == 0 && level.getBrightness(LightLayer.BLOCK, at) == 0) {
                    caves++;
                    break;
                }
            }
        }
        if (cols < SAMPLE_COLUMNS / 2) {
            // Too few chunks loaded to say; try again later.
            return;
        }
        // Fields by the water (17.16.2): every second column, so small fields count.
        int nearWater = 0, buffered = 0;
        for (int x = x0; x < x0 + size; x += 2) {
            for (int z = z0; z < z0 + size; z += 2) {
                if (!ready(level, x >> 4, z >> 4)) {
                    continue;
                }
                BlockState gs = level.getBlockState(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z));
                if (!(gs.getBlock() instanceof FarmBlock) && !(gs.getBlock() instanceof CropBlock)) {
                    continue;
                }
                int strip = stripToWater(level, x, z);
                if (strip >= 0) {
                    nearWater++;
                    if (strip >= 4) {
                        buffered++;
                    }
                }
            }
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
        p.elevation = height / cols;
        p.cropNearWater = nearWater;
        p.strip = nearWater == 0 ? 0 : (double) buffered / nearWater;
        p.builtSeen = builtSeen;
        p.enemies = caves > 0 || p.forest >= 0.05 || p.shrub >= 0.05;
        p.moisture = Model.clamp(0.35 + p.water * 1.5 + (lastLoaded != null && level.isRainingAt(lastLoaded) ? 0.15 : 0), 0, 1);
        AABB box = new AABB(x0, level.getMinBuildHeight(), z0, x0 + size, level.getMaxBuildHeight(), z0 + size);
        // Wild herds count as grazing; a pen (animals plus fences or buildings) also counts as a threat (17.16.1).
        p.grazers = level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Sheep || e instanceof Cow || e instanceof Goat).size();
        p.villagers = level.getEntitiesOfClass(Villager.class, box).size();
        if (p.grazers >= 2) {
            p.disturb();
        }
        if (first) {
            p.plants = plants;
            p.forage = Math.max(0.05, p.grass);
            p.sampled = true;
            quality(level, key, p);
            p.game = Model.gameCap(p) * 0.8;
            p.gameLag = p.game;
            p.fish = 0.8;
        }
        p.sampledAt = level.getGameTime();
        quality(level, key, p);
        markDirty(level);
    }

    /**
     * From a crop column, the nearest water in the four directions within 10 blocks: -1 if none, else how many grass
     * columns lie between the crop and the water.
     */
    private static int stripToWater(ServerLevel level, int x, int z) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int[] d : SIDES) {
            int grass = 0;
            for (int i = 1; i <= 10 && i < bestDist; i++) {
                int cx = x + d[0] * i;
                int cz = z + d[1] * i;
                if (!ready(level, cx >> 4, cz >> 4)) {
                    break;
                }
                BlockState g = level.getBlockState(new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1, cz));
                if (g.getFluidState().is(Fluids.WATER)) {
                    best = grass;
                    bestDist = i;
                    break;
                }
                if (g.is(Blocks.GRASS_BLOCK)) {
                    grass++;
                }
            }
        }
        return best;
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
        Patch p = data(level).patches.get(keyAt(pos));
        return p == null || random.nextDouble() < 0.5 + 0.5 * Model.soilFactor(p);
    }

    /** A crop grew a stage: it takes fertility from the field (17.6). */
    public static void cropGrew(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        Patch p = data(level).patches.get(keyAt(pos));
        if (p != null) {
            // Soybeans fix nitrogen (11.3): they give back half what another crop takes.
            double uptake = Params.uptake / hectares();
            p.fertility = com.cavmkii.seancraft.food.Kitchen.fixesNitrogen(level.getBlockState(pos))
                    ? Math.min(Params.leachAbove, p.fertility + uptake * 0.5) : Math.max(0, p.fertility - uptake);
            markDirty(level);
        }
    }

    /** Harvest yield multiplier for a crop here: soil, pests on wheat, and pollinators from good habitat nearby. */
    public static double yieldMultiplier(ServerLevel level, BlockPos pos, Block crop) {
        if (!enabled()) {
            return 1;
        }
        Patch p = update(level, pos);
        double best = bestQualityNear(level, keyAt(pos), 2);
        double pollinators = best >= 80 ? 0.25 : best >= 60 ? 0.15 : 0;
        double pests = crop == Blocks.WHEAT ? 1 - Model.pestLoss(p) : 1;
        return (0.4 + 0.6 * Model.soilFactor(p)) * pests * (1 + pollinators);
    }

    /** Insecticide (17.7): pests drop, the natural-enemy bonus is off for a week, and the pests build resistance. */
    static void spray(Patch p) {
        p.pest *= 1 - Params.insecticideKill * (1 - p.resistance);
        p.enemiesOffUntil = p.day + Params.enemiesOffDays;
        p.resistance = Math.min(Params.resistanceMax, p.resistance + Params.resistanceStep);
    }

    /** Recomputes a patch's quality, with boss blight (18.6). */
    private static void quality(ServerLevel level, long key, Patch p) {
        Quality.update(p, (dx, dz) -> existing(level, key, dx, dz),
                com.cavmkii.seancraft.world.Blight.patchDistance(level, ChunkPos.getX(key), ChunkPos.getZ(key)));
    }

    // ================================================================== home bases

    /** Built ground inside a home base never changes through succession or grazing (17.8). */
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

    /** Algal scum: a dull green dust. */
    private static final DustParticleOptions ALGAE = new DustParticleOptions(new Vector3f(0.35f, 0.55f, 0.12f), 1.6f);
    /** Butterflies over good ground: small pale wings. */
    private static final DustParticleOptions WINGS = new DustParticleOptions(new Vector3f(0.98f, 0.92f, 0.55f), 0.7f);

    /**
     * Every 2 seconds per player: green water over a polluted patch, butterflies over good ground, the quality band
     * when they walk into a Good or Remnant patch, and what's holding back the crop they look at (17.11).
     */
    private static void signs(ServerLevel level, ServerPlayer player) {
        if (!ready(level, player.chunkPosition().x, player.chunkPosition().z)) {
            return;
        }
        long key = keyAt(player.blockPosition());
        Patch p = data(level).patches.get(key);
        if (p == null || !p.sampled) {
            return;
        }
        RandomSource random = level.getRandom();
        if (p.holdsWater() && p.pollution >= Params.greenAt) {
            int spots = (int) Math.min(30, 10 + p.pollution * 20);
            for (int i = 0; i < spots; i++) {
                BlockPos top = surface(level, player.blockPosition().offset(random.nextInt(25) - 12, 0, random.nextInt(25) - 12));
                if (level.getFluidState(top).is(Fluids.WATER) && level.getBlockState(top.above()).isAir()) {
                    level.sendParticles(ALGAE, top.getX() + 0.5, top.getY() + 0.95, top.getZ() + 0.5, 6, 0.45, 0, 0.45, 0);
                }
            }
        }
        if (p.quality >= 60 && level.isDay()) {
            for (int i = 0; i < 4; i++) {
                BlockPos top = surface(level, player.blockPosition().offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
                if (level.getBlockState(top).is(BlockTags.FLOWERS) || Params.PLANTS.getOrDefault(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).getBlock()).toString(), 0.0) >= 3) {
                    level.sendParticles(WINGS, top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 2, 0.4, 0.3, 0.4, 0.02);
                }
            }
        }
        // Walking into good ground shows its band, the way a biome name would (17.11).
        Long last = LAST_PATCH.put(player.getUUID(), key);
        if (last != null && last != key && p.quality >= 60) {
            Patch before = data(level).patches.get(last);
            if (before == null || !Quality.band(before.quality).equals(Quality.band(p.quality))) {
                ServerPlayNetworking.send(player, new com.cavmkii.seancraft.tarkov.TarkovNet.Banner(Quality.band(p.quality) + " ground",
                        p.quality >= 80 ? "Old prairie: seed comes from here" : "Rich, varied grassland", 0xFF000000 | Quality.color(p.quality)));
            }
        }
        // The crop the player is looking at: what's holding its yield back, and pest resistance (17.7, 17.11).
        HitResult hit = player.pick(5, 0, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockState crop = level.getBlockState(bh.getBlockPos());
            if (crop.getBlock() instanceof CropBlock) {
                boolean wheat = crop.is(Blocks.WHEAT);
                String line = crop.getBlock() instanceof com.cavmkii.seancraft.food.Kitchen.ClimateCrop cc && !cc.inClimate(level, bh.getBlockPos())
                        ? crop.getBlock().getName().getString() + ": won't grow here (needs a " + cc.climate.label + " biome, or fertile mulch)"
                        : crop.getBlock().getName().getString() + ": held back by " + Model.limiting(p, wheat)
                        + (wheat && p.resistance > 0.05 ? String.format(Locale.ROOT, "  (pests %.0f%% resistant)", p.resistance * 100) : "");
                player.displayClientMessage(Component.literal(line).withColor(0xD9CBB0), true);
            }
        }
    }

    private static BlockPos surface(ServerLevel level, BlockPos at) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at).below();
    }

    /** Every 20 seconds per player: rabbits and foxes to match the patch, guano, pest signs, dead fish, fox raids, bare dirt. */
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
        double coverShown = Math.min(1, p.grass * 1.5 + p.shrub);
        sampleAnimals(level, player, EntityType.RABBIT, Rabbit.class, (int) Math.round(Params.rabbitCap * p.game * coverShown));
        // Foxes have no population of their own: they follow the rabbits a few days behind (17.4).
        sampleAnimals(level, player, EntityType.FOX, Fox.class, (int) Math.round(Params.foxCap * Math.max(0, p.gameLag - 0.2) * coverShown));
        // Guano under resting bats (17.7).
        for (net.minecraft.world.entity.ambient.Bat bat : level.getEntitiesOfClass(net.minecraft.world.entity.ambient.Bat.class, player.getBoundingBox().inflate(48))) {
            if (bat.isResting() && random.nextInt(8) == 0) {
                level.addFreshEntity(new ItemEntity(level, bat.getX(), bat.getY() - 0.2, bat.getZ(), new ItemStack(EcoContent.BAT_GUANO)));
            }
        }
        // Aphids on wheat show as specks over the crop.
        if (p.wheat > 0 && p.pest > 0.15) {
            for (int i = 0; i < 20; i++) {
                BlockPos c = at.offset(random.nextInt(25) - 12, random.nextInt(7) - 3, random.nextInt(25) - 12);
                if (level.getBlockState(c).is(Blocks.WHEAT)) {
                    level.sendParticles(ParticleTypes.MYCELIUM, c.getX() + 0.5, c.getY() + 0.6, c.getZ() + 0.5, (int) Math.min(12, p.pest * 15), 0.3, 0.2, 0.3, 0);
                }
            }
        }
        // A fish kill: dead fish floating (17.6).
        if (p.holdsWater() && p.pollution > Params.deadAt && p.fish > 0.05) {
            int dead = 1 + random.nextInt(3);
            for (int i = 0; i < 24 && dead > 0; i++) {
                BlockPos top = surface(level, at.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16));
                if (!level.getFluidState(top).is(Fluids.WATER) || !level.getBlockState(top.above()).isAir()) {
                    continue;
                }
                dead--;
                ItemEntity fish = new ItemEntity(level, top.getX() + 0.5, top.getY() + 0.9, top.getZ() + 0.5, new ItemStack(Items.COD));
                fish.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Dead fish"));
                fish.setNeverPickUp();
                ((com.cavmkii.seancraft.mixin.ItemEntityAgeAccessor) fish).seancraft$setAge(6000 - 1200);
                level.addFreshEntity(fish);
            }
        }
        foxRaid(level, player, p);
        // Overgrazed grass turns to dirt (17.11), never inside a home base.
        if (p.forage < 0.15) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16)).below();
            if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) && !protectedGround(level, top)) {
                level.setBlockAndUpdate(top, Blocks.DIRT.defaultBlockState());
            }
        }
    }

    /**
     * Fox raids on dense pens (17.4): at night, where foxes are about, a crowded pen with no cover and no fence ring
     * loses a few animals. The fix is the ecological one: cover, fencing, room.
     */
    private static void foxRaid(ServerLevel level, ServerPlayer player, Patch p) {
        RandomSource random = level.getRandom();
        if (level.isDay() || p.gameLag < 0.35 || p.shrub + p.forest >= 0.1 || random.nextInt(30) != 0) {
            return;
        }
        List<Animal> stock = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(32, 8, 32),
                a -> a instanceof Chicken || a instanceof Sheep || a instanceof Pig || a instanceof Cow);
        for (Animal a : stock) {
            List<Animal> crowd = level.getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(3),
                    b -> b instanceof Chicken || b instanceof Sheep || b instanceof Pig || b instanceof Cow);
            if (crowd.size() < 6) {
                continue;
            }
            int fences = 0;
            for (BlockPos b : BlockPos.betweenClosed(a.blockPosition().offset(-6, -1, -6), a.blockPosition().offset(6, 1, 6))) {
                if (level.getBlockState(b).is(BlockTags.FENCES) || level.getBlockState(b).is(BlockTags.WALLS)) {
                    fences++;
                }
            }
            if (fences >= 16) {
                return; // a fenced pen keeps them out
            }
            crowd.sort(java.util.Comparator.comparingDouble(Animal::getMaxHealth));
            Fox fox = EntityType.FOX.create(level);
            if (fox != null) {
                fox.moveTo(a.getX() + 4, a.getY(), a.getZ() + 4, random.nextFloat() * 360, 0);
                fox.addTag(SAMPLE_TAG);
                level.addFreshEntity(fox);
            }
            int killed = 2 + random.nextInt(2);
            for (int i = 0; i < killed && i < crowd.size(); i++) {
                crowd.get(i).hurt(fox != null ? level.damageSources().mobAttack(fox) : level.damageSources().generic(), 10000);
            }
            return;
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
            if (!ready(level, c.getX() >> 4, c.getZ() >> 4)) {
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
            Patch p = update(level, child.blockPosition());
            p.game = Math.min(1, p.game + Params.gamePerKill * 0.5);
            markDirty(level);
        }
    }
}
