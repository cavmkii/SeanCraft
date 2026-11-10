package com.cavmkii.seancraft.ecology;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One patch's state (design doc 17.2). Only numbers whose effect players can see within a session or two are kept
 * (17.1): land cover, one abundance value per game group, soil fertility and moisture, pest level and resistance, and
 * water pollution. Everything else was cut, not hidden.
 */
public final class Patch {
    // ---- land cover, shares of sampled columns (17.2)
    public double grass, crop, wheat, shrub, forest, water, built, bare, path;
    /** Grazing livestock seen in the patch at the last sample, and villagers (threat layer, 17.16.1). */
    public int grazers, villagers;
    /** Plant species in the plant sample, block id to count (17.16). */
    public Map<String, Integer> plants = new HashMap<>();
    /** A cave, roost, hedgerow or tree cover in the patch: pest growth cut by a third nearby (17.7). */
    public boolean enemies;
    public boolean sampled;
    public long sampledAt = Long.MIN_VALUE;
    /** Mean surface height of the sampled columns. */
    public double elevation = Double.NaN;
    /** Where this patch's runoff goes: the first water patch downhill, worked out once (17.6); Long.MIN_VALUE for none. */
    public long downhill = Long.MIN_VALUE;
    public boolean downhillKnown;
    /** Share of crop columns near water with a 4+ block grass strip before it (17.16.2), and how many there were. */
    public double strip;
    public int cropNearWater;

    // ---- abundance, 0 to 1 (17.4)
    /** Small game (rabbits). */
    public double game = 0.5;
    /** Small game a few days ago: foxes follow it (17.4). */
    public double gameLag = 0.5;
    /** Grass and forage left after grazing. */
    public double forage = 0.8;
    /** Fish, if the patch holds water. */
    public double fish = 0.8;

    // ---- soil (17.6), 0 to 100; 50 is fresh ground
    public double fertility = 50;
    /** Fertilizer still breaking down: guano, bone meal, rotten flesh (a day or two, 17.6). */
    public double pendingFast;
    public double moisture = 0.5;

    // ---- pests (17.7), per patch: the level 0 to 1 and resistance 0 to 0.9
    public double pest;
    public double resistance;
    /** Ecological day until which insecticide has switched off the natural-enemy bonus. */
    public double enemiesOffUntil = Double.NEGATIVE_INFINITY;

    // ---- water (17.6): pollution, 0 up; green past Params.greenAt, fish die past Params.deadAt
    public double pollution;

    // ---- space (17.5)
    /** Open edges to the four neighbours at the last daily update (0 to 4). */
    public int openEdges = 4;

    // ---- disturbance and time
    /** Ecological day of the last fire or grazing (17.16.1). */
    public double lastDisturbance = Double.NaN;
    /** Day of the disturbance before that, to tell "too often" apart (17.16.1). */
    public double prevDisturbance = Double.NaN;
    /** Ecological day the patch was last updated to. */
    public double day = Double.NaN;

