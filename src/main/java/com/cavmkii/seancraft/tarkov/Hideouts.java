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

/** Every Home Bench (home base) in one dimension, with its module levels. Saved with that dimension. The class keeps its old name. */
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
            int perimeter = level(Module.PERIMETER);
            return perimeter == 0 ? Cfg.homeRadius : Module.PERIMETER_RADIUS[Math.min(perimeter, 3) - 1];
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


    /**
     * Why a Home Bench can't go here, or null if it can (2.4): inside another player's home base, or too close to a
     * Summoning Pedestal. A player's own earlier bench is picked up when the new one goes down.
     */
    public static String placementProblem(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (player != null && player.isCreative()) {
            return null;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        for (Hideout h : get(level).list) {
            if (h.contains(at) && h.owner != null && player != null && !h.owner.equals(player.getUUID())) {
                return "This is someone else's home base. Build here with them, or found your own elsewhere.";
            }
        }
        if (com.cavmkii.seancraft.boss.BossSites.nearSite(level, at, Cfg.pedestalClearance)) {
            return "Too close to a boss dungeon (a Home Bench needs " + (int) Cfg.pedestalClearance + " blocks).";
        }
        return null;
    }

    /** A player's Home Benches, oldest first, in every dimension: level and position. */
    public static List<Object[]> benchesOf(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
        List<Object[]> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Hideout h : get(level).list) {
                if (player.equals(h.owner)) {
                    out.add(new Object[] {level, h.pos});
                }
            }
        }
        return out;
    }

    /** Admin and testing: set a module's level at the home base the player stands in. */
    public static String setLevel(net.minecraft.server.level.ServerPlayer player, String module, int level) {
        Module m;
        try {
            m = Module.valueOf(module.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "No module " + module + ".";
        }
        Hideout h = at(player.serverLevel(), player.position());
        if (h == null) {
            return "Stand in a home base.";
        }
        h.levels[m.ordinal()] = Math.min(level, m.max());
        get(player.serverLevel()).setDirty();
        return m.label + " is now level " + h.levels[m.ordinal()] + ".";
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
            list.add(c);
        }
        tag.put("hideouts", list);
        return tag;
    }
}
