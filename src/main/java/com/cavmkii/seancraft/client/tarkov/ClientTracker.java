package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tetris.TetrisNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The gather tracker: items and how many you want, shown top-left while you're out of the hideout. Saved on the
 * server with your inventory; this side edits it and sends the whole list back.
 */
public final class ClientTracker {
    public static final int MAX = 6;

    /** {@code key} is an item id, or {@code #tag} when any of a kind counts (hideout costs like planks). */
    public record Entry(String key, Item item, TagKey<Item> tag, int goal) {
        public String name() {
            if (tag == null) {
                return new ItemStack(item).getHoverName().getString();
            }
            String path = tag.location().getPath();
            return "Any " + path.replace('_', ' ');
        }

        boolean matches(ItemStack s) {
            return tag != null ? s.is(tag) : s.is(item);
        }
    }

    /** The list as last sent, until the server's sync catches up, so several adds in a row don't undo each other. */
    private static List<String> sent;

    private ClientTracker() {}

    private static List<String> raw() {
        List<String> synced = ClientTetris.sync.tracked();
        if (sent != null && sent.equals(synced)) {
            sent = null;
        }
        return sent != null ? sent : synced;
    }

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (String s : raw()) {
            int eq = s.lastIndexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = s.substring(0, eq);
            boolean isTag = key.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(isTag ? key.substring(1) : key);
            TagKey<Item> tag = isTag && id != null ? TagKey.create(Registries.ITEM, id) : null;
            Item item = id == null ? Items.AIR : tag != null ? icon(tag) : BuiltInRegistries.ITEM.get(id);
            try {
                if (item != Items.AIR) {
                    out.add(new Entry(key, item, tag, Math.max(1, Integer.parseInt(s.substring(eq + 1)))));
                }
            } catch (NumberFormatException ignored) {
                // A bad entry: skip it.
            }
        }
        return out;
    }

    /** Something from the tag to draw: the oak one when there is one, else the first. */
    private static Item icon(TagKey<Item> tag) {
        Item first = Items.BARRIER;
        for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            String path = BuiltInRegistries.ITEM.getKey(h.value()).getPath();
            if (path.startsWith("oak_")) {
                return h.value();
            }
            if (first == Items.BARRIER) {
                first = h.value();
            }
        }
        return first;
    }

    private static void send(List<Entry> list) {
        List<String> out = new ArrayList<>();
        for (Entry e : list) {
            out.add(e.key + "=" + e.goal);
        }
        sent = out;
        ClientPlayNetworking.send(new TetrisNet.Track(out));
    }

    /** Adds to the goal if it's already tracked, else adds a new line (oldest dropped past the limit). */
    public static void add(Item item, int amount) {
        add(BuiltInRegistries.ITEM.getKey(item).toString(), amount);
    }

    /** By key: an item id or {@code #tag}. */
    public static void add(String key, int amount) {
        List<Entry> list = entries();
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e.key.equals(key)) {
                list.set(i, new Entry(key, e.item, e.tag, e.goal + amount));
                send(list);
                return;
            }
        }
        list.add(new Entry(key, Items.AIR, null, Math.max(1, amount)));
        while (list.size() > MAX) {
            list.remove(0);
        }
        send(list);
    }

    public static void change(int index, int delta) {
        List<Entry> list = entries();
        if (index >= 0 && index < list.size()) {
            Entry e = list.get(index);
            list.set(index, new Entry(e.key, e.item, e.tag, Math.max(1, e.goal + delta)));
            send(list);
        }
    }

    public static void remove(int index) {
        List<Entry> list = entries();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            send(list);
        }
    }

    /** How many you carry (hotbar, off-hand and grids). */
    public static int have(Entry e) {
        int n = 0;
        for (ItemStack s : ClientTetris.carried()) {
            if (e.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Top-left while you're out (raid or spawn), not in the hideout. */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || ClientTarkov.raid.state() == 1) {
            return;
        }
        List<Entry> list = entries();
        if (list.isEmpty()) {
            return;
        }
        // Top left, on the kit's HUD panel (C.5).
        int x = 11;
        int y = 9;
        Kit.hudPanel(g, 6, 6, 136, 13 + list.size() * 12);
        Kit.text(g, mc.font, "GATHER", x, y, Kit.GOLD_LIGHT);
        y += 11;
        for (Entry e : list) {
            int have = have(e);
            boolean done = have >= e.goal;
            g.pose().pushPose();
            g.pose().translate(x, y - 1, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.renderItem(new ItemStack(e.item), 0, 0);
            g.pose().popPose();
            String name = mc.font.plainSubstrByWidth(e.name(), 70);
            Kit.text(g, mc.font, name, x + 13, y, done ? Kit.MOSS : Kit.BONE);
            String count = Math.min(have, 9999) + "/" + e.goal;
            Kit.text(g, mc.font, count, x + 126 - mc.font.width(count), y, done ? Kit.MOSS : Kit.BONE);
            y += 12;
        }
    }
}
