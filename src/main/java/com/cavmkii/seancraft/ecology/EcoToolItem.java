package com.cavmkii.seancraft.ecology;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The soil kit, insecticide and bat guano (17.6, 17.7, 17.11): each is used on the ground or a crop. */
public class EcoToolItem extends Item {
    public enum Kind { SOIL_KIT, INSECTICIDE, BAT_GUANO }

    private final Kind kind;

    public EcoToolItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!Ecology.enabled()) {
            player.displayClientMessage(Component.literal("The ecology model is off (Ecology.Enabled).").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Patch p = Ecology.patchAt(level, pos);
        if (p == null) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        switch (kind) {
            case SOIL_KIT -> report(player, p, cropAt(level, pos));
            case INSECTICIDE -> {
                if (!(state.getBlock() instanceof CropBlock) && !(state.getBlock() instanceof FarmBlock)) {
                    player.displayClientMessage(Component.literal("Use it on crops or farmland.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.FAIL;
                }
                double before = p.pest;
                Ecology.spray(p);
                level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 2, 0.5, 2, 0.01);
                level.playSound(null, pos, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
                player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                        "Sprayed the patch: pests %.0f -> %.0f per ha, bats hit too. Resistance now %.0f%%.",
                        before, p.pest, p.resistance * 100)).withStyle(ChatFormatting.GRAY), true);
                stack.consume(1, player);
            }
            case BAT_GUANO -> {
                if (!(state.getBlock() instanceof CropBlock) && !(state.getBlock() instanceof FarmBlock) && !state.is(Blocks.GRASS_BLOCK)
                        && !state.is(Blocks.DIRT)) {
                    player.displayClientMessage(Component.literal("Spread it on farmland, crops or soil.").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.FAIL;
                }
                p.organicFast += Params.guanoOrganic;
                Ecology.markDirty(level);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.4, 0.2, 0.4, 0);
                player.displayClientMessage(Component.literal("Guano in the soil: it'll feed the crops over the next few days.")
                        .withStyle(ChatFormatting.GRAY), true);
                stack.consume(1, player);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** The crop on or at a block, if any. */
    private static Block cropAt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof CropBlock) {
            return s.getBlock();
        }
        BlockState up = level.getBlockState(pos.above());
        return up.getBlock() instanceof CropBlock ? up.getBlock() : null;
    }

    /** The soil kit's reading (17.11): soil, what limits a crop here, and the patch's ecosystem quality. */
    static void report(ServerPlayer player, Patch p, Block crop) {
        int ash = 0x8F8371;
        int bone = 0xD9CBB0;
        player.sendSystemMessage(Component.literal("Soil kit").withColor(0xE0BC62));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Nitrogen %.0f   Phosphorus %.0f   Organic matter %.0f   Moisture %.0f%%",
                p.nitrogen, p.phosphorus, p.organicFast + p.organicSlow, p.moisture * 100)).withColor(bone));
        boolean wheat = crop == Blocks.WHEAT;
        String what = crop == null ? "A crop here" : crop.getName().getString();
        String lim = Model.limiting(p, wheat || crop == null && p.wheat > 0);
        player.sendSystemMessage(Component.literal(what + " is held back by: " + lim
                + (p.wheat > 0 && p.pest > 20 ? String.format(Locale.ROOT, "  (aphids %.0f per ha)", p.pest) : "")).withColor(bone));
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Ecosystem quality: %s (%.0f)%s", Quality.band(p.quality),
                p.quality, p.limitedBy.isEmpty() ? "" : ": " + p.limitedBy)).withColor(Quality.color(p.quality)));
        player.sendSystemMessage(Component.literal("Readings are for the whole 64 x 64 block patch.").withColor(ash));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String text = switch (kind) {
            case SOIL_KIT -> "Use on the ground: soil nutrients, what limits a crop there, and the patch's ecosystem quality.";
            case INSECTICIDE -> "Use on crops: knocks pests down, but kills the bats that eat them too, and pests build resistance.";
            case BAT_GUANO -> "Fast fertilizer: use on farmland or soil. It breaks down over a few days.";
        };
        lines.add(Component.literal(text).withColor(0xD9CBB0));
    }
}
