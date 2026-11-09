package com.cavmkii.seancraft.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Boss fights in progress (design doc 8.4.5): a ring about 40 blocks across around each pedestal, where blocks can't be
 * broken or placed while the boss lives. Summons wait out the boss's 3-second rise here.
 */
public final class Arenas {
    public static final double RADIUS = 20;
    static final int RISE_TICKS = 60;

    record Fight(ResourceKey<Level> dimension, BlockPos center, UUID boss) {}

    private record Summon(ResourceKey<Level> dimension, BlockPos center, Function<Level, BossEntity> make, long at) {}

    private static final List<Fight> ACTIVE = new ArrayList<>();
    private static final List<Summon> PENDING = new ArrayList<>();

    private Arenas() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Arenas::tick);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.is(net.minecraft.world.level.block.Blocks.SCULK_SHRIEKER) && level instanceof net.minecraft.server.level.ServerLevel server) {
                // Bo Daddy's shriekers are props: breaking one resets his meter (A.5).
                Fight f = at(level, Vec3.atCenterOf(pos));
                if (f != null && server.getEntity(f.boss) instanceof BoDaddy bo) {
                    bo.shriekerBroken();
                    return true;
                }
            }
            if (locked(level, pos, player)) {
                player.displayClientMessage(Component.literal("Not while the boss is alive.").withStyle(ChatFormatting.RED), true);
                return false;
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.getItemInHand(hand).getItem() instanceof BlockItem && locked(level, hit.getBlockPos().relative(hit.getDirection()), player)) {
                player.displayClientMessage(Component.literal("Not while the boss is alive.").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean locked(Level level, BlockPos pos, Player player) {
        return !level.isClientSide && !player.isCreative() && at(level, Vec3.atCenterOf(pos)) != null;
    }

    /** The fight whose ring holds a point, or null. */
    static Fight at(Level level, Vec3 point) {
        for (Fight f : ACTIVE) {
            if (f.dimension == level.dimension() && inRing(f.center, point)) {
                return f;
            }
        }
        return null;
    }

    /** The centre of the live ring holding a point, or null. */
    public static BlockPos ringAt(Level level, Vec3 point) {
        Fight f = at(level, point);
        return f == null ? null : f.center;
    }

    public static boolean inRing(BlockPos center, Vec3 point) {
        double dx = point.x - (center.getX() + 0.5);
        double dz = point.z - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(point.y - center.getY()) < 16;
    }

    /** True while the player stands in a live boss ring: raid threat stops rising, and death is a boss death. */
    public static boolean inFight(ServerPlayer player) {
        return at(player.level(), player.position()) != null;
    }

    public static boolean busy(Level level, BlockPos center) {
        for (Fight f : ACTIVE) {
            if (f.dimension == level.dimension() && f.center.equals(center)) {
                return true;
            }
        }
        for (Summon s : PENDING) {
            if (s.dimension == level.dimension() && s.center.equals(center)) {
                return true;
            }
        }
        return false;
    }

    static void begin(BossEntity boss) {
        end(boss.getUUID());
        ACTIVE.add(new Fight(boss.level().dimension(), boss.center(), boss.getUUID()));
    }

    static void end(UUID boss) {
        ACTIVE.removeIf(f -> f.boss.equals(boss));
    }

    /** The totem went in: the boss rises from the pedestal after 3 seconds (8.4.1). */
    public static void summon(ServerLevel level, BlockPos center, EntityType<? extends BossEntity> type) {
        PENDING.add(new Summon(level.dimension(), center.immutable(), type::create, level.getGameTime() + RISE_TICKS));
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Summon> it = PENDING.iterator();
        while (it.hasNext()) {
            Summon s = it.next();
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            Vec3 c = Vec3.atBottomCenterOf(s.center.above());
            long left = s.at - level.getGameTime();
            if (left > 0) {
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + (RISE_TICKS - left) / 30.0, c.z, 6, 0.4, 0.2, 0.4, 0.01);
                continue;
            }
            it.remove();
            BossEntity boss = s.make.apply(level);
            if (boss != null) {
                boss.moveTo(c.x, c.y, c.z, 0, 0);
                boss.setCenter(s.center);
                level.addFreshEntity(boss);
                boss.start();
            }
        }
    }
}
