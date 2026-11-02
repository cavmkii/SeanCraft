package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.parts.PartItems;
import com.cavmkii.seancraft.parts.WeaponBenchMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * The stations inside a hideout, one of each kind (the closest to the bench), so their screens can tab between each
 * other (2.5.3): the Hideout Bench, a Weapon Bench, a crafting table and each module. Opening one from a tab works
 * from anywhere in the same hideout.
 */
public final class Stations {
    public static final String HIDEOUT = "hideout";
    public static final String WEAPON_BENCH = "weapon_bench";
    public static final String CRAFTING_TABLE = "crafting_table";

    private record Cached(long time, Map<String, BlockPos> found) {}

    /** Scans are a few seconds old at most; keyed by the hideout's bench. */
    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();

    private Stations() {}

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(TarkovNet.OpenStation.TYPE, (p, ctx) -> open(ctx.player(), p.pos()));
    }

    /** A module block's station kind, like "module_workshop". */
    public static String moduleKind(Module module) {
        return "module_" + module.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String kindOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ModuleBlock module) {
            return moduleKind(module.module);
        }
        return block == PartItems.WEAPON_BENCH ? WEAPON_BENCH : block == Blocks.CRAFTING_TABLE ? CRAFTING_TABLE : null;
    }

    public static Map<String, BlockPos> find(ServerLevel level, Hideouts.Hideout h) {
        long now = level.getGameTime();
        Cached cached = CACHE.get(h.pos);
        if (cached != null && now - cached.time < 60) {
            return cached.found;
        }
        Map<String, BlockPos> found = new LinkedHashMap<>();
        found.put(HIDEOUT, h.pos);
        Map<String, Double> best = new HashMap<>();
        double r = h.radius();
        int minY = Math.max(level.getMinBuildHeight(), h.pos.getY() - 24);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, h.pos.getY() + 24);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int cx = (int) Math.floor((h.pos.getX() - r) / 16); cx <= (int) Math.floor((h.pos.getX() + r) / 16); cx++) {
            for (int cz = (int) Math.floor((h.pos.getZ() - r) / 16); cz <= (int) Math.floor((h.pos.getZ() + r) / 16); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int si = 0; si < chunk.getSectionsCount(); si++) {
                    LevelChunkSection section = chunk.getSection(si);
                    int baseY = level.getSectionYFromSectionIndex(si) << 4;
                    // Skip sections that can't hold a station (most of them), before looking at single blocks.
                    if (baseY + 15 < minY || baseY > maxY || section.hasOnlyAir() || !section.maybeHas(s -> kindOf(s) != null)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                String kind = kindOf(section.getBlockState(x, y, z));
                                if (kind == null) {
                                    continue;
                                }
                                at.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                double d = at.distSqr(h.pos);
                                if (h.contains(Vec3.atCenterOf(at)) && d < best.getOrDefault(kind, Double.MAX_VALUE)) {
                                    best.put(kind, d);
                                    found.put(kind, at.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        CACHE.put(h.pos, new Cached(now, found));
        return found;
    }

    /** Tells the player which stations their hideout has (an empty list outside one). */
    public static void send(ServerPlayer player) {
        Hideouts.Hideout h = Hideouts.at(player.serverLevel(), player.position());
        List<String> kinds = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        if (h != null) {
            find(player.serverLevel(), h).forEach((kind, pos) -> {
                kinds.add(kind);
                positions.add(pos);
            });
        }
        ServerPlayNetworking.send(player, new TarkovNet.StationList(kinds, positions));
    }

    /** Opens the station at a position for a tab click, if it's in the hideout the player is standing in. */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Hideouts.Hideout h = Hideouts.at(level, player.position());
        if (h == null || !h.contains(Vec3.atCenterOf(pos)) || !player.isAlive()) {
            return;
        }
        if (pos.equals(h.pos)) {
            player.closeContainer();
            Tarkov.openBench(player, pos);
            return;
        }
        if (level.getBlockState(pos).getBlock() instanceof ModuleBlock module && pos.equals(h.modules[module.module.ordinal()])) {
            player.closeContainer();
            Tarkov.useModule(player, h, module.module);
            return;
        }
        String kind = kindOf(level.getBlockState(pos));
        if (WEAPON_BENCH.equals(kind)) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WeaponBenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.literal("Weapon Bench")));
            send(player);
        } else if (CRAFTING_TABLE.equals(kind)) {
            player.closeContainer();
            com.cavmkii.seancraft.tetris.Tetris.openCrafting(player);
        }
    }
}
