package com.cavmkii.seancraft.hazard;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.boss.BossContent;
import com.cavmkii.seancraft.boss.BossItems;
import com.cavmkii.seancraft.boss.BossSites;
import com.cavmkii.seancraft.tarkov.Distance;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Biome hazards (design doc 9.3.3, 10). Darkness in the deep dark and unlit pits below Y 0, heat in the desert, cold in
 * the frozen far lands and on the high frozen peaks. Without the hazard's key or a potion pass, it drains health. Keys
 * are boss drops; a pass is 3 minutes of tolerance from the hazard's potion.
 */
public final class Hazards {
    public enum Hazard {
        DARKNESS("Darkness", "Night Vision", 0x2A1040),
        HEAT("Heat", "Fire Resistance", 0xE07010),
        COLD("Cold", "Warmth", 0x80C8FF);

        public final String label;
        public final String potion;
        public final int glow;

        Hazard(String label, String potion, int glow) {
            this.label = label;
            this.potion = potion;
            this.glow = glow;
        }
    }

    /** What a player's hazard is doing to them, for the HUD. */
    public static final int DRAINING = 0, KEY = 1, PASS = 2;

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, SeanCraft.id("hazard"));
    /** Share of max health drained each second with no tolerance: about a minute and a half to empty. */
    static final float DRAIN_PER_SECOND = 0.01f;
    static final int PASS_TICKS = 3 * 60 * 20;
    /** Below Y 0, a spot at least this lit is a lit tunnel or cavern, not a pit. */
    static final int LIT = 8;
    /** Frozen biomes only count as hazards this far out; frozen and jagged peaks count anywhere. */
    static final double FROZEN_FAR = Distance.FAR_LANDS.from;
    static final int HIGH_PEAKS_Y = 120;

    public static final MobEffect WARMTH_EFFECT = new MobEffect(MobEffectCategory.BENEFICIAL, 0xE8743B) {};
    public static final Holder<MobEffect> WARMTH = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, SeanCraft.id("warmth"), WARMTH_EFFECT);
    public static final Holder<Potion> WARMTH_POTION = Registry.registerForHolder(BuiltInRegistries.POTION, SeanCraft.id("warmth"),
            new Potion("warmth", new MobEffectInstance(WARMTH, PASS_TICKS)));

    /** Pass expiry per hazard, by player, in game time. Not saved: a pass is only 3 minutes. */
    private static final Map<UUID, long[]> PASSES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

    private Hazards() {}

    /** Server to client: the hazard you're in (-1 for none), what it's doing, and seconds left on your pass. */
    public record Sync(int hazard, int state, int passSeconds) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SeanCraft.id("hazard"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::hazard, ByteBufCodecs.VAR_INT, Sync::state, ByteBufCodecs.VAR_INT, Sync::passSeconds, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> builder.addMix(Potions.AWKWARD, Items.MAGMA_BLOCK, WARMTH_POTION));
        ServerTickEvents.END_SERVER_TICK.register(Hazards::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PASSES.remove(handler.player.getUUID());
            LAST_SENT.remove(handler.player.getUUID());
        });
    }

    /** A potion took effect: its hazard is tolerated for 3 minutes from now, however long the effect itself lasts. */
    public static void onEffect(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        Hazard hazard = effect.is(MobEffects.NIGHT_VISION) ? Hazard.DARKNESS : effect.is(MobEffects.FIRE_RESISTANCE) ? Hazard.HEAT
                : effect.is(WARMTH) ? Hazard.COLD : null;
        if (hazard != null) {
            PASSES.computeIfAbsent(player.getUUID(), id -> new long[Hazard.values().length])[hazard.ordinal()] =
                    player.serverLevel().getGameTime() + PASS_TICKS;
        }
    }

    /** The hazard at a player's spot, or null. Boss arenas are never hazards, so every dungeon can be reached. */
    public static Hazard at(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || player.isCreative() || player.isSpectator()) {
            return null;
        }
        BlockPos pos = player.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);
        Hazard hazard = null;
        if (biome.is(Biomes.DEEP_DARK) || pos.getY() < 0 && level.getBrightness(LightLayer.BLOCK, pos) < LIT
                && level.getBrightness(LightLayer.BLOCK, pos.above()) < LIT) {
            hazard = Hazard.DARKNESS;
        } else if (biome.is(Biomes.DESERT)) {
            hazard = Hazard.HEAT;
        } else if (biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) && pos.getY() >= HIGH_PEAKS_Y
                || frozen(biome) && Distance.blocksFromSpawn(level, player.position()) >= FROZEN_FAR) {
            hazard = Hazard.COLD;
        }
        if (hazard != null && BossSites.nearSite(level, player.position(), 32)) {
            return null;
        }
        return hazard;
    }

    /** Frozen biomes, not just snowy ones: the snowy taiga has no hazard (9.3.3). */
    private static boolean frozen(Holder<Biome> biome) {
        return biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN) || biome.is(Biomes.FROZEN_RIVER) || biome.is(Biomes.SNOWY_BEACH)
                || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.GROVE);
    }

    /** The boss drop that makes a hazard harmless, if it exists yet. Heat and cold keys come from tier 2 bosses. */
    public static boolean hasKey(ServerPlayer player, Hazard hazard) {
        return switch (hazard) {
            case DARKNESS -> BossItems.FollowerLantern.carries(player);
            case HEAT, COLD -> false;
        };
    }

    private static long passLeft(ServerPlayer player, Hazard hazard) {
        long[] passes = PASSES.get(player.getUUID());
        return passes == null ? 0 : Math.max(0, passes[hazard.ordinal()] - player.serverLevel().getGameTime());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Hazard hazard = player.isAlive() ? at(player) : null;
            int state = -1;
            long pass = 0;
            if (hazard != null) {
                pass = passLeft(player, hazard);
                state = hasKey(player, hazard) ? KEY : pass > 0 ? PASS : DRAINING;
                if (state == DRAINING) {
                    drain(player, hazard);
                }
            }
            int packed = hazard == null ? -1 : hazard.ordinal() * 4 + state;
            Integer last = LAST_SENT.get(player.getUUID());
            if (last == null || last != packed || state == PASS) {
                LAST_SENT.put(player.getUUID(), packed);
                ServerPlayNetworking.send(player, new Sync(hazard == null ? -1 : hazard.ordinal(), state, (int) (pass / 20)));
            }
        }
    }

    private static void drain(ServerPlayer player, Hazard hazard) {
        switch (hazard) {
            case DARKNESS -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            case COLD -> player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + 60));
            case HEAT -> {}
        }
        player.hurt(player.damageSources().source(DAMAGE), player.getMaxHealth() * DRAIN_PER_SECOND);
    }

    /** The key item for a hazard, for the HUD's hint. */
    public static String keyName(Hazard hazard) {
        return switch (hazard) {
            case DARKNESS -> BossContent.FOLLOWER_LANTERN.getDescription().getString();
            case HEAT -> "Isaac's Cooler";
            case COLD -> "Mr Huber's Warden's Coat";
        };
    }
}
