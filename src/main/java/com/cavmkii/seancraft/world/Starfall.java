package com.cavmkii.seancraft.world;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.ecology.Ecology;
import com.cavmkii.seancraft.tarkov.Hideouts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Starfall (design doc 18.4): rarely, on a clear night, a streak crosses the sky and a star comes down within about
 * 1,000 blocks of someone. Where it lands there's a crater of blackstone, basalt and magma with ancient debris at the
 * bottom. The crater is dug when its ground is next loaded, so nothing generates chunks for it.
 */
public final class Starfall extends SavedData {
    /** Server to client: a streak from {@code from} toward {@code to}, high over the player. */
    public record Streak(Vec3 from, Vec3 to) implements CustomPacketPayload {
        public static final Type<Streak> TYPE = new Type<>(SeanCraft.id("starfall"));
        public static final StreamCodec<FriendlyByteBuf, Streak> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVec3(s.from);
            buf.writeVec3(s.to);
        }, buf -> new Streak(buf.readVec3(), buf.readVec3()));

        @Override
        public Type<Streak> type() {
            return TYPE;
        }
    }

    /** Craters waiting for their ground to load. */
    private final List<BlockPos> pending = new ArrayList<>();
    private long lastNight = -1;

    private static final Factory<Starfall> FACTORY = new Factory<>(Starfall::new, Starfall::load, null);

    static Starfall get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "seancraft_starfall");
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Streak.TYPE, Streak.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) {
                return;
            }
            ServerLevel level = server.overworld();
            Starfall data = get(server);
            // Once a night, about two hours in, if the sky is clear.
            long day = level.getDayTime() / 24000;
            long time = level.getDayTime() % 24000;
            if (time >= 15000 && time < 16000 && data.lastNight != day) {
                data.lastNight = day;
                data.setDirty();
                if (!level.isRaining() && level.random.nextDouble() < Cfg.starfallChance) {
                    fall(level, null);
                }
            }
            data.dig(level);
        });
    }

    /** A star falls near a random player (or {@code near}, for testing). Returns where, or null. */
    public static BlockPos fall(ServerLevel level, ServerPlayer near) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }
        RandomSource random = level.random;
        ServerPlayer player = near != null ? near : players.get(random.nextInt(players.size()));
        // A test drop lands where the player is looking, so the streak crosses their view.
        double angle = near != null ? Math.atan2(near.getLookAngle().z, near.getLookAngle().x) : random.nextDouble() * Math.PI * 2;
        double dist = near != null ? 48 + random.nextDouble() * 32 : 250 + random.nextDouble() * 750;
        BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * dist, 0, player.getZ() + Math.sin(angle) * dist);
        if (Hideouts.at(level, Vec3.atCenterOf(at)) != null) {
            return null;
        }
        Starfall data = get(level.getServer());
        data.pending.add(at);
        data.setDirty();
        for (ServerPlayer p : players) {
            double dx = at.getX() - p.getX();
            double dz = at.getZ() - p.getZ();
            if (dx * dx + dz * dz > 1500 * 1500) {
                continue;
            }
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            // Below the clouds, from overhead to low over the horizon where it came down.
            double cloudRoom = Math.max(20, 180 - p.getY());
            Vec3 from = p.position().add(dir.scale(-40)).add(dir.z * 30, Math.min(60, cloudRoom), -dir.x * 30);
            Vec3 to = p.position().add(dir.scale(110)).add(0, Math.min(12, cloudRoom / 4), 0);
            ServerPlayNetworking.send(p, new Streak(from, to));
            p.displayClientMessage(Component.literal("Something fell from the sky to the " + bearing(dx, dz) + ".").withColor(0xE0BC62), true);
        }
        SeanCraft.LOG.info("Starfall: a crater will be at {}", at.toShortString());
        return at;
    }

    private static String bearing(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return names[(int) Math.floorMod(Math.round(a / 45), 8)];
    }

    /** Digs any crater whose ground (and the chunks around it) are loaded now. */
    private void dig(ServerLevel level) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            BlockPos at = pending.get(i);
            boolean loaded = true;
            for (int dx = -1; dx <= 1 && loaded; dx++) {
                for (int dz = -1; dz <= 1 && loaded; dz++) {
                    loaded = Ecology.ready(level, (at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
                }
            }
            if (loaded) {
                crater(level, at);
                pending.remove(i);
                setDirty();
            }
        }
    }

    /** The nearest dry ground within 24 blocks (loaded chunks only), or the spot itself if there's none. */
    private static BlockPos dry(ServerLevel level, BlockPos at) {
        for (int r = 0; r <= 24; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos p = at.offset(dx, 0, dz);
                    if (!Ecology.ready(level, p.getX() >> 4, p.getZ() >> 4)) {
                        continue;
                    }
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, p);
                    if (level.getFluidState(top.below()).isEmpty() && level.getFluidState(top).isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return at;
    }

    private static void crater(ServerLevel level, BlockPos landing) {
        RandomSource random = level.random;
        BlockPos at = dry(level, landing);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, at).below();
        int r = 5 + random.nextInt(3);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1) {
                    continue;
                }
                BlockPos col = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, ground.offset(dx, 0, dz)).below();
                int depth = d <= r ? (int) Math.round(Math.sqrt(r * r - d * d) * 0.6) : 0;
                int floor = Math.min(col.getY(), ground.getY()) - depth;
                // Clear the bowl, then line it.
                for (int y = floor + 1; y <= col.getY() + 3; y++) {
                    level.setBlock(new BlockPos(col.getX(), y, col.getZ()), Blocks.AIR.defaultBlockState(), 2);
                }
                BlockState lining = d > r ? (random.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE).defaultBlockState()
                        : random.nextFloat() < 0.25f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : random.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                level.setBlock(new BlockPos(col.getX(), floor, col.getZ()), lining, 2);
                if (d <= r && random.nextFloat() < 0.08f) {
                    level.setBlock(new BlockPos(col.getX(), floor + 1, col.getZ()), Wildfire.FLAME.defaultBlockState(), 3);
                }
            }
        }
        // The star itself: ancient debris in an obsidian bed at the bottom.
        BlockPos bottom = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, ground).below();
        level.setBlock(bottom, Blocks.ANCIENT_DEBRIS.defaultBlockState(), 2);
        int extra = random.nextInt(3);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            BlockPos p = bottom.offset(around[i][0], 0, around[i][1]);
            level.setBlock(p, (i < extra ? Blocks.ANCIENT_DEBRIS : Blocks.OBSIDIAN).defaultBlockState(), 2);
            level.setBlock(p.below(), Blocks.OBSIDIAN.defaultBlockState(), 2);
        }
        level.setBlock(bottom.below(), Blocks.OBSIDIAN.defaultBlockState(), 2);
        level.playSound(null, bottom, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6f, 0.5f);
        SeanCraft.LOG.info("Starfall crater at {}", bottom.toShortString());
    }

    private static Starfall load(CompoundTag tag, HolderLookup.Provider registries) {
        Starfall s = new Starfall();
        for (long l : tag.getLongArray("pending")) {
            s.pending.add(BlockPos.of(l));
        }
        s.lastNight = tag.getLong("lastNight");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("pending", pending.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLong("lastNight", lastNight);
        return tag;
    }
}
