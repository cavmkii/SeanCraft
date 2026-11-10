package com.cavmkii.seancraft.ecology;

import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Ecosystem quality, 0 to 100 (17.16), from three things players can see and change: plant variety, nearby threats,
 * and whether the ground is burned or grazed on schedule and not over-fertilized.
 * Score = 100 x (variety / 40) x (1 - threats) x disturbance.
 */
public final class Quality {
    private Quality() {}

    public static String band(double score) {
        return score < 20 ? "Degraded" : score < 40 ? "Weedy" : score < 60 ? "Fair" : score < 80 ? "Good" : "Remnant";
    }

    public static int color(double score) {
        return score < 20 ? 0xC8402E : score < 40 ? 0xC8662A : score < 60 ? 0xD9CBB0 : score < 80 ? 0x7D9A3A : 0x5FAE3C;
    }

    /** Plant variety: the conservatism values of the distinct species in the plant sample, summed and capped. */
    public static double variety(Patch p) {
        double sum = 0;
        for (String id : p.plants.keySet()) {
            if (p.plants.get(id) > 0) {
                sum += Params.PLANTS.getOrDefault(id, 0.0);
            }
        }
        return Math.min(Params.varietyCap, sum);
    }

    /**
     * Recomputes a patch's score and what holds it back most, from it and the 5 x 5 patches around (17.16.4).
     * {@code neighbour} returns a sampled patch at an offset, or null.
     */
    public static void update(Patch p, BiFunction<Integer, Integer, Patch> neighbour) {
        if (!p.sampled) {
            return;
        }
        // Threats (17.16.1): each source in reach subtracts its weight, halved for each patch of distance.
        double d = 0;
        String worst = "";
        double worstD = 0;
        String[] names = {"a village nearby", "cropland next to it", "a crowded livestock pen", "roads and paths", "buildings"};
        int[] reach = {2, 1, 1, 1, 1};
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Patch n = dx == 0 && dz == 0 ? p : neighbour.apply(dx, dz);
                if (n == null || !n.sampled) {
                    continue;
                }
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                double fall = Math.pow(0.5, dist);
                double[] w = {
                        n.villagers > 0 ? 1.0 * Math.min(1, n.villagers / 10.0) : 0,
                        0.5 * Math.min(1, n.crop * 2),
                        n.grazers >= 6 && n.built >= 0.02 ? 0.6 : 0,
                        0.4 * Math.min(1, n.path * 4),
                        0.3 * Math.min(1, n.built * 3)};
                for (int i = 0; i < w.length; i++) {
                    if (dist > reach[i]) {
                        continue;
                    }
                    double v = w[i] * fall;
                    d += v;
                    if (v > worstD) {
                        worstD = v;
                        worst = names[i];
                    }
                }
            }
        }
        d = Math.min(1, d);
        p.threat = d;
        p.worstThreat = worst;
        // Disturbance and fertilizer: on schedule scores in full, otherwise a third off.
        double undisturbed = p.daysUndisturbed();
        boolean overdue = undisturbed > Params.disturbanceMax;
        boolean tooOften = !Double.isNaN(p.prevDisturbance) && !Double.isNaN(p.lastDisturbance)
                && p.lastDisturbance - p.prevDisturbance < Params.disturbanceMin && undisturbed < Params.disturbanceMin;
        boolean overFed = p.fertility > Params.overFertilized;
        double disturbance = overdue || tooOften || overFed ? 2.0 / 3 : 1;
        double variety = variety(p) / Params.varietyCap;
        p.quality = 100 * variety * (1 - d) * disturbance;

        // What holds it back most (17.11).
        double varietyLoss = 1 - variety;
        double threatLoss = d;
        double disturbLoss = 1 - disturbance;
        if (Math.max(varietyLoss, Math.max(threatLoss, disturbLoss)) < 0.08) {
            p.limitedBy = "";
        } else if (disturbLoss >= varietyLoss && disturbLoss >= threatLoss) {
            p.limitedBy = overFed ? "over-fertilized" : overdue ? String.format(Locale.ROOT, "no fire or grazing in %.0f days", undisturbed)
                    : "burned or grazed too often";
        } else if (threatLoss >= varietyLoss) {
            p.limitedBy = worst.isEmpty() ? "threats nearby" : worst;
        } else {
            p.limitedBy = "few kinds of plants";
        }
    }
}
