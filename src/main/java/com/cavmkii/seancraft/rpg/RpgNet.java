package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.SeanCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Packets. The server owns all character data; the client only displays it and asks for changes. */
public final class RpgNet {
    private RpgNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(SeanCraft.id(name));
    }

    /** Server to client: the whole character (sent on login and whenever it changes). */
    public record Sync(PlayerData data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = payloadType("rpg_sync");
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC =
                StreamCodec.of((buf, p) -> p.data.write(buf), buf -> new Sync(PlayerData.read(buf)));
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** Server to client, several times a second: resources and cooldowns. */
    public record Resources(float mana, float rage, float stamina, int[] cooldowns, int[] cooldownTotals) implements CustomPacketPayload {
        public static final Type<Resources> TYPE = payloadType("rpg_resources");
        public static final StreamCodec<FriendlyByteBuf, Resources> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeFloat(p.mana);
            buf.writeFloat(p.rage);
            buf.writeFloat(p.stamina);
            for (int i = 0; i < PlayerData.SLOTS; i++) {
                buf.writeVarInt(p.cooldowns[i]);
                buf.writeVarInt(p.cooldownTotals[i]);
            }
        }, buf -> {
            float mana = buf.readFloat();
            float rage = buf.readFloat();
            float stamina = buf.readFloat();
            int[] cd = new int[PlayerData.SLOTS];
            int[] total = new int[PlayerData.SLOTS];
            for (int i = 0; i < PlayerData.SLOTS; i++) {
                cd[i] = buf.readVarInt();
                total[i] = buf.readVarInt();
            }
            return new Resources(mana, rage, stamina, cd, total);
        });
        @Override public Type<Resources> type() { return TYPE; }
    }

    /** Server to client: a level-up or skill-up popup. */
    public record Toast(String title, String body) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = payloadType("rpg_toast");
        public static final StreamCodec<FriendlyByteBuf, Toast> CODEC =
                StreamCodec.of((buf, p) -> { buf.writeUtf(p.title); buf.writeUtf(p.body); }, buf -> new Toast(buf.readUtf(), buf.readUtf()));
        @Override public Type<Toast> type() { return TYPE; }
    }

    /** Server to client: open the Altar of Regret confirmation. */
    public record OpenRespec(BlockPos pos, int refundPercent, boolean needsOrb, boolean hasOrb, int spent) implements CustomPacketPayload {
        public static final Type<OpenRespec> TYPE = payloadType("rpg_open_respec");
        public static final StreamCodec<FriendlyByteBuf, OpenRespec> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos);
            buf.writeVarInt(p.refundPercent);
            buf.writeBoolean(p.needsOrb);
            buf.writeBoolean(p.hasOrb);
            buf.writeVarInt(p.spent);
        }, buf -> new OpenRespec(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<OpenRespec> type() { return TYPE; }
    }

    /** Client to server: take a web node. */
    public record Allocate(int node) implements CustomPacketPayload {
        public static final Type<Allocate> TYPE = payloadType("rpg_allocate");
        public static final StreamCodec<FriendlyByteBuf, Allocate> CODEC =
                StreamCodec.of((buf, p) -> buf.writeVarInt(p.node), buf -> new Allocate(buf.readVarInt()));
        @Override public Type<Allocate> type() { return TYPE; }
    }

    /** Client to server: cast the skill in a slot. */
    public record Cast(int slot) implements CustomPacketPayload {
        public static final Type<Cast> TYPE = payloadType("rpg_cast");
        public static final StreamCodec<FriendlyByteBuf, Cast> CODEC =
                StreamCodec.of((buf, p) -> buf.writeVarInt(p.slot), buf -> new Cast(buf.readVarInt()));
        @Override public Type<Cast> type() { return TYPE; }
    }

    /** Client to server: put a skill in a slot ("" clears it). */
    public record SetSlot(int slot, String skill) implements CustomPacketPayload {
        public static final Type<SetSlot> TYPE = payloadType("rpg_set_slot");
        public static final StreamCodec<FriendlyByteBuf, SetSlot> CODEC =
                StreamCodec.of((buf, p) -> { buf.writeVarInt(p.slot); buf.writeUtf(p.skill); }, buf -> new SetSlot(buf.readVarInt(), buf.readUtf()));
        @Override public Type<SetSlot> type() { return TYPE; }
    }

    /** Client to server: confirm a respec at an altar. */
    public record Respec(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Respec> TYPE = payloadType("rpg_respec");
        public static final StreamCodec<FriendlyByteBuf, Respec> CODEC =
                StreamCodec.of((buf, p) -> buf.writeBlockPos(p.pos), buf -> new Respec(buf.readBlockPos()));
        @Override public Type<Respec> type() { return TYPE; }
    }

    /** Client to server: pick an origin (6.2.1). */
    public record ChooseOrigin(int origin) implements CustomPacketPayload {
        public static final Type<ChooseOrigin> TYPE = payloadType("rpg_choose_origin");
        public static final StreamCodec<FriendlyByteBuf, ChooseOrigin> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ChooseOrigin::origin, ChooseOrigin::new);
        @Override public Type<ChooseOrigin> type() { return TYPE; }
    }

    /** Client to server: place one attribute point freed by a respec (6.6). */
    public record AssignAttribute(int attr) implements CustomPacketPayload {
        public static final Type<AssignAttribute> TYPE = payloadType("rpg_assign_attribute");
        public static final StreamCodec<FriendlyByteBuf, AssignAttribute> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, AssignAttribute::attr, AssignAttribute::new);
        @Override public Type<AssignAttribute> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ChooseOrigin.TYPE, ChooseOrigin.CODEC);
        PayloadTypeRegistry.playC2S().register(AssignAttribute.TYPE, AssignAttribute.CODEC);
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.playS2C().register(Resources.TYPE, Resources.CODEC);
        PayloadTypeRegistry.playS2C().register(Toast.TYPE, Toast.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenRespec.TYPE, OpenRespec.CODEC);
        PayloadTypeRegistry.playC2S().register(Allocate.TYPE, Allocate.CODEC);
        PayloadTypeRegistry.playC2S().register(Cast.TYPE, Cast.CODEC);
        PayloadTypeRegistry.playC2S().register(SetSlot.TYPE, SetSlot.CODEC);
        PayloadTypeRegistry.playC2S().register(Respec.TYPE, Respec.CODEC);
    }
}
