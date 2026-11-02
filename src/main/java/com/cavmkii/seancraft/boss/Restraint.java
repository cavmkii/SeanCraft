package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.SeanCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Boss control effects on players: roots (Mark's net, Daylan's cuffs), stuns (the taser), staggers (heavy sonic hits)
 * and slows (Bo Daddy's croon). A player under one can't be given another until it ends plus a second (A.4 control
 * limit); staggers skip that rule but Bass Drop stops them.
 */
public final class Restraint {
    private static final ResourceLocation ROOT = SeanCraft.id("boss_root");
    private static final ResourceLocation SLOW = SeanCraft.id("boss_slow");
    static final int GRACE_TICKS = 20;

    private record Hold(long rootUntil, boolean slashBreaks, long slowUntil) {}

    private static final Map<UUID, Hold> HELD = new HashMap<>();

    private Restraint() {}

    private static long now(ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }

    /** Rooted, stunned or slowed now, or within a second of it ending. */
    public static boolean controlled(ServerPlayer player) {
        Hold h = HELD.get(player.getUUID());
        return h != null && now(player) < Math.max(h.rootUntil, h.slowUntil) + GRACE_TICKS;
    }

    /** Rooted or stunned right now (Freeze! doesn't ticket these players). */
    public static boolean rooted(ServerPlayer player) {
        Hold h = HELD.get(player.getUUID());
        return h != null && now(player) < h.rootUntil;
    }

    /** Can't move or jump for a while. A slash swing breaks it if {@code slashBreaks}. False if the control limit stopped it. */
    public static boolean root(ServerPlayer player, int ticks, boolean slashBreaks, String message) {
        if (controlled(player)) {
            return false;
        }
        Hold old = HELD.get(player.getUUID());
        HELD.put(player.getUUID(), new Hold(now(player) + ticks, slashBreaks, old != null ? old.slowUntil : 0));
        modify(player, Attributes.MOVEMENT_SPEED, ROOT, -1);
        modify(player, Attributes.JUMP_STRENGTH, ROOT, -1);
        if (message != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
        return true;
    }

    /** Movement cut by {@code fraction} for a while. False if the control limit stopped it. */
    public static boolean slow(ServerPlayer player, float fraction, int ticks) {
        if (controlled(player)) {
            return false;
        }
        Hold old = HELD.get(player.getUUID());
        HELD.put(player.getUUID(), new Hold(old != null ? old.rootUntil : 0, old != null && old.slashBreaks, now(player) + ticks));
        modify(player, Attributes.MOVEMENT_SPEED, SLOW, -fraction);
        return true;
    }

    /** A heavy hit's stagger: a 0.6-second stop. Bass Drop makes the player immune. */
    public static void stagger(ServerPlayer player) {
        stagger(player, 12);
    }

    /** A stagger of any length: 0.6 s for heavy hits and poise, 1 s for a guard break (9.6.1). */
    public static void stagger(ServerPlayer player, int ticks) {
        if (Trophies.active(player, Trophies.Power.BASS_DROP) || rooted(player) || com.cavmkii.seancraft.rpg.Rpg.unstaggerable(player)) {
            return;
        }
        Hold old = HELD.get(player.getUUID());
        HELD.put(player.getUUID(), new Hold(now(player) + ticks, false, old != null ? old.slowUntil : 0));
        modify(player, Attributes.MOVEMENT_SPEED, ROOT, -1);
        modify(player, Attributes.JUMP_STRENGTH, ROOT, -1);
    }

    private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unmodify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static void unroot(ServerPlayer player) {
        unmodify(player, Attributes.MOVEMENT_SPEED, ROOT);
        unmodify(player, Attributes.JUMP_STRENGTH, ROOT);
    }

    /** Every tick for every player: holds run out, and never outlive a death or a server restart. */
    public static void tick(ServerPlayer player) {
        Hold h = HELD.get(player.getUUID());
        long now = now(player);
        if (h == null) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && (speed.getModifier(ROOT) != null || speed.getModifier(SLOW) != null)) {
                unroot(player);
                unmodify(player, Attributes.MOVEMENT_SPEED, SLOW);
            }
            return;
        }
        if (!player.isAlive()) {
            HELD.remove(player.getUUID());
            unroot(player);
            unmodify(player, Attributes.MOVEMENT_SPEED, SLOW);
            return;
        }
        if (now >= h.rootUntil) {
            unroot(player);
        }
        if (now >= h.slowUntil) {
            unmodify(player, Attributes.MOVEMENT_SPEED, SLOW);
        }
        if (now >= Math.max(h.rootUntil, h.slowUntil) + GRACE_TICKS) {
            HELD.remove(player.getUUID());
        }
    }

    /** A slash swing (sword or axe) cuts a net or cuffs. Returns true if it freed the player. */
    public static boolean onSwing(ServerPlayer player) {
        Hold h = HELD.get(player.getUUID());
        if (h == null || !h.slashBreaks || now(player) >= h.rootUntil) {
            return false;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.is(net.minecraft.tags.ItemTags.SWORDS) && !weapon.is(net.minecraft.tags.ItemTags.AXES)) {
            return false;
        }
        HELD.put(player.getUUID(), new Hold(now(player), false, h.slowUntil));
        unroot(player);
        for (BossEntity boss : player.serverLevel().getEntitiesOfClass(BossEntity.class, player.getBoundingBox().inflate(Arenas.RADIUS * 2))) {
            boss.say("cut_free");
            break;
        }
        return true;
    }
}
