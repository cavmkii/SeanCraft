package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.Cfg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Every Hideout Bench in one dimension, with its module levels. Saved with that dimension. */
public final class Hideouts extends SavedData {
    private static final String NAME = "seancraft_hideouts";

    public static final class Hideout {
        public final BlockPos pos;
        public final int[] levels = new int[Module.values().length];
        /** Module being built, or -1. */
        public int building = -1;
        public long buildDone;
        public long buildStarted;
        /** Materials put toward each module's next level so far, per cost line. */
        public final int[][] given = new int[Module.values().length][];
        /** Who placed the bench; it counts against their hideout limit. Null for benches placed before owners existed. */
        public java.util.UUID owner;
        /** Where each module's block stands, or null: a module only works while its block is in the hideout (2.5). */
        public final BlockPos[] modules = new BlockPos[Module.values().length];
        /** Parts waiting in the Recovery crate (9.4.9), by player. */
        public final java.util.Map<java.util.UUID, List<net.minecraft.world.item.ItemStack>> recovery = new java.util.HashMap<>();

        Hideout(BlockPos pos) {
            this.pos = pos;
        }

        /** A module's level, or 0 while its block isn't placed here. */
        public int level(Module module) {
            return modules[module.ordinal()] != null ? levels[module.ordinal()] : 0;
        }

        /**
         * A module's block went down here. It brings its own level; a hideout from before module blocks keeps the
         * level it had if that's higher.
         */
        void place(Module module, BlockPos at, int carried) {
            int i = module.ordinal();
            modules[i] = at.immutable();
            levels[i] = Math.max(levels[i], carried);
        }

        /** A module's block was broken: the hideout loses the module and the block takes the level with it. */
        int pickUp(Module module) {
            int i = module.ordinal();
            int kept = levels[i];
            modules[i] = null;
            levels[i] = 0;
            given[i] = null;
            if (building == i) {
                building = -1;
            }
            return kept;
        }

        /** Contributions toward a module's next level, sized to its cost list. */
        public int[] given(Module module) {
            int i = module.ordinal();
            int lines = level(module) < module.max() ? module.levels.get(level(module)).costs().size() : 0;
            if (given[i] == null || given[i].length != lines) {
                int[] fresh = new int[lines];
                if (given[i] != null) {
                    System.arraycopy(given[i], 0, fresh, 0, Math.min(lines, given[i].length));
                }
                given[i] = fresh;
            }
            return given[i];
        }

        public double radius() {
            return Cfg.hideoutRadius + level(Module.PERIMETER) * Module.PERIMETER_STEP;
        }

        public boolean contains(Vec3 point) {
            double r = radius();
            return Vec3.atCenterOf(pos).distanceToSqr(point) <= r * r;
        }
    }

    public final List<Hideout> list = new ArrayList<>();

