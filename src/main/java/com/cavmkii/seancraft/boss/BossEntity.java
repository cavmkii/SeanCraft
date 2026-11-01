package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.Speech;
import com.cavmkii.seancraft.combat.Damage;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Rpg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

/**
 * Every boss runs on this (design doc 8.4): a boss bar, no despawning, no knockback, a leash to its arena, and a fight
 * loop of reposition, pick an attack, telegraph it, hit, recover, driven by the boss's data file. Phases are health
 * thresholds with an invulnerable transition between them.
 */
public abstract class BossEntity extends PathfinderMob {
    public enum State { IDLE, WINDUP, ACTIVE, RECOVER, TRANSITION }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
    /** Vanilla caps max health at 1024; the real pool (2,000 and up) is tracked as a multiple of this. */
    static final float VANILLA_HEALTH = 1000;
    static final int RESET_TICKS = 600;
    static final int TARGET_SWITCH_TICKS = 300;
    static final int MAX_IDLE_TICKS = 60;
    static final int TRANSITION_TICKS = 60;
    static final int MAX_SCALED_PLAYERS = 6;

    protected BlockPos center = BlockPos.ZERO;
    private boolean started;
    protected int phase;
    private int stateTicks;
    protected BossDef.Attack current;
    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private String last = "";
    private String beforeLast = "";
    private final Set<UUID> hitThisAttack = new HashSet<>();
    private final Map<UUID, Long> lastDamage = new HashMap<>();
    private int maxPlayers = 1;
    private int emptyTicks;
    private long nextTargetSwitch;
    protected Vec3 aim = Vec3.ZERO;
    /** The split of the attack being dealt, read by the damage pipeline. */
    public Map<Damage.Type, Float> dealing;
    /**
     * Where the attack being dealt lands on a player, if not where a swing from its eyes would: "legs" for stomps,
     * "spread" for blasts that catch the whole body. Read by body-part location.
     */
    public String hitting;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Boss"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    protected BossEntity(EntityType<? extends BossEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder bossAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, VANILLA_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    protected abstract String defId();

