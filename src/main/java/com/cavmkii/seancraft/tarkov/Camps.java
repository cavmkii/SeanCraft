package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.Cfg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Camps (design doc 2.5), one dimension's worth: where each player's camp stands. One per player; a new one packs the
 * old one up. A lit camp keeps hostile mobs from spawning close by, sets respawn like a bed and counts as a fire with a
 * roof for Rested.
 */
public final class Camps extends SavedData {
    private static final String NAME = "seancraft_camps";
    /** Camp position to owner (null owner for camps placed in creative). */
    public final Map<BlockPos, UUID> camps = new HashMap<>();

    public static Camps get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Camps::new, Camps::load, null), NAME);
    }

    static void init() {
        // The block and kit register with TarkovContent.
    }

    /** The camp within {@code range} of a point, or null. */
    public static BlockPos near(ServerLevel level, Vec3 point, double range) {
        for (BlockPos pos : get(level).camps.keySet()) {
            if (Vec3.atCenterOf(pos).distanceToSqr(point) <= range * range) {
                return pos;
            }
        }
        return null;
    }

    /** True if a camp is close enough to count as a fire and a roof for resting (12). */
    public static boolean shelters(ServerLevel level, BlockPos at) {
        return near(level, Vec3.atCenterOf(at), 4) != null;
    }

    /** A player's camp in this dimension, or null. */
    public BlockPos of(UUID owner) {
        for (Map.Entry<BlockPos, UUID> e : camps.entrySet()) {
            if (owner.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    void put(BlockPos pos, UUID owner) {
        camps.put(pos.immutable(), owner);
        setDirty();
    }

    void remove(BlockPos pos) {
        if (camps.remove(pos) != null) {
            setDirty();
        }
    }

    public static double safeRadius() {
        return Cfg.campSafeRadius;
    }

    private static Camps load(CompoundTag tag, HolderLookup.Provider registries) {
        Camps data = new Camps();
        for (Tag t : tag.getList("camps", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.camps.put(BlockPos.of(c.getLong("pos")), c.hasUUID("owner") ? c.getUUID("owner") : null);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        camps.forEach((pos, owner) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            if (owner != null) {
                c.putUUID("owner", owner);
            }
            list.add(c);
        });
        tag.put("camps", list);
        return tag;
    }
}
