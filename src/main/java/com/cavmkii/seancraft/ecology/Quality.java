package com.cavmkii.seancraft.ecology;

import com.cavmkii.seancraft.Cfg;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Ecosystem quality, 0 to 100 (17.16): floristic quality, habitat quality minus threats (the InVEST form), and
 * structure and soil. Score = 100 x Q x (0.6 x min(1, FQI / benchmark) + 0.2 x structure + 0.2 x soil).
 */
public final class Quality {
    private Quality() {}

    public static String band(double score) {
        return score < 20 ? "Degraded" : score < 40 ? "Weedy" : score < 60 ? "Fair" : score < 80 ? "Good" : "Remnant";
    }

    public static int color(double score) {
        return score < 20 ? 0xC8402E : score < 40 ? 0xC8662A : score < 60 ? 0xD9CBB0 : score < 80 ? 0x7D9A3A : 0x5FAE3C;
    }

    /** Floristic quality index: mean C x sqrt(species), over the plant species in the sample. */
    public static double fqi(Patch p) {
        double sum = 0;
        int n = 0;
        for (String id : p.plants.keySet()) {
            Double c = Params.PLANTS.get(id);
            if (c != null) {
                sum += c;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n * Math.sqrt(n);
    }

    /** Habitat suitability of the patch's land cover (H). */
    static double habitat(Patch p) {
        double total = p.grass + p.crop + p.shrub + p.forest + p.water + p.built + p.bare + p.path;
        if (total <= 0) {
            return 0.5;
        }
        return (p.grass * 1 + p.forest * 1 + p.shrub * 0.8 + p.water * 0.7 + p.crop * 0.3 + p.bare * 0.1 + p.path * 0.1) / total;
    }

    /**
     * Recomputes a patch's score and what holds it back most, from it and the 7 x 7 patches around (threat layer,
     * 17.16.4). {@code neighbour} returns a sampled patch at an offset, or null.
     */
    public static void update(Patch p, BiFunction<Integer, Integer, Patch> neighbour) {
        if (!p.sampled) {
            return;
        }
        // Threats, decaying with distance (17.16.1): villages reach 3 patches, cropland, pens, roads and builds 1.
        double d = 0;
        String worst = "";
        double worstD = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Patch n = dx == 0 && dz == 0 ? p : neighbour.apply(dx, dz);
                if (n == null || !n.sampled) {
                    continue;
                }
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                double[] w = {
                        n.villagers > 0 ? 1.0 * Math.min(1, n.villagers / 10.0) * falloff(dist, 3) : 0,
                        0.5 * n.crop * 2 * falloff(dist, 1),
                        n.grazers >= 6 && n.built >= 0.02 ? 0.6 * falloff(dist, 1) : 0,
                        0.4 * n.path * 4 * falloff(dist, 1),
                        0.3 * n.built * 3 * falloff(dist, 1)};
                String[] names = {"a village nearby", "cropland next to it", "a crowded livestock pen", "roads and paths", "buildings"};
                for (int i = 0; i < w.length; i++) {
                    d += w[i];
                    if (w[i] > worstD) {
                        worstD = w[i];
                        worst = names[i];
                    }
                }
            }
        }
        double z = Cfg.ecoQualityZ;
        double k = Cfg.ecoQualityK;
        double h = habitat(p);
        double q = h * (1 - Math.pow(d, z) / (Math.pow(d, z) + Math.pow(k, z)));
        p.threat = d;
        p.worstThreat = worst;
        p.habitatQ = q;
        // Structure: the layers grassland needs are ground and herbs; woodland adds shrubs and canopy.
        boolean woods = p.forest > p.grass;
        int need = woods ? 4 : 2;
        int have = (p.layerGround ? 1 : 0) + (p.layerHerbs ? 1 : 0) + (woods ? (p.layerShrubs ? 1 : 0) + (p.layerCanopy ? 1 : 0) : 0);
        double structure = (double) have / need;
        // Soil: organic matter, minus a penalty for excess nitrogen.
        double om = Math.min(1, (p.organicFast + p.organicSlow) / 80);
        double excess = Math.max(0, p.nitrogen - Params.excessNitrogen) / 60;
        double soil = Math.max(0, om - excess);
        double fqi = fqi(p);
        double floristic = Math.min(1, fqi / Params.fqiBenchmark);
        p.quality = 100 * q * (0.6 * floristic + 0.2 * structure + 0.2 * soil);

        // What holds it back most (17.11): the biggest shortfall among the parts.
        double threatLoss = 1 - q / Math.max(0.01, h);
        double[] gaps = {0.6 * (1 - floristic), 0.2 * (1 - structure), 0.2 * (1 - soil), threatLoss, 1 - h};
        int at = 0;
        for (int i = 1; i < gaps.length; i++) {
            if (gaps[i] > gaps[at]) {
                at = i;
            }
        }
        double undisturbed = p.daysUndisturbed();
        if (gaps[at] < 0.08) {
            p.limitedBy = "";
        } else if (at == 0) {
            p.limitedBy = p.nitrogen > Params.excessNitrogen ? "too much nitrogen"
                    : undisturbed > Params.disturbanceMax ? String.format(Locale.ROOT, "no fire or grazing in %.0f days", undisturbed)
                    : "few conservative plants";
        } else if (at == 1) {
            p.limitedBy = woods ? "missing layers (shrubs or canopy)" : "bare ground, no herb layer";
        } else if (at == 2) {
            p.limitedBy = excess > 0 ? "too much nitrogen" : "low organic matter";
        } else if (at == 3) {
            p.limitedBy = worst.isEmpty() ? "threats nearby" : worst;
        } else {
            p.limitedBy = "poor habitat cover";
        }
    }

    private static double falloff(int dist, int reach) {
        return dist > reach ? 0 : 1 - (double) dist / (reach + 1);
    }
}
