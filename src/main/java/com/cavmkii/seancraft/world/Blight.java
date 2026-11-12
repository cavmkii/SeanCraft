package com.cavmkii.seancraft.world;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.boss.BossSites;
import com.cavmkii.seancraft.ecology.Ecology;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Boss blight (design doc 18.6): while nobody on the server has beaten a boss, the patches within Blight.Radius of its
 * dungeon are blighted. Grass and leaves there turn grey (client tint, no blocks change), the threat counts against
 * ecosystem quality (weight 1.0, halved per patch of distance) and quality is held at Weedy or worse. The first kill
 * lifts it, and the land recovers through the ordinary model.
 */
public final class Blight {
    public record Zones(List<BlockPos> centres, double radius) implements CustomPacketPayload {
        public static final Type<Zones> TYPE = new Type<>(SeanCraft.id("blight"));
        public static final StreamCodec<FriendlyByteBuf, Zones> CODEC = StreamCodec.of((buf, z) -> {
            buf.writeVarInt(z.centres.size());
            z.centres.forEach(buf::writeBlockPos);
            buf.writeDouble(z.radius);
        }, buf -> {
            int n = buf.readVarInt();
            List<BlockPos> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(buf.readBlockPos());
            }
            return new Zones(list, buf.readDouble());
        });

        @Override
        public Type<Zones> type() {
            return TYPE;
        }
    }

    private static volatile List<BlockPos> active = List.of();

    private Blight() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Zones.TYPE, Zones.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            refresh(server);
            ServerPlayNetworking.send(handler.player, zones());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 0 && refresh(server)) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, zones());
                }
            }
        });
    }

    /** Re-reads the undefeated dungeons; true if the list changed. */
    private static boolean refresh(MinecraftServer server) {
        List<BlockPos> next = new ArrayList<>();
        if (Cfg.blightRadius > 0) {
            for (BossSites.Site site : BossSites.get(server.overworld()).sites.values()) {
                if (!site.beaten) {
                    next.add(site.pos);
                }
            }
        }
        if (next.equals(active)) {
            return false;
        }
        active = List.copyOf(next);
        return true;
    }

    private static Zones zones() {
        return new Zones(active, (Cfg.blightRadius + 0.5) * Ecology.patchChunks() * 16);
    }

    /** Patches from a patch to the nearest blighted dungeon's patch (Chebyshev), or -1 outside every blight. */
    public static int patchDistance(ServerLevel level, int px, int pz) {
        if (level.dimension() != Level.OVERWORLD || active.isEmpty()) {
            return -1;
        }
        int size = Ecology.patchChunks() * 16;
        int best = -1;
        for (BlockPos c : active) {
            int d = Math.max(Math.abs(Math.floorDiv(c.getX(), size) - px), Math.abs(Math.floorDiv(c.getZ(), size) - pz));
            if (d <= Cfg.blightRadius && (best < 0 || d < best)) {
                best = d;
            }
        }
        return best;
    }
}
