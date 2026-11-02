package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.PartItems;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import com.cavmkii.seancraft.parts.WeaponBenchMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Part recovery (design doc 9.4.9): when a part-built weapon or armor piece is lost on a raid or boss death, its
 * striking part or armor plates come back at level 1 in a Recovery crate in the hideout where the player respawns. The
 * other parts, and anything not part-built, are lost as usual.
 */
public final class Recovery {
    private Recovery() {}

    /** The striking part of a part-built item, at level 1; plates come back as many as the piece took. */
    static List<ItemStack> coreParts(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        Family.Stock stock = Family.stock(stack);
        if (stock == null) {
            return out;
        }
        var parts = Parts.installed(stack);
        Parts.Installed core = parts.get(PartSlot.HEAD);
        int count = 1;
        if (core == null && parts.get(PartSlot.PLATE) != null) {
            core = parts.get(PartSlot.PLATE);
            count = WeaponBenchMenu.platesFor(stock.family());
        }
        if (core == null && stock.family().shield()) {
            core = parts.get(PartSlot.GUARD);
        }
        if (core != null) {
            out.add(new ItemStack(PartItems.get(core.part()), count));
        }
        return out;
    }

    /** Called before a raid or boss death takes the player's things. */
    public static void onDeath(ServerPlayer player, List<ItemStack> lost) {
        List<ItemStack> parts = new ArrayList<>();
        for (ItemStack s : lost) {
            parts.addAll(coreParts(s));
        }
        if (parts.isEmpty()) {
            return;
        }
        ServerLevel level = player.server.getLevel(player.getRespawnDimension());
        BlockPos respawn = player.getRespawnPosition();
        Hideouts.Hideout h = level != null && respawn != null ? Hideouts.at(level, Vec3.atCenterOf(respawn)) : null;
        if (h == null) {
            // No bed in a hideout: the first hideout they own.
            level = player.server.overworld();
            for (Hideouts.Hideout o : Hideouts.get(level).list) {
                if (player.getUUID().equals(o.owner)) {
                    h = o;
                    break;
                }
            }
        }
        if (h == null || level == null) {
            return;
        }
        h.recovery.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).addAll(parts);
        Hideouts.get(level).setDirty();
        ensureCrate(level, h);
        player.sendSystemMessage(Component.literal(parts.size() + " striking part" + (parts.size() == 1 ? "" : "s")
                + " went back to the Recovery crate in your hideout.").withStyle(ChatFormatting.GOLD));
    }

    /** Puts a Recovery crate beside the Hideout Bench if the hideout doesn't have one in reach. */
    static void ensureCrate(ServerLevel level, Hideouts.Hideout h) {
        BlockPos center = h.pos;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-6, -3, -6), center.offset(6, 3, 6))) {
            if (level.getBlockState(p).is(TarkovContent.RECOVERY_CRATE)) {
                return;
            }
        }
        for (int r = 1; r <= 3; r++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos p = center.relative(d, r).above(dy);
                    if (level.getBlockState(p).canBeReplaced() && level.getBlockState(p.below()).isSolid()) {
                        BlockState crate = TarkovContent.RECOVERY_CRATE.defaultBlockState()
                                .setValue(com.cavmkii.seancraft.FacingBlock.FACING, d);
                        level.setBlockAndUpdate(p, crate);
                        return;
                    }
                }
            }
        }
    }

    /** Using the crate hands back everything waiting for that player. */
    public static void open(ServerPlayer player, BlockPos pos) {
        Hideouts.Hideout h = Hideouts.at(player.serverLevel(), Vec3.atCenterOf(pos));
        List<ItemStack> items = h == null ? null : h.recovery.remove(player.getUUID());
        if (items == null || items.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing of yours in here.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Hideouts.get(player.serverLevel()).setDirty();
        for (ItemStack s : items) {
            com.cavmkii.seancraft.tetris.Tetris.give(player, s);
        }
        player.displayClientMessage(Component.literal("Recovered " + items.size() + " part" + (items.size() == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GOLD), true);
    }
}
