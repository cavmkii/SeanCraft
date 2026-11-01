package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Distance;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Boss dungeons (design doc 8.4.10, 10). Each boss with a home gets one site in the overworld, in its home biome and
 * inside its tier's distance band from world spawn. The site is chosen when the server starts and the arena is built
 * the first time a player comes near: a 40-block ring in the boss's style with the Summoning Pedestal in the middle.
 */
public final class BossSites extends SavedData {
    private static final String NAME = "seancraft_boss_sites";
    static final int RADIUS = 20;
    /** A player this close (horizontally) gets the arena built. */
    static final double BUILD_DISTANCE = 96;
    /** No Hideout Bench within this of a pedestal (2.5, Raid.PedestalClearance). */
    public static final double PEDESTAL_CLEARANCE = 100;

    /** A dungeon: its pedestal (or, until built, where it will go) and whether it's built. */
    public static final class Site {
        public BlockPos pos;
        public boolean built;

        Site(BlockPos pos, boolean built) {
            this.pos = pos;
            this.built = built;
        }
    }

    public final Map<String, Site> sites = new LinkedHashMap<>();

    public static BossSites get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BossSites::new, BossSites::load, null), NAME);
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> plan(server.overworld()));
        ServerTickEvents.END_SERVER_TICK.register(BossSites::tick);
    }

    /** Distance band for a tier (10): tier 1 at 500-2,000 blocks, tier 2 at 2,000-4,500, tier 3 at 4,500-8,000. */
    static double[] band(int tier) {
        return switch (tier) {
            case 1 -> new double[] {500, 2000};
            case 2 -> new double[] {2000, 4500};
            default -> new double[] {4500, 8000};
        };
    }

    // ================================================================== choosing sites

    private static void plan(ServerLevel level) {
        BossSites data = get(level);
        for (BossDef def : BossDef.all()) {
            if (def.home() == null || data.sites.containsKey(def.id())) {
                continue;
            }
            BlockPos pos = find(level, def);
            data.sites.put(def.id(), new Site(pos, false));
            data.setDirty();
            SeanCraft.LOG.info("{}'s dungeon will be at {} ({} blocks from spawn)", def.name(), pos.toShortString(),
                    (int) Distance.blocksFromSpawn(level, Vec3.atCenterOf(pos)));
        }
    }

    private static Predicate<Holder<Biome>> matcher(BossDef.Home home) {
        return biome -> {
            for (String b : home.biomes()) {
                if (b.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(b.substring(1))))
                        : biome.is(ResourceLocation.parse(b))) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Tries points at random angles and distances in the band, looking for the home biome near each. */
    private static BlockPos find(ServerLevel level, BossDef def) {
        BlockPos spawn = level.getSharedSpawnPos();
        double[] band = band(def.tier());
        Random random = new Random(level.getSeed() ^ def.id().hashCode());
        int y = def.home().underground() ? -40 : level.getSeaLevel() + 8;
        Predicate<Holder<Biome>> wanted = matcher(def.home());
        BlockPos fallback = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = band[0] + 150 + random.nextDouble() * (band[1] - band[0] - 300);
            BlockPos origin = new BlockPos(spawn.getX() + (int) (Math.cos(angle) * dist), y, spawn.getZ() + (int) (Math.sin(angle) * dist));
            if (fallback == null) {
                fallback = origin;
            }
            // A vertical step bigger than the world keeps the search at this one height (surface, or deep-dark depth).
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(wanted, origin, 640, 32, 512);
            if (found == null) {
                continue;
            }
            BlockPos p = flattest(level, def, wanted, found.getFirst());
            if (p == null) {
                continue;
            }
            double d = Math.sqrt(Math.pow(p.getX() - spawn.getX(), 2) + Math.pow(p.getZ() - spawn.getZ(), 2));
            if (d >= band[0] + RADIUS && d <= band[1] - RADIUS && clearOfStructures(level, p)) {
                return new BlockPos(p.getX(), y, p.getZ());
            }
        }
        SeanCraft.LOG.warn("No {} biome found for {} in its distance band; the dungeon goes in whatever is there.", def.home().biomes(), def.name());
        return fallback;
    }

    /** Ancient cities (wardens), trial chambers and villages: an arena dug or flattened into one would wreck both. */
    static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> AVOIDED = TagKey.create(Registries.STRUCTURE,
            SeanCraft.id("avoided_by_dungeons"));
    static final int STRUCTURE_CLEARANCE = 96;

    private static boolean clearOfStructures(ServerLevel level, BlockPos p) {
        BlockPos near = level.findNearestMapStructure(AVOIDED, p, 8, false);
        return near == null || Math.hypot(near.getX() - p.getX(), near.getZ() - p.getZ()) > STRUCTURE_CLEARANCE;
    }

    /** Most the ground may rise or fall across a surface arena before the spot is passed over. */
    static final int MAX_RELIEF = 10;

    /**
     * Near a biome hit, the spot where the ring would sit on the flattest ground (read from the terrain generator, so
     * nothing has to be generated). Null if nothing nearby is flat enough. Underground arenas are carved, so any spot
     * will do.
     */
    private static BlockPos flattest(ServerLevel level, BossDef def, Predicate<Holder<Biome>> wanted, BlockPos hit) {
        if (def.home().underground()) {
            return hit;
        }
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        BlockPos best = null;
        int bestRelief = Integer.MAX_VALUE;
        for (int ox = -64; ox <= 64; ox += 32) {
            for (int oz = -64; oz <= 64; oz += 32) {
                int x = hit.getX() + ox;
                int z = hit.getZ() + oz;
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
                        net.minecraft.core.QuartPos.fromBlock(surface), net.minecraft.core.QuartPos.fromBlock(z), random.sampler());
                if (!wanted.test(biome)) {
                    continue;
                }
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (int i = 0; i < 9; i++) {
                    double a = Math.PI * 2 * i / 8;
                    double r = i == 8 ? 0 : RADIUS;
                    int h = generator.getBaseHeight(x + (int) (Math.cos(a) * r), z + (int) (Math.sin(a) * r), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
                // Mostly dry: a ring on open water would just be a sand island.
                int relief = max - min + (surface < level.getSeaLevel() ? 6 : 0);
                if (relief < bestRelief) {
                    bestRelief = relief;
                    best = new BlockPos(x, surface, z);
                }
            }
        }
        return bestRelief <= MAX_RELIEF ? best : null;
    }

    // ================================================================== queries

    /** True within {@code range} blocks (horizontally, and 24 vertically) of any dungeon. */
    public static boolean nearSite(ServerLevel level, Vec3 point, double range) {
        if (level != level.getServer().overworld()) {
            return false;
        }
        for (Site s : get(level).sites.values()) {
            double dx = s.pos.getX() + 0.5 - point.x;
            double dz = s.pos.getZ() + 0.5 - point.z;
            if (dx * dx + dz * dz <= range * range && (!s.built || Math.abs(s.pos.getY() - point.y) < 24 + range / 4)) {
                return true;
            }
        }
        return false;
    }

    /** The boss id for a totem, from its entity type (mark, daylan, bo_daddy). */
    static String bossId(BossContent.TotemItem totem) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(totem.boss()).getPath();
    }

    /** "About 1,200 blocks north-east, underground." for the totem's recipe entry, or empty if there's no site. */
    public static String hint(ServerPlayer player, BossContent.TotemItem totem) {
        Site s = get(player.server.overworld()).sites.get(bossId(totem));
        if (s == null) {
            return "";
        }
        BossDef def = BossDef.get(bossId(totem));
        double dx = s.pos.getX() - player.getX();
        double dz = s.pos.getZ() - player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 50) * 50;
        String where = player.level() == player.server.overworld() ? "About " + String.format("%,d", dist) + " blocks " + compass(dx, dz)
                : "In the overworld";
        return "Dungeon: " + where + (def != null && def.home() != null && def.home().underground() ? ", underground" : "")
                + ". Crafting it gives you a map.";
    }

    private static String compass(double dx, double dz) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    /** An explorer map marked with the boss's dungeon, like a cartographer's mansion map. */
    public static ItemStack map(ServerLevel overworld, BossContent.TotemItem totem) {
        Site s = get(overworld).sites.get(bossId(totem));
        if (s == null) {
            return ItemStack.EMPTY;
        }
        ItemStack map = MapItem.create(overworld, s.pos.getX(), s.pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(overworld, map);
        MapItemSavedData.addTargetDecoration(map, s.pos, "+", MapDecorationTypes.RED_X);
        BossDef def = BossDef.get(bossId(totem));
        map.set(DataComponents.ITEM_NAME, Component.literal((def != null ? def.name() : "Boss") + "'s Dungeon"));
        return map;
    }

    /** Admin list: every dungeon, where it is and whether it's built. */
    public static List<String> describe(ServerLevel overworld) {
        List<String> out = new ArrayList<>();
        get(overworld).sites.forEach((id, s) -> out.add(id + ": " + s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ()
                + (s.built ? " (built)" : " (not built yet)")));
        return out;
    }

    // ================================================================== building

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        BossSites data = get(level);
        for (Map.Entry<String, Site> e : data.sites.entrySet()) {
            Site s = e.getValue();
            if (s.built) {
                continue;
            }
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - s.pos.getX();
                double dz = p.getZ() - s.pos.getZ();
                if (dx * dx + dz * dz < BUILD_DISTANCE * BUILD_DISTANCE) {
                    build(level, e.getKey(), s);
                    data.setDirty();
                    break;
                }
            }
        }
    }

    /** Builds the arena for a site now (also used by the admin command). */
    public static boolean build(ServerLevel level, String boss) {
        Site s = get(level).sites.get(boss);
        if (s == null) {
            return false;
        }
        build(level, boss, s);
        get(level).setDirty();
        return true;
    }

    private static void build(ServerLevel level, String boss, Site s) {
        BossDef def = BossDef.get(boss);
        String style = def != null && def.home() != null ? def.home().style() : "beach";
        for (int cx = (s.pos.getX() - RADIUS - 2) >> 4; cx <= (s.pos.getX() + RADIUS + 2) >> 4; cx++) {
            for (int cz = (s.pos.getZ() - RADIUS - 2) >> 4; cz <= (s.pos.getZ() + RADIUS + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Random random = new Random(s.pos.asLong());
        BlockPos pedestal = switch (style) {
            case "cavern" -> cavern(level, s.pos, random);
            case "photo_shoot" -> photoShoot(level, s.pos, random);
            default -> beach(level, s.pos, random);
        };
        level.setBlock(pedestal, BossContent.PEDESTAL.defaultBlockState(), 2);
        s.pos = pedestal;
        s.built = true;
        SeanCraft.LOG.info("Built {}'s dungeon at {}", boss, pedestal.toShortString());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    /** Flattens the ring at the surface: a floor, solid ground under it, open air above. Returns the standing level. */
    private static int clearing(ServerLevel level, BlockPos c, BlockState floor, BlockState fill) {
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()), level.getSeaLevel() + 1);
        // Open to the sky: clear up to the highest thing in the ring, so no overhang is left hanging over it.
        int top = y;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX() + dx, c.getZ() + dz));
            }
        }
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                if (dx * dx + dz * dz > (RADIUS + 1) * (RADIUS + 1)) {
                    continue;
                }
                BlockPos column = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                for (int yy = y; yy <= top; yy++) {
                    set(level, column.atY(yy), Blocks.AIR.defaultBlockState());
                }
                set(level, column.below(), floor);
                for (int dy = 2; dy <= 12; dy++) {
                    BlockPos below = column.below(dy);
                    if (!level.getBlockState(below).canBeReplaced() && level.getFluidState(below).isEmpty()) {
                        break;
                    }
                    set(level, below, fill);
                }
            }
        }
        return y;
    }

    /** Mark: a sandy landing with a pier, barrels of tackle and lanterns on posts round the edge. */
    private static BlockPos beach(ServerLevel level, BlockPos c, Random random) {
        int y = clearing(level, c, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
        BlockPos mid = new BlockPos(c.getX(), y, c.getZ());
        for (int dx = 3; dx <= RADIUS; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, mid.offset(dx, -1, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        posts(level, mid, 18, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
        for (int i = 0; i < 5; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            int r = 6 + random.nextInt(10);
            set(level, mid.offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r)), Blocks.BARREL.defaultBlockState());
        }
        return mid.below();
    }

    /**
     * Daylan: a photo-shoot clearing in the cherry grove. Ring lights on stands, a backdrop with a taped X, a showroom
     * car built from blocks, and petals everywhere.
     */
    private static BlockPos photoShoot(ServerLevel level, BlockPos c, Random random) {
        int y = clearing(level, c, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        BlockPos mid = new BlockPos(c.getX(), y, c.getZ());
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + 0.3;
            BlockPos stand = mid.offset((int) Math.round(Math.cos(a) * 11), 0, (int) Math.round(Math.sin(a) * 11));
            set(level, stand, Blocks.BIRCH_FENCE.defaultBlockState());
            set(level, stand.above(), Blocks.BIRCH_FENCE.defaultBlockState());
            set(level, stand.above(2), Blocks.END_ROD.defaultBlockState());
        }
        // The backdrop, with the X taped on it.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                boolean tape = Math.abs(dx) == Math.abs(dy - 2) && Math.abs(dx) <= 2 && dy >= 0 && dy <= 4;
                set(level, mid.offset(dx, dy, -16), (tape ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState());
            }
        }
        // The car: wheels, body, windows, and a light bar on the roof.
        BlockPos car = mid.offset(12, 0, 2);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                boolean wheel = (dx == 0 || dx == 2) && (dz == 0 || dz == 4);
                set(level, car.offset(dx, 0, dz), (wheel ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState());
                set(level, car.offset(dx, 1, dz), Blocks.WHITE_CONCRETE.defaultBlockState());
                if (dz >= 1 && dz <= 3) {
                    set(level, car.offset(dx, 2, dz), (dz == 2 ? Blocks.WHITE_CONCRETE : Blocks.GLASS).defaultBlockState());
                }
            }
        }
        set(level, car.offset(0, 3, 2), Blocks.RED_STAINED_GLASS.defaultBlockState());
        set(level, car.offset(2, 3, 2), Blocks.BLUE_STAINED_GLASS.defaultBlockState());
        for (int i = 0; i < 90; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2 + random.nextDouble() * (RADIUS - 2);
            BlockPos p = mid.offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r));
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) {
                set(level, p, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + random.nextInt(4))
                        .setValue(PinkPetalsBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong()))));
            }
        }
        return mid.below();
    }

    /**
     * Bo Daddy: a lit cavern carved at the edge of the deep dark. A domed, walled hall, a blackstone stage in the
     * middle, lanterns hanging from the roof, sculk creeping in round the edges and three shriekers.
     */
    private static BlockPos cavern(ServerLevel level, BlockPos c, Random random) {
        int y = c.getY();
        int wall = RADIUS + 2;
        for (int dx = -wall; dx <= wall; dx++) {
            for (int dz = -wall; dz <= wall; dz++) {
                double r2 = dx * dx + dz * dz;
                if (r2 > wall * wall) {
                    continue;
                }
                BlockPos column = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                if (r2 > RADIUS * RADIUS) {
                    // A solid wall all round, so caves the dig cuts into can't pour water or lava in. You tunnel in.
                    for (int dy = -2; dy <= 11; dy++) {
                        set(level, column.above(dy), Blocks.DEEPSLATE.defaultBlockState());
                    }
                    continue;
                }
                double r = Math.sqrt(r2);
                int height = 10 - (int) (r2 / (RADIUS * RADIUS) * 5);
                for (int dy = 0; dy < height; dy++) {
                    set(level, column.above(dy), Blocks.AIR.defaultBlockState());
                }
                // Light panels in the roof: it's a lit cavern, a stage, not the deep dark.
                boolean panel = Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0;
                set(level, column.above(height), (panel ? Blocks.SHROOMLIGHT : Blocks.DEEPSLATE).defaultBlockState());
                set(level, column.above(height + 1), Blocks.DEEPSLATE.defaultBlockState());
                BlockState floor = r <= 4 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : r >= 16 + random.nextInt(3) ? Blocks.SCULK.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                set(level, column.below(), floor);
                set(level, column.below(2), Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        BlockPos mid = new BlockPos(c.getX(), y, c.getZ());
        // Lanterns from the roof, and stage lights set in the floor.
        for (int ring : new int[] {7, 13}) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + ring;
                int dx = (int) Math.round(Math.cos(a) * ring);
                int dz = (int) Math.round(Math.sin(a) * ring);
                int height = 10 - (int) ((dx * dx + dz * dz) / (double) (RADIUS * RADIUS) * 5);
                set(level, mid.offset(dx, height - 1, dz), Blocks.CHAIN.defaultBlockState());
                set(level, mid.offset(dx, height - 2, dz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            set(level, mid.offset((int) Math.round(Math.cos(a) * 5), -1, (int) Math.round(Math.sin(a) * 5)), Blocks.SHROOMLIGHT.defaultBlockState());
        }
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + 0.5;
            BlockPos p = mid.offset((int) Math.round(Math.cos(a) * BoDaddy.SHRIEKER_RADIUS), 0, (int) Math.round(Math.sin(a) * BoDaddy.SHRIEKER_RADIUS));
            set(level, p, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(BlockStateProperties.CAN_SUMMON, false));
        }
        return mid.below();
    }

    /** Fence posts with lanterns, evenly round a circle. */
    private static void posts(ServerLevel level, BlockPos mid, int radius, int count, BlockState post) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            BlockPos p = mid.offset((int) Math.round(Math.cos(a) * radius), 0, (int) Math.round(Math.sin(a) * radius));
            set(level, p, post);
            set(level, p.above(), Blocks.LANTERN.defaultBlockState());
        }
    }

    // ================================================================== saving

    private static BossSites load(CompoundTag tag, HolderLookup.Provider registries) {
        BossSites data = new BossSites();
        CompoundTag sites = tag.getCompound("sites");
        for (String id : sites.getAllKeys()) {
            CompoundTag s = sites.getCompound(id);
            data.sites.put(id, new Site(BlockPos.of(s.getLong("pos")), s.getBoolean("built")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag sites = new CompoundTag();
        this.sites.forEach((id, s) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", s.pos.asLong());
            t.putBoolean("built", s.built);
            sites.put(id, t);
        });
        tag.put("sites", sites);
        return tag;
    }
}
