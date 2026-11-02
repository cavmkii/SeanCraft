package com.cavmkii.seancraft.pattern;

import com.cavmkii.seancraft.SeanCraft;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** All crafting patterns (4.8), loaded from data on the server and sent to clients for the recipe books. */
public final class Patterns {
    /** The live list: the server's own, or on the client the copy the server sent. */
    public static volatile List<Pattern> ALL = List.of();

    public record Sync(List<String> ids, List<String> json) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SeanCraft.id("patterns"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::ids,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::json, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Patterns() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SeanCraft.id("patterns");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                List<Pattern> out = new ArrayList<>();
                for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("seancraft_patterns", l -> l.getPath().endsWith(".json")).entrySet()) {
                    String path = e.getKey().getPath();
                    String id = path.substring("seancraft_patterns/".length(), path.length() - 5);
                    try (Reader reader = e.getValue().openAsReader()) {
                        out.add(Pattern.parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch (Exception ex) {
                        SeanCraft.LOG.error("Couldn't read pattern {}: {}", e.getKey(), ex.toString());
                    }
                }
                out.sort(java.util.Comparator.comparing(p -> p.id));
                ALL = List.copyOf(out);
                SeanCraft.LOG.info("Loaded {} crafting patterns", out.size());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, sync()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> {
            for (var player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, sync());
            }
        });
    }

    static Sync sync() {
        List<String> ids = new ArrayList<>();
        List<String> json = new ArrayList<>();
        for (Pattern p : ALL) {
            ids.add(p.id);
            json.add(p.json);
        }
        return new Sync(ids, json);
    }

    /** Client: the server's patterns. */
    public static void receive(Sync sync) {
        List<Pattern> out = new ArrayList<>();
        for (int i = 0; i < sync.ids().size(); i++) {
            out.add(Pattern.parse(sync.ids().get(i), JsonParser.parseString(sync.json().get(i)).getAsJsonObject()));
        }
        ALL = List.copyOf(out);
    }

    public static List<Pattern> station(String station) {
        List<Pattern> out = new ArrayList<>();
        for (Pattern p : ALL) {
            if (p.station.equals(station)) {
                out.add(p);
            }
        }
        return out;
    }
}
