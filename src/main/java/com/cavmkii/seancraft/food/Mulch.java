package com.cavmkii.seancraft.food;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Hideouts;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Fertile mulch (design doc 11.3): used on farmland inside a home base, it makes that block fertile, so any crop grows
 * on it whatever the climate. It changes only that block and adds nothing to the patch's fertility.
 */
public final class Mulch extends SavedData {
    private final LongOpenHashSet blocks = new LongOpenHashSet();

    static final class MulchItem extends Item {
        MulchItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            BlockPos pos = ctx.getClickedPos();
            // Clicking the crop on it counts as clicking the farmland.
            if (ctx.getLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
                pos = pos.below();
            }
            if (!ctx.getLevel().getBlockState(pos).is(Blocks.FARMLAND)) {
                return InteractionResult.PASS;
            }
            if (!(ctx.getLevel() instanceof ServerLevel level)) {
                return InteractionResult.SUCCESS;
            }
            if (Hideouts.at(level, Vec3.atCenterOf(pos)) == null) {
                if (ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.literal("Mulch only works at your home base.").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
            Mulch m = get(level);
            if (!m.blocks.add(pos.asLong())) {
                if (ctx.getPlayer() != null) {
                    ctx.getPlayer().displayClientMessage(Component.literal("This farmland is already mulched.").withStyle(ChatFormatting.GRAY), true);
                }
                return InteractionResult.FAIL;
            }
            m.setDirty();
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 12, 0.3, 0.05, 0.3, 0);
            level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.CONSUME;
        }
    }

    public static final Item FERTILE_MULCH = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("fertile_mulch"), new MulchItem(new Item.Properties()));

    static {
        Kitchen.ITEMS.add(FERTILE_MULCH);
    }

    private static final Factory<Mulch> FACTORY = new Factory<>(Mulch::new, Mulch::load, null);

    static Mulch get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "seancraft_mulch");
    }

    /** Mulched farmland: the block must still be farmland. */
    public static boolean fertile(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.FARMLAND) && get(level).blocks.contains(pos.asLong());
    }

    static void init() {
        Kitchen.fertileFarmland = (level, pos) -> level instanceof ServerLevel s && fertile(s, pos);
    }

    private static Mulch load(CompoundTag tag, HolderLookup.Provider registries) {
        Mulch m = new Mulch();
        for (long l : tag.getLongArray("blocks")) {
            m.blocks.add(l);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("blocks", blocks.toLongArray());
        return tag;
    }
}
