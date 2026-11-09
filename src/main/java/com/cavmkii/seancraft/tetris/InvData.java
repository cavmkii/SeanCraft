package com.cavmkii.seancraft.tetris;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/** Per-player inventory settings: the auto-refill toggles and the gather list. The items are vanilla's own inventory. */
public final class InvData {
    /** Refill a hotbar slot from the inventory when its last item gets used up (3.3). */
    public boolean autoRefill = true;
    /** ...and from the stash too, at home with a Storehouse. */
    public boolean refillFromStash;
    /** Gather tracker: "item id=goal" entries shown on the HUD while you're away from home (3.7). */
    public final List<String> tracked = new ArrayList<>();

    public static final Codec<InvData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("autoRefill", true).forGetter(d -> d.autoRefill),
            Codec.BOOL.optionalFieldOf("refillFromStash", false).forGetter(d -> d.refillFromStash),
            Codec.STRING.listOf().optionalFieldOf("tracked", List.of()).forGetter(d -> d.tracked)
    ).apply(i, (refill, refillStash, tracked) -> {
        InvData d = new InvData();
        d.autoRefill = refill;
        d.refillFromStash = refillStash;
        d.tracked.addAll(tracked);
        return d;
    }));
}