    // ---- cached quality (17.16)
    public double quality;
    public double threat;
    public String worstThreat = "";
    public String limitedBy = "";
    /** Blocks the last sample counted as built, for the debug readout (not saved). */
    public java.util.Set<String> builtSeen = new java.util.TreeSet<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", 2);
        t.putDouble("grass", grass);
        t.putDouble("crop", crop);
        t.putDouble("wheat", wheat);
        t.putDouble("shrub", shrub);
        t.putDouble("forest", forest);
        t.putDouble("water", water);
        t.putDouble("built", built);
        t.putDouble("bare", bare);
        t.putDouble("path", path);
        t.putInt("grazers", grazers);
        t.putInt("villagers", villagers);
        CompoundTag p = new CompoundTag();
        plants.forEach(p::putInt);
        t.put("plants", p);
        t.putBoolean("enemies", enemies);
        t.putBoolean("sampled", sampled);
        t.putDouble("elevation", elevation);
        t.putLong("downhill", downhill);
        t.putBoolean("downhillKnown", downhillKnown);
        t.putDouble("strip", strip);
        t.putInt("cropNearWater", cropNearWater);
        t.putDouble("game", game);
        t.putDouble("gameLag", gameLag);
        t.putDouble("forage", forage);
        t.putDouble("fish", fish);
        t.putDouble("fertility", fertility);
        t.putDouble("pendingFast", pendingFast);
        t.putDouble("moisture", moisture);
        t.putDouble("pest", pest);
        t.putDouble("resistance", resistance);
        t.putDouble("enemiesOff", enemiesOffUntil);
        t.putDouble("pollution", pollution);
        t.putInt("openEdges", openEdges);
        t.putDouble("disturbed", lastDisturbance);
        t.putDouble("prevDisturbed", prevDisturbance);
        t.putDouble("day", day);
        t.putDouble("quality", quality);
        t.putString("limitedBy", limitedBy);
        return t;
    }

    public static Patch load(CompoundTag t) {
        Patch x = new Patch();
        x.grass = t.getDouble("grass");
        x.crop = t.getDouble("crop");
        x.wheat = t.getDouble("wheat");
        x.shrub = t.getDouble("shrub");
        x.forest = t.getDouble("forest");
        x.water = t.getDouble("water");
        x.built = t.getDouble("built");
        x.bare = t.getDouble("bare");
        x.path = t.getDouble("path");
        x.grazers = t.getInt("grazers");
        x.villagers = t.getInt("villagers");
        CompoundTag p = t.getCompound("plants");
        for (String k : p.getAllKeys()) {
            x.plants.put(k, p.getInt(k));
        }
        x.day = t.getDouble("day");
        x.lastDisturbance = t.getDouble("disturbed");
        x.quality = t.getDouble("quality");
        x.limitedBy = t.getString("limitedBy");
        x.moisture = t.contains("moisture") ? t.getDouble("moisture") : 0.5;
        if (t.getInt("v") < 2) {
            // A patch from the first model (densities, N/P and decomposer pools): keep its cover, plants and soil,
            // translated, and sample it again for the rest.
            x.fertility = Math.max(5, Math.min(100, t.getDouble("n") * 50 / 60));
            x.game = Math.max(0.05, Math.min(1, t.getDouble("rabbits") / 40));
            x.gameLag = x.game;
            x.forage = t.contains("grassBiomass") ? t.getDouble("grassBiomass") : 0.8;
            x.pest = Math.min(1, t.getDouble("pest") / 1000);
            x.resistance = t.getDouble("resistance");
            x.fish = 0.8;
            x.prevDisturbance = Double.NaN;
            x.sampled = false;
            return x;
        }
        x.enemies = t.getBoolean("enemies");
        x.sampled = t.getBoolean("sampled");
        x.elevation = t.getDouble("elevation");
        x.downhill = t.getLong("downhill");
        x.downhillKnown = t.getBoolean("downhillKnown");
        x.strip = t.getDouble("strip");
        x.cropNearWater = t.getInt("cropNearWater");
        x.game = t.getDouble("game");
        x.gameLag = t.getDouble("gameLag");
        x.forage = t.getDouble("forage");
        x.fish = t.getDouble("fish");
        x.fertility = t.getDouble("fertility");
        x.pendingFast = t.getDouble("pendingFast");
        x.pest = t.getDouble("pest");
        x.resistance = t.getDouble("resistance");
        x.enemiesOffUntil = t.getDouble("enemiesOff");
        x.pollution = t.getDouble("pollution");
        x.openEdges = t.getInt("openEdges");
        x.prevDisturbance = t.getDouble("prevDisturbed");
        return x;
    }

    /** Days since the last fire or grazing; the patch's age if it has never been disturbed. */
    public double daysUndisturbed() {
        return Double.isNaN(lastDisturbance) || Double.isNaN(day) ? 0 : Math.max(0, day - lastDisturbance);
    }

    /** A disturbance now (fire, grazing): remembered with the one before, to spot "too often". */
    public void disturb() {
        if (Double.isNaN(lastDisturbance) || day - lastDisturbance > 0.5) {
            prevDisturbance = lastDisturbance;
        }
        lastDisturbance = day;
    }

    public boolean holdsWater() {
        return sampled && water >= Params.waterMin;
    }
}
