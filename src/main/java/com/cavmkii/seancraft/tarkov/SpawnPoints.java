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
 * who die without a bed start at a random one. The area around each is safe: no raid starts until you leave it.
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
                + "). Safe here: the raid starts when you leave.").withStyle(ChatFormatting.GRAY));
    }

    // ================================================================== building

    /**
     * Admin: moves the spawn points other than world spawn out to the configured distance, for worlds made before it
     * changed. New shelters are built; the old ones stay as plain buildings and stop restocking.
     */
    public static String respread(ServerLevel level) {
        SpawnPoints data = get(level);
        if (data.points.isEmpty()) {
            return "This world has no spawn points.";
        }
        BlockPos center = data.points.get(0);
        for (BlockPos old : data.points.subList(1, data.points.size())) {
            data.chests.removeIf(c -> c.closerThan(old, 10));
        }
        int extra = data.points.size() - 1;
        data.points.subList(1, data.points.size()).clear();
        StringBuilder out = new StringBuilder("Spawn points now:");
        out.append(" ").append(center.toShortString());
        for (int i = 0; i < extra; i++) {
            double angle = i * Math.PI * 2 / Math.max(1, extra) + level.random.nextDouble() * 0.6;
            int x = center.getX() + (int) (Math.cos(angle) * Cfg.spawnDistance);
            int z = center.getZ() + (int) (Math.sin(angle) * Cfg.spawnDistance);
            level.getChunk(x >> 4, z >> 4);
            int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel());
            BlockPos floor = new BlockPos(x, y, z);
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
        BlockPos center = level.getSharedSpawnPos();
        for (int i = 0; i < Cfg.spawnPoints; i++) {
            int x = center.getX();
            int z = center.getZ();
            if (i > 0) {
                double angle = (i - 1) * Math.PI * 2 / Math.max(1, Cfg.spawnPoints - 1) + level.random.nextDouble() * 0.6;
                x += (int) (Math.cos(angle) * Cfg.spawnDistance);
                z += (int) (Math.sin(angle) * Cfg.spawnDistance);
            }
            level.getChunk(x >> 4, z >> 4);
            int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel());
            BlockPos floor = new BlockPos(x, y, z);
            shelter(level, floor, data.chests);
            data.points.add(floor);
            SeanCraft.LOG.info("Built spawn point {} at {}", i + 1, floor.toShortString());
        }
        data.built = true;
        data.lastRefill = level.getGameTime();
        data.setDirty();
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
            if (level.isLoaded(c) && level.getBlockEntity(c) instanceof RandomizableContainer box && box.getLootTable() == null
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
