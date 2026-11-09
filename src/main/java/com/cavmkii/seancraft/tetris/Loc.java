package com.cavmkii.seancraft.tetris;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Where an item is on the INVENTORY tab. HAND: a vanilla inventory slot (0-8 the hotbar, 9-35 the main inventory).
 * EQUIP: 0-3 armor (head to feet), 6 off-hand. CONTAINER: open container (or stash chest) index and grid cell, or -1
 * and a raw slot for overflow. DISCARD: the ground.
 */
public record Loc(int kind, int a, int b, int c) {
    public static final int HAND = 0;
    public static final int EQUIP = 2;
    public static final int CONTAINER = 3;
    public static final int DISCARD = 4;
    /** CRAFTING tab: a cell (a = 0-8) of the crafting grid. */
    public static final int CRAFT = 6;
    /** CRAFTING tab: one entry (a = index) of the merged materials list; as a target, back to your inventory. */
    public static final int MATERIAL = 8;

    public static Loc hand(int slot) { return new Loc(HAND, slot, 0, 0); }
    public static Loc equip(int slot) { return new Loc(EQUIP, slot, 0, 0); }
    public static Loc container(int index, int x, int y) { return new Loc(CONTAINER, index, x, y); }
    public static Loc containerOverflow(int index, int slot) { return new Loc(CONTAINER, index, -1, slot); }
    public static Loc discard() { return new Loc(DISCARD, 0, 0, 0); }
    public static Loc craft(int cell) { return new Loc(CRAFT, cell, 0, 0); }
    public static Loc material(int index) { return new Loc(MATERIAL, index, 0, 0); }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeVarInt(c);
    }

    public static Loc read(FriendlyByteBuf buf) {
        return new Loc(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
