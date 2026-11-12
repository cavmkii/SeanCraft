package com.cavmkii.seancraft.compat;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Distance;
import com.cavmkii.seancraft.tarkov.Med;
import com.cavmkii.seancraft.tarkov.TarkovContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * When Dungeons Arise (design doc 18.1): its dungeons stay out of the Heartland, only a share of the rest are built
 * (World.DungeonsKeep) so they're sparse, and their chests get extra loot further out. Matched by structure and loot
 * table namespace, so nothing here touches WDA's code.
 */
public final class Dungeons {
    public static final String NS = "dungeons_arise";

    /** World spawn, for world-gen threads that have no level to ask. */
    private static volatile BlockPos spawn = BlockPos.ZERO;

    /** A loot condition: the chest is at least this many bands out (0 Heartland .. 3 Far lands). */
    public record BandAtLeast(int band) implements LootItemCondition {
        static final MapCodec<BandAtLeast> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("band").forGetter(BandAtLeast::band)).apply(i, BandAtLeast::new));
        static final LootItemConditionType TYPE = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, SeanCraft.id("band_at_least"),
                new LootItemConditionType(CODEC));

        @Override
        public LootItemConditionType getType() {
            return TYPE;
        }

        @Override
        public boolean test(LootContext ctx) {
            Vec3 at = ctx.getParamOrNull(LootContextParams.ORIGIN);
            return at != null && Distance.at(ctx.getLevel(), at).ordinal() >= band;
        }
    }

    private Dungeons() {}

    /** World spawn as last seen on the server, for world-gen code. */
    public static BlockPos spawn() {
        return spawn;
    }

    public static void init() {
        BandAtLeast.TYPE.getClass();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> spawn = server.overworld().getSharedSpawnPos());
        // Earlier, so the spawn area's own chunks see it (an existing world knows its spawn when it loads).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server, level) -> {
            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                spawn = level.getSharedSpawnPos();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 200 == 0) {
                spawn = server.overworld().getSharedSpawnPos();
            }
        });
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!key.location().getNamespace().equals(NS) || !key.location().getPath().startsWith("chests/")) {
                return;
            }
            // Frontier and further: an extra roll of supplies; the Far lands another, richer one.
            builder.withPool(LootPool.lootPool().when(() -> new BandAtLeast(Distance.FRONTIER.ordinal()))
                    .setRolls(UniformGenerator.between(1, 2))
                    .add(entry(TarkovContent.MEDS.get(Med.FIELD_KIT), 3, 1, 1))
                    .add(entry(TarkovContent.MEDS.get(Med.PAINKILLERS), 4, 1, 2))
                    .add(entry(Items.IRON_INGOT, 6, 2, 5))
                    .add(entry(Items.GOLD_INGOT, 4, 2, 4))
                    .add(entry(Items.DIAMOND, 2, 1, 2)));
            builder.withPool(LootPool.lootPool().when(() -> new BandAtLeast(Distance.FAR_LANDS.ordinal()))
                    .setRolls(UniformGenerator.between(1, 3))
                    .add(entry(TarkovContent.MEDS.get(Med.SURGICAL_KIT), 3, 1, 1))
                    .add(entry(Items.DIAMOND, 5, 1, 3))
                    .add(entry(Items.NETHERITE_SCRAP, 2, 1, 1))
                    .add(entry(Items.ECHO_SHARD, 2, 1, 2))
                    .add(entry(Items.BREEZE_ROD, 2, 1, 2)));
        });
    }

    private static LootItem.Builder<?> entry(Item item, int weight, int min, int max) {
        var e = LootItem.lootTableItem(item).setWeight(weight);
        if (max > 1) {
            e.apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
        }
        return e;
    }

    /**
     * Whether a WDA structure may start in this chunk: never in the Heartland, and only a World.DungeonsKeep share
     * elsewhere, picked from the seed so the same world always gets the same ones. Anything else is left alone.
     */
    public static boolean allowed(Holder<Structure> structure, ChunkPos chunk, long seed) {
        if (!structure.unwrapKey().map(k -> k.location().getNamespace().equals(NS)).orElse(false)) {
            return true;
        }
        BlockPos s = spawn;
        double dx = chunk.getMiddleBlockX() - s.getX();
        double dz = chunk.getMiddleBlockZ() - s.getZ();
        if (Cfg.distanceBands.length > 0 && dx * dx + dz * dz < Cfg.distanceBands[0] * Cfg.distanceBands[0]) {
            return false;
        }
        long h = seed ^ chunk.toLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53 < Cfg.dungeonsKeep;
    }
}
