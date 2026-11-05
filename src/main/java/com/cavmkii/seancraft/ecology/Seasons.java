package com.cavmkii.seancraft.ecology;

import java.lang.reflect.Method;
import net.minecraft.world.level.Level;

/**
 * Phenology from Serene Seasons' calendar (17.10), when it's installed. Read by reflection so SeanCraft runs without
 * it; with no seasons mod every multiplier is 1.
 */
public final class Seasons {
    /** Multipliers for breeding (rabbits, mice), pest growth and grass regrowth; and whether plants spread. */
    public record Season(String name, double breeding, double pest, double grass, boolean spreads) {}

    public static final Season NONE = new Season("none", 1, 1, 1, true);
    private static final Season SPRING = new Season("spring", 1.5, 0.8, 1.3, true);
    private static final Season SUMMER = new Season("summer", 1, 1.3, 1, true);
    private static final Season AUTUMN = new Season("autumn", 0.5, 0.6, 0.7, false);
    /** Winter: no breeding, the pest dies back, grass barely grows. */
    private static final Season WINTER = new Season("winter", 0, -0.3, 0.2, false);

    private static boolean looked;
    private static Method getState;
    private static Method getSeason;

    private Seasons() {}

    public static Season of(Level level) {
        if (!looked) {
            looked = true;
            try {
                Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
                getState = helper.getMethod("getSeasonState", Level.class);
                getSeason = Class.forName("sereneseasons.api.season.ISeasonState").getMethod("getSeason");
            } catch (ReflectiveOperationException | LinkageError e) {
                getState = null;
            }
        }
        if (getState == null) {
            return NONE;
        }
        try {
            Object season = getSeason.invoke(getState.invoke(null, level));
            return switch (String.valueOf(season)) {
                case "SPRING" -> SPRING;
                case "SUMMER" -> SUMMER;
                case "AUTUMN" -> AUTUMN;
                case "WINTER" -> WINTER;
                default -> NONE;
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NONE;
        }
    }
}
