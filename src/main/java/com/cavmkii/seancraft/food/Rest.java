package com.cavmkii.seancraft.food;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.cavmkii.seancraft.tarkov.Module;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rest (design doc 12): stay by a fire under a roof for 20 seconds without being hurt and you're Rested, with faster
 * health and stamina regeneration and faster skill gain. Comfort nearby makes it last longer.
 */
public final class Rest {
    static final int RESTING_SECONDS = 20;
    static final int FIRE_RANGE = 4;
    static final int COMFORT_RANGE = 8;
    static final int BASE_MINUTES = 8;

    public static final Holder<MobEffect> RESTING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, SeanCraft.id("resting"),
            new MobEffect(MobEffectCategory.BENEFICIAL, 0xE8A040) {});
    public static final Holder<MobEffect> RESTED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, SeanCraft.id("rested"),
            new MobEffect(MobEffectCategory.BENEFICIAL, 0xF0C060) {});

    private static final Map<UUID, Integer> RESTING_FOR = new HashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

    private Rest() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    tick(player);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player && taken > 0) {
                LAST_HURT.put(player.getUUID(), player.serverLevel().getGameTime());
            }
        });
        // Living quarters 3: a night's sleep at home leaves you Rested.
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayer player && player.serverLevel().isDay()) {
                Hideouts.Hideout home = Hideouts.at(player.serverLevel(), Vec3.atCenterOf(sleepingPos));
                if (home != null && home.level(Module.LIVING_QUARTERS) >= 3) {
                    int comfort = comfort(player.serverLevel(), sleepingPos);
                    int ticks = (int) Math.round(minutes(comfort) * 1200 * com.cavmkii.seancraft.rpg.Rpg.stats(player).preparation());
                    MobEffectInstance rested = player.getEffect(RESTED);
                    if (rested == null || rested.getDuration() < ticks) {
                        player.addEffect(new MobEffectInstance(RESTED, ticks, 0, true, false, true));
                        player.displayClientMessage(Component.literal("Rested (comfort " + comfort + "): " + minutes(comfort) + " minutes")
                                .withStyle(ChatFormatting.GOLD), true);
                    }
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RESTING_FOR.remove(handler.player.getUUID());
            LAST_HURT.remove(handler.player.getUUID());
        });
    }

    public static boolean rested(Player player) {
        return player.hasEffect(RESTED);
    }

    /** Rested: +50% health regeneration. */
    public static float regenMultiplier(Player player) {
        return rested(player) ? 1.5f : 1;
    }

    /** Rested: +100% stamina regeneration. */
    public static float staminaRegenMultiplier(Player player) {
        return rested(player) ? 2 : 1;
    }

    /** Rested: +50% skill gain. */
    public static double skillMultiplier(Player player) {
        return rested(player) ? 1.5 : 1;
    }

    private static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        boolean hurt = level.getGameTime() - LAST_HURT.getOrDefault(id, Long.MIN_VALUE / 2) < RESTING_SECONDS * 20;
        // A camp's fire has an awning: it counts as both (2.5).
        boolean camp = com.cavmkii.seancraft.tarkov.Camps.shelters(level, player.blockPosition());
        if (!player.isAlive() || hurt || !camp && (!nearFire(level, player.blockPosition()) || !underRoof(level, player.blockPosition()))) {
            if (RESTING_FOR.remove(id) != null) {
                player.removeEffect(RESTING);
            }
            return;
        }
        int comfort = -1;
        int seconds = RESTING_FOR.merge(id, 1, Integer::sum);
        MobEffectInstance rested = player.getEffect(RESTED);
        if (seconds == 1) {
            comfort = comfort(level, player.blockPosition());
            // Already Rested for longer than this place would give: nothing to gain here.
            if (rested != null && rested.getDuration() >= minutes(comfort) * 1200 - 400) {
                RESTING_FOR.put(id, RESTING_SECONDS + 1);
            }
        }
        if (seconds <= RESTING_SECONDS) {
            player.addEffect(new MobEffectInstance(RESTING, (RESTING_SECONDS - seconds + 1) * 20, 0, true, false, true));
            return;
        }
        if (seconds == RESTING_SECONDS + 1) {
            player.removeEffect(RESTING);
            if (comfort < 0) {
                comfort = comfort(level, player.blockPosition());
            }
            int minutes = minutes(comfort);
            // INT makes Rested last longer (6.3).
            int ticks = (int) Math.round(minutes * 1200 * com.cavmkii.seancraft.rpg.Rpg.stats(player).preparation());
            if (rested == null || rested.getDuration() < ticks) {
                player.addEffect(new MobEffectInstance(RESTED, ticks, 0, true, false, true));
                player.displayClientMessage(Component.literal("Rested (comfort " + comfort + "): " + minutes + " minutes")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    static int minutes(int comfort) {
        return BASE_MINUTES + comfort;
    }

    /** A lit campfire, fireplace (fire) or furnace within a few blocks. */
    static boolean nearFire(ServerLevel level, BlockPos at) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIRE_RANGE, -2, -FIRE_RANGE), at.offset(FIRE_RANGE, 2, FIRE_RANGE))) {
            if (fire(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    private static boolean fire(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CampfireBlock || block instanceof AbstractFurnaceBlock) {
            return state.getValue(CampfireBlock.LIT);
        }
        return block instanceof BaseFireBlock;
    }

    /** Something solid somewhere overhead. */
    static boolean underRoof(ServerLevel level, BlockPos at) {
        return !level.canSeeSky(at.above());
    }

    /**
     * Comfort (12): the best of each kind of thing nearby counts once (a fire, a bed, a carpet, a banner, a table,
     * plants), plus one for each module built in the hideout you're in.
     */
    static int comfort(ServerLevel level, BlockPos at) {
        boolean fire = false, bed = false, carpet = false, banner = false, table = false, plants = false;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-COMFORT_RANGE, -3, -COMFORT_RANGE), at.offset(COMFORT_RANGE, 4, COMFORT_RANGE))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            fire |= fire(state);
            bed |= block instanceof BedBlock;
            carpet |= block instanceof CarpetBlock;
            banner |= block instanceof BannerBlock || block instanceof WallBannerBlock;
            table |= block == Blocks.CRAFTING_TABLE || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE
                    || block == Blocks.SMITHING_TABLE || block == Blocks.ENCHANTING_TABLE;
            plants |= block instanceof FlowerPotBlock || block instanceof FlowerBlock || state.is(BlockTags.FLOWERS);
        }
        int comfort = (fire ? 1 : 0) + (bed ? 1 : 0) + (carpet ? 1 : 0) + (banner ? 1 : 0) + (table ? 1 : 0) + (plants ? 1 : 0);
        // Nature (12, 17.16.2): a Good patch nearby adds 1, a Remnant 2.
        comfort += com.cavmkii.seancraft.ecology.Ecology.comfortBonus(level, at);
        Hideouts.Hideout home = Hideouts.at(level, Vec3.atCenterOf(at));
        if (home != null) {
            for (Module module : Module.values()) {
                if (home.level(module) > 0) {
                    comfort++;
                }
                // Living quarters adds more (12): 2 a level.
                if (module == Module.LIVING_QUARTERS) {
                    comfort += 2 * home.level(module);
                }
            }
        }
        return comfort;
    }
}
