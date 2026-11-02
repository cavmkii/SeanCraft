package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Rpg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.combat_roll.CombatRollMod;
import net.combat_roll.api.event.ServerSideRollEvents;
import net.combat_roll.config.ServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Souls-lite stamina rules (design doc 9.6). Swings and rolls cost stamina, regeneration is a flat 25 a second that
 * pauses for a second after any stamina action, and running dry weakens swings and stops sprints and rolls until the
 * bar recovers. Better Combat provides the swings and Combat Roll the roll; this class only sets the costs and the
 * roll's invulnerability window, which it applies itself from the moment the roll starts.
 */
public final class Combat {
    public static final float ROLL_COST = 20;
    /** Exhausted players get sprint and rolls back once the bar is this full again. */
    public static final float RECOVER_FRACTION = 0.25f;
    static final float REGEN_PER_SECOND = 25;
    /** Sprinting is the one part of vanilla hunger left, paid in stamina (design doc 11). */
    static final float SPRINT_PER_SECOND = 8;
    static final int REGEN_PAUSE_TICKS = 20;
    public static final int ROLL_IFRAME_TICKS = 8;
    /** Combat Roll's recharge, cut from its 4 seconds so stamina is the real limit (9.6.3). */
    static final float ROLL_RECHARGE_SECONDS = 0.75f;

    private static final Map<UUID, State> STATE = new HashMap<>();

    private static final class State {
        long regenPausedUntil;
        long rollStart = Long.MIN_VALUE / 2;
        boolean weakSwing;
        boolean heavySwing;
        boolean exhausted;
        Load.Tier load = Load.Tier.LIGHT;
    }

    /** The player's equip load tier, as of the last half-second check. */
    public static Load.Tier load(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? Load.Tier.LIGHT : s.load;
    }

    private Combat() {}

