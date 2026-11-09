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
    // Food web (17.4)
    public static double grassRegrowth = 0.3;
    public static double grazePerRabbit = 0.004;
    public static double grazePerGrazer = 0.03;
    public static double rabbitR = 0.25;
    public static double rabbitK = 40;
    public static double mouseR = 0.4;
    public static double mouseK = 60;
    /** Fox attack rate and handling time. Raised from the doc's 0.05 anchor so foxes run near 1 per 6 rabbits. */
    public static double foxA = 0.5;
    public static double foxH = 0.4;
    public static double foxEfficiency = 0.08;
    /** Fox background death. Raised from the doc's 0.02 anchor, which let foxes eat rabbits out. */
    public static double foxDeath = 0.1;
    /** Below this many rabbits per hectare, foxes switch to mice (Type III). */
    public static double foxSwitch = 8;
    // Pest and bats (17.7)
    public static double pestR = 0.5;
    public static double pestK = 1000;
    public static double pestImmigration = 2;
    public static double batAttack = 0.1;
    public static double batHandling = 0.005;
    public static double batRecovery = 0.05;
    public static double insecticideKill = 0.9;
    public static double insecticideBatKill = 0.8;
    public static double resistanceStep = 0.1;
    public static double resistanceMax = 0.9;
    // Soil (17.6), per crop growth stage for uptake
    public static double uptakeN = 0.004;
    public static double uptakeP = 0.0012;
    public static double decayFast = 0.15;
    public static double decaySlow = 0.01;
    public static double releaseN = 0.5;
    public static double releaseP = 0.1;
    public static double weatheringP = 0.05;
    public static double backgroundN = 0.2;
    public static double leachAbove = 120;
    public static double baselineN = 60;
    public static double baselineP = 25;
    public static double vegetationUptake = 0.05;
    public static double leachRate = 0.1;
    public static double leadplantFixation = 3;
    public static double bluestemCarbon = 2;
    public static double guanoOrganic = 6;
    public static double boneMealOrganic = 1.5;
    // Water (phase 2, 17.6). Load is in soil nutrient units; phosphorus counts more, since it drives algae in fresh water.
    public static double leachAboveP = 50;
    public static double phosphorusLoadWeight = 3;
    /** Water share a patch needs to count as holding water. */
    public static double waterMin = 0.03;
    /** Share of the load a patch's water loses per day: to the next water downhill (flush), and to the sediment (settle). */
    public static double flushRate = 0.1;
    public static double settleRate = 0.08;
    /** Load mixing between neighbouring water patches per day. */
    public static double waterMixing = 0.1;
    public static double algaeR = 0.4;
    /** Concentration (load per quarter-patch of water) at which algae grow at half speed. */
    public static double algaeHalf = 15;
    public static double algaeDeath = 0.06;
    /** Oxygen moves toward 1 - oxygenAlgae x algae at this rate per day. */
    public static double oxygenRate = 0.6;
    public static double oxygenAlgae = 1.0;
    /** Below this oxygen fish start dying, at up to fishSuffocation of them a day. */
    public static double lowOxygen = 0.35;
    public static double fishSuffocation = 1.5;
    /** Fish that find their way in from elsewhere per day, as a share of what the water can hold, while it has oxygen. */
    public static double fishImmigration = 0.01;
    public static double fishR = 0.15;
    /** Fish per hectare of open water. */
    public static double fishK = 60;
    /** Below this many fish per hectare of water, catches start getting away (yields fall as the stock thins). */
    public static double fishFull = 30;
    /** Share of runoff a Fair or better strip between field and water stops, and a grassy patch on the way. */
    public static double stripIntercept = 0.8;
    public static double landIntercept = 0.4;
    // Space (phase 2, 17.5): share of the difference that moves between neighbours per day, at full permeability.
    public static double rabbitDispersal = 0.08;
    public static double foxDispersal = 0.15;
    public static double fishDispersal = 0.1;
    /** Chance per random tick an isolated conservative plant (C 7+) dies back: the extinction debt. */
    public static double isolationLoss = 0.003;
    // Quality (17.16). The doc benchmarks FQI against 40; with the plants a Minecraft patch can hold, 20 is reachable.
    public static double fqiBenchmark = 20;
    public static double excessNitrogen = 90;
    public static double disturbanceMin = 5;
    public static double disturbanceMax = 40;
    // Visible animals per hectare of density, and caps (17.3)
    public static double rabbitsShown = 0.3;
    public static double foxesShown = 0.6;
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
