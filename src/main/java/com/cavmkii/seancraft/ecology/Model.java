package com.cavmkii.seancraft.ecology;

/**
 * The patch model (design doc 17.4, 17.6, 17.7), kept to what players can see. Each value eases toward a target by a
 * set fraction per day, worked out in one calculation however many days have passed (17.2), so a daily update and a
 * 30-day catch-up cost the same. No predator-prey or nutrient-pool equations run.
 */
public final class Model {
    private Model() {}

    /** What a step leaves for {@link Ecology} to route: fertility that washed out, and water pollution that cleared. */
    public record Outflow(double leached, double cleared) {}

    /** {@code v} moved toward {@code target} at {@code rate} per day for {@code days}. */
    static double ease(double v, double target, double rate, double days) {
        return target + (v - target) * Math.exp(-rate * days);
    }

    /**
     * Steps a patch forward {@code days} ecological days.
     *
     * @param season multipliers from {@link Seasons}: breeding, pest growth, grass regrowth
     * @param enemiesNear a cave, roost, hedgerow or tree cover within one patch (17.7)
     */
    public static Outflow step(Patch p, double days, Seasons.Season season, boolean enemiesNear) {
        double area = Ecology.hectares();
        // Forage regrows toward the grass cover, less what livestock graze off.
        double grazing = Params.grazePerGrazer * p.grazers / Math.max(0.01, area);
        double forageTarget = clamp(p.grass + p.bare * 0.2 - grazing, 0.02, 1);
        p.forage = ease(p.forage, forageTarget, Params.forageRegrowth * season.grass(), days);

        // Small game refills to a cap set by forage and quality; a patch closed off on every side holds less (17.5).
        double cap = gameCap(p);
        p.game = ease(p.game, cap, Params.gameRefill * (p.game < cap ? season.breeding() : 1), days);
        p.gameLag = ease(p.gameLag, p.game, Params.gameLagRate, days);

        // Fertilizer breaks down into fertility over a day or two (17.6); leadplant fixes a little.
        double released = p.pendingFast * (1 - Math.exp(-Params.fertilizerRelease * days));
        p.pendingFast -= released;
        double sample = Math.max(1, Ecology.sampleSize(p));
        double fixed = Params.leadplantFixation * p.plants.getOrDefault(EcoContent.id(EcoContent.Prairie.LEADPLANT), 0) / sample * days;
        p.fertility += released + fixed;
        if (p.fertility < Params.fertilityBaseline) {
            p.fertility = ease(p.fertility, Params.fertilityBaseline, Params.fertilityRecovery, days);
        }
        // Past what the ground holds, it washes out to the water downhill (Ecology routes it).
        double leached = 0;
        if (p.fertility > Params.leachAbove) {
            leached = (p.fertility - Params.leachAbove) * (1 - Math.exp(-Params.leachRate * days));
            p.fertility -= leached;
        }
        p.fertility = clamp(p.fertility, 0, 150);

        // Pests on wheat: logistic toward a full infestation; natural enemies nearby cut the growth (17.7).
        if (p.wheat > 0.001) {
            double host = Math.min(1, p.wheat / 0.15);
            boolean enemiesOn = enemiesNear && p.day >= p.enemiesOffUntil;
            double r = Params.pestGrowth * season.pest() * host * (enemiesOn ? 1 - Params.enemyCut : 1);
            double p0 = Math.max(0.01, p.pest);
            p.pest = 1 / (1 + (1 - p0) / p0 * Math.exp(-r * days));
        } else {
            p.pest *= Math.exp(-0.5 * days);
        }
        p.resistance = Math.max(0, p.resistance - 0.005 * days);

        // Water: pollution clears over weeks; past deadAt fish die, otherwise they come back.
        double cleared = 0;
        if (p.holdsWater()) {
            double before = p.pollution;
            p.pollution = before * Math.exp(-Params.pollutionDecay * days);
            cleared = before - p.pollution;
            p.fish = p.pollution > Params.deadAt ? ease(p.fish, 0, Params.fishDie, days) : ease(p.fish, 1, Params.fishRefill, days);
        }
        p.day += days;
        return new Outflow(leached, cleared);
    }

    /** The small-game cap: forage, quality and whether the patch is cut off (17.4, 17.5). */
    static double gameCap(Patch p) {
        double cap = Math.min(1, p.forage * 1.2) * (0.6 + 0.4 * Math.min(1, p.quality / 60));
        return p.openEdges == 0 ? cap * Params.isolatedCap : cap;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Pollution a water patch gets from {@code amount} of leached fertility: bigger water dilutes it. */
    static double pollutionFrom(Patch water, double amount) {
        return amount * Params.pollutionPerUnit * 0.25 / Math.max(Params.waterMin, water.water);
    }

    // ---- crops (17.6, 17.7)

    /** 0..1: how well a crop grows here, from fertility and moisture. */
    public static double soilFactor(Patch p) {
        return Math.min(fFactor(p), wFactor(p));
    }

    static double fFactor(Patch p) {
        return clamp(p.fertility / 40, 0.1, 1);
    }

    static double wFactor(Patch p) {
        return clamp(p.moisture / 0.3, 0.2, 1);
    }

    /** Share of a wheat harvest pests take. */
    public static double pestLoss(Patch p) {
        return Params.pestLossMax * p.pest;
    }

    /** 0..1: the share of bites that land, from the fish left (17.3: yields fall as a stock thins). */
    public static double fishAvailability(Patch p) {
        return p.holdsWater() ? clamp(p.fish, 0.05, 1) : 1;
    }

    /** Ease of crossing a patch for game: grass and hedges are easy, roads, walls and farmland hard (17.5). */
    static double permeability(Patch p) {
        double total = p.grass + p.crop + p.shrub + p.forest + p.water + p.built + p.bare + p.path;
        if (total <= 0) {
            return 0.5;
        }
        return (p.grass + p.shrub + p.forest * 0.6 + p.bare * 0.4 + p.crop * 0.15 + p.path * 0.1 + p.water * 0.05 + p.built * 0.05) / total;
    }

    /** What holds a crop back most here, for the soil kit and the crop readout. */
    public static String limiting(Patch p, boolean wheat) {
        double f = fFactor(p);
        double w = wFactor(p);
        double pest = wheat ? 1 - pestLoss(p) : 1;
        double min = Math.min(f, Math.min(w, pest));
        if (min >= 0.97) {
            return "nothing much";
        }
        return min == f ? "worn-out soil" : min == w ? "water" : "pests";
    }
}
