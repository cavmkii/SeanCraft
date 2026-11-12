package com.cavmkii.seancraft.compat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.parts.PartDef;
import com.cavmkii.seancraft.parts.PartItems;
import com.cavmkii.seancraft.tarkov.Distance;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Enhanced Celestials 2 (design doc 18.1): which moon is up, and what SeanCraft does about it. A blood moon doubles
 * elite chances again, sends hunting parties in every band but the Heartland, and makes monsters drop weapon parts;
 * a harvest moon speeds crops. EC2's own effects (more spawns, no sleeping) come from its data.
 *
 * <p>EC2 is read by reflection: if its API changes under us, the moon just reads as none and SeanCraft carries on.
 */
public final class Celestials {
    public enum Moon { NONE, BLOOD, HARVEST, OTHER }

    private static boolean broken;
    private static long cachedAt = Long.MIN_VALUE;
    private static Moon cached = Moon.NONE;

    private Celestials() {}

    public static void init() {
        // Blood moon: monsters killed by a player sometimes drop a weapon part, better further out.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer)
                    || !(entity.level() instanceof ServerLevel level) || moon(level) != Moon.BLOOD) {
                return;
            }
            boolean elite = entity.getCustomName() != null && entity.getCustomName().getString().contains("★");
            if (level.random.nextFloat() >= (elite ? 0.25f : 0.05f)) {
                return;
            }
            Distance band = Distance.at(level, entity.position());
            int tier = Math.min(5, Math.max(1, band.ordinal() + level.random.nextInt(2)));
            List<PartDef> pool = new ArrayList<>();
            for (PartDef def : PartDef.ALL) {
                if (def.tier() == tier) {
                    pool.add(def);
                }
            }
            if (!pool.isEmpty()) {
                PartDef def = pool.get(level.random.nextInt(pool.size()));
                level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.3, entity.getZ(), new ItemStack(PartItems.get(def))));
            }
        });
    }

    /** The moon over a level now; the overworld's for the overworld, NONE elsewhere and by day. */
    public static Moon moon(ServerLevel level) {
        if (broken || level.dimension() != Level.OVERWORLD) {
            return Moon.NONE;
        }
        long now = level.getGameTime();
        if (now == cachedAt) {
            return cached;
        }
        cachedAt = now;
        Moon moon = read(level);
        if (moon != cached) {
            SeanCraft.LOG.info("Moon: {}", moon);
        }
        cached = moon;
        return cached;
    }

    public static boolean bloodMoon(ServerLevel level) {
        return moon(level) == Moon.BLOOD;
    }

    public static boolean harvestMoon(ServerLevel level) {
        return moon(level) == Moon.HARVEST;
    }

    private static java.lang.reflect.Method forecastOf, isActive, holderOf;

    private static Moon read(ServerLevel level) {
        try {
            if (forecastOf == null) {
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("enhancedcelestials2core")) {
                    broken = true;
                    return Moon.NONE;
                }
                // EnhancedCelestials.lunarForecastWorldData(Level) -> Optional<LunarForecast>
                Class<?> ec = Class.forName("dev.corgitaco.enhancedcelestials2core.EnhancedCelestials");
                forecastOf = ec.getMethod("lunarForecastWorldData", Level.class);
                Class<?> forecast = Class.forName("dev.corgitaco.enhancedcelestials2core.api.lunarevent.LunarForecast");
                isActive = forecast.getMethod("isEventActive");
                holderOf = forecast.getMethod("currentLunarEventHolder");
            }
            java.util.Optional<?> forecast = (java.util.Optional<?>) forecastOf.invoke(null, level);
            if (forecast.isEmpty() || !(Boolean) isActive.invoke(forecast.get())) {
                return Moon.NONE;
            }
            if (!(holderOf.invoke(forecast.get()) instanceof net.minecraft.core.Holder<?> holder)) {
                return Moon.NONE;
            }
            String path = holder.unwrapKey().map(k -> k.location().getPath()).orElse("");
            // The default pack's ids: blood_moon, super_blood_moon, harvest_moon, super_harvest_moon.
            return path.contains("blood") ? Moon.BLOOD : path.contains("harvest") ? Moon.HARVEST : Moon.OTHER;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            SeanCraft.LOG.error("Couldn't read Enhanced Celestials 2's moon; blood and harvest moon effects are off: {}", e.toString());
            return Moon.NONE;
        }
    }
}