    public BossDef def() {
        return BossDef.get(defId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0);
        builder.define(PHASE, 0);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    public int phaseIndex() {
        return entityData.get(PHASE);
    }

    BlockPos center() {
        return center;
    }

    void setCenter(BlockPos center) {
        this.center = center.immutable();
    }

    /** The fight begins (after the rise, or on loading a fight in progress). */
    void start() {
        started = true;
        Arenas.begin(this);
        applyPhase(false);
        if (phase == 0 && getHealth() >= getMaxHealth() - 0.01f) {
            say("intro");
        }
    }

    private BossDef.Phase phaseDef() {
        List<BossDef.Phase> phases = def().phases();
        return phases.get(Math.min(phase, phases.size() - 1));
    }

    private void applyPhase(boolean announce) {
        BossDef.Phase p = phaseDef();
        entityData.set(PHASE, phase);
        bar.setName(Component.literal(barName()));
        bar.setColor(phase == 0 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        setCustomName(Component.literal(p.name()));
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.addOrReplacePermanentModifier(new AttributeModifier(SeanCraft.id("boss_phase_scale"), p.scale() - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (announce) {
            onPhase(phase);
        }
    }

    /** The boss's whole health pool, with +85% per extra player who has stood in the ring (8.4.6). */
    float totalHealth() {
        return def().health() * (1 + 0.85f * (maxPlayers - 1));
    }

    float damageMultiplier() {
        return 1 + 0.1f * (maxPlayers - 1);
    }

    /** Health on the boss's own scale, for its numbers. */
    public float realHealth() {
        return getHealth() / getMaxHealth() * totalHealth();
    }

    // ================================================================== damage taken

    /** Resistance by type from the data file. Spirit only hurts bosses listed as weak to it (9.1). */
    public float resistance(Damage.Type type) {
        Float value = def().resist().get(type);
        if (value != null) {
            return value;
        }
        return type == Damage.Type.SPIRIT || type == Damage.Type.CHOP || type == Damage.Type.PICKAXE ? 0 : 1;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!started || state() == State.TRANSITION) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && !Arenas.inRing(center, player.position())) {
            // Outside attackers do nothing (8.4.5).
            return false;
        }
        amount = incoming(source, amount);
        if (amount <= 0) {
            return false;
        }
        feedback(source);
        if (attacker instanceof ServerPlayer player) {
            lastDamage.put(player.getUUID(), level().getGameTime());
        }
        // The pipeline turns this into real damage; the vanilla bar holds the boss's pool at a smaller scale.
        return super.hurt(source, amount * getMaxHealth() / totalHealth());
    }

    /** A boss's own rules for an incoming hit, before the pipeline (Mark's Tenure, for example). Zero blocks it. */
    protected float incoming(DamageSource source, float amount) {
        return amount;
    }

    /** Hits it's weak to burst and make it flinch; resisted ones spark dully (8.4.9). */
    private void feedback(DamageSource source) {
        Damage.Split split = Damage.split(source);
        if (split == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        float weak = 0, resisted = 0, total = 0;
        for (Damage.Type type : Damage.Type.values()) {
            float share = split.get(type);
            total += share;
            float r = resistance(type);
            if (r > 1) {
                weak += share;
            } else if (r < 1) {
                resisted += share;
            }
        }
        if (total <= 0) {
            return;
        }
        if (weak / total >= 0.5f) {
            level.sendParticles(ParticleTypes.CRIT, getX(), getY(0.6), getZ(), 20, 0.4, 0.5, 0.4, 0.3);
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 0.7f);
        } else if (resisted / total >= 0.5f) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.6), getZ(), 6, 0.2, 0.3, 0.2, 0.01);
            level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.6f, 0.6f);
        }
    }

    /** A phase can't be skipped: health stops at the next threshold, which starts the transition. */
    @Override
    public void setHealth(float health) {
        List<BossDef.Phase> phases = level().isClientSide || def() == null ? List.of() : def().phases();
        if (phase + 1 < phases.size()) {
            float floor = phases.get(phase + 1).from() * getMaxHealth();
            if (health <= floor && state() != State.TRANSITION && started) {
                health = floor;
                beginTransition();
            }
        }
        super.setHealth(health);
    }

    private void beginTransition() {
        setState(State.TRANSITION);
        current = null;
        getNavigation().stop();
    }

    // ================================================================== the fight loop

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (def() == null) {
            return;
        }
        if (!started) {
            start();
        }
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        List<ServerPlayer> players = playersInRing();
        maxPlayers = Math.max(maxPlayers, Math.min(MAX_SCALED_PLAYERS, players.size()));
        updateBar(level);

        if (players.isEmpty()) {
            if (++emptyTicks >= RESET_TICKS) {
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(this) < 80 * 80) {
                        p.sendSystemMessage(Component.literal(phaseDef().name() + " is gone. The totem is lost.").withStyle(ChatFormatting.GRAY));
                    }
                }
                Arenas.end(getUUID());
                bar.removeAllPlayers();
                discard();
            }
            return;
        }
        emptyTicks = 0;
        leash();
        pickTarget(players, now);
        LivingEntity target = getTarget();

        stateTicks++;
        switch (state()) {
            case TRANSITION -> {
                if (stateTicks == 1) {
                    say("transition");
                }
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY(0.5), getZ(), 3, 0.4, 0.6, 0.4, 0.02);
                transitionTick(phase + 1, stateTicks);
                if (stateTicks >= TRANSITION_TICKS) {
                    phase++;
                    applyPhase(true);
                    setState(State.IDLE);
                }
            }
            case IDLE -> {
                if (target != null) {
                    reposition(target);
                    BossDef.Attack next = choose(target, now, stateTicks >= MAX_IDLE_TICKS);
                    if (next != null && stateTicks >= 15) {
                        beginAttack(next, target, now);
                    }
                }
            }
            case WINDUP -> {
                if (target != null) {
                    getLookControl().setLookAt(target, 30, 30);
                }
                telegraph(current, stateTicks, target);
                if (stateTicks >= current.windup()) {
                    hitThisAttack.clear();
                    setState(State.ACTIVE);
                }
            }
            case ACTIVE -> {
                perform(current, stateTicks, target);
                if (stateTicks >= Math.max(1, current.active())) {
                    setState(State.RECOVER);
                }
            }
            case RECOVER -> {
                if (stateTicks >= current.recovery()) {
                    current = null;
                    setState(State.IDLE);
                }
            }
        }
        if (getHealth() < getMaxHealth() * 0.15f && !saidLow) {
            saidLow = true;
            say("low");
        }
    }

    private boolean saidLow;

    private void setState(State state) {
        entityData.set(STATE, state.ordinal());
        stateTicks = 0;
    }

    protected List<ServerPlayer> playersInRing() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p.isAlive() && !p.isSpectator() && !p.isCreative() && Arenas.inRing(center, p.position())) {
                out.add(p);
            }
        }
        return out;
    }

    private void updateBar(ServerLevel level) {
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (tickCount % 10 == 0) {
            bar.setName(Component.literal(barName()));
        }
        for (ServerPlayer p : level.players()) {
            boolean in = Arenas.inRing(center, p.position()) && p.isAlive();
            if (in && !bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            } else if (!in && bar.getPlayers().contains(p)) {
                bar.removePlayer(p);
            }
        }
    }

    /** Pulled out of the ring, it walks back to the middle; it never chases anyone out (8.4.5). */
    private void leash() {
        Vec3 mid = Vec3.atBottomCenterOf(center.above());
        if (!Arenas.inRing(center, position())) {
            getNavigation().moveTo(mid.x, mid.y, mid.z, 1.2);
            if (position().distanceTo(mid) > Arenas.RADIUS + 8) {
                teleportTo(mid.x, mid.y, mid.z);
            }
        }
    }

    /** Switch targets at least every 15 seconds, favouring whoever hit it most recently (8.4.6). */
    private void pickTarget(List<ServerPlayer> players, long now) {
        LivingEntity target = getTarget();
        boolean valid = target instanceof ServerPlayer p && players.contains(p);
        if (valid && now < nextTargetSwitch) {
            return;
        }
        ServerPlayer best = null;
        long bestTime = Long.MIN_VALUE;
        for (ServerPlayer p : players) {
            long t = lastDamage.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
            if (p == target && players.size() > 1) {
                continue;
            }
            if (t > bestTime || (t == bestTime && (best == null || p.distanceToSqr(this) < best.distanceToSqr(this)))) {
                best = p;
                bestTime = t;
            }
        }
        setTarget(best != null ? best : players.get(0));
        nextTargetSwitch = now + TARGET_SWITCH_TICKS;
    }

    /** Move to suit the next attack: keep a distance band, or walk in (from the phase's data). */
    protected void reposition(LivingEntity target) {
        BossDef.Phase p = phaseDef();
        double dist = distanceTo(target);
        getLookControl().setLookAt(target, 30, 30);
        if (p.approach()) {
            if (dist > 2.5) {
                getNavigation().moveTo(target, p.speed());
            } else {
                getNavigation().stop();
            }
            return;
        }
        if (p.keepMax() <= 0) {
            return;
        }
        if (dist > p.keepMax()) {
            getNavigation().moveTo(target, p.speed());
        } else if (dist < p.keepMin()) {
            Vec3 away = position().subtract(target.position()).normalize().scale(p.keepMin() - dist + 2);
            Vec3 to = position().add(away);
            if (!Arenas.inRing(center, to)) {
                to = Vec3.atBottomCenterOf(center.above());
            }
            getNavigation().moveTo(to.x, to.y, to.z, p.speed());
        } else {
            getNavigation().stop();
        }
    }

    /**
     * An attack this phase can use from here, off cooldown, not used twice in a row already; picked by weight. Past
     * the idle limit, range is ignored so the boss is never idle for long (8.4.2).
     */
    private BossDef.Attack choose(LivingEntity target, long now, boolean anyRange) {
        double dist = distanceTo(target);
        List<BossDef.Attack> valid = new ArrayList<>();
        int weights = 0;
        for (BossDef.Attack a : def().attacks()) {
            if (!a.phases().contains(phase + 1) || cooldownUntil.getOrDefault(a.id(), 0L) > now) {
                continue;
            }
            if (a.id().equals(last) && a.id().equals(beforeLast)) {
                continue;
            }
            if (!anyRange && (dist < a.minRange() || dist > a.maxRange())) {
                continue;
            }
            if (!allowed(a, target)) {
                continue;
            }
            valid.add(a);
            weights += a.weight();
        }
        if (valid.isEmpty()) {
            return null;
        }
        int roll = getRandom().nextInt(Math.max(1, weights));
        for (BossDef.Attack a : valid) {
            roll -= a.weight();
            if (roll < 0) {
                return a;
            }
        }
        return valid.get(0);
    }

    /** A boss's own conditions on an attack, beyond range and cooldown (Daylan only types with nobody close). */
    protected boolean allowed(BossDef.Attack attack, LivingEntity target) {
        return true;
    }

    /** Each tick of the invulnerable transition into phase {@code next} (0-based). */
    protected void transitionTick(int next, int tick) {}

    /** The boss bar's text; the phase name unless a boss shows more (Daylan's follower count). */
    protected String barName() {
        return phaseDef().name();
    }

    private void beginAttack(BossDef.Attack attack, LivingEntity target, long now) {
        current = attack;
        beforeLast = last;
        last = attack.id();
        // More players, more attacks: cooldowns shrink, wind-ups and recovery don't (8.4.6).
        cooldownUntil.put(attack.id(), now + Math.round(attack.cooldown() / (1 + 0.2f * (maxPlayers - 1))));
        aim = target.position();
        getNavigation().stop();
        setState(State.WINDUP);
        windupSound(attack);
    }

    protected void windupSound(BossDef.Attack attack) {
        level().playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.4f, 1.6f);
    }

    /** The wind-up's particles and sounds. Bosses add their own per action. */
    protected void telegraph(BossDef.Attack attack, int tick, LivingEntity target) {
        if (level() instanceof ServerLevel level && tick % 3 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    /** Hitboxes are live: deal the attack. Called every tick of its Active time. */
    protected abstract void perform(BossDef.Attack attack, int tick, LivingEntity target);

    /** Hit a player with the current attack, at most once per attack (9.5.4). Returns true if it landed. */
    protected boolean hit(ServerPlayer player, BossDef.Attack attack, float damage) {
        if (!hitThisAttack.add(player.getUUID())) {
            return false;
        }
        hitting = attack.extra().has("body") ? attack.extra().get("body").getAsString() : null;
        boolean landed = strike(player, damage, attack.split());
        if (landed && attack.flag("stagger")) {
            Restraint.stagger(player);
        }
        return landed;
    }

    /** Damage a player as this boss with a given type split, outside the once-per-attack rule (minions, zones). */
    protected boolean strike(ServerPlayer player, float damage, Map<Damage.Type, Float> split) {
        dealing = split;
        try {
            return player.hurt(damageSources().mobAttack(this), damage * damageMultiplier());
        } finally {
            dealing = null;
            hitting = null;
        }
    }

    /** Lets the current attack hit everyone again (each stomp of a multi-hit attack is its own hit). */
    protected void resetHits() {
        hitThisAttack.clear();
    }

    /** Within {@code radius} of the boss and inside an arc of {@code angle} degrees in front of it (or touching it). */
    protected boolean inArc(ServerPlayer player, float radius, float angle) {
        Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
        if (to.length() > radius + 0.6 || Math.abs(player.getY() - getY()) > 3) {
            return false;
        }
        double off = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
        return to.length() < 1 || off <= Math.toRadians(angle / 2);
    }

    /** Within {@code radius} of a point, level with it give or take 3 blocks. */
    protected static boolean near(ServerPlayer player, Vec3 point, float radius) {
        double dx = player.getX() - point.x;
        double dz = player.getZ() - point.z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - point.y) < 3;
    }

    /** Within {@code width} of the line from the boss's eyes toward {@code target}, up to {@code reach} along it. */
    protected boolean onLine(ServerPlayer player, Vec3 target, float reach, float width) {
        Vec3 from = getEyePosition();
        Vec3 dir = target.subtract(from).normalize();
        Vec3 at = player.position().add(0, 1, 0);
        double along = at.subtract(from).dot(dir);
        return along >= 0 && along <= reach && at.distanceTo(from.add(dir.scale(along))) <= width;
    }

    /** Particles in a flat ring, for zones and shockwaves. */
    protected void ring(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 at, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.15, at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** A projectile from one of its attacks reached a player. */
    void shotHit(ServerPlayer player, BossDef.Attack attack) {
        hit(player, attack, attack.damage());
    }

    // ================================================================== lines, death, rewards

    protected void say(String key) {
        String line = def() == null ? "" : def().line(key);
        if (!line.isEmpty()) {
            Speech.say(this, phaseDef().name(), ChatFormatting.GOLD, line, true);
        }
    }

    protected void onPhase(int phase) {}

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && def() != null) {
            say("death");
            String retired = def().line("retired");
            if (!retired.isEmpty()) {
                bar.setName(Component.literal(retired));
            }
            bar.setProgress(0);
            List<ServerPlayer> players = playersInRing();
            Arenas.end(getUUID());
            reward(players);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            bar.removeAllPlayers();
            if (reason.shouldDestroy()) {
                Arenas.end(getUUID());
            }
        }
        super.remove(reason);
    }

    /**
     * Rewards per player in the ring at the kill (8.4.8), in a chest on the pedestal: everyone gets the key material and
     * a roll of rare materials; a first kill also gives the trophy and class XP.
     */
    private void reward(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) level();
        List<ItemStack> loot = new ArrayList<>();
        for (ServerPlayer player : players) {
            Set<String> beaten = player.getAttachedOrCreate(BossContent.BEATEN);
            boolean first = !beaten.contains(defId());
            if (first) {
                Set<String> next = new HashSet<>(beaten);
                next.add(defId());
                player.setAttached(BossContent.BEATEN, next);
                long xp = 0;
                PlayerData data = Rpg.data(player);
                for (int i = 0; i < def().tier(); i++) {
                    xp += PlayerData.xpToNext(Math.min(PlayerData.MAX_LEVEL - 1, data.level + i));
                }
                Rpg.addClassXp(player, xp);
            }
            loot.addAll(drops(player, first));
        }
        BlockPos chestPos = center.above();
        if (!level.getBlockState(chestPos).isAir() && !level.getBlockState(chestPos).canBeReplaced()) {
            chestPos = chestPos.above();
        }
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : loot) {
                if (slot < chest.getContainerSize()) {
                    chest.setItem(slot++, stack);
                } else {
                    net.minecraft.world.Containers.dropItemStack(level, chestPos.getX() + 0.5, chestPos.getY() + 1, chestPos.getZ() + 0.5, stack);
                }
            }
        }
    }

    /** One player's share: key material for everyone, the trophy on a first kill, and a roll of rare materials. */
    protected abstract List<ItemStack> drops(ServerPlayer player, boolean firstKill);

    // ================================================================== rules

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        // No boats, no minecarts (8.4.5).
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // Damage comes from attack hitboxes, never from touching the boss (8.4.2).
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("Center", center.asLong());
        tag.putInt("Phase", phase);
        tag.putInt("MaxPlayers", maxPlayers);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        center = BlockPos.of(tag.getLong("Center"));
        phase = tag.getInt("Phase");
        maxPlayers = Math.max(1, tag.getInt("MaxPlayers"));
    }
}
