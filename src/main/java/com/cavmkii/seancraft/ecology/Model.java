package com.cavmkii.seancraft.ecology;

/**
 * The phase 1 grassland loop (17.4, 17.6, 17.7): grass, rabbits, mice and foxes in a Rosenzweig-MacArthur web with
 * fox prey-switching, soil decomposition and leaching, and a wheat pest held down by bats. Euler steps of at most
 * 0.05 days, so a 30-day catch-up is 600 cheap steps.
 */
public final class Model {
    private static final double DT = 0.05;

    private Model() {}

    /**
     * Steps a patch forward {@code days} ecological days.
     *
     * @param season multipliers from {@link Seasons}: breeding, pest growth, grass regrowth
     * @param goodNearby true when a Good or Remnant patch is within 2 patches (natural enemies, 17.16.2)
     */
    public static void step(Patch p, double days, Seasons.Season season, double qualityBonus, boolean goodNearby) {
        double left = days;
        while (left > 1e-9) {
            double dt = Math.min(DT, left);
            left -= dt;
            stepOnce(p, dt, season, qualityBonus, goodNearby);
        }
        p.day += days;
    }

    private static void stepOnce(Patch p, double dt, Seasons.Season s, double qualityBonus, boolean goodNearby) {
        // Grass regrows toward the patch's grass cover and is grazed down by rabbits and livestock.
        double cover = Math.max(0.05, p.grass + p.bare * 0.2);
        double area = Ecology.hectares();
        double livestock = p.grazers / Math.max(0.01, area);
        double dG = Params.grassRegrowth * s.grass() * (cover - p.grassBiomass)
                - Params.grazePerRabbit * p.rabbits * p.grassBiomass - Params.grazePerGrazer * livestock * p.grassBiomass;
        // Rabbits: logistic, carrying capacity from grazable grass; larger and steadier in good habitat (17.16.2).
        double kR = Params.rabbitK * Math.max(0.02, p.grassBiomass) * (1 + qualityBonus);
        double kM = Params.mouseK * Math.max(0.05, p.grass * 0.4 + p.crop + p.shrub * 0.5);
        // Fox functional response: Type II on rabbits, switching to mice below foxSwitch (Type III refuge).
        double switchR = p.rabbits * p.rabbits / (p.rabbits * p.rabbits + Params.foxSwitch * Params.foxSwitch);
        double a = Params.foxA;
        double h = Params.foxH;
        double killR = a * p.rabbits * switchR / (1 + a * h * (p.rabbits * switchR + p.mice * (1 - switchR) * 0.5));
        double killM = a * p.mice * 0.5 * (1 - switchR) / (1 + a * h * (p.rabbits * switchR + p.mice * (1 - switchR) * 0.5));
        double dR = Params.rabbitR * s.breeding() * p.rabbits * (1 - p.rabbits / kR) - p.foxes * killR;
        double dM = Params.mouseR * s.breeding() * p.mice * (1 - p.mice / kM) - p.foxes * killM;
        double dF = p.foxes * (Params.foxEfficiency * (killR + 0.3 * killM) - Params.foxDeath);
        p.grassBiomass = clamp(p.grassBiomass + dG * dt, 0, 1);
        p.rabbits = Math.max(0.05, p.rabbits + dR * dt);
        p.mice = Math.max(0.1, p.mice + dM * dt);
        // A few foxes always wander in from the wider landscape, so a patch is never fox-free for good.
        p.foxes = Math.max(0.02, p.foxes + dF * dt);

        // Soil: decomposer pools release nutrients on a delay; prairie roots build slow carbon; leadplant fixes N.
        double fromFast = Params.decayFast * p.organicFast * dt;
        double fromSlow = Params.decaySlow * p.organicSlow * dt;
        p.organicFast -= fromFast;
        p.organicSlow -= fromSlow;
        p.organicSlow += 0.3 * fromFast;
        double released = (fromFast * 0.7 + fromSlow);
        p.nitrogen += released * Params.releaseN + Params.backgroundN * dt;
        p.phosphorus += released * Params.releaseP + Params.weatheringP * dt;
        double sample = Math.max(1, Ecology.sampleSize(p));
        p.organicSlow += Params.bluestemCarbon * p.plants.getOrDefault(EcoContent.id(EcoContent.Prairie.BIG_BLUESTEM), 0) / sample * dt;
        p.nitrogen += Params.leadplantFixation * p.plants.getOrDefault(EcoContent.id(EcoContent.Prairie.LEADPLANT), 0) / sample * dt;
        // Wild vegetation takes up what's above the natural level and holds it in litter; it doesn't refill a
        // depleted field, so a field farmed hard stays depleted until decomposition and inputs bring it back.
        double veg = Math.min(1, p.grass + p.shrub + p.forest);
        if (p.nitrogen > Params.baselineN) {
            double take = Params.vegetationUptake * veg * (p.nitrogen - Params.baselineN) * dt;
            p.nitrogen -= take;
            p.organicSlow += take * 0.2;
        }
        if (p.phosphorus > Params.baselineP) {
            p.phosphorus -= Params.vegetationUptake * veg * (p.phosphorus - Params.baselineP) * dt;
        }
        // Leaching: what the ground can't hold washes out (phase 2 routes it to the water downstream).
        if (p.nitrogen > Params.leachAbove) {
            p.nitrogen -= (p.nitrogen - Params.leachAbove) * Params.leachRate * dt;
        }
        p.nitrogen = clamp(p.nitrogen, 0, 250);
        p.phosphorus = clamp(p.phosphorus, 0, 120);

        // Wheat pest: grows on its host, faster on big continuous plantings; bats eat it (Type II).
        if (p.wheat > 0.001) {
            double host = Math.min(1, p.wheat / 0.15);
            double r = Params.pestR * s.pest() * host * (goodNearby ? 0.7 : 1);
            double k = Params.pestK * Math.max(0.05, p.wheat * 4);
            double eaten = p.bats * Params.batAttack * p.pest / (1 + Params.batAttack * Params.batHandling * p.pest);
            double dP = r * p.pest * (1 - p.pest / k) + Params.pestImmigration * host - eaten;
            p.pest = Math.max(0, p.pest + dP * dt);
        } else {
            p.pest = Math.max(0, p.pest - p.pest * 0.5 * dt);
        }
        // Bats drift back toward what the roosts nearby can hold, slower than the pest recovers (resurgence).
        p.bats = Math.max(0, p.bats + Params.batRecovery * (p.roost - p.bats) * dt);
        // Resistance fades slowly without spraying.
        p.resistance = Math.max(0, p.resistance - 0.005 * dt);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- crops (17.6, 17.7)

    /** 0..1: how well a crop grows here, from the scarcest of nitrogen, phosphorus and moisture. */
    public static double soilFactor(Patch p) {
        return Math.min(Math.min(nFactor(p), pFactor(p)), wFactor(p));
    }

    static double nFactor(Patch p) {
        return clamp(p.nitrogen / 40, 0.1, 1);
    }

    static double pFactor(Patch p) {
        return clamp(p.phosphorus / 15, 0.1, 1);
    }

    static double wFactor(Patch p) {
        return clamp(p.moisture / 0.3, 0.2, 1);
    }

    /** Share of a wheat harvest the pest takes. */
    public static double pestLoss(Patch p) {
        return Math.min(0.6, p.pest / (p.pest + 300));
    }

    /** What holds a crop back most here, for the soil kit. */
    public static String limiting(Patch p, boolean wheat) {
        double n = nFactor(p);
        double ph = pFactor(p);
        double w = wFactor(p);
        double pest = wheat ? 1 - pestLoss(p) : 1;
        double min = Math.min(Math.min(n, ph), Math.min(w, pest));
        if (min >= 0.97) {
            return "nothing much";
        }
        return min == n ? "nitrogen" : min == ph ? "phosphorus" : min == w ? "water" : "pests";
    }
}
