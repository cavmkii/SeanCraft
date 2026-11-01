package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.rpg.Rpg;
import com.cavmkii.seancraft.rpg.Skill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Body-part health on the server: where hits land, bleeds and fractures, their effects, the Medstation's care,
 * and treating parts with meds. The parts are kept in step with the player's real health.
 */
public final class Health {
    public static final ResourceKey<DamageType> BLEED = ResourceKey.create(Registries.DAMAGE_TYPE, SeanCraft.id("bleed"));

    static final class Runtime {
        BodyPart bleeding;
        int fractureCareTicks;
        boolean bleedCareNoticed;
        boolean dirty = true;
        BodyPart chosen;
        long chosenUntil;
        boolean resetBody;
        /** The part and cut share the damage pipeline chose for the hit now landing (see {@link #pending}). */
        DamageSource pendingSource;
        BodyPart pendingPart;
        float pendingCut = -1;
        float pendingHealth;
    }

    /** The damage pipeline picked a part for this hit (and maybe moved it to the thorax); onHit uses the same one. */
    public static void pending(ServerPlayer player, DamageSource source, BodyPart part, float cut) {
        Runtime rt = rt(player);
        rt.pendingSource = source;
        rt.pendingPart = part;
        rt.pendingCut = cut;
        rt.pendingHealth = player.getHealth();
    }

    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

    private Health() {}

