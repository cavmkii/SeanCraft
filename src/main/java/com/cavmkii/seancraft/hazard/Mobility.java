package com.cavmkii.seancraft.hazard;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.boss.BossContent;
import com.cavmkii.seancraft.combat.Combat;
import com.cavmkii.seancraft.rpg.Rpg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * The Mobility hazard (design doc 9.3.3): in the jungle canopy and on high peaks (mountain biomes above World.PeakY),
 * each block climbed on ladders or vines, or pillared up, costs heavy stamina, and falls break legs more often. Out of
 * stamina, you slip. Mark's Reel skips all of it. No drain and no HUD warning: the cost is stamina and falls.
 */
public final class Mobility {
    /** Stamina per block climbed (base max stamina is 100). */
    static final float COST = 8;
    /** Fall fracture chance multiplier where it applies. */
    static final float FALL_FACTOR = 2;

    private static final Map<UUID, Double> CLIMB_FROM = new HashMap<>();

    private Mobility() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CLIMB_FROM.remove(handler.player.getUUID()));
        // Pillaring: placing a block under your own feet mid-jump.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || !(player.getItemInHand(hand).getItem() instanceof BlockItem)
                    || hit.getDirection() != Direction.UP || player.onGround() || !applies(sp)
                    || !hit.getBlockPos().equals(player.blockPosition().below()) && !hit.getBlockPos().equals(player.blockPosition().below(2))) {
                return InteractionResult.PASS;
            }
            if (!spend(sp)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /** Where it applies to this player now. */
    public static boolean applies(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD || carriesReel(player)) {
            return false;
        }
        Holder<Biome> biome = player.level().getBiome(player.blockPosition());
        return biome.is(BiomeTags.IS_JUNGLE)
                || player.getY() >= Cfg.peakY && (biome.is(ConventionalBiomeTags.IS_MOUNTAIN) || biome.is(ConventionalBiomeTags.IS_MOUNTAIN_PEAK)
                || biome.is(ConventionalBiomeTags.IS_MOUNTAIN_SLOPE));
    }

    private static boolean carriesReel(ServerPlayer player) {
        return player.getInventory().contains(new ItemStack(BossContent.MARKS_REEL));
    }

    /** A fall's fracture chance multiplier at the player's spot. */
    public static float fallFactor(ServerPlayer player) {
        return applies(player) ? FALL_FACTOR : 1;
    }

    private static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.isAlive() || !player.onClimbable() || !applies(player)) {
            CLIMB_FROM.remove(id);
            return;
        }
        double from = CLIMB_FROM.computeIfAbsent(id, k -> player.getY());
        if (player.getY() < from) {
            CLIMB_FROM.put(id, player.getY());
        } else if (player.getY() - from >= 1) {
            CLIMB_FROM.put(id, from + 1);
            if (!spend(player)) {
                // Too tired to hold on: slide back down.
                player.setDeltaMovement(new Vec3(player.getDeltaMovement().x, -0.3, player.getDeltaMovement().z));
                player.hurtMarked = true;
                CLIMB_FROM.put(id, player.getY() - 1);
            }
        }
    }

    /** Pays for one block of climbing; false (and a message) if there isn't the stamina. */
    private static boolean spend(ServerPlayer player) {
        if (Rpg.data(player).stamina < COST) {
            player.displayClientMessage(Component.literal("Too tired to climb.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Combat.spendStamina(player, COST);
        return true;
    }
}
