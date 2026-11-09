package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * A few fixed spawn points in the overworld, each with a small shelter and supply chests. New players and players
 * who die without a bed or camp start at a random one. Each sits on land, and no hostiles spawn around it.
 * Empty chests are restocked every so often.
 */
public final class SpawnPoints extends SavedData {
    private static final String NAME = "seancraft_spawns";

    public final List<BlockPos> points = new ArrayList<>();
    public final List<BlockPos> chests = new ArrayList<>();
    public boolean built;
    public long lastRefill;

    public static SpawnPoints get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SpawnPoints::new, SpawnPoints::load, null), NAME);
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (Cfg.spawnPoints > 0) {
                build(server.overworld());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SpawnPoints::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && newPlayer.getRespawnPosition() == null) {
                sendToSpawn(newPlayer);
            }
        });
    }

    /** True inside a spawn point's safe area (overworld only). */
    public static boolean safe(ServerLevel level, Vec3 pos) {
        if (Cfg.spawnPoints <= 0 || level != level.getServer().overworld()) {
            return false;
        }
        double r = Cfg.spawnRadius;
        for (BlockPos p : get(level).points) {
            double dx = p.getX() + 0.5 - pos.x;
            double dz = p.getZ() + 0.5 - pos.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(p.getY() - pos.y) < 32) {
                return true;
            }
        }
        return false;
    }

    /** Puts a player at a random spawn point. */
    public static void sendToSpawn(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        SpawnPoints data = get(overworld);
        if (data.points.isEmpty()) {
            return;
        }
        int i = player.getRandom().nextInt(data.points.size());
        BlockPos p = data.points.get(i);
        player.teleportTo(overworld, p.getX() + 0.5, p.getY(), p.getZ() + 1.5, player.getYRot(), 0);
        player.sendSystemMessage(Component.literal("Spawn point " + (i + 1) + " (" + p.getX() + ", " + p.getZ()
                + "). Safe here: no hostile spawns near it.").withStyle(ChatFormatting.GRAY));
    }

    // ================================================================== building

    /**
     * Admin: rebuilds the spawn points other than world spawn at the configured distance, on land; world spawn's point
     * moves too (with the world spawn) if it's at sea. New shelters are built; the old ones stay as plain buildings
     * and stop restocking.
     */
    public static String respread(ServerLevel level) {
        SpawnPoints data = get(level);
        if (data.points.isEmpty()) {
            return "This world has no spawn points.";
        }
        BlockPos center = data.points.get(0);
        // Point 1 (world spawn) moves too if it's out at sea, and the world spawn with it.
        boolean moveCenter = atSea(level, center);
        for (BlockPos old : data.points.subList(moveCenter ? 0 : 1, data.points.size())) {
            data.chests.removeIf(c -> c.closerThan(old, 10));
        }
        int extra = data.points.size() - 1;
        data.points.subList(1, data.points.size()).clear();
        if (moveCenter) {
            BlockPos land = findLand(level, center.getX(), center.getZ(), 512, null, 0);
            center = floorAt(level, land.getX(), land.getZ());
            level.setDefaultSpawnPos(center, 0);
            shelter(level, center, data.chests);
            data.points.set(0, center);
        }
        StringBuilder out = new StringBuilder("Spawn points now:");
        out.append(" ").append(center.toShortString());
        for (int i = 0; i < extra; i++) {
            double angle = i * Math.PI * 2 / Math.max(1, extra) + level.random.nextDouble() * 0.6;
            BlockPos land = findLand(level, center.getX() + (int) (Math.cos(angle) * Cfg.spawnDistance),
                    center.getZ() + (int) (Math.sin(angle) * Cfg.spawnDistance), (int) (Cfg.spawnDistance * 0.6), center, Cfg.spawnDistance / 2);
            BlockPos floor = floorAt(level, land.getX(), land.getZ());
            shelter(level, floor, data.chests);
            data.points.add(floor);
            out.append(", ").append(floor.toShortString());
        }
        data.setDirty();
        return out.toString();
    }

    private static void build(ServerLevel level) {
        SpawnPoints data = get(level);
        if (data.built) {
            return;
        }
        if (level.getGameTime() > 6000) {
            // An existing world: don't clear space for shelters where people may have built.
            data.built = true;
            data.setDirty();
            SeanCraft.LOG.info("Existing world: no spawn points built (they're only made in new worlds).");
            return;
        }
        // Spawn points go on land: world spawn is moved to land if it isn't (vanilla can pick a beach or a sliver),
        // and each other point takes the nearest land to its spot 400 blocks out.
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos center = findLand(level, spawn.getX(), spawn.getZ(), 512, null, 0);
        if (center.getX() != spawn.getX() || center.getZ() != spawn.getZ()) {
            level.setDefaultSpawnPos(center, 0);
        }
        for (int i = 0; i < Cfg.spawnPoints; i++) {
            BlockPos at = center;
            if (i > 0) {
                double angle = (i - 1) * Math.PI * 2 / Math.max(1, Cfg.spawnPoints - 1) + level.random.nextDouble() * 0.6;
                at = findLand(level, center.getX() + (int) (Math.cos(angle) * Cfg.spawnDistance),
                        center.getZ() + (int) (Math.sin(angle) * Cfg.spawnDistance), (int) (Cfg.spawnDistance * 0.6), center,
                        Cfg.spawnDistance / 2);
            }
            BlockPos floor = floorAt(level, at.getX(), at.getZ());
            shelter(level, floor, data.chests);
            data.points.add(floor);
            SeanCraft.LOG.info("Built spawn point {} at {}", i + 1, floor.toShortString());
        }
        data.built = true;
        data.lastRefill = level.getGameTime();
        data.setDirty();
    }

    /** True if most of the surface just outside a shelter (8 blocks out, four sides) is water. */
    private static boolean atSea(ServerLevel level, BlockPos p) {
        int wet = 0;
        for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            if (!level.getFluidState(floorAt(level, p.getX() + d[0], p.getZ() + d[1]).below()).isEmpty()) {
                wet++;
            }
        }
        return wet >= 3;
    }

    /** The standing level for a shelter at a column (loads that chunk; only used when building). */
    private static BlockPos floorAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel());
        return new BlockPos(x, y, z);
    }

    /**
     * The nearest dry land to a spot, within {@code maxR} blocks, read from the world's own generator (so any biome
     * or terrain mod is respected) without generating chunks: terrain above sea level and not an ocean or river
     * biome. Rings every 24 blocks outward. Falls back to the spot itself if there's no land in reach.
     */
    static BlockPos findLand(ServerLevel level, int x, int z, int maxR, BlockPos awayFrom, double minDist) {
        int sea = level.getSeaLevel();
        int checked = 0;
        for (int r = 0; r <= maxR; r += 24) {
            int steps = r == 0 ? 1 : Math.max(8, (int) (Math.PI * 2 * r / 24));
            double offset = level.random.nextDouble();
            for (int k = 0; k < steps; k++) {
                double a = (k + offset) * Math.PI * 2 / steps;
                int cx = x + (int) Math.round(Math.cos(a) * r);
                int cz = z + (int) Math.round(Math.sin(a) * r);
                if (awayFrom != null && Math.hypot(cx - awayFrom.getX(), cz - awayFrom.getZ()) < minDist) {
                    continue;
                }
                int h = landHeight(level, cx, cz);
                if (h < 0) {
                    continue;
                }
                // Not a sliver or a lone island: most of the ground 48 blocks around has to be land too.
                int around = 0;
                for (int j = 0; j < 8; j++) {
                    double b = j * Math.PI / 4;
                    if (landHeight(level, cx + (int) (Math.cos(b) * 48), cz + (int) (Math.sin(b) * 48)) >= 0) {
                        around++;
                    }
                }
                if (around < 6) {
                    continue;
                }
                // The generator predicts terrain for chunks not made yet; already-made chunks (an older world, or a
                // different terrain mod back then) may differ, so check the real blocks too.
                if (checked++ >= 40) {
                    break;
                }
                BlockPos real = floorAt(level, cx, cz);
                if (!level.getFluidState(real.below()).isEmpty() || real.getY() <= sea) {
                    continue;
                }
                return real;
            }
        }
        if (maxR < 1500) {
            return findLand(level, x, z, maxR * 3, awayFrom, minDist);
        }
        SeanCraft.LOG.warn("No land within {} blocks of {}, {}; the spawn point goes there anyway.", maxR, x, z);
        return new BlockPos(x, sea, z);
    }

    /** Surface height at a column if it's dry land (above sea level, not an ocean or river biome), else -1. */
    private static int landHeight(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        var random = source.randomState();
        int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        if (h <= level.getSeaLevel() + 1) {
            return -1;
        }
        var biome = generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
                net.minecraft.core.QuartPos.fromBlock(h), net.minecraft.core.QuartPos.fromBlock(z), random.sampler());
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_OCEAN)
                || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_RIVER)) {
            return -1;
        }
        return h;
    }

    /** A 7x7 roofed platform with three supply chests, a barrel and a lantern. {@code p} is the standing level. */
    private static void shelter(ServerLevel level, BlockPos p, List<BlockPos> chests) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(p.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(p.offset(dx, -1, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                // Prop it up on slopes and water.
                for (int dy = -2; dy >= -8; dy--) {
                    BlockPos below = p.offset(dx, dy, dz);
                    if (!level.getBlockState(below).canBeReplaced() && level.getFluidState(below).isEmpty()) {
                        break;
                    }
                    level.setBlock(below, Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
                level.setBlock(p.offset(dx, 3, dz), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            }
        }
        for (int cx : new int[] {-3, 3}) {
            for (int cz : new int[] {-3, 3}) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(p.offset(cx, dy, cz), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
                }
            }
        }
        for (int cx : new int[] {-2, 0, 2}) {
            BlockPos c = p.offset(cx, 0, -2);
            level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
            RandomizableContainer.setBlockEntityLootTable(level, level.random, c, TarkovLoot.SPAWN_CACHE);
            chests.add(c);
        }
        BlockPos barrel = p.offset(2, 0, 2);
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 2);
        RandomizableContainer.setBlockEntityLootTable(level, level.random, barrel, TarkovLoot.SPAWN_CACHE);
        chests.add(barrel);
        level.setBlock(p.offset(0, 2, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    // ================================================================== restocking

    private static void tick(MinecraftServer server) {
        if (Cfg.spawnPoints <= 0 || server.getTickCount() % 1200 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        SpawnPoints data = get(level);
        long now = level.getGameTime();
        if (!data.built || now - data.lastRefill < Cfg.spawnRefillMinutes * 1200) {
            return;
        }
        data.lastRefill = now;
        data.setDirty();
        for (BlockPos c : data.chests) {
            if (level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) != null && level.getBlockEntity(c) instanceof RandomizableContainer box && box.getLootTable() == null
                    && box instanceof Container container && container.isEmpty()) {
                box.setLootTable(TarkovLoot.SPAWN_CACHE, level.random.nextLong());
                container.setChanged();
            }
        }
    }

    // ================================================================== saving

    private static SpawnPoints load(CompoundTag tag, HolderLookup.Provider registries) {
        SpawnPoints data = new SpawnPoints();
        for (long l : tag.getLongArray("points")) {
            data.points.add(BlockPos.of(l));
        }
        for (long l : tag.getLongArray("chests")) {
            data.chests.add(BlockPos.of(l));
        }
        data.built = tag.getBoolean("built");
        data.lastRefill = tag.getLong("lastRefill");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("points", new LongArrayTag(points.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put("chests", new LongArrayTag(chests.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.putBoolean("built", built);
        tag.putLong("lastRefill", lastRefill);
        return tag;
    }
}
