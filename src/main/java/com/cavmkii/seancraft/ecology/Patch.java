package com.cavmkii.seancraft.ecology;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One patch's state (design doc 17.2): land cover from the last sample, populations as densities per hectare, soil
 * pest pressure, and (phase 2) water and fish. The model steps it forward in ecological days (17.4 to 17.7).
 */
public final class Patch {
    // ---- land cover, shares of sampled columns (17.2)
    public double grass, crop, wheat, shrub, forest, water, built, bare, path;
    /** Bats a roost could hold per hectare, from caves and tree cover (17.7). */
    public double roost;
    /** Grazing livestock seen in the patch at the last sample, and villagers (threat layer, 17.16.1). */
    public int grazers, villagers;
    /** Plant species seen in the sample, block id to count (17.16). */
    public Map<String, Integer> plants = new HashMap<>();
    /** Vegetation layers present: ground, herbs, shrubs, canopy (17.16). */
    public boolean layerGround, layerHerbs, layerShrubs, layerCanopy;
    public boolean sampled;
    public long sampledAt = Long.MIN_VALUE;

    // ---- populations, per hectare (17.4)
    public double grassBiomass = 0.8;
    public double rabbits = 20;
    public double mice = 30;
    public double foxes = 0.8;
    public double bats = 2;
    /** Wheat pest (aphids) density per hectare of the patch, and its insecticide resistance 0..1 (17.7). */
    public double pest = 0;
    public double resistance = 0;

    // ---- soil (17.6), arbitrary units: nitrogen around 60 and phosphorus around 25 on fresh ground
    public double nitrogen = 60;
    public double phosphorus = 25;
    public double organicFast = 5;
    public double organicSlow = 40;
    public double moisture = 0.5;

    // ---- water and space (phase 2: 17.5, 17.6)
    /** Mean surface height of the sampled columns, for which way water runs (17.6). */
    public double elevation = Double.NaN;
    /** Share of crop columns near water that have a 4+ block grass strip between them and the water (17.16.2). */
    public double strip = 0;
    /** Crop columns within reach of water at the last sample (for the debug readout). */
    public int cropNearWater;
    /** Nutrients leached from this patch's soil since it was last routed downstream (not saved). */
    public double leached;
    /** Water state, for patches that hold water: nutrient load, algae 0..1, dissolved oxygen 0..1. */
    public double load = 0, algae = 0.02, oxygen = 1;
    /** Fish per hectare of the patch, and fish lost to low oxygen per day lately (for the dead-fish sign). */
    public double fish = 0, fishKill = 0;
    /** 0..1: how well connected the patch is to good habitat around it (17.5); low means the extinction debt runs. */
    public double connectivity = 1;

    // ---- disturbance and time
    /** Ecological day of the last fire or grazing (17.16.1). */
    public double lastDisturbance = Double.NaN;
    /** Ecological day the patch was last stepped to. */
    public double day = Double.NaN;

    // ---- cached quality (17.16)
    public double quality;
    /** Summed threat (D) and the habitat score after threats (Q) at the last update, for the debug readout. */
    public double threat, habitatQ;
    public String worstThreat = "";
    /** Blocks the last sample counted as built, for the debug readout (not saved). */
    public java.util.Set<String> builtSeen = new java.util.TreeSet<>();
    public String limitedBy = "";

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("grass", grass);
        t.putDouble("crop", crop);
        t.putDouble("wheat", wheat);
        t.putDouble("shrub", shrub);
        t.putDouble("forest", forest);
        t.putDouble("water", water);
        t.putDouble("built", built);
        t.putDouble("bare", bare);
        t.putDouble("path", path);
        t.putDouble("roost", roost);
        t.putInt("grazers", grazers);
        t.putInt("villagers", villagers);
        CompoundTag p = new CompoundTag();
        plants.forEach(p::putInt);
        t.put("plants", p);
        t.putByte("layers", (byte) ((layerGround ? 1 : 0) | (layerHerbs ? 2 : 0) | (layerShrubs ? 4 : 0) | (layerCanopy ? 8 : 0)));
        t.putBoolean("sampled", sampled);
        t.putDouble("grassBiomass", grassBiomass);
        t.putDouble("rabbits", rabbits);
        t.putDouble("mice", mice);
        t.putDouble("foxes", foxes);
        t.putDouble("bats", bats);
        t.putDouble("pest", pest);
        t.putDouble("resistance", resistance);
        t.putDouble("n", nitrogen);
        t.putDouble("p", phosphorus);
        t.putDouble("omFast", organicFast);
        t.putDouble("omSlow", organicSlow);
        t.putDouble("moisture", moisture);
        t.putDouble("disturbed", lastDisturbance);
        t.putDouble("day", day);
        t.putDouble("quality", quality);
        t.putString("limitedBy", limitedBy);
        t.putDouble("elevation", elevation);
        t.putDouble("strip", strip);
        t.putInt("cropNearWater", cropNearWater);
        t.putDouble("load", load);
        t.putDouble("algae", algae);
        t.putDouble("oxygen", oxygen);
        t.putDouble("fish", fish);
        t.putDouble("fishKill", fishKill);
        t.putDouble("connectivity", connectivity);
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
        x.roost = t.getDouble("roost");
        x.grazers = t.getInt("grazers");
        x.villagers = t.getInt("villagers");
        CompoundTag p = t.getCompound("plants");
        for (String k : p.getAllKeys()) {
            x.plants.put(k, p.getInt(k));
        }
        int layers = t.getByte("layers");
        x.layerGround = (layers & 1) != 0;
        x.layerHerbs = (layers & 2) != 0;
        x.layerShrubs = (layers & 4) != 0;
        x.layerCanopy = (layers & 8) != 0;
        x.sampled = t.getBoolean("sampled");
        x.grassBiomass = t.getDouble("grassBiomass");
        x.rabbits = t.getDouble("rabbits");
        x.mice = t.getDouble("mice");
        x.foxes = t.getDouble("foxes");
        x.bats = t.getDouble("bats");
        x.pest = t.getDouble("pest");
        x.resistance = t.getDouble("resistance");
        x.nitrogen = t.getDouble("n");
        x.phosphorus = t.getDouble("p");
        x.organicFast = t.getDouble("omFast");
        x.organicSlow = t.getDouble("omSlow");
        x.moisture = t.getDouble("moisture");
        x.lastDisturbance = t.getDouble("disturbed");
        x.day = t.getDouble("day");
        x.quality = t.getDouble("quality");
        x.limitedBy = t.getString("limitedBy");
        x.elevation = t.contains("elevation") ? t.getDouble("elevation") : Double.NaN;
        x.strip = t.getDouble("strip");
        x.cropNearWater = t.getInt("cropNearWater");
        x.load = t.getDouble("load");
        x.algae = t.contains("algae") ? t.getDouble("algae") : 0.02;
        x.oxygen = t.contains("oxygen") ? t.getDouble("oxygen") : 1;
        // Patches saved before phase 2 start with the water stocked, not empty.
        x.fish = t.contains("fish") ? t.getDouble("fish") : Params.fishK * x.water * 0.8;
        x.fishKill = t.getDouble("fishKill");
        x.connectivity = t.contains("connectivity") ? t.getDouble("connectivity") : 1;
        return x;
    }

    /** Days since the last fire or grazing; the patch's age if it has never been disturbed. */
    public double daysUndisturbed() {
        return Double.isNaN(lastDisturbance) || Double.isNaN(day) ? 0 : Math.max(0, day - lastDisturbance);
    }
}