    public static Hideouts get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Hideouts::new, Hideouts::load, null), NAME);
    }

    /** The hideout whose zone contains a point, or null (the biggest-level one if zones overlap). */
    public static Hideout at(ServerLevel level, Vec3 point) {
        Hideout best = null;
        for (Hideout h : get(level).list) {
            if (h.contains(point) && (best == null || h.radius() > best.radius())) {
                best = h;
            }
        }
        return best;
    }

    public static Hideout byPos(ServerLevel level, BlockPos pos) {
        for (Hideout h : get(level).list) {
            if (h.pos.equals(pos)) {
                return h;
            }
        }
        return null;
    }

    void add(BlockPos pos) {
        if (byPosIn(pos) == null) {
            list.add(new Hideout(pos.immutable()));
            setDirty();
        }
    }

    void setOwner(BlockPos pos, java.util.UUID owner) {
        Hideout h = byPosIn(pos);
        if (h != null) {
            h.owner = owner;
            setDirty();
        }
    }

    /** Hideout Benches a player has placed, in every dimension. */
    public static int ownedBy(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Hideout h : get(level).list) {
                if (player.equals(h.owner)) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Why a Hideout Bench can't go here, or null if it can (2.5): too close to another hideout or a spawn point, or
     * the player already has their limit.
     */
    public static String placementProblem(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (player != null && player.isCreative()) {
            return null;
        }
        double spacing = Cfg.hideoutSpacing;
        Vec3 at = Vec3.atCenterOf(pos);
        for (Hideout h : get(level).list) {
            double d = Math.sqrt(Vec3.atCenterOf(h.pos).distanceToSqr(at));
            if (d < spacing) {
                return "Too close to another hideout (" + (int) d + " blocks; hideouts need " + (int) spacing + ").";
            }
        }
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            for (BlockPos p : SpawnPoints.get(level).points) {
                double d = Math.sqrt(Vec3.atCenterOf(p).distanceToSqr(at));
                if (d < spacing) {
                    return "Too close to a spawn point (" + (int) d + " blocks; hideouts need " + (int) spacing + "). Head "
                            + away(Vec3.atCenterOf(p), at) + " another " + (int) Math.ceil(spacing - d) + ".";
                }
            }
        }
        if (com.cavmkii.seancraft.boss.BossSites.nearSite(level, at, com.cavmkii.seancraft.Cfg.pedestalClearance)) {
            return "Too close to a boss dungeon (hideouts need " + (int) com.cavmkii.seancraft.Cfg.pedestalClearance + " blocks).";
        }
        if (player != null && ownedBy(level.getServer(), player.getUUID()) >= Cfg.maxHideouts) {
            return "You already have " + Cfg.maxHideouts + " hideouts. Break one of your Hideout Benches to move it.";
        }
        return null;
    }

    /** The compass direction leading away from {@code from}, as seen at {@code at}. */
    private static String away(Vec3 from, Vec3 at) {
        double dx = at.x - from.x;
        double dz = at.z - from.z;
        String ns = Math.abs(dz) > Math.abs(dx) * 0.4 ? (dz > 0 ? "south" : "north") : "";
        String ew = Math.abs(dx) > Math.abs(dz) * 0.4 ? (dx > 0 ? "east" : "west") : "";
        return ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
    }

    void remove(BlockPos pos) {
        if (list.removeIf(h -> h.pos.equals(pos))) {
            setDirty();
        }
    }

    private Hideout byPosIn(BlockPos pos) {
        for (Hideout h : list) {
            if (h.pos.equals(pos)) {
                return h;
            }
        }
        return null;
    }

    private static Hideouts load(CompoundTag tag, HolderLookup.Provider registries) {
        Hideouts data = new Hideouts();
        for (Tag t : tag.getList("hideouts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Hideout h = new Hideout(BlockPos.of(c.getLong("pos")));
            int[] levels = c.getIntArray("levels");
            System.arraycopy(levels, 0, h.levels, 0, Math.min(levels.length, h.levels.length));
            h.building = c.contains("building") ? c.getInt("building") : -1;
            h.buildDone = c.getLong("buildDone");
            h.buildStarted = c.getLong("buildStarted");
            if (c.hasUUID("owner")) {
                h.owner = c.getUUID("owner");
            }
            for (Module m : Module.values()) {
                if (c.contains("given_" + m.name())) {
                    h.given[m.ordinal()] = c.getIntArray("given_" + m.name());
                }
                if (c.contains("module_" + m.name())) {
                    h.modules[m.ordinal()] = BlockPos.of(c.getLong("module_" + m.name()));
                }
            }
            for (Tag rt : c.getList("recovery", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) rt;
                List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
                for (Tag it : r.getList("items", Tag.TAG_COMPOUND)) {
                    net.minecraft.world.item.ItemStack.parse(registries, it).ifPresent(items::add);
                }
                h.recovery.put(r.getUUID("player"), items);
            }
            data.list.add(h);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Hideout h : this.list) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", h.pos.asLong());
            c.putIntArray("levels", h.levels);
            c.putInt("building", h.building);
            c.putLong("buildDone", h.buildDone);
            c.putLong("buildStarted", h.buildStarted);
            if (h.owner != null) {
                c.putUUID("owner", h.owner);
            }
            for (Module m : Module.values()) {
                if (h.given[m.ordinal()] != null) {
                    c.putIntArray("given_" + m.name(), h.given[m.ordinal()]);
                }
                if (h.modules[m.ordinal()] != null) {
                    c.putLong("module_" + m.name(), h.modules[m.ordinal()].asLong());
                }
            }
            ListTag rec = new ListTag();
            h.recovery.forEach((player, items) -> {
                CompoundTag r = new CompoundTag();
                r.putUUID("player", player);
                ListTag its = new ListTag();
                for (net.minecraft.world.item.ItemStack s : items) {
                    if (!s.isEmpty()) {
                        its.add(s.save(registries));
                    }
                }
                r.put("items", its);
                rec.add(r);
            });
            c.put("recovery", rec);
            list.add(c);
        }
        tag.put("hideouts", list);
        return tag;
    }
}
