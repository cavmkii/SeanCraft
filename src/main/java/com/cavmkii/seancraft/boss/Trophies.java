package com.cavmkii.seancraft.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.serialization.MapCodec;

/**
 * Trophies and their powers (design doc 8.2.3, 8.3): a boss trophy goes on a Trophy Rack at your home base, where you
 * pick one as your active power. A key uses it: about 5 minutes on, about 20 minutes to recharge.
 */
public final class Trophies {
    public enum Power {
        TAILWIND("Tailwind", "Faster swimming"),
        CLOUT("Clout", "Half again as much skill and class XP"),
        BASS_DROP("Bass Drop", "No staggers, and your heavy hits stagger");

        public final String label;
        public final String effect;
        public final int ticks = 5 * 60 * 20;
        public final int cooldown = 20 * 60 * 20;

        Power(String label, String effect) {
            this.label = label;
            this.effect = effect;
        }

        /** The effect mid-sentence: first letter lowercased, so "XP" stays "XP". */
        public String lower() {
            return Character.toLowerCase(effect.charAt(0)) + effect.substring(1);
        }
    }

    /** A player's chosen power and its timers, in game time. Kept through death. */
    public record State(String selected, long activeUntil, long readyAt) {
        static final State NONE = new State("", 0, 0);
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("selected").forGetter(State::selected),
                Codec.LONG.fieldOf("activeUntil").forGetter(State::activeUntil),
                Codec.LONG.fieldOf("readyAt").forGetter(State::readyAt)
        ).apply(i, State::new));

        Power power() {
            for (Power p : Power.values()) {
                if (p.name().equals(selected)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** Client to server: the power key was pressed. */
    public record Use() implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(com.cavmkii.seancraft.SeanCraft.id("use_power"));
        public static final StreamCodec<FriendlyByteBuf, Use> CODEC = StreamCodec.unit(new Use());

        @Override
        public Type<Use> type() {
            return TYPE;
        }
    }

    private Trophies() {}

    static void init() {
        PayloadTypeRegistry.playC2S().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, ctx) -> use(ctx.player()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    tick(player);
                }
            }
        });
    }

    static State state(Player player) {
        return player.getAttachedOrElse(BossContent.POWER, State.NONE);
    }

    /** True while this power is switched on for the player. */
    public static boolean active(Player player, Power power) {
        State s = state(player);
        return s.power() == power && player.level().getGameTime() < s.activeUntil();
    }

    /** XP multiplier from Clout. */
    public static double xpMultiplier(Player player) {
        return active(player, Power.CLOUT) ? 1.5 : 1;
    }

    /** Hits taken at or above this (a tier 1 heavy hit) count as heavy for Bass Drop. */
    static final float HEAVY_HIT = 40;

    /** Bass Drop: a heavy melee hit staggers the mob it lands on (bosses and players excepted). */
    public static void afterHit(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float dealt) {
        if (dealt >= HEAVY_HIT && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
                && !(victim instanceof Player) && !(victim instanceof BossEntity) && active(player, Power.BASS_DROP)) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9, false, false));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12, 4, false, false));
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY(0.6), victim.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    static void use(ServerPlayer player) {
        State s = state(player);
        Power power = s.power();
        long now = player.serverLevel().getGameTime();
        if (power == null) {
            player.displayClientMessage(Component.literal("No trophy power chosen. Pick one at a Trophy Rack at your home base.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (now < s.readyAt()) {
            long seconds = (s.readyAt() - now) / 20;
            player.displayClientMessage(Component.literal(power.label + " is ready in " + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ".")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.setAttached(BossContent.POWER, new State(s.selected(), now + power.ticks, now + power.cooldown));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(Component.literal(power.label + ": " + power.lower() + " for 5 minutes.")
                .withStyle(ChatFormatting.GOLD), true);
        tick(player);
    }

    private static void tick(ServerPlayer player) {
        State s = state(player);
        if (s.power() == Power.TAILWIND && player.serverLevel().getGameTime() < s.activeUntil()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, (int) Math.min(s.activeUntil() - player.serverLevel().getGameTime(), 40) + 20,
                    0, true, false, true));
        }
    }

    // ================================================================== trophy items

    public static class TrophyItem extends Item {
        public final Power power;

        public TrophyItem(Power power, Properties properties) {
            super(properties);
            this.power = power;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Power: " + power.label + " (" + power.lower() + ")").withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal("Put it on a Trophy Rack at your home base to choose it.").withStyle(ChatFormatting.GRAY));
        }
    }

    // ================================================================== the rack

    public static class RackBlock extends BaseEntityBlock {
        public static final MapCodec<RackBlock> CODEC = simpleCodec(RackBlock::new);

        public RackBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(com.cavmkii.seancraft.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
            builder.add(com.cavmkii.seancraft.FacingBlock.FACING);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return defaultBlockState().setValue(com.cavmkii.seancraft.FacingBlock.FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new RackEntity(pos, state);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        /** A trophy in hand goes onto the rack. */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (!(stack.getItem() instanceof TrophyItem trophy)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof RackEntity rack) {
                rack.trophies.add(BuiltInRegistries.ITEM.getKey(trophy).toString());
                rack.setChanged();
                stack.consume(1, player);
                player.displayClientMessage(Component.literal(stack.getHoverName().getString() + " is on the rack. Use the rack to choose its power.")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        /** Empty hand: choose the next power on the rack. Sneaking: take the last trophy back. */
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof RackEntity rack)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (player.isSecondaryUseActive()) {
                if (!rack.trophies.isEmpty()) {
                    String id = rack.trophies.remove(rack.trophies.size() - 1);
                    rack.setChanged();
                    player.getInventory().placeItemBackInInventory(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
                }
                return InteractionResult.SUCCESS;
            }
            if (com.cavmkii.seancraft.tarkov.Hideouts.at(server, Vec3.atCenterOf(pos)) == null) {
                player.displayClientMessage(Component.literal("A Trophy Rack only works at a home base.").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            List<Power> powers = new ArrayList<>();
            for (String id : rack.trophies) {
                if (BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)) instanceof TrophyItem t && !powers.contains(t.power)) {
                    powers.add(t.power);
                }
            }
            if (powers.isEmpty()) {
                player.displayClientMessage(Component.literal("No trophies on this rack yet.").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS;
            }
            State s = state(player);
            int i = powers.indexOf(s.power());
            Power next = powers.get((i + 1) % powers.size());
            player.setAttached(BossContent.POWER, new State(next.name(), s.activeUntil(), s.readyAt()));
            player.displayClientMessage(Component.literal("Active power: " + next.label + " (" + next.lower()
                    + "). Use it with the trophy power key.").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RackEntity rack) {
                for (String id : rack.trophies) {
                    Block.popResource(level, pos, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    public static class RackEntity extends BlockEntity {
        final List<String> trophies = new ArrayList<>();

        public RackEntity(BlockPos pos, BlockState state) {
            super(BossContent.RACK_ENTITY, pos, state);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ListTag list = new ListTag();
            trophies.forEach(id -> list.add(StringTag.valueOf(id)));
            tag.put("Trophies", list);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            trophies.clear();
            for (Tag t : tag.getList("Trophies", Tag.TAG_STRING)) {
                trophies.add(t.getAsString());
            }
        }
    }
}
