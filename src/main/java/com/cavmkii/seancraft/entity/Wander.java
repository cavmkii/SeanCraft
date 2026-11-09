package com.cavmkii.seancraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Long-distance travel on top of vanilla pathfinding. A destination is walked toward in legs of
 * about 24 blocks; an anchor keeps the mob loitering within a range of a point. Paths stop at the
 * edge of loaded chunks, so a far destination is given up on once progress stalls.
 */
public final class Wander {
    private static final double LEG = 24;
    private static final int STUCK_CHECK_TICKS = 200;

    private final PathfinderMob mob;
    private Vec3 destination;
    private Vec3 anchor;
    private double anchorRange = 8;
    private Vec3 progressFrom;
    private long progressCheckAt;
    private long nextStroll;

    public Wander(PathfinderMob mob) {
        this.mob = mob;
    }

    public boolean hasDestination() {
        return destination != null;
    }

    public void setDestination(Vec3 target) {
        destination = target;
        progressFrom = mob.position();
        progressCheckAt = mob.level().getGameTime() + STUCK_CHECK_TICKS;
    }

    public void clearDestination() {
        destination = null;
    }

    /** Pin the loiter point. Range is how far the mob may roam around it. */
    public void setAnchor(Vec3 point, double range) {
        anchor = point;
        anchorRange = range;
    }

    /** Stand still: anchor here with a tiny range and stop walking. */
    public void hold() {
        destination = null;
        anchor = mob.position();
        anchorRange = 0.5;
        mob.getNavigation().stop();
    }

    /**
     * Advance toward the destination, or loiter around the anchor if there is none. Returns true when
     * there is no destination (arrived, gave up because stuck, or never had one).
     */
    public boolean step(double speed) {
        long now = mob.level().getGameTime();
        if (destination != null) {
            Vec3 pos = mob.position();
            double flat = horizontalDistance(pos, destination);
            if (flat < 4) {
                anchor = destination;
                destination = null;
                return true;
            }
            if (now >= progressCheckAt) {
                if (horizontalDistance(progressFrom, pos) < 3) {
                    // Blocked, or at the edge of the loaded world. Settle here.
                    anchor = pos;
                    destination = null;
                    return true;
                }
                progressFrom = pos;
                progressCheckAt = now + STUCK_CHECK_TICKS;
            }
            if (mob.getNavigation().isDone()) {
                Vec3 dir = new Vec3(destination.x - pos.x, 0, destination.z - pos.z).normalize();
                double len = Math.min(LEG, flat);
                Vec3 leg = pos.add(dir.scale(len));
                BlockPos ground = surface(leg);
                if (ground == null) {
                    ground = surface(pos.add(dir.scale(len / 2)));
                }
                if (ground != null) {
                    mob.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, speed);
                }
            }
            return false;
        }

        if (anchor == null) {
            anchor = mob.position();
        }
        double fromAnchor = horizontalDistance(mob.position(), anchor);
        if (fromAnchor > anchorRange + 1) {
            mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, speed);
        } else if (anchorRange > 2 && now >= nextStroll && mob.getNavigation().isDone()) {
            RandomSource random = mob.getRandom();
            nextStroll = now + 60 + random.nextInt(100);
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * anchorRange;
            BlockPos ground = surface(anchor.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist));
            if (ground != null) {
                mob.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, speed * 0.7);
            }
        }
        return true;
    }

    private BlockPos surface(Vec3 at) {
        return surface((ServerLevel) mob.level(), at);
    }

    /** Top solid block position at a point, or null if that chunk isn't loaded. */
    public static BlockPos surface(ServerLevel level, Vec3 at) {
        BlockPos column = BlockPos.containing(at.x, 0, at.z);
        if (level.getChunkSource().getChunkNow(column.getX() >> 4, column.getZ() >> 4) == null) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new BlockPos(column.getX(), y, column.getZ());
    }

    /**
     * A random point between min and max blocks from origin that isn't ocean or river. Unloaded
     * points are allowed (they're travel goals); loaded ones must also be dry ground.
     */
    public static Vec3 pickLandPoint(ServerLevel level, Vec3 origin, double min, double max, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(random.nextDouble(), min, max);
            double x = origin.x + Math.cos(angle) * dist;
            double z = origin.z + Math.sin(angle) * dist;
            BlockPos column = BlockPos.containing(x, origin.y, z);
            var biome = level.getBiome(column);
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
                continue;
            }
            BlockPos ground = surface(level, new Vec3(x, 0, z));
            if (ground == null) {
                return new Vec3(x, origin.y, z);
            }
            if (level.getFluidState(ground.below()).isEmpty() && level.getFluidState(ground).isEmpty()) {
                return Vec3.atBottomCenterOf(ground);
            }
        }
        return null;
    }

    /** Like {@link #pickLandPoint}, but only accepts loaded, entity-ticking, dry ground. Used for spawning. */
    public static BlockPos pickSpawnPoint(ServerLevel level, Vec3 origin, double min, double max, RandomSource random) {
        for (int attempt = 0; attempt < 40; attempt++) {
            // Later attempts move closer in case the far ring isn't loaded.
            double shrink = attempt < 20 ? 1 : 0.5;
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(random.nextDouble(), min, max) * shrink;
            Vec3 at = origin.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            BlockPos ground = surface(level, at);
            if (ground == null || !level.isPositionEntityTicking(ground)) {
                continue;
            }
            var biome = level.getBiome(ground);
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
                continue;
            }
            // The heightmap counts water as motion-blocking, so a lake surface shows up here as fluid below.
            if (!level.getFluidState(ground.below()).isEmpty() || !level.getFluidState(ground).isEmpty()) {
                continue;
            }
            return ground;
        }
        return null;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