    private static Runtime rt(ServerPlayer player) {
        return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime());
    }

    static void init() {
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            // Only a real night's sleep: you wake in the morning.
            if (entity instanceof ServerPlayer player && player.isAlive() && player.level().getDayTime() % 24000 < 1000) {
                restAfterSleep(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player && taken > 0 && player.isAlive()) {
                onHit(player, source, taken);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                // Not now: Fabric copies the old player's data over after this event, which would bring the
                // destroyed parts back (and a destroyed head would kill again on every respawn). Reset next tick.
                rt(newPlayer).resetBody = true;
            }
            rt(newPlayer).dirty = true;
        });
        ServerTickEvents.END_SERVER_TICK.register(Health::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                spreadShot(arrow);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TarkovNet.TreatPart.TYPE, (payload, ctx) -> treatFromScreen(ctx.player(), payload.slot(), payload.part()));
    }

    // ================================================================== hits

    private static void onHit(ServerPlayer player, DamageSource source, float taken) {
        TarkovData d = Tarkov.data(player);
        Runtime rt = rt(player);
        float max = player.getMaxHealth();
        boolean fall = source.is(DamageTypeTags.IS_FALL);
        boolean chosen = rt.pendingSource == source;
        BodyPart part = source.is(BLEED) ? rt.bleeding : chosen ? rt.pendingPart : locate(player, source, fall);
        float cut = chosen && rt.pendingCut >= 0 ? rt.pendingCut : cutting(source);
        if (chosen) {
            // What the hit really took. Fabric's figure comes before the Resistance effect, so a fully resisted
            // fall would otherwise still break a leg.
            taken = Math.min(taken, Math.max(0, rt.pendingHealth - player.getHealth()));
        }
        rt.pendingSource = null;
        if (taken <= 0) {
            return;
        }
        if (part == null) {
            reconcile(player); // Fire, poison and the like spread over every part.
            vitalGone(player, d, source);
            return;
        }
        if (fall) {
            fall(player, d, rt, taken, max);
            return;
        }
        Body.damage(d, part, taken, max);
        if (Body.destroyed(d, part) && d.bleed[part.ordinal()] > 0) {
            d.bleed[part.ordinal()] = 0;
            Body.sync(d, part.ordinal());
        }
        rt.dirty = true;
        reconcile(player);
        if (vitalGone(player, d, source)) {
            return;
        }
        if (source.is(BLEED)) {
            return;
        }
        // Severity is the damage the part took over its maximum (5.2): a third of a thorax is a 33% injury.
        float severity = Math.min(1, taken / Math.max(1, max * part.share));
        if (severity < MIN_SEVERITY) {
            return;
        }
        float chance = Math.min(1, severity * 1.5f);
        if (source.getDirectEntity() == null) {
            return;
        }
        // Slash and pierce mostly bleed, blunt mostly breaks bones (9.1).
        if (player.getRandom().nextFloat() < chance * cut) {
            inflict(player, severity >= Body.HEAVY ? Body.HEAVY_BLEED : Body.LIGHT_BLEED, part, severity);
        }
        if (part.limb() && player.getRandom().nextFloat() < chance * (1 - cut)) {
            inflict(player, Body.FRACTURE, part, severity);
        }
    }

    /** A destroyed head or thorax kills (5.1). Returns true if it did. */
    private static boolean vitalGone(ServerPlayer player, TarkovData d, DamageSource source) {
        if (!player.isAlive() || player.isCreative() || (!Body.destroyed(d, BodyPart.HEAD) && !Body.destroyed(d, BodyPart.THORAX))) {
            return false;
        }
        player.setHealth(0);
        player.die(source);
        return true;
    }

    /** A fall lands on both legs, half each, and either can break. */
    private static void fall(ServerPlayer player, TarkovData d, Runtime rt, float taken, float max) {
        for (BodyPart leg : new BodyPart[] {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
            Body.damage(d, leg, taken / 2, max);
            if (Body.destroyed(d, leg) && d.bleed[leg.ordinal()] > 0) {
                d.bleed[leg.ordinal()] = 0;
                Body.sync(d, leg.ordinal());
            }
        }
        rt.dirty = true;
        reconcile(player);
        float severity = Math.min(1, taken / 2 / Math.max(1, max * BodyPart.LEFT_LEG.share));
        if (severity < MIN_SEVERITY) {
            return;
        }
        for (BodyPart leg : new BodyPart[] {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
            if (!Body.destroyed(d, leg) && player.getRandom().nextFloat() < Math.min(0.9f, severity * 1.5f)) {
                inflict(player, Body.FRACTURE, leg, severity);
            }
        }
    }

    /** Hits smaller than this share of the part they land on are grazes: they hurt but don't injure. */
    private static final float MIN_SEVERITY = 0.08f;

    /** How much of a hit is cutting (bleeds) rather than crushing (fractures). */
    private static float cutting(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return 1;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return 0.2f;
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES) || weapon.is(Items.TRIDENT)) {
                return 0.9f;
            }
            if (weapon.is(Items.MACE)) {
                return 0;
            }
            String type = attacker.getType().builtInRegistryHolder().key().location().getPath();
            if (type.contains("spider") || type.contains("wolf") || type.contains("cat") || type.contains("fox") || type.contains("bee")
                    || type.contains("phantom") || type.contains("guardian")) {
                return 0.8f;
            }
            return 0.25f;
        }
        return 0.5f;
    }

    /** The part a hit lands on, by height and side. Null for damage with no direction (it spreads). */
    public static BodyPart locate(ServerPlayer player, DamageSource source, boolean fall) {
        if (fall) {
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        }
        if (source.getEntity() instanceof com.cavmkii.seancraft.boss.BossEntity boss && boss.hitting != null) {
            // Boss stomps land on the legs and blasts anywhere, not at the height of its eyes.
            return boss.hitting.equals("legs") ? (player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG)
                    : Body.weighted(player.getRandom());
        }
        Entity direct = source.getDirectEntity();
        if (direct == null || direct.distanceToSqr(player) > 100) {
            return direct == null ? null : Body.weighted(player.getRandom());
        }
        double height;
        if (direct instanceof Projectile) {
            height = direct.getY() - player.getY();
        } else if (direct instanceof LivingEntity attacker) {
            height = attacker.getEyeY() - player.getY() - 0.3 + (player.getRandom().nextDouble() - 0.5) * 0.8;
        } else {
            height = direct.getY() - player.getY();
        }
        double h = height / Math.max(0.1, player.getBbHeight()) * 1.8;
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() + 90);
        boolean onRight = direct.position().subtract(player.position()).dot(right) > 0;
        BodyPart arm = onRight ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
        if (h > 1.55) {
            return BodyPart.HEAD;
        }
        if (h > 1.15) {
            return player.getRandom().nextFloat() < 0.3f ? arm : BodyPart.THORAX;
        }
        if (h > 0.85) {
            return player.getRandom().nextFloat() < 0.3f ? arm : BodyPart.STOMACH;
        }
        return onRight ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
    }

    /** Adds a bleed or fracture of some severity; another one on the same part stacks with it. */
    private static void inflict(ServerPlayer player, int flag, BodyPart part, float severity) {
        TarkovData d = Tarkov.data(player);
        int i = part.ordinal();
        if (flag != Body.FRACTURE && Body.destroyed(d, part)) {
            return; // a destroyed part doesn't bleed
        }
        if (flag == Body.FRACTURE) {
            Body.addFracture(d, part, severity);
        } else {
            Body.addBleed(d, part, severity);
        }
        rt(player).dirty = true;
        int pct = Math.round((flag == Body.FRACTURE ? d.fracture[i] : d.bleed[i]) * 100);
        String name = flag == Body.FRACTURE ? "Fracture" : d.bleed[i] >= Body.HEAVY ? "Heavy bleed" : "Light bleed";
        player.displayClientMessage(Component.literal(name + " " + pct + "% - " + part.label).withStyle(ChatFormatting.RED), true);
    }

    public static String conditionName(int flag) {
        return flag == Body.HEAVY_BLEED ? "Heavy bleed" : flag == Body.LIGHT_BLEED ? "Light bleed" : "Fracture";
    }

    // ================================================================== tick

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            TarkovData d = Tarkov.data(player);
            Runtime rt = rt(player);
            if (rt.resetBody) {
                rt.resetBody = false;
                Body.reset(d);
                rt.dirty = true;
            }
            if (d.painkillerTicks > 0) {
                d.painkillerTicks--;
                if (d.painkillerTicks % 20 == 0) {
                    rt.dirty = true;
                }
            }
            if (t % 5 == 0) {
                reconcile(player);
            }
            if (t % 4 == 0) {
                applyEffects(player, d);
            }
            if (t % 40 == 0) {
                hideoutCare(player, d, rt);
                bleed(player, d, rt);
            }
            if (rt.dirty && t % 5 == 0) {
                rt.dirty = false;
                ServerPlayNetworking.send(player, TarkovNet.BodySync.of(d));
            }
        }
    }

    /** A night's sleep heals every part that isn't destroyed by a share of its health. Destroyed parts still need surgery. */
    static void restAfterSleep(ServerPlayer player) {
        TarkovData d = Tarkov.data(player);
        float max = Math.max(1, player.getMaxHealth());
        float healed = 0;
        for (BodyPart p : BodyPart.values()) {
            healed += Body.heal(d, p, (float) (max * p.share * com.cavmkii.seancraft.Cfg.sleepHealPercent / 100), max, false);
        }
        if (healed > 0) {
            player.setHealth(Math.min(max * Body.capFraction(d), player.getHealth() + healed));
            rt(player).dirty = true;
            player.displayClientMessage(Component.literal("You wake up feeling better.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /**
     * Vanilla healing (food, regeneration, potions) can't fill hearts that belong to destroyed parts. Without this
     * the reconcile below would still clamp it, but the hearts would flicker up and back down.
     */
    public static float capHeal(net.minecraft.world.entity.LivingEntity entity, float amount) {
        if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return amount;
        }
        float cap = player.getMaxHealth() * Body.capFraction(Tarkov.data(player));
        return Math.max(0, Math.min(amount, cap - player.getHealth()));
    }

    /** Keep the parts and the real health bar in step. Destroyed parts cap how high health can go. */
    static void reconcile(ServerPlayer player) {
        TarkovData d = Tarkov.data(player);
        float max = Math.max(1, player.getMaxHealth());
        float actual = Math.min(1, player.getHealth() / max);
        float expected = Body.total(d);
        float diff = actual - expected;
        if (Math.abs(diff) * max < 0.05f) {
            return;
        }
        Body.spread(d, diff, -1);
        rt(player).dirty = true;
        float now = Body.total(d);
        if (actual > now + 0.0005f) {
            player.setHealth(now * max);
        }
    }

    private static void bleed(ServerPlayer player, TarkovData d, Runtime rt) {
        float max = player.getMaxHealth();
        for (BodyPart p : BodyPart.values()) {
            if (Body.destroyed(d, p) && d.bleed[p.ordinal()] > 0) {
                // Nothing left there to bleed.
                d.bleed[p.ordinal()] = 0;
                Body.sync(d, p.ordinal());
                rt.dirty = true;
                continue;
            }
            // From a trickle at 10% to 2% of max health every 2 seconds at 100%.
            float sev = d.bleed[p.ordinal()];
            float drain = sev <= 0 ? 0 : Math.max(0.1f + 0.4f * sev, max * (0.004f + 0.016f * sev));
            if (drain > 0 && player.isAlive()) {
                rt.bleeding = p;
                // Bleeding ignores the usual hurt cooldown so several parts can drain at once.
                player.invulnerableTime = 0;
                player.hurt(bleedSource(player), drain * 2);
                rt.bleeding = null;
            }
        }
    }

    private static DamageSource bleedSource(ServerPlayer player) {
        Holder<DamageType> type = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(BLEED)
                .map(h -> (Holder<DamageType>) h)
                .orElseGet(() -> player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC));
        return new DamageSource(type);
    }

    /** Spectrum penalties from {@link Injury}: walking, sprinting, jumping, swing strength and speed, mining. */
    private static void applyEffects(ServerPlayer player, TarkovData d) {
        Injury inj = Injury.of(d);
        modifier(player, Attributes.MOVEMENT_SPEED, "injured_leg", inj.move());
        modifier(player, Attributes.MOVEMENT_SPEED, "injured_sprint", player.isSprinting() ? inj.sprint() : 0);
        modifier(player, Attributes.JUMP_STRENGTH, "injured_jump", inj.jump());
        modifier(player, Attributes.ATTACK_DAMAGE, "injured_swing", inj.damage());
        modifier(player, Attributes.ATTACK_SPEED, "injured_arm", inj.attackSpeed());
        modifier(player, Attributes.BLOCK_BREAK_SPEED, "injured_arm_mining", inj.mining());
        if (inj.noSprint() && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    /** Arrows (and tridents) from someone with bad arms fly off line, more the worse the arms. */
    private static void spreadShot(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        if (arrow.tickCount > 0 || !(arrow.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        float aim = Injury.of(Tarkov.data(player)).aim();
        if (aim <= 0.05f) {
            return;
        }
        Vec3 v = arrow.getDeltaMovement();
        double speed = v.length();
        if (speed < 0.01) {
            return;
        }
        var random = player.getRandom();
        float yaw = (float) (random.nextGaussian() * aim * 0.5);
        float pitch = (float) (random.nextGaussian() * aim * 0.5);
        Vec3 turned = v.yRot(yaw * Mth.DEG_TO_RAD).xRot(pitch * Mth.DEG_TO_RAD);
        arrow.setDeltaMovement(turned);
    }

    private static void modifier(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0) {
            instance.removeModifier(SeanCraft.id(name));
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(SeanCraft.id(name), amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Medstation 1: bleeding stops in the hideout. Medstation 2: fractures mend after a minute there. */
    private static void hideoutCare(ServerPlayer player, TarkovData d, Runtime rt) {
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        int med = home != null ? home.level(Module.MEDSTATION) : 0;
        if (med <= 0) {
            rt.fractureCareTicks = 0;
            return;
        }
        if (Body.hasAny(d, Body.LIGHT_BLEED | Body.HEAVY_BLEED)) {
            Body.clearAll(d, Body.LIGHT_BLEED | Body.HEAVY_BLEED);
            rt.dirty = true;
            player.displayClientMessage(Component.literal("The Medstation stops your bleeding.").withStyle(ChatFormatting.GREEN), true);
        }
        if (med >= 2 && Body.hasAny(d, Body.FRACTURE)) {
            rt.fractureCareTicks += 40;
            if (rt.fractureCareTicks >= 1200) {
                Body.clearAll(d, Body.FRACTURE);
                rt.fractureCareTicks = 0;
                rt.dirty = true;
                player.displayClientMessage(Component.literal("The Medstation sets your fractures.").withStyle(ChatFormatting.GREEN), true);
            }
        } else {
            rt.fractureCareTicks = 0;
        }
    }

    // ================================================================== meds

    /** What a med would do on a part, or false if nothing. */
    static boolean helps(TarkovData d, Med med, BodyPart p) {
        if (med.limbsOnly && !p.limb()) {
            return false;
        }
        if (med.restores() && d.scar[p.ordinal()] > 0.001f) {
            return true;
        }
        if (Body.destroyed(d, p)) {
            // A destroyed part needs surgery to come back, but any bandage can still stop it bleeding.
            return med.revives || med.stopsLight && Body.has(d, p, Body.LIGHT_BLEED) || med.stopsHeavy && Body.has(d, p, Body.HEAVY_BLEED);
        }
        return med.stopsLight && Body.has(d, p, Body.LIGHT_BLEED)
                || med.stopsHeavy && Body.has(d, p, Body.HEAVY_BLEED)
                || med.fixesFracture && Body.has(d, p, Body.FRACTURE)
                || med.heal > 0 && d.body[p.ordinal()] < 0.999f;
    }

    /** The part to treat: the one picked on the STATUS screen in the last few seconds, else the one that needs it most. */
    public static BodyPart target(ServerPlayer player, Med med) {
        TarkovData d = Tarkov.data(player);
        if (med.painkillerTicks > 0) {
            return BodyPart.HEAD;
        }
        Runtime rt = rt(player);
        if (rt.chosen != null && player.serverLevel().getGameTime() < rt.chosenUntil && helps(d, med, rt.chosen)) {
            return rt.chosen;
        }
        BodyPart best = null;
        int bestScore = 0;
        for (BodyPart p : BodyPart.values()) {
            if (!helps(d, med, p)) {
                continue;
            }
            int score = (Body.destroyed(d, p) ? 1000 : 0) + (Body.has(d, p, Body.HEAVY_BLEED) ? 500 : 0)
                    + (Body.has(d, p, Body.LIGHT_BLEED) ? 200 : 0) + (Body.has(d, p, Body.FRACTURE) ? 100 : 0)
                    + (int) ((1 - d.body[p.ordinal()]) * 99) + (med.restores() ? (int) (d.scar[p.ordinal()] * 300) : 0);
            if (score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    /** Applies a med to a part. Brewing makes heals and painkillers stronger. Returns true if it did anything. */
    public static boolean treat(ServerPlayer player, Med med, BodyPart part) {
        if (part == null) {
            return false;
        }
        TarkovData d = Tarkov.data(player);
        double brewing = 1 + Skill.BREWING.effectValue(Rpg.data(player).skillLevel(Skill.BREWING)) / 100.0;
        boolean did = false;
        if (med.painkillerTicks > 0) {
            d.painkillerTicks = Math.max(d.painkillerTicks, (int) (med.painkillerTicks * brewing));
            did = true;
        } else {
            if (!helps(d, med, part)) {
                return false;
            }
            float max = player.getMaxHealth();
            int i = part.ordinal();
            if (med.restores() && d.scar[i] > 0.001f) {
                d.scar[i] = 0;
                did = true;
            }
            if (med.revives && Body.destroyed(d, part)) {
                if (!med.restores()) {
                    // A surgical kit saves the part, but it never quite comes back.
                    d.scar[i] = Math.max(d.scar[i], Med.KIT_SCAR);
                }
                Body.heal(d, part, 0, max, true);
                did = true;
            }
            if (med.stopsLight && Body.has(d, part, Body.LIGHT_BLEED) || med.stopsHeavy && Body.has(d, part, Body.HEAVY_BLEED)) {
                Body.set(d, part, Body.LIGHT_BLEED, false);
                if (med.stopsHeavy) {
                    Body.set(d, part, Body.HEAVY_BLEED, false);
                }
                did = true;
            }
            if (med.fixesFracture && Body.has(d, part, Body.FRACTURE)) {
                Body.set(d, part, Body.FRACTURE, false);
                did = true;
            }
            if (med.heal > 0) {
                float used = Body.heal(d, part, (float) (max * med.heal * brewing), max, false);
                if (used > 0) {
                    player.setHealth(Math.min(max, player.getHealth() + used));
                    did = true;
                }
            }
        }
        if (did) {
            rt(player).dirty = true;
            Rpg.addSkillXp(player, Skill.BREWING, 5);
            player.displayClientMessage(Component.literal(med.label + (med.painkillerTicks > 0 ? " taken." : " used on " + part.label.toLowerCase() + "."))
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return did;
    }

    /** /seancraft injure: sets a condition on a part directly. Returns what happened, or null for an unknown kind. */
    public static String injure(ServerPlayer player, BodyPart part, String kind, float severity) {
        TarkovData d = Tarkov.data(player);
        int i = part.ordinal();
        float max = player.getMaxHealth();
        switch (kind) {
            case "bleed" -> {
                d.bleed[i] = severity;
                Body.sync(d, i);
            }
            case "fracture" -> {
                d.fracture[i] = severity;
                Body.sync(d, i);
            }
            case "scar" -> {
                d.scar[i] = Math.min(0.9f, severity);
                Body.heal(d, part, 0, max, false);
            }
            case "destroy" -> {
                if (part == BodyPart.HEAD || part == BodyPart.THORAX) {
                    return "Destroying the " + part.label.toLowerCase() + " would kill you; pick a limb or the stomach.";
                }
                Body.damage(d, part, d.body[i] * part.share * max, max);
                player.setHealth(Math.max(1, Body.total(d) * max));
            }
            case "heal" -> {
                d.bleed[i] = 0;
                d.fracture[i] = 0;
                d.scar[i] = 0;
                Body.sync(d, i);
                float used = Body.heal(d, part, max, max, true);
                player.setHealth(Math.min(max, player.getHealth() + used));
            }
            default -> {
                return null;
            }
        }
        reconcile(player);
        rt(player).dirty = true;
        return part.label + ": " + kind + (kind.equals("destroy") || kind.equals("heal") ? "" : " " + Math.round(severity * 100) + "%");
    }

    /** STATUS screen: use a carried med (index into Tetris.carried) on a chosen part right away. */
    private static void treatFromScreen(ServerPlayer player, int slot, int partIndex) {
        java.util.List<ItemStack> carried = com.cavmkii.seancraft.tetris.Tetris.carried(player);
        if (slot < 0 || slot >= carried.size() || partIndex < 0 || partIndex >= BodyPart.values().length) {
            return;
        }
        ItemStack stack = carried.get(slot);
        if (!(stack.getItem() instanceof MedItem medItem)) {
            return;
        }
        BodyPart part = BodyPart.values()[partIndex];
        if (treat(player, medItem.med, part) && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
