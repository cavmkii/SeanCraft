package com.cavmkii.seancraft.ecology;

import com.cavmkii.seancraft.SeanCraft;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Ecology tuning (17.14: "all parameters are data files"). Defaults live here; data/seancraft/ecology/params.json
 * (or a datapack) overrides any of them by field name, plus a "plants" map of block id to coefficient of
 * conservatism (17.16). Rates are per ecological day; densities per hectare.
 */
public final class Params {
    // Game (17.4, 17.5): abundance 0 to 1 that refills toward a cap. Rates are per ecological day.
    /** Refill rate toward the cap: about 95% of the way in 3 days. */
    public static double gameRefill = 1.0;
    /** Foxes follow small game this fast: a few days behind. */
    public static double gameLagRate = 0.35;
    /** Abundance one rabbit kill takes off its patch. */
    public static double gamePerKill = 0.06;
    /** Daily step toward the average of open neighbours, and from a Good or Remnant neighbour. */
    public static double neighbourStep = 0.15;
    public static double neighbourStepGood = 0.3;
    /** A patch closed off on all four sides holds this share of its normal cap. */
    public static double isolatedCap = 0.6;
    /** An edge is open if both patches are at least this easy to cross, or a corridor (hedges, trees) crosses it. */
    public static double openEdge = 0.35;
    public static double corridor = 0.15;
    public static double forageRegrowth = 0.3;
    public static double grazePerGrazer = 0.03;
    // Soil (17.6), fertility 0 to 100.
    public static double fertilityBaseline = 50;
    /** Unfarmed ground creeps back toward the baseline this slowly. */
    public static double fertilityRecovery = 0.01;
    /** Fertility a crop takes per growth stage. */
    public static double uptake = 0.01;
    /** Fertilizer breaks down at this rate per day (most of it in a day or two). */
    public static double fertilizerRelease = 1.0;
    public static double boneMeal = 1.0;
    public static double guano = 5;
    public static double rottenFlesh = 2;
    /** Leadplant adds this much a day at full share of the plant sample. */
    public static double leadplantFixation = 2;
    /** Above this, fertility washes out to the water downhill, at leachRate of the excess a day. */
    public static double leachAbove = 80;
    public static double leachRate = 0.2;
    /** Over this, the patch counts as over-fertilized (quality, prairie plants). */
    public static double overFertilized = 75;
    // Pests (17.7): a level 0 to 1 per patch.
    public static double pestGrowth = 0.5;
    /** Natural enemies within one patch cut pest growth by this share. */
    public static double enemyCut = 0.33;
    public static double insecticideKill = 0.9;
    public static double resistanceStep = 0.1;
    public static double resistanceMax = 0.9;
    /** Days insecticide switches off the natural-enemy bonus. */
    public static double enemiesOffDays = 7;
    /** Most of a wheat harvest pests can take, at full level. */
    public static double pestLossMax = 0.6;
    // Water (17.6): pollution 0 up.
    public static double waterMin = 0.03;
    /** Pollution per unit of fertility leached, for a patch a quarter water; bigger water dilutes it. */
    public static double pollutionPerUnit = 0.02;
    /** Pollution clears this fast a day; half of what clears flows on to the water downhill. */
    public static double pollutionDecay = 0.1;
    public static double greenAt = 0.3;
    public static double deadAt = 0.6;
    public static double fishRefill = 0.15;
    public static double fishDie = 0.5;
    public static double fishPerCatch = 0.03;
    /** Share of runoff a 4+ block grass strip stops in a Fair or better patch (17.16.2). */
    public static double stripIntercept = 0.8;
    /** Chance per random tick an isolated conservative plant (C 7+) dies back: the extinction debt. */
    public static double isolationLoss = 0.003;
    // Quality (17.16).
    public static double varietyCap = 40;
    public static double disturbanceMin = 5;
    public static double disturbanceMax = 40;
    // Visible animals at full abundance, and caps (17.3).
    public static int rabbitCap = 6;
    public static int foxCap = 3;

    /** Coefficient of conservatism per plant block (17.16); anything unlisted is not counted as a plant. */
    public static final Map<String, Double> PLANTS = new HashMap<>();

    static {
        defaultPlants();
    }

    private Params() {}

    private static void defaultPlants() {
        PLANTS.clear();
        for (String id : new String[] {"short_grass", "tall_grass", "dandelion"}) {
            PLANTS.put("minecraft:" + id, 0.0);
        }
        PLANTS.put("minecraft:poppy", 1.5);
        PLANTS.put("minecraft:dead_bush", 1.0);
        PLANTS.put("minecraft:oxeye_daisy", 2.0);
        for (String id : new String[] {"cornflower", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "sweet_berry_bush", "fern", "large_fern"}) {
            PLANTS.put("minecraft:" + id, 3.0);
        }
        for (String id : new String[] {"azure_bluet", "sunflower", "rose_bush"}) {
            PLANTS.put("minecraft:" + id, 4.0);
        }
        for (String id : new String[] {"allium", "peony", "lilac"}) {
            PLANTS.put("minecraft:" + id, 5.0);
        }
        PLANTS.put("minecraft:lily_of_the_valley", 6.0);
        for (EcoContent.Prairie p : EcoContent.Prairie.values()) {
            PLANTS.put(SeanCraft.id(p.id).toString(), p.c);
        }
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SeanCraft.id("ecology_params");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                defaultPlants();
                manager.getResource(SeanCraft.id("ecology/params.json")).ifPresent(res -> {
                    try (Reader r = res.openAsReader()) {
                        apply(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception e) {
                        SeanCraft.LOG.warn("Couldn't read ecology params: {}", e.toString());
                    }
                });
            }
        });
    }

    private static void apply(JsonObject o) throws IllegalAccessException {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().equals("plants")) {
                for (Map.Entry<String, JsonElement> p : e.getValue().getAsJsonObject().entrySet()) {
                    PLANTS.put(p.getKey(), p.getValue().getAsDouble());
                }
                continue;
            }
            Field f;
            try {
                f = Params.class.getField(e.getKey());
            } catch (NoSuchFieldException ex) {
                SeanCraft.LOG.warn("Unknown ecology param {}", e.getKey());
                continue;
            }
            if (f.getType() == int.class) {
                f.setInt(null, e.getValue().getAsInt());
            } else if (f.getType() == double.class) {
                f.setDouble(null, e.getValue().getAsDouble());
            }
        }
    }
}
