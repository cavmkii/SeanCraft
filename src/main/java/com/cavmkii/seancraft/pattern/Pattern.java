package com.cavmkii.seancraft.pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One crafting pattern (design doc 4.8): up to three rows, "." for an empty square, letters standing for items. Shapes
 * can sit anywhere in the 3x3 grid and may be mirrored, as in vanilla. Patterns are data files under
 * {@code data/<ns>/seancraft_patterns/}, so they can be retuned without code.
 *
 * <p>Two stations use them. Weapon Bench part patterns ({@code "station": "part"}) name a part kind and use fixed
 * letters: M metal, W wood, H hide, G gem, C catalyst, S stick; the material placed decides the part's material.
 * Hideout Bench patterns ({@code "station": "hideout"}) map their letters to items or tags and name a result.
 */
public final class Pattern {
    public final String id;
    public final String station;
    public final String[] rows;
    /** Hideout patterns: letter to item id or "#tag". */
    public final Map<Character, String> key;
    /** Part patterns: the part kind's name; hideout patterns: null. */
    public final String part;
    public final ResourceLocation result;
    public final int count;
    public final String category;
    /** Module gate (2.5): a hideout recipe only works where this module has reached this level. */
    public final String gateModule;
    public final int gateLevel;
    /** The letter standing for the previous item in a line (an empty rig, backpack or pouch), or 0. */
    public final char previous;
    public final String json;

    private Pattern(String id, JsonObject o) {
        this.id = id;
        this.json = o.toString();
        this.station = o.has("station") ? o.get("station").getAsString() : "hideout";
        var arr = o.getAsJsonArray("pattern");
        this.rows = new String[arr.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = arr.get(i).getAsString().replace(" ", "");
        }
        this.key = new HashMap<>();
        if (o.has("key")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("key").entrySet()) {
                key.put(e.getKey().charAt(0), e.getValue().getAsString());
            }
        }
        this.part = o.has("part") ? o.get("part").getAsString().toUpperCase(Locale.ROOT) : null;
        JsonObject res = o.has("result") ? o.getAsJsonObject("result") : null;
        this.result = res == null ? null : ResourceLocation.parse(res.get("id").getAsString());
        this.count = res != null && res.has("count") ? res.get("count").getAsInt() : 1;
        this.category = o.has("category") ? o.get("category").getAsString() : "Other";
        JsonObject gate = o.has("gate") ? o.getAsJsonObject("gate") : null;
        this.gateModule = gate == null ? null : gate.get("module").getAsString().toUpperCase(Locale.ROOT);
        this.gateLevel = gate == null ? 0 : gate.get("level").getAsInt();
        this.previous = o.has("previous") ? o.get("previous").getAsString().charAt(0) : 0;
    }

    public static Pattern parse(String id, JsonObject o) {
        return new Pattern(id, o);
    }

    public int width() {
        int w = 0;
        for (String r : rows) {
            w = Math.max(w, r.length());
        }
        return w;
    }

    public ItemStack resultStack() {
        Item item = result == null ? null : BuiltInRegistries.ITEM.get(result);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Whether a stack fits a hideout pattern's letter. */
    public boolean accepts(char letter, ItemStack stack) {
        String spec = key.get(letter);
        if (spec == null || stack.isEmpty()) {
            return false;
        }
        if (spec.startsWith("#")) {
            return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(spec.substring(1))));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.parse(spec));
    }

    /** An item to show for a letter in the book's ghost (the first of a tag). */
    public ItemStack example(char letter) {
        String spec = key.get(letter);
        if (spec == null) {
            return ItemStack.EMPTY;
        }
        if (spec.startsWith("#")) {
            var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(spec.substring(1)));
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                return new ItemStack(holder.value());
            }
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec)));
    }

    /**
     * Where the pattern sits in a 3x3 grid: the letter for each of the 9 squares (0 for empty), trying every offset and
     * the mirror, or null. {@code test} says whether a square's stack fits a letter; every other square must be empty.
     */
    public char[] place(ItemStack[] grid, java.util.function.BiPredicate<Character, ItemStack> test) {
        int h = rows.length;
        int w = width();
        for (int mirror = 0; mirror < 2; mirror++) {
            for (int oy = 0; oy + h <= 3; oy++) {
                for (int ox = 0; ox + w <= 3; ox++) {
                    char[] at = new char[9];
                    boolean ok = true;
                    for (int y = 0; y < h && ok; y++) {
                        String row = rows[y];
                        for (int x = 0; x < w; x++) {
                            int sx = mirror == 1 ? w - 1 - x : x;
                            char c = sx < row.length() ? row.charAt(sx) : '.';
                            if (c != '.') {
                                at[(oy + y) * 3 + ox + x] = c;
                            }
                        }
                    }
                    for (int i = 0; i < 9 && ok; i++) {
                        ok = at[i] == 0 ? grid[i].isEmpty() : test.test(at[i], grid[i]);
                    }
                    if (ok) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /** The pattern drawn at the top left of an empty grid, for the book's ghost: the letter for each square. */
    public char[] ghost() {
        char[] at = new char[9];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c != '.') {
                    at[y * 3 + x] = c;
                }
            }
        }
        return at;
    }
}
