package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.SeanCraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TarkovNet {
    private TarkovNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(SeanCraft.id(name));
    }

    /** Server to client once a second: 0 unknown, 1 hideout, 2 raid, 3 spawn safe zone; raid time, threat, seconds to next threat (-1 none). */
    /** {@code band} is a {@link Distance} ordinal; {@code fobLeft} the raid's FOB seconds left, or -1 outside a raid. */
    public record RaidState(int state, int raidSeconds, int threat, int nextThreat, int band, int fobLeft, boolean fobPaused)
            implements CustomPacketPayload {
        public static final Type<RaidState> TYPE = payloadType("raid_state");
        public static final StreamCodec<FriendlyByteBuf, RaidState> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.state);
            buf.writeVarInt(p.raidSeconds);
            buf.writeVarInt(p.threat);
            buf.writeVarInt(p.nextThreat + 1);
            buf.writeVarInt(p.band);
            buf.writeVarInt(p.fobLeft + 1);
            buf.writeBoolean(p.fobPaused);
        }, buf -> new RaidState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(),
                buf.readVarInt() - 1, buf.readBoolean()));
        @Override public Type<RaidState> type() { return TYPE; }
    }

    /**
     * One cost on the Hideout screen. For a recipe, {@code have} is what you and the stash hold. For a module,
     * {@code have} is what's been contributed so far and {@code carry} what you could still put in. {@code track} is
     * what the gather list follows: an item id, or {@code #tag} for things like any planks.
     */
    public record CostLine(String label, String icon, int have, int need, int carry, String track) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(label);
            buf.writeUtf(icon);
            buf.writeVarInt(have);
            buf.writeVarInt(need);
            buf.writeVarInt(carry);
            buf.writeUtf(track);
        }

        static CostLine read(FriendlyByteBuf buf) {
            return new CostLine(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }
    }

    /** One row on the Hideout screen: a module's next level or a recipe. */
    public record Entry(int kind, String id, String title, String subtitle, String icon, int status, String note,
            List<CostLine> costs, int progress) {
        public static final int MODULE = 0;
        public static final int RECIPE = 1;
        public static final int READY = 0;
        public static final int LOCKED = 1;
        public static final int MAXED = 2;
        public static final int BUILDING = 3;
        public static final int SHORT = 4;
        public static final int BUSY = 5;
        /** Not everything is in yet, but you have some of what's missing: CONTRIBUTE. */
        public static final int GIVE = 6;

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeUtf(id);
            buf.writeUtf(title);
            buf.writeUtf(subtitle);
            buf.writeUtf(icon);
            buf.writeVarInt(status);
            buf.writeUtf(note);
            buf.writeVarInt(costs.size());
            costs.forEach(c -> c.write(buf));
            buf.writeVarInt(progress);
        }

        static Entry read(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            String id = buf.readUtf();
            String title = buf.readUtf();
            String subtitle = buf.readUtf();
            String icon = buf.readUtf();
            int status = buf.readVarInt();
            String note = buf.readUtf();
            int n = buf.readVarInt();
            List<CostLine> costs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                costs.add(CostLine.read(buf));
            }
            return new Entry(kind, id, title, subtitle, icon, status, note, costs, buf.readVarInt());
        }
    }

    /** Server to client: open or refresh the Hideout screen. */
    /** {@code station} is which station's screen this is: "hideout", or a module's kind (Stations.moduleKind). */
    public record HideoutView(BlockPos pos, int radius, String summary, List<Entry> entries, String station) implements CustomPacketPayload {
        public static final Type<HideoutView> TYPE = payloadType("hideout_view");
        public static final StreamCodec<FriendlyByteBuf, HideoutView> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos);
            buf.writeVarInt(p.radius);
            buf.writeUtf(p.summary);
            buf.writeVarInt(p.entries.size());
            p.entries.forEach(e -> e.write(buf));
            buf.writeUtf(p.station);
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            int radius = buf.readVarInt();
            String summary = buf.readUtf();
            int n = buf.readVarInt();
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                entries.add(Entry.read(buf));
            }
            return new HideoutView(pos, radius, summary, entries, buf.readUtf());
        });
        @Override public Type<HideoutView> type() { return TYPE; }
    }

    /**
     * Server to client: the stations in the hideout you're in, one of each kind ("hideout", "weapon_bench",
     * "crafting_table"), for the tabs along the top of their screens.
     */
    public record StationList(List<String> kinds, List<BlockPos> positions) implements CustomPacketPayload {
        public static final Type<StationList> TYPE = payloadType("stations");
        public static final StreamCodec<FriendlyByteBuf, StationList> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.kinds.size());
            for (int i = 0; i < p.kinds.size(); i++) {
                buf.writeUtf(p.kinds.get(i));
                buf.writeBlockPos(p.positions.get(i));
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<String> kinds = new ArrayList<>();
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                kinds.add(buf.readUtf());
                positions.add(buf.readBlockPos());
            }
            return new StationList(kinds, positions);
        });
        @Override public Type<StationList> type() { return TYPE; }
    }

    /** Client to server: open the station at a position (from a tab), if it's in your hideout. */
    public record OpenStation(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenStation> TYPE = payloadType("open_station");
        public static final StreamCodec<FriendlyByteBuf, OpenStation> CODEC =
                StreamCodec.of((buf, p) -> buf.writeBlockPos(p.pos), buf -> new OpenStation(buf.readBlockPos()));
        @Override public Type<OpenStation> type() { return TYPE; }
    }

    /** Client to server: build a module or craft a recipe at a bench (kind -1 just refreshes). */
    public record HideoutAction(BlockPos pos, int kind, String id) implements CustomPacketPayload {
        public static final Type<HideoutAction> TYPE = payloadType("hideout_action");
        public static final StreamCodec<FriendlyByteBuf, HideoutAction> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos);
            buf.writeVarInt(p.kind + 1);
            buf.writeUtf(p.id);
        }, buf -> new HideoutAction(buf.readBlockPos(), buf.readVarInt() - 1, buf.readUtf()));
        @Override public Type<HideoutAction> type() { return TYPE; }
    }

    /** Client to server: cure a status effect with a carried item (index into Tetris.carried). */
    public record CureEffect(String effect, int slot) implements CustomPacketPayload {
        public static final Type<CureEffect> TYPE = payloadType("cure_effect");
        public static final StreamCodec<FriendlyByteBuf, CureEffect> CODEC =
                StreamCodec.of((buf, p) -> { buf.writeUtf(p.effect); buf.writeVarInt(p.slot); }, buf -> new CureEffect(buf.readUtf(), buf.readVarInt()));
        @Override public Type<CureEffect> type() { return TYPE; }
    }

    /** Server to client: a short banner at the top of the screen (raid started, extracted, threat). */
    public record Banner(String title, String subtitle, int color) implements CustomPacketPayload {
        public static final Type<Banner> TYPE = payloadType("banner");
        public static final StreamCodec<FriendlyByteBuf, Banner> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.title); buf.writeUtf(p.subtitle); buf.writeInt(p.color); },
                buf -> new Banner(buf.readUtf(), buf.readUtf(), buf.readInt()));
        @Override public Type<Banner> type() { return TYPE; }
    }

    /** Server to client: body-part health, conditions and painkiller time. */
    public record BodySync(float[] body, int[] conditions, int painkillerTicks, float[] bleed, float[] fracture, float[] scar)
            implements CustomPacketPayload {
        public static final Type<BodySync> TYPE = payloadType("body_sync");
        public static final StreamCodec<FriendlyByteBuf, BodySync> CODEC = StreamCodec.of((buf, p) -> {
            for (int i = 0; i < 7; i++) {
                buf.writeFloat(p.body[i]);
                buf.writeVarInt(p.conditions[i]);
                buf.writeFloat(p.bleed[i]);
                buf.writeFloat(p.fracture[i]);
                buf.writeFloat(p.scar[i]);
            }
            buf.writeVarInt(p.painkillerTicks);
        }, buf -> {
            float[] body = new float[7];
            int[] conditions = new int[7];
            float[] bleed = new float[7];
            float[] fracture = new float[7];
            float[] scar = new float[7];
            for (int i = 0; i < 7; i++) {
                body[i] = buf.readFloat();
                conditions[i] = buf.readVarInt();
                bleed[i] = buf.readFloat();
                fracture[i] = buf.readFloat();
                scar[i] = buf.readFloat();
            }
            return new BodySync(body, conditions, buf.readVarInt(), bleed, fracture, scar);
        });
        @Override public Type<BodySync> type() { return TYPE; }

        public static BodySync of(TarkovData d) {
            return new BodySync(d.body.clone(), d.conditions.clone(), d.painkillerTicks, d.bleed.clone(), d.fracture.clone(), d.scar.clone());
        }

        public Injury injury() {
            return Injury.of(body, fracture, scar, bleed, painkillerTicks);
        }
    }

    /** Client to server: use the med in an inventory slot on a body part. */
    public record TreatPart(int slot, int part) implements CustomPacketPayload {
        public static final Type<TreatPart> TYPE = payloadType("treat_part");
        public static final StreamCodec<FriendlyByteBuf, TreatPart> CODEC =
                StreamCodec.of((buf, p) -> { buf.writeVarInt(p.slot); buf.writeVarInt(p.part); }, buf -> new TreatPart(buf.readVarInt(), buf.readVarInt()));
        @Override public Type<TreatPart> type() { return TYPE; }
    }

    static void register() {
        PayloadTypeRegistry.playS2C().register(BodySync.TYPE, BodySync.CODEC);
        PayloadTypeRegistry.playS2C().register(Banner.TYPE, Banner.CODEC);
        PayloadTypeRegistry.playC2S().register(TreatPart.TYPE, TreatPart.CODEC);
        PayloadTypeRegistry.playC2S().register(CureEffect.TYPE, CureEffect.CODEC);
        PayloadTypeRegistry.playS2C().register(RaidState.TYPE, RaidState.CODEC);
        PayloadTypeRegistry.playS2C().register(HideoutView.TYPE, HideoutView.CODEC);
        PayloadTypeRegistry.playC2S().register(HideoutAction.TYPE, HideoutAction.CODEC);
        PayloadTypeRegistry.playS2C().register(StationList.TYPE, StationList.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenStation.TYPE, OpenStation.CODEC);
    }
}
