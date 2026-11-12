package com.cavmkii.seancraft.client.world;

import com.cavmkii.seancraft.client.tarkov.ClientTarkov;
import com.cavmkii.seancraft.tarkov.Distance;
import com.cavmkii.seancraft.tarkov.TarkovNet;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * Readable danger on the client (design doc 18.2, 18.6, 18.9): band fog and a darker sound bed further out, a quiet
 * sting crossing into the Frontier and Far lands, the elite aura, and blight's grey grass and leaves.
 */
public final class Atmosphere {
    /** Fog distance by band: Heartland and Wilds untouched, a little closer in the Frontier, more in the Far lands. */
    private static final float[] FOG = {1, 1, 0.85f, 0.7f};
    private static final DustParticleOptions AURA = new DustParticleOptions(new Vector3f(0.55f, 0.08f, 0.08f), 0.9f);

    /** Blight centres and radius in blocks, from the server. */
    private static volatile List<BlockPos> blight = List.of();
    private static volatile double blightRadius;

    /** Falling stars in flight: start, end, ticks elapsed. */
    private static final List<double[]> STREAKS = new java.util.ArrayList<>();
    static final int STREAK_TICKS = 30;

    private static int lastBand = -1;
    private static int nextAmbience = 400;

    private Atmosphere() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Atmosphere::tick);
        // Blighted ground: grass, foliage and vines lose their colour (18.6). The blocks themselves don't change.
        ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> blighted(level, pos,
                level == null || pos == null ? 0x7CBD6B : BiomeColors.getAverageGrassColor(level, pos)),
                Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.POTTED_FERN, Blocks.SUGAR_CANE);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> blighted(level, pos,
                level == null || pos == null ? 0x48B518 : BiomeColors.getAverageFoliageColor(level, pos)),
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.VINE);
    }

    /** The server's blight zones changed: re-tint the world. */
    public static void blight(List<BlockPos> centres, double radius) {
        boolean changed = !centres.equals(blight) || radius != blightRadius;
        blight = List.copyOf(centres);
        blightRadius = radius;
        Minecraft mc = Minecraft.getInstance();
        if (changed && mc.levelRenderer != null && mc.level != null) {
            mc.levelRenderer.allChanged();
        }
    }

    /** A star falls (18.4): a bright streak across the sky over a second and a half. */
    public static void streak(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        STREAKS.add(new double[] {from.x, from.y, from.z, to.x, to.y, to.z, 0});
    }

    private static void streaks(Minecraft mc) {
        for (java.util.Iterator<double[]> it = STREAKS.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            double t0 = s[6] / STREAK_TICKS;
            double t1 = (s[6] + 1) / STREAK_TICKS;
            for (int i = 0; i < 10; i++) {
                double t = t0 + (t1 - t0) * i / 10;
                double x = s[0] + (s[3] - s[0]) * t, y = s[1] + (s[4] - s[1]) * t, z = s[2] + (s[5] - s[2]) * t;
                mc.level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, true, x, y, z, 0, -0.02, 0);
                if (i % 3 == 0) {
                    mc.level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.FLAME, true, x, y, z, 0, 0, 0);
                }
            }
            if (s[6] == 0) {
                play(mc, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5f, 0.5f);
            }
            if (++s[6] >= STREAK_TICKS) {
                play(mc, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 2f, 0.5f);
                it.remove();
            }
        }
    }

    public static boolean inBlight(BlockPos pos) {
        double r2 = blightRadius * blightRadius;
        for (BlockPos c : blight) {
            double dx = pos.getX() - c.getX();
            double dz = pos.getZ() - c.getZ();
            if (dx * dx + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    private static int blighted(BlockAndTintGetter level, BlockPos pos, int color) {
        if (pos == null || blight.isEmpty() || !inBlight(pos)) {
            return color;
        }
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        int grey = (r * 30 + g * 59 + b * 11) / 100;
        // Mostly grey, a little brown left in it.
        r = (r * 2 + grey * 5 + 0x6A * 3) / 10;
        g = (g * 2 + grey * 5 + 0x5E * 3) / 10;
        b = (b * 2 + grey * 5 + 0x4E * 3) / 10;
        return r << 16 | g << 8 | b;
    }

    /** Fog multiplier for the player's band, outdoors in the overworld and off safe ground. */
    public static float fogFactor() {
        Minecraft mc = Minecraft.getInstance();
        TarkovNet.PlaceState s = ClientTarkov.place;
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || s == null || s.place() == TarkovNet.PlaceState.UNKNOWN || s.safe()) {
            return 1;
        }
        return FOG[Math.min(Math.max(0, s.band()), FOG.length - 1)];
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        RandomSource random = mc.level.random;
        streaks(mc);
        // Elite aura (18.2): a few dark red motes on any elite within 32 blocks.
        if (mc.level.getGameTime() % 3 == 0) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.hasCustomName() && e.isAlive() && e.distanceToSqr(mc.player) < 32 * 32 && e.getCustomName().getString().startsWith("★")) {
                    mc.level.addParticle(AURA, e.getRandomX(0.7), e.getRandomY(), e.getRandomZ(0.7), 0, 0.02, 0);
                }
            }
        }
        TarkovNet.PlaceState s = ClientTarkov.place;
        if (s == null || s.place() == TarkovNet.PlaceState.UNKNOWN || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        int band = s.band();
        // A quiet sting crossing into the Frontier or the Far lands (18.9).
        if (lastBand >= 0 && band > lastBand && band >= Distance.FRONTIER.ordinal()) {
            play(mc, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), 0.5f, band == Distance.FAR_LANDS.ordinal() ? 0.5f : 0.7f);
        }
        lastBand = band;
        // The sound bed darkens further out (18.2): now and then a low mood sound, more often in the Far lands.
        if (band >= Distance.FRONTIER.ordinal() && !s.safe() && --nextAmbience <= 0) {
            boolean far = band >= Distance.FAR_LANDS.ordinal();
            SoundEvent sound = far && random.nextBoolean() ? SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value() : SoundEvents.AMBIENT_CAVE.value();
            play(mc, sound, far ? 0.35f : 0.25f, 0.6f + random.nextFloat() * 0.2f);
            nextAmbience = (far ? 600 : 1200) + random.nextInt(far ? 600 : 1200);
        }
    }

    private static void play(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.AMBIENT, volume, pitch, false);
    }
}
