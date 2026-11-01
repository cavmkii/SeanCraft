package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.tetris.Tetris;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Boss drops that do something when carried or used: the Follower Lantern, the Subwoofer and the Blue Check. */
public final class BossItems {
    private BossItems() {}

    /** Daylan's Follower Lantern (A.4): his one real follower. Carrying it anywhere keeps Darkness off you. */
    public static class FollowerLantern extends Item {
        public FollowerLantern(Properties properties) {
            super(properties);
        }

        public static boolean carries(Player player) {
            for (ItemStack stack : Tetris.carried(player)) {
                if (stack.is(BossContent.FOLLOWER_LANTERN)) {
                    return true;
                }
            }
            return false;
        }

        /** Once a second for every player. */
        static void tick(ServerPlayer player) {
            if (player.hasEffect(MobEffects.DARKNESS) && carries(player)) {
                player.removeEffect(MobEffects.DARKNESS);
            }
        }
    }

    /** Bo Daddy's Subwoofer (A.5): one bass hit and every mob within 32 blocks shows through walls for 5 seconds. */
    public static class Subwoofer extends Item {
        static final double RANGE = 32;
        static final int GLOW_TICKS = 100;
        static final int COOLDOWN_TICKS = 30 * 20;

        public Subwoofer(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 2f, 0.5f);
                level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.8f);
                int found = 0;
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE))) {
                    if (mob.distanceTo(player) <= RANGE) {
                        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
                        found++;
                    }
                }
                player.displayClientMessage(Component.literal(found == 0 ? "Nothing out there." : found + (found == 1 ? " thing" : " things") + " out there.")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    /** Daylan's Blue Check (A.4): a checkmark after your name in chat. Use it again to take it off. */
    public static class BlueCheck extends Item {
        static final String TEAM = "seancraft_verified";

        public BlueCheck(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) {
                return InteractionResultHolder.success(stack);
            }
            Scoreboard board = level.getScoreboard();
            PlayerTeam team = board.getPlayerTeam(TEAM);
            if (team == null) {
                team = board.addPlayerTeam(TEAM);
                team.setPlayerSuffix(Component.literal(" ✔").withStyle(ChatFormatting.AQUA));
            }
            String name = player.getScoreboardName();
            PlayerTeam current = board.getPlayersTeam(name);
            if (current == team) {
                board.removePlayerFromTeam(name, team);
                player.displayClientMessage(Component.literal("Unverified. Back to being a real person."), true);
            } else if (current != null) {
                player.displayClientMessage(Component.literal("You're already on a team.").withStyle(ChatFormatting.RED), true);
            } else {
                board.addPlayerToTeam(name, team);
                level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1.6f);
                player.displayClientMessage(Component.literal("Verified. Your opinions now matter more.").withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.success(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            lines.add(Component.literal("Puts a ✔ after your name in chat. Use again to remove.").withStyle(ChatFormatting.GRAY));
        }
    }
}