    public static void init() {
        ServerSideRollEvents.PLAYER_START_ROLLING.register((player, velocity) -> onRoll(player));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> configureRoll());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATE.remove(handler.player.getUUID()));
    }

    private static State state(ServerPlayer player) {
        return STATE.computeIfAbsent(player.getUUID(), id -> new State());
    }

    /** Rolls cost stamina, not hunger, and get no invulnerability from Combat Roll: SeanCraft applies its own. */
    private static void configureRoll() {
        ServerConfig config = CombatRollMod.config;
        if (config == null) {
            return;
        }
        config.exhaust_on_roll = 0;
        config.food_level_required = 0;
        config.invulnerable_ticks_upon_roll = 0;
        config.roll_cooldown = ROLL_RECHARGE_SECONDS;
    }

    /**
     * A quarter of the weapon's base damage, on the 9.5 scale (vanilla damage x 5). Read from the weapon itself, so
     * injuries, effects and skills change what a swing does but not what it costs. AGI makes AGI weapons cheaper (6.5.1).
     */
    public static float swingCost(ServerPlayer player) {
        double[] damage = {player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)};
        player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        double efficiency = com.cavmkii.seancraft.rpg.Rpg.stats(player).staminaEfficiency()
                * com.cavmkii.seancraft.rpg.Scaling.agiShare(player.getMainHandItem());
        float underRequirement = com.cavmkii.seancraft.rpg.Scaling.meets(player.getMainHandItem(), com.cavmkii.seancraft.rpg.Rpg.stats(player))
                ? 1 : com.cavmkii.seancraft.rpg.Scaling.UNDER_REQUIREMENT_STAMINA;
        return (float) (damage[0] * 5 / 4 * (1 - efficiency) * underRequirement);
    }

    /** Better Combat started a swing (server side). Charges for it, or marks it weak if the bar can't cover it. */
    public static void onSwing(ServerPlayer player) {
        com.cavmkii.seancraft.boss.Restraint.onSwing(player);
        if (player.isCreative()) {
            State c = state(player);
            c.heavySwing = player.isShiftKeyDown();
            c.weakSwing = false;
            if (c.heavySwing) {
                Heavy.perform(player);
            }
            com.cavmkii.seancraft.rpg.Magic.onSwing(player, c.heavySwing);
            return;
        }
        State s = state(player);
        // A heavy attack (9.4.1): attack while sneaking. Twice the stamina, x1.75 damage, twice the poise.
        s.heavySwing = player.isShiftKeyDown();
        float cost = swingCost(player) * (s.heavySwing ? 2 : 1);
        PlayerData data = Rpg.data(player);
        s.weakSwing = data.stamina < cost;
        spend(player, s, data, cost);
        if (s.heavySwing && !s.weakSwing) {
            Heavy.perform(player);
        }
        com.cavmkii.seancraft.rpg.Magic.onSwing(player, s.heavySwing);
    }

    public static boolean heavySwing(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s != null && s.heavySwing && !s.weakSwing;
    }

    /** Spends stamina outside a swing or roll (blocking). */
    public static void spendStamina(ServerPlayer player, float cost) {
        if (!player.isCreative()) {
            spend(player, state(player), Rpg.data(player), cost);
        }
    }

    private static void onRoll(ServerPlayer player) {
        State s = state(player);
        s.rollStart = player.serverLevel().getGameTime();
        if (!player.isCreative()) {
            spend(player, s, Rpg.data(player), ROLL_COST);
        }
        SeanCraft.LOG.debug("{} started a roll at tick {}", player.getName().getString(), s.rollStart);
    }

    private static void spend(ServerPlayer player, State s, PlayerData data, float cost) {
        data.stamina = Math.max(0, data.stamina - cost);
        s.regenPausedUntil = player.serverLevel().getGameTime() + REGEN_PAUSE_TICKS;
        if (data.stamina <= 0) {
            s.exhausted = true;
            player.setSprinting(false);
        }
    }

    /** Per tick, from the character tick: sprinting, flat regeneration after the pause, and the exhausted state. */
    public static void tick(ServerPlayer player, PlayerData data, float max) {
        State s = state(player);
        Blocking.tick(player);
        if (player.tickCount % 10 == 0) {
            Load.State load = Load.of(player, Rpg.stats(player).str());
            s.load = load.tier();
            Load.apply(player, load);
        }
        if (player.isSprinting() && !player.isCreative() && !player.isPassenger()) {
            spend(player, s, data, SPRINT_PER_SECOND / 20f);
        } else if (player.serverLevel().getGameTime() >= s.regenPausedUntil) {
            data.stamina = Math.min(max, data.stamina + REGEN_PER_SECOND * com.cavmkii.seancraft.food.Rest.staminaRegenMultiplier(player) / 20f);
        }
        data.stamina = Math.min(max, data.stamina);
        if (s.exhausted) {
            if (data.stamina >= max * RECOVER_FRACTION) {
                s.exhausted = false;
            } else if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }
    }

    /** Swings started with no stamina deal half damage. */
    public static float swingMultiplier(ServerPlayer attacker) {
        State s = STATE.get(attacker.getUUID());
        if (s == null) {
            return 1f;
        }
        return s.weakSwing ? 0.5f : s.heavySwing ? Heavy.DAMAGE : 1f;
    }

    /**
     * True while a roll's window protects the player from this hit. Only attacks count (anything with an attacker or a
     * projectile); falls, fire, lava, hazards and effects already applied get through.
     */
    public static boolean rollDodges(ServerPlayer player, DamageSource source) {
        State s = STATE.get(player.getUUID());
        // The window follows equip load: 8 ticks, 4 under Heavy load (9.6.3).
        if (s == null || player.serverLevel().getGameTime() - s.rollStart >= Math.max(s.load.rollTicks, 1) || s.load.rollTicks == 0) {
            return false;
        }
        boolean attack = source.getEntity() != null || source.getDirectEntity() != null;
        if (!attack || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        SeanCraft.LOG.debug("{} rolled through {} ({} ticks into the roll)", player.getName().getString(), source.getMsgId(),
                player.serverLevel().getGameTime() - s.rollStart);
        return true;
    }
}
