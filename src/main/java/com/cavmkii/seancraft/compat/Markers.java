package com.cavmkii.seancraft.compat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Camps;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.cavmkii.seancraft.tarkov.Module;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Map markers for JourneyMap (design doc 18.1): your home base, your camps and your last three graves. A home base with
 * a Map room shares the whole group's markers with its owner (the Map room's level 1). The server sends the list; the
 * client hands it to JourneyMap if it's installed, and ignores it otherwise.
 */
public final class Markers extends SavedData {
    public static final int HOME = 0, CAMP = 1, GRAVE = 2;
    static final int GRAVES_KEPT = 3;

    public record Marker(String name, int kind, String dimension, long pos) {}

    public record Sync(List<Marker> markers) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SeanCraft.id("markers"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.markers.size());
            for (Marker m : s.markers) {
                buf.writeUtf(m.name);
                buf.writeVarInt(m.kind);
                buf.writeUtf(m.dimension);
                buf.writeLong(m.pos);
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Marker> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                out.add(new Marker(buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readLong()));
            }
            return new Sync(out);
        });

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** Graves by player: dimension and position, newest last. */
    private final Map<UUID, List<Marker>> graves = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private static final Factory<Markers> FACTORY = new Factory<>(Markers::new, Markers::load, null);

    static Markers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "seancraft_markers");
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SENT.remove(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                List<Marker> list = of(server, player);
                Integer last = SENT.put(player.getUUID(), list.hashCode());
                if (last == null || last != list.hashCode()) {
                    ServerPlayNetworking.send(player, new Sync(list));
                }
            }
        });
    }

    /** A grave was placed (from the YAGM hook). */
    public static void grave(ServerLevel level, UUID player, BlockPos pos) {
        Markers m = get(level.getServer());
        List<Marker> list = m.graves.computeIfAbsent(player, k -> new ArrayList<>());
        long day = level.getDayTime() / 24000 + 1;
        list.add(new Marker("Grave (day " + day + ")", GRAVE, level.dimension().location().toString(), pos.asLong()));
        while (list.size() > GRAVES_KEPT) {
            list.remove(0);
        }
        m.setDirty();
    }

    /** One player's markers, plus everyone's if they own a home base with a Map room. */
    static List<Marker> of(MinecraftServer server, ServerPlayer player) {
        boolean shared = false;
        for (ServerLevel level : server.getAllLevels()) {
            for (Hideouts.Hideout h : Hideouts.get(level).list) {
                if (player.getUUID().equals(h.owner) && h.level(Module.MAP_ROOM) >= 1) {
                    shared = true;
                }
            }
        }
        List<Marker> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            for (Hideouts.Hideout h : Hideouts.get(level).list) {
                if (h.owner == null || !shared && !h.owner.equals(player.getUUID())) {
                    continue;
                }
                out.add(new Marker(label(server, player, h.owner, "Home base"), HOME, dim, h.pos.asLong()));
            }
            for (Map.Entry<BlockPos, UUID> e : Camps.get(level).camps.entrySet()) {
                if (e.getValue() == null || !shared && !e.getValue().equals(player.getUUID())) {
                    continue;
                }
                out.add(new Marker(label(server, player, e.getValue(), "Camp"), CAMP, dim, e.getKey().asLong()));
            }
        }
        for (Map.Entry<UUID, List<Marker>> e : get(server).graves.entrySet()) {
            if (!shared && !e.getKey().equals(player.getUUID())) {
                continue;
            }
            for (Marker g : e.getValue()) {
                out.add(new Marker(label(server, player, e.getKey(), g.name), GRAVE, g.dimension, g.pos));
            }
        }
        return out;
    }

    private static String label(MinecraftServer server, ServerPlayer viewer, UUID owner, String what) {
        if (owner.equals(viewer.getUUID())) {
            return what;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        String name = p != null ? p.getGameProfile().getName()
                : server.getProfileCache() == null ? "?" : server.getProfileCache().get(owner).map(g -> g.getName()).orElse("?");
        return name + "'s " + what.substring(0, 1).toLowerCase() + what.substring(1);
    }

    private static Markers load(CompoundTag tag, HolderLookup.Provider registries) {
        Markers m = new Markers();
        CompoundTag graves = tag.getCompound("graves");
        for (String key : graves.getAllKeys()) {
            List<Marker> list = new ArrayList<>();
            for (Tag t : graves.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                list.add(new Marker(c.getString("name"), GRAVE, c.getString("dim"), c.getLong("pos")));
            }
            m.graves.put(UUID.fromString(key), list);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag graves = new CompoundTag();
        this.graves.forEach((id, list) -> {
            ListTag l = new ListTag();
            for (Marker g : list) {
                CompoundTag c = new CompoundTag();
                c.putString("name", g.name);
                c.putString("dim", g.dimension);
                c.putLong("pos", g.pos);
                l.add(c);
            }
            graves.put(id.toString(), l);
        });
        tag.put("graves", graves);
        return tag;
    }
}
