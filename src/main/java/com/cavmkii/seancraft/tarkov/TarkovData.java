package com.cavmkii.seancraft.tarkov;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-player state: body health and injuries, painkillers, whether a new player has been placed at a spawn point.
 * Kept through death.
 */
public final class TarkovData {
    /** Health of each body part as a fraction of its share (see {@link BodyPart}). */
    public final float[] body = {1, 1, 1, 1, 1, 1, 1};
    /** Conditions per body part, as {@link Body} bit flags. */
    public final int[] conditions = new int[7];
    public int painkillerTicks;
    /** Bleed severity per part, 0-1. At 0.5 and up it's a heavy bleed. Kept in step with {@link #conditions}. */
    public final float[] bleed = new float[7];
    /** Fracture severity per part, 0-1. */
    public final float[] fracture = new float[7];
    /**
     * Lasting damage per part, 0-1: the share of the part's health it can't get back. Left by a surgical kit; a
     * surgery case clears it.
     */
    public final float[] scar = new float[7];
    /** Whether a new player has been placed at a spawn point yet. */
    public boolean placedAtSpawn;

    public static final Codec<TarkovData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.listOf().optionalFieldOf("body", List.of()).forGetter(d -> {
                List<Float> list = new ArrayList<>();
                for (float f : d.body) {
                    list.add(f);
                }
                return list;
            }),
            Codec.INT.listOf().optionalFieldOf("conditions", List.of()).forGetter(d -> {
                List<Integer> list = new ArrayList<>();
                for (int c : d.conditions) {
                    list.add(c);
                }
                return list;
            }),
            Codec.INT.optionalFieldOf("painkiller", 0).forGetter(d -> d.painkillerTicks),
            Codec.BOOL.optionalFieldOf("placedAtSpawn", false).forGetter(d -> d.placedAtSpawn),
            Codec.FLOAT.listOf().optionalFieldOf("bleed", List.of()).forGetter(d -> floats(d.bleed)),
            Codec.FLOAT.listOf().optionalFieldOf("fracture", List.of()).forGetter(d -> floats(d.fracture)),
            Codec.FLOAT.listOf().optionalFieldOf("scar", List.of()).forGetter(d -> floats(d.scar))
    ).apply(i, (body, conditions, painkiller, placed, bleed, fracture, scar) -> {
        TarkovData d = new TarkovData();
        for (int p = 0; p < body.size() && p < d.body.length; p++) {
            d.body[p] = body.get(p);
        }
        for (int p = 0; p < conditions.size() && p < d.conditions.length; p++) {
            d.conditions[p] = conditions.get(p);
        }
        d.painkillerTicks = painkiller;
        d.placedAtSpawn = placed;
        fill(d.bleed, bleed);
        fill(d.fracture, fracture);
        fill(d.scar, scar);
        Body.migrate(d);
        return d;
    }));

    private static List<Float> floats(float[] a) {
        List<Float> list = new ArrayList<>();
        for (float f : a) {
            list.add(f);
        }
        return list;
    }

    private static void fill(float[] into, List<Float> from) {
        for (int p = 0; p < from.size() && p < into.length; p++) {
            into[p] = from.get(p);
        }
    }
}
