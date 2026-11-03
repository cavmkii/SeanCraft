package com.cavmkii.seancraft.tetris;

import com.cavmkii.seancraft.SeanCraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public final class TetrisNet {
    private TetrisNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(SeanCraft.id(name));
    }

    private static void writeList(RegistryFriendlyByteBuf buf, List<Placed> list) {
        buf.writeVarInt(list.size());
        list.forEach(p -> p.write(buf));
    }

    private static List<Placed> readList(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Placed> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Placed.read(buf));
        }
        return list;
    }

    /**
     * One container shown on the right of GEAR (an opened chest, or one stash chest) as a grid. Overflow: items
     * that had no room in the grid, with their raw slots.
     */
    public record ContainerView(BlockPos pos, String name, int width, int height, List<Placed> items, List<ItemStack> overflow,
            List<Integer> overflowSlots, int filter, ItemStack icon) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(name);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            writeList(buf, items);
            buf.writeVarInt(overflow.size());
            for (int i = 0; i < overflow.size(); i++) {
                ItemStack.STREAM_CODEC.encode(buf, overflow.get(i));
                buf.writeVarInt(overflowSlots.get(i));
            }
            buf.writeVarInt(filter);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon);
        }

        static ContainerView read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf();
            int w = buf.readVarInt();
            int h = buf.readVarInt();
            List<Placed> items = readList(buf);
            int n = buf.readVarInt();
            List<ItemStack> overflow = new ArrayList<>();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                overflow.add(ItemStack.STREAM_CODEC.decode(buf));
                slots.add(buf.readVarInt());
            }
            int filter = buf.readVarInt();
            return new ContainerView(pos, name, w, h, items, overflow, slots, filter, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
    }

    /** Server to client: the whole Tetris inventory and whatever container or stash is in reach. */
    public record Sync(ItemStack rig, ItemStack backpack, List<Placed> pockets, List<Placed> rigItems, List<Placed> backpackItems,
            List<Placed> pouch, int pouchW, int pouchH, List<ItemStack> unsorted, boolean stash, List<ContainerView> containers,
            boolean autoRefill, boolean refillFromStash, List<String> tracked, boolean opened) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = payloadType("inv_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, p) -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.rig);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.backpack);
            writeList(buf, p.pockets);
            writeList(buf, p.rigItems);
            writeList(buf, p.backpackItems);
            writeList(buf, p.pouch);
            buf.writeVarInt(p.pouchW);
            buf.writeVarInt(p.pouchH);
            buf.writeVarInt(p.unsorted.size());
            p.unsorted.forEach(s -> ItemStack.STREAM_CODEC.encode(buf, s));
            buf.writeBoolean(p.stash);
            buf.writeBoolean(p.autoRefill);
            buf.writeBoolean(p.refillFromStash);
            buf.writeVarInt(p.tracked.size());
            p.tracked.forEach(buf::writeUtf);
            buf.writeVarInt(p.containers.size());
            p.containers.forEach(c -> c.write(buf));
            buf.writeBoolean(p.opened);
        }, buf -> {
            ItemStack rig = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            ItemStack backpack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            List<Placed> pockets = readList(buf);
            List<Placed> rigItems = readList(buf);
            List<Placed> backpackItems = readList(buf);
            List<Placed> pouch = readList(buf);
            int pw = buf.readVarInt();
            int ph = buf.readVarInt();
            int n = buf.readVarInt();
            List<ItemStack> unsorted = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                unsorted.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            boolean stash = buf.readBoolean();
            boolean refill = buf.readBoolean();
            boolean refillStash = buf.readBoolean();
            int t = buf.readVarInt();
            List<String> tracked = new ArrayList<>();
            for (int i = 0; i < t; i++) {
                tracked.add(buf.readUtf());
            }
            int c = buf.readVarInt();
            List<ContainerView> containers = new ArrayList<>();
            for (int i = 0; i < c; i++) {
                containers.add(ContainerView.read(buf));
            }
            boolean opened = buf.readBoolean();
            return new Sync(rig, backpack, pockets, rigItems, backpackItems, pouch, pw, ph, unsorted, stash, containers, refill, refillStash, tracked,
                    opened);
        });
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** Server to client: open the Tarkov screen on a tab. */
    public record Open(int tab) implements CustomPacketPayload {
        public static final Type<Open> TYPE = payloadType("inv_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
                StreamCodec.of((buf, p) -> buf.writeVarInt(p.tab), buf -> new Open(buf.readVarInt()));
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Client to server: move (part of) an item. */
    public record Move(Loc from, Loc to, boolean rotated, int count) implements CustomPacketPayload {
        public static final Type<Move> TYPE = payloadType("inv_move");
        public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.of((buf, p) -> {
            p.from.write(buf);
            p.to.write(buf);
            buf.writeBoolean(p.rotated);
            buf.writeVarInt(p.count);
        }, buf -> new Move(Loc.read(buf), Loc.read(buf), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<Move> type() { return TYPE; }
    }

    /** Client to server: Ctrl+click transfer. */
    public record QuickMove(Loc from) implements CustomPacketPayload {
        public static final Type<QuickMove> TYPE = payloadType("inv_quick");
        public static final StreamCodec<RegistryFriendlyByteBuf, QuickMove> CODEC =
                StreamCodec.of((buf, p) -> p.from.write(buf), buf -> new QuickMove(Loc.read(buf)));
        @Override public Type<QuickMove> type() { return TYPE; }
    }

    /**
     * Client to server: a click on the grid panel shown beside vanilla container screens. Swaps with the menu's
     * cursor item. Button 0 left (whole stack), 1 right (half, or put down one), 2 shift (send into the menu).
     */
    public record PanelClick(Loc loc, boolean rotated, int button) implements CustomPacketPayload {
        public static final Type<PanelClick> TYPE = payloadType("inv_panel");
        public static final StreamCodec<RegistryFriendlyByteBuf, PanelClick> CODEC = StreamCodec.of((buf, p) -> {
            p.loc.write(buf);
            buf.writeBoolean(p.rotated);
            buf.writeVarInt(p.button);
        }, buf -> new PanelClick(Loc.read(buf), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<PanelClick> type() { return TYPE; }
    }

    /** Client to server: the whole gather tracker ("item id=goal" entries), replacing what was there. */
    public record Track(List<String> entries) implements CustomPacketPayload {
        public static final Type<Track> TYPE = payloadType("inv_track");
        public static final StreamCodec<RegistryFriendlyByteBuf, Track> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.entries.size());
            p.entries.forEach(buf::writeUtf);
        }, buf -> {
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(buf.readUtf());
            }
            return new Track(list);
        });
        @Override public Type<Track> type() { return TYPE; }
    }

    /** Client to server: rename a container on the right of GEAR (empty name: back to its default). */
    public record ContainerName(int index, String name) implements CustomPacketPayload {
        public static final Type<ContainerName> TYPE = payloadType("inv_container_name");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContainerName> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.index); buf.writeUtf(p.name, 40); }, buf -> new ContainerName(buf.readVarInt(), buf.readUtf(40)));
        @Override public Type<ContainerName> type() { return TYPE; }
    }

    /** Client to server: a container's own buttons. Action 0 sort, 1 take all, 2 set its storage filter to {@code value}. */
    public record ContainerAction(int index, int action, int value) implements CustomPacketPayload {
        public static final Type<ContainerAction> TYPE = payloadType("inv_container_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContainerAction> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.index); buf.writeVarInt(p.action); buf.writeVarInt(p.value); },
                buf -> new ContainerAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<ContainerAction> type() { return TYPE; }
    }

    /**
     * Client to server: buttons. 0 sort, 1 transfer all to stash, 2 drop all unsorted, 3 close container,
     * 4 screen opened, 5 screen closed, 6 toggle hotbar auto-refill, 7 toggle refilling from the stash, 8 organize the stash by its filters.
     */
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = payloadType("inv_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.of((buf, p) -> buf.writeVarInt(p.action), buf -> new Action(buf.readVarInt()));
        @Override public Type<Action> type() { return TYPE; }
    }

    public record CraftEntry(String id, ItemStack result, int times) {}

    /**
     * Server to client: the CRAFTING tab. Recipes you can make now (the Hideout Bench first when you're outside a
     * hideout), the grid's nine cells and what they make (or that it needs a crafting table), the merged materials
     * you can draw on, whether you're in a hideout, and whether a crafting table is in reach.
     */
    public record CraftList(List<CraftEntry> entries, List<ItemStack> grid, ItemStack result, boolean needsTable,
            List<ItemStack> materials, boolean inHideout, boolean table, String selected) implements CustomPacketPayload {
        public static final Type<CraftList> TYPE = payloadType("craft_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftList> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.entries.size());
            for (CraftEntry e : p.entries) {
                buf.writeUtf(e.id);
                ItemStack.STREAM_CODEC.encode(buf, e.result);
                buf.writeVarInt(e.times);
            }
            for (int i = 0; i < 9; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.grid.get(i));
            }
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.result);
            buf.writeBoolean(p.needsTable);
            buf.writeVarInt(p.materials.size());
            p.materials.forEach(m -> ItemStack.STREAM_CODEC.encode(buf, m));
            buf.writeBoolean(p.inHideout);
            buf.writeBoolean(p.table);
            buf.writeUtf(p.selected);
        }, buf -> {
            int n = buf.readVarInt();
            List<CraftEntry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new CraftEntry(buf.readUtf(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt()));
            }
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                grid.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            ItemStack result = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            boolean needsTable = buf.readBoolean();
            int m = buf.readVarInt();
            List<ItemStack> materials = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                materials.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            return new CraftList(list, grid, result, needsTable, materials, buf.readBoolean(), buf.readBoolean(), buf.readUtf());
        });
        @Override public Type<CraftList> type() { return TYPE; }
    }

    /** Client to server: "" refresh, "#grid" craft from the grid (times), "#clear" empty the grid, or a recipe id to lay out. */
    public record Craft(String id, int times) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = payloadType("craft");
        public static final StreamCodec<RegistryFriendlyByteBuf, Craft> CODEC =
                StreamCodec.of((buf, p) -> { buf.writeUtf(p.id); buf.writeVarInt(p.times); }, buf -> new Craft(buf.readUtf(), buf.readVarInt()));
        @Override public Type<Craft> type() { return TYPE; }
    }

    private static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> list) {
        buf.writeVarInt(list.size());
        list.forEach(st -> {
            ItemStack.STREAM_CODEC.encode(buf, st.copyWithCount(1));
            buf.writeVarInt(st.getCount());
        });
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = ItemStack.STREAM_CODEC.decode(buf);
            st.setCount(buf.readVarInt());
            out.add(st);
        }
        return out;
    }

    /**
     * Server to client, while a furnace is open: fuel and things it can cook, merged with total counts (which can go
     * past a stack), from what you carry and from the stash.
     */
    public record FurnaceStock(boolean stash, List<ItemStack> fuelYou, List<ItemStack> fuelStash, List<ItemStack> smeltYou,
            List<ItemStack> smeltStash) implements CustomPacketPayload {
        public static final Type<FurnaceStock> TYPE = payloadType("furnace_stock");
        public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceStock> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.stash);
            writeStacks(buf, p.fuelYou);
            writeStacks(buf, p.fuelStash);
            writeStacks(buf, p.smeltYou);
            writeStacks(buf, p.smeltStash);
        }, buf -> new FurnaceStock(buf.readBoolean(), readStacks(buf), readStacks(buf), readStacks(buf), readStacks(buf)));
        @Override public Type<FurnaceStock> type() { return TYPE; }
    }

    /**
     * Client to server: put some of an item into the open furnace. {@code stash} picks where it comes from,
     * {@code fuel} the fuel slot (else the input), {@code count} 0 for as much as fits.
     */
    public record FurnaceFill(ItemStack item, boolean stash, boolean fuel, int count) implements CustomPacketPayload {
        public static final Type<FurnaceFill> TYPE = payloadType("furnace_fill");
        public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceFill> CODEC = StreamCodec.of((buf, p) -> {
            ItemStack.STREAM_CODEC.encode(buf, p.item.copyWithCount(1));
            buf.writeBoolean(p.stash);
            buf.writeBoolean(p.fuel);
            buf.writeVarInt(p.count);
        }, buf -> new FurnaceFill(ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<FurnaceFill> type() { return TYPE; }
    }

    /** Server to client: the hideout stash, merged by item, for the STASH panel on station screens. */
    public record StashStock(boolean home, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<StashStock> TYPE = payloadType("stash_stock");
        public static final StreamCodec<RegistryFriendlyByteBuf, StashStock> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.home);
            writeStacks(buf, p.items);
        }, buf -> new StashStock(buf.readBoolean(), readStacks(buf)));
        @Override public Type<StashStock> type() { return TYPE; }
    }

    /**
     * Client to server, on a station screen: take {@code count} of an item from the stash onto the cursor (0 for a full
     * stack), or with an empty item put the cursor's stack away into the stash.
     */
    public record StashTake(ItemStack item, int count) implements CustomPacketPayload {
        public static final Type<StashTake> TYPE = payloadType("stash_take");
        public static final StreamCodec<RegistryFriendlyByteBuf, StashTake> CODEC = StreamCodec.of((buf, p) -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.item.isEmpty() ? ItemStack.EMPTY : p.item.copyWithCount(1));
            buf.writeVarInt(p.count);
        }, buf -> new StashTake(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));
        @Override public Type<StashTake> type() { return TYPE; }
    }

    static void register() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.playS2C().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.playS2C().register(CraftList.TYPE, CraftList.CODEC);
        PayloadTypeRegistry.playC2S().register(Move.TYPE, Move.CODEC);
        PayloadTypeRegistry.playC2S().register(QuickMove.TYPE, QuickMove.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.playC2S().register(Craft.TYPE, Craft.CODEC);
        PayloadTypeRegistry.playC2S().register(PanelClick.TYPE, PanelClick.CODEC);
        PayloadTypeRegistry.playC2S().register(ContainerAction.TYPE, ContainerAction.CODEC);
        PayloadTypeRegistry.playC2S().register(ContainerName.TYPE, ContainerName.CODEC);
        PayloadTypeRegistry.playC2S().register(Track.TYPE, Track.CODEC);
        PayloadTypeRegistry.playS2C().register(FurnaceStock.TYPE, FurnaceStock.CODEC);
        PayloadTypeRegistry.playC2S().register(FurnaceFill.TYPE, FurnaceFill.CODEC);
        PayloadTypeRegistry.playS2C().register(StashStock.TYPE, StashStock.CODEC);
        PayloadTypeRegistry.playC2S().register(StashTake.TYPE, StashTake.CODEC);
    }
}
