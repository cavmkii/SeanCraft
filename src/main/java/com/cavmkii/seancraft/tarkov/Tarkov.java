package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.entity.Wander;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/**
 * TarkoValheim's raid loop on the server. Outside every hideout is raid; the longer you're out, the higher the
 * threat (elite mobs, hunting parties). Dying in raid costs your kit, except the secure pouch. Hideout
 * benches hold modules and craft hideout-only items from what you carry plus the stash.
 */
public final class Tarkov {
    public static final int MAX_THREAT = 4;
    private static final int[] WAVE_SECONDS = {0, 150, 100, 70, 50};
    private static final int[] WAVE_SIZE = {0, 1, 2, 3, 3};
    private static final double[] ONE_STAR = {0, 0.10, 0.20, 0.35, 0.50};
    private static final double[] TWO_STAR = {0, 0, 0.05, 0.10, 0.20};
    private static final String ROLLED_TAG = "seancraft_threat_rolled";

    /** Per-session raid state. */
    static final class Runtime {
        long graceUntil;
        Boolean inRaid;
        long raidStart;
        int announcedThreat;
        long nextWave;
        long lastRaidTicks;
        Distance band;
        /** Raid time paused by FOB tables so far this raid. */
        long fobUsedTicks;
        long lastHurt = Long.MIN_VALUE / 2;
    }

    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

    private Tarkov() {}

    public static TarkovData data(Player player) {
        return player.getAttachedOrCreate(TarkovContent.DATA);
    }

    private static Runtime runtime(ServerPlayer player) {
        return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime());
    }

    public static void init() {
        TarkovContent.init();
        TarkovNet.register();
        Health.init();
        Stations.init();
        Cures.init();
        SpawnPoints.init();
        TarkovLoot.init();

        ServerTickEvents.END_SERVER_TICK.register(Tarkov::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Runtime rt = runtime(handler.player);
            rt.graceUntil = handler.player.serverLevel().getGameTime() + 100;
            rt.inRaid = null;
            TarkovData data = data(handler.player);
            if (!data.placedAtSpawn) {
                data.placedAtSpawn = true;
                // Only brand-new characters; anyone who has already played stays where they are.
                if (handler.player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) < 1200) {
                    SpawnPoints.sendToSpawn(handler.player);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNTIME.remove(handler.player.getUUID()));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Runtime rt = runtime(newPlayer);
            rt.graceUntil = newPlayer.serverLevel().getGameTime() + 60;
            rt.inRaid = null;
            rt.announcedThreat = 0;
        });
        ServerEntityEvents.ENTITY_LOAD.register(Tarkov::rollElite);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player) {
                runtime(player).lastHurt = player.serverLevel().getGameTime();
            }
        });
        // Only a bed in a hideout sets your respawn point (2.5), so a bed beside a boss arena doesn't skip the trip back.
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, sleepingPos) -> {
            if (!(player instanceof ServerPlayer sp) || Hideouts.at(sp.serverLevel(), Vec3.atCenterOf(sleepingPos)) != null) {
                return true;
            }
            sp.displayClientMessage(Component.literal("This bed isn't in a hideout, so it doesn't set your spawn.").withStyle(ChatFormatting.GRAY), true);
            return false;
        });

        ServerPlayNetworking.registerGlobalReceiver(TarkovNet.HideoutAction.TYPE, (payload, ctx) -> {
            if (payload.kind() == -2 || payload.kind() == -3) {
                // A module station's UPGRADE (-2) or CRAFT (-3) button.
                Hideouts.Hideout h = Hideouts.at(ctx.player().serverLevel(), ctx.player().position());
                Module module;
                try {
                    module = Module.valueOf(payload.id());
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (h != null && h.modules[module.ordinal()] != null) {
                    ctx.player().closeContainer();
                    if (payload.kind() == -2) {
                        openModule(ctx.player(), h, module);
                    } else {
                        HideoutCraftMenu.open(ctx.player(), h.pos, module);
                    }
                }
                return;
            }
            if (payload.kind() >= 0) {
                hideoutAction(ctx.player(), payload.pos(), payload.kind(), payload.id());
            }
            openHideout(ctx.player(), payload.pos());
        });
    }

    // ================================================================== tick

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            tickHideouts(level);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
                tickPlayer(player);
            }
        }
    }

    private static void tickHideouts(ServerLevel level) {
        Hideouts data = Hideouts.get(level);
        long now = level.getGameTime();
        for (Hideouts.Hideout h : data.list) {
            if (h.building >= 0 && now >= h.buildDone) {
                Module module = Module.values()[h.building];
                h.levels[h.building]++;
                h.building = -1;
                data.setDirty();
                for (ServerPlayer p : level.players()) {
                    if (h.contains(p.position())) {
                        p.sendSystemMessage(Component.literal(module.label + " is now level " + h.level(module) + ".")
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        Runtime rt = runtime(player);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Hideouts.Hideout home = Hideouts.at(level, player.position());
        TarkovData data = data(player);

        if (now < rt.graceUntil) {
            ServerPlayNetworking.send(player, new TarkovNet.RaidState(0, 0, 0, -1, 0, -1, false));
            return;
        }

        Distance band = Distance.at(level, player.position());
        if (rt.band != null && band != rt.band && Cfg.distanceScaling) {
            title(player, band.label.toUpperCase(java.util.Locale.ROOT), band == Distance.HEARTLAND ? "Monsters here are ordinary"
                    : String.format("Monsters: +%d%% health, +%d%% damage", Math.round(band.health * 100), Math.round(band.damage * 100)),
                    band.ordinal() > rt.band.ordinal() ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        }
        rt.band = band;

        boolean atSpawn = home == null && SpawnPoints.safe(level, player.position());
        boolean inRaid = home == null && !atSpawn;
        if (rt.inRaid == null || rt.inRaid != inRaid) {
            if (inRaid) {
                rt.raidStart = now;
                rt.fobUsedTicks = 0;
                rt.announcedThreat = 0;
                title(player, "RAID STARTED", "Only your secure pouch is safe", ChatFormatting.RED);
            } else if (rt.inRaid != null) {
                title(player, "EXTRACTED", "Raid time " + clock(now - rt.raidStart), ChatFormatting.GREEN);
            }
            rt.inRaid = inRaid;
        }

        if (home != null) {
            data.lastIntel = home.level(Module.INTEL);
            if (home.level(Module.MEDSTATION) >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
            }
            ServerPlayNetworking.send(player, new TarkovNet.RaidState(1, 0, 0, -1, band.ordinal(), -1, false));
            return;
        }
        if (atSpawn) {
            ServerPlayNetworking.send(player, new TarkovNet.RaidState(3, 0, 0, -1, band.ordinal(), -1, false));
            return;
        }

        // The Frontier, the Far lands and the Nether start at threat level 2 (2.3.1).
        long headStart = (long) (Distance.HEAD_START_MINUTES * 1200);
        if ((band.headStart || level.dimension() == Level.NETHER) && Cfg.raidHostility && now - rt.raidStart < headStart) {
            rt.raidStart = now - headStart;
        }
        boolean paused = fobPauses(player, rt, now);
        if (com.cavmkii.seancraft.boss.Arenas.inFight(player)) {
            // Raid threat stops rising while a boss is alive (8.4.5); this costs no FOB time.
            rt.raidStart = Math.min(rt.raidStart + 20, now);
        } else if (paused) {
            // This runs once a second: push the raid's start back by the second that just passed.
            rt.raidStart = Math.min(rt.raidStart + 20, now);
            rt.fobUsedTicks += 20;
        }
        long raidTicks = now - rt.raidStart;
        rt.lastRaidTicks = raidTicks;
        int threat = threat(raidTicks, data.lastIntel);
        if (threat > rt.announcedThreat) {
            String[] lines = {"", "The land stirs. Monsters grow bolder.", "You're being hunted.", "They're closing in.", "Get out. Now."};
            title(player, "THREAT " + threat, lines[threat], ChatFormatting.GOLD);
            rt.nextWave = now + 400;
        }
        rt.announcedThreat = threat;
        if (threat > 0 && Cfg.huntingParties && now >= rt.nextWave) {
            rt.nextWave = now + (long) (WAVE_SECONDS[threat] * 20 * (0.8 + player.getRandom().nextDouble() * 0.4));
            sendParty(player, threat, data.lastIntel);
        }
        int fobLeft = (int) Math.max(0, (fobBudgetTicks() - rt.fobUsedTicks) / 20);
        ServerPlayNetworking.send(player, new TarkovNet.RaidState(2, (int) (raidTicks / 20), threat, secondsToNextThreat(raidTicks, data.lastIntel),
                band.ordinal(), fobLeft, paused));
    }

    private static long fobBudgetTicks() {
        return (long) (Cfg.fobPauseMinutes * 1200);
    }

    /**
     * A FOB table in the player's chunk stops the raid timer (2.6), while the raid's FOB time lasts, and not with a
     * monster within 16 blocks or for 10 seconds after the player is hurt.
     */
    private static boolean fobPauses(ServerPlayer player, Runtime rt, long now) {
        if (rt.fobUsedTicks >= fobBudgetTicks() || now - rt.lastHurt < 200 || !Fobs.get(player.serverLevel()).has(player.chunkPosition())) {
            return false;
        }
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
    }

    /** Raid minute at which a threat level (1-4) starts. The Intel center pushes every level back. */
    private static double threatStart(int level, int intel) {
        double[] at = Cfg.threatMinutes;
        double base = at[Math.min(level, at.length) - 1];
        return base + intel * Module.INTEL_CALM_MINUTES;
    }

    static int threat(long raidTicks, int intel) {
        if (!Cfg.raidHostility) {
            return 0;
        }
        double minutes = raidTicks / 1200.0;
        int threat = 0;
        while (threat < MAX_THREAT && minutes >= threatStart(threat + 1, intel)) {
            threat++;
        }
        return threat;
    }

    private static int secondsToNextThreat(long raidTicks, int intel) {
        int threat = threat(raidTicks, intel);
        if (!Cfg.raidHostility || threat >= MAX_THREAT) {
            return -1;
        }
        return (int) Math.max(0, threatStart(threat + 1, intel) * 60 - raidTicks / 20.0);
    }

    /** Current threat for a player, 0 if not in raid. */
    public static int threatFor(ServerPlayer player) {
        Runtime rt = RUNTIME.get(player.getUUID());
        if (rt == null || rt.inRaid == null || !rt.inRaid) {
            return 0;
        }
        return threat(player.serverLevel().getGameTime() - rt.raidStart, data(player).lastIntel);
    }

    public static boolean inRaid(ServerPlayer player) {
        Runtime rt = RUNTIME.get(player.getUUID());
        if (rt != null && rt.inRaid != null) {
            return rt.inRaid;
        }
        return Hideouts.at(player.serverLevel(), player.position()) == null && !SpawnPoints.safe(player.serverLevel(), player.position());
    }

    // ================================================================== elites and hunting parties

    /**
     * Hostile mobs get tougher with distance from spawn, and near a player deep in a raid they may be elite. Both are
     * decided once, where the mob first appears, and the mob keeps them.
     */
    private static void rollElite(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob) || !(entity instanceof net.minecraft.world.entity.monster.Enemy)
                || entity.getTags().contains(ROLLED_TAG)) {
            return;
        }
        entity.addTag(ROLLED_TAG);
        int threat = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) < 90 * 90) {
                threat = Math.max(threat, threatFor(player));
            }
        }
        scale(mob, threat > 0 ? rollStars(threat, level.random.nextDouble()) : 0, Distance.at(level, mob.position()));
    }

    private static int rollStars(int threat, double roll) {
        if (roll < TWO_STAR[threat]) {
            return 2;
        }
        return roll < TWO_STAR[threat] + ONE_STAR[threat] ? 1 : 0;
    }

    /** Tag recording the distance band a mob appeared in, for its XP (2.3.1). */
    public static final String BAND_TAG = "seancraft_band_";

    /**
     * Distance and elite bonuses, added together and capped (2.3.2): each elite star is +100% health and +50% damage
     * on top of the band's own bonus.
     */
    private static void scale(Mob mob, int stars, Distance band) {
        mob.addTag(BAND_TAG + band.ordinal());
        double healthBonus = Math.min(band.health + stars, Distance.MAX_HEALTH_MULTIPLIER - 1);
        double damageBonus = Math.min(band.damage + stars * 0.5, Distance.MAX_DAMAGE_MULTIPLIER - 1);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && healthBonus > 0) {
            health.addOrReplacePermanentModifier(new AttributeModifier(SeanCraft.id("elite_health"), healthBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damageBonus > 0) {
            damage.addOrReplacePermanentModifier(new AttributeModifier(SeanCraft.id("elite_damage"), damageBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (stars > 0) {
            mob.setCustomName(Component.literal("★".repeat(stars) + " ").withStyle(ChatFormatting.GOLD).append(mob.getType().getDescription()));
        }
    }

    /** XP multiplier for a killed mob, from the band it appeared in. */
    public static float xpMultiplier(Entity mob) {
        for (Distance band : Distance.values()) {
            if (mob.getTags().contains(BAND_TAG + band.ordinal())) {
                return (float) (1 + band.rewards);
            }
        }
        return 1;
    }

    /** A few of the local biome's monsters, 30-45 blocks away, sent straight at you. */
    private static void sendParty(ServerPlayer player, int threat, int intel) {
        ServerLevel level = player.serverLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        List<EntityType<? extends Mob>> pool = hunters(level, player.blockPosition());
        if (pool.isEmpty()) {
            return;
        }
        if (intel >= 2) {
            player.displayClientMessage(Component.literal("Intel: a hunting party is coming for you.").withStyle(ChatFormatting.RED), true);
        }
        for (int i = 0; i < WAVE_SIZE[threat]; i++) {
            BlockPos spot = findSpot(level, player);
            if (spot == null) {
                continue;
            }
            Mob mob = pool.get(level.random.nextInt(pool.size())).create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            mob.addTag(ROLLED_TAG);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                // Keeps undead from burning before they reach you.
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0f);
            }
            AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (follow != null) {
                follow.addOrReplacePermanentModifier(new AttributeModifier(SeanCraft.id("hunter"), 32, AttributeModifier.Operation.ADD_VALUE));
            }
            scale(mob, rollStars(threat, level.random.nextDouble()), Distance.at(level, mob.position()));
            level.addFreshEntity(mob);
            mob.setTarget(player);
        }
    }

    private static List<EntityType<? extends Mob>> hunters(ServerLevel level, BlockPos pos) {
        List<EntityType<? extends Mob>> pool = new ArrayList<>();
        if (level.dimension() == Level.NETHER) {
            pool.add(EntityType.WITHER_SKELETON);
            pool.add(EntityType.BLAZE);
            pool.add(EntityType.PIGLIN_BRUTE);
            return pool;
        }
        if (level.dimension() == Level.END) {
            pool.add(EntityType.ENDERMAN);
            return pool;
        }
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            pool.add(EntityType.DROWNED);
        } else if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
            pool.add(EntityType.HUSK);
            pool.add(EntityType.SKELETON);
        } else if (biome.value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
            pool.add(EntityType.STRAY);
            pool.add(EntityType.ZOMBIE);
        } else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            pool.add(EntityType.WITCH);
            pool.add(EntityType.ZOMBIE);
            pool.add(EntityType.SLIME);
        } else if (biome.is(Biomes.DARK_FOREST)) {
            pool.add(EntityType.VINDICATOR);
            pool.add(EntityType.PILLAGER);
        } else if (biome.is(BiomeTags.IS_JUNGLE)) {
            pool.add(EntityType.SPIDER);
            pool.add(EntityType.ZOMBIE);
        } else {
            pool.add(EntityType.ZOMBIE);
            pool.add(EntityType.SKELETON);
            pool.add(EntityType.SPIDER);
        }
        return pool;
    }

    private static BlockPos findSpot(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 30 + level.random.nextDouble() * 15;
            Vec3 at = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            BlockPos ground = Wander.surface(level, at);
            if (ground != null && Math.abs(ground.getY() - player.getY()) < 25 && level.getFluidState(ground.below()).isEmpty()
                    && Hideouts.at(level, Vec3.atCenterOf(ground)) == null && !SpawnPoints.safe(level, Vec3.atCenterOf(ground))) {
                return ground;
            }
        }
        return null;
    }

    // ================================================================== death

    /** Called (from a mixin) just before a dying player's inventory would drop. */
    /** Share of carried stacks lost on a boss death. */
    static final float BOSS_DEATH_LOSS = 0.3f;

    public static void beforeDeathDrops(ServerPlayer player) {
        boolean raid = inRaid(player);
        Runtime rt = runtime(player);
        String time = rt.inRaid != null && rt.inRaid ? " Raid time " + clock(player.serverLevel().getGameTime() - rt.raidStart) + "." : "";
        boolean bossFight = com.cavmkii.seancraft.boss.Arenas.inFight(player);
        if (raid && !bossFight && !player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
            // Striking parts of lost part-built gear go back to the hideout (9.4.9).
            Recovery.onDeath(player, com.cavmkii.seancraft.tetris.Tetris.atRisk(player, Cfg.raidDeathRule == Cfg.DeathRule.VANILLA));
        }
        if (bossFight) {
            // A boss death costs a random share of what you carry, lighter than a raid death (8.4.7). The rest drops
            // where you fell, for the run back.
            int lost = com.cavmkii.seancraft.tetris.Tetris.onBossDeath(player, BOSS_DEATH_LOSS);
            player.sendSystemMessage(Component.literal("Down in the boss fight. You lost " + lost
                    + " stacks; the rest is where you fell. The fight goes on while anyone is still in the ring.").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        com.cavmkii.seancraft.tetris.Tetris.onDeath(player, raid, Cfg.raidDeathRule);
        if (!raid) {
            return;
        }
        switch (Cfg.raidDeathRule) {
            case LOSE_EVERYTHING -> {
                player.getInventory().clearContent();
                player.sendSystemMessage(Component.literal("Killed in action." + time + " Everything outside your secure pouch is gone.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case KEEP_SECURE -> player.sendSystemMessage(Component.literal("Killed in action." + time + " Your secure pouch is safe.")
                    .withStyle(ChatFormatting.DARK_RED));
            case VANILLA -> {
                // Everything drops, the secure pouch too (handled with the grids).
            }
        }
    }

    // ================================================================== hideout bench

    /** Which module's screen each player has open, or none for the Hideout Bench's own (2.5). */
    private static final Map<UUID, Module> FOCUS = new HashMap<>();

    /** The Hideout Bench's screen: its crafts, module blocks among them. */
    /** The Hideout Bench: its 3x3 crafting grid (4.8). */
    public static void openBench(ServerPlayer player, BlockPos pos) {
        FOCUS.remove(player.getUUID());
        HideoutCraftMenu.open(player, pos);
    }

    /** A module block's screen: build and upgrade that module (2.5). Actions still go through the hideout's bench. */
    public static void openModule(ServerPlayer player, Hideouts.Hideout h, Module module) {
        FOCUS.put(player.getUUID(), module);
        openHideout(player, h.pos);
    }

    /**
     * Using a module block: once it's built and has recipes, its crafting grid; otherwise its build-and-upgrade screen.
     * Each screen has a button over to the other.
     */
    public static void useModule(ServerPlayer player, Hideouts.Hideout h, Module module) {
        if (h.level(module) >= 1 && HideoutCraftMenu.crafts(module)) {
            FOCUS.remove(player.getUUID());
            HideoutCraftMenu.open(player, h.pos, module);
        } else {
            openModule(player, h, module);
        }
    }

    public static void openHideout(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Hideouts.Hideout h = Hideouts.byPos(level, pos);
        // In reach of the bench, or anywhere in its hideout (opened from another station's tab or a module block).
        if (h == null || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64 && !h.contains(player.position())) {
            return;
        }
        Module focus = FOCUS.get(player.getUUID());
        if (focus != null && h.modules[focus.ordinal()] == null) {
            focus = null;
            FOCUS.remove(player.getUUID());
        }
        long now = level.getGameTime();
        List<TarkovNet.Entry> entries = new ArrayList<>();
        for (Module module : Module.values()) {
            if (module != focus) {
                continue;
            }
            int current = h.level(module);
            String title = module.label + " " + current + "/" + module.max();
            String icon = id(module.icon);
            if (current >= module.max()) {
                entries.add(new TarkovNet.Entry(TarkovNet.Entry.MODULE, module.name(), title, module.levels.get(module.max() - 1).effect(),
                        icon, TarkovNet.Entry.MAXED, "Fully upgraded", List.of(), 0));
                continue;
            }
            Module.Level next = module.levels.get(current);
            int[] given = h.given(module);
            List<TarkovNet.CostLine> costs = new ArrayList<>();
            boolean complete = true;
            boolean canGive = false;
            for (int c = 0; c < next.costs().size(); c++) {
                Cost cost = next.costs().get(c);
                boolean underway = h.building == module.ordinal();
                int put = underway ? cost.count() : given[c];
                int missing = Math.max(0, cost.count() - put);
                int carry = Math.min(missing, Stash.count(player, h, cost));
                complete &= missing == 0;
                canGive |= carry > 0;
                costs.add(new TarkovNet.CostLine(cost.label(), id(cost.icon()), put, cost.count(), carry, track(cost)));
            }
            int status;
            String note;
            int progress = 0;
            if (h.building == module.ordinal()) {
                status = TarkovNet.Entry.BUILDING;
                long total = Math.max(1, h.buildDone - h.buildStarted);
                progress = (int) Math.min(100, (now - h.buildStarted) * 100 / total);
                note = "Building, " + Math.max(0, (h.buildDone - now) / 20) + "s left";
            } else if (!complete) {
                status = canGive || player.getAbilities().instabuild ? TarkovNet.Entry.GIVE : TarkovNet.Entry.SHORT;
                note = canGive ? "Put in what you have" : "Bring more materials";
            } else if (h.building >= 0) {
                status = TarkovNet.Entry.BUSY;
                note = "Another module is being built";
            } else if (next.needs() != null && h.level(next.needs()) < next.needsLevel()) {
                status = TarkovNet.Entry.LOCKED;
                note = "Needs " + next.needs().label + " " + next.needsLevel();
            } else {
                status = TarkovNet.Entry.READY;
                note = "Build level " + (current + 1) + " (" + buildSeconds(h, next) + "s)";
            }
            entries.add(new TarkovNet.Entry(TarkovNet.Entry.MODULE, module.name(), title, "Next: " + next.effect(), icon, status, note, costs, progress));
        }
        for (HideoutRecipe recipe : focus != null ? List.<HideoutRecipe>of() : HideoutRecipe.ALL) {
            ItemStack result = recipe.result().get();
            int status;
            String note;
            if (recipe.gate() != null && h.level(recipe.gate()) < recipe.gateLevel()) {
                status = TarkovNet.Entry.LOCKED;
                note = "Needs " + recipe.gate().label + " " + recipe.gateLevel();
            } else if (!Stash.canAfford(player, h, recipe.costs())) {
                status = TarkovNet.Entry.SHORT;
                note = "Not enough materials";
            } else {
                status = TarkovNet.Entry.READY;
                note = "Craft";
            }
            String description = result.getItem() instanceof com.cavmkii.seancraft.boss.BossContent.TotemItem totem
                    ? com.cavmkii.seancraft.boss.BossSites.hint(player, totem) : describe(result);
            if (recipe.id().startsWith(HideoutRecipe.UPGRADE)) {
                description = "Upgrade: trade in an empty " + recipe.costs().get(0).label() + " for half the materials.";
            }
            entries.add(new TarkovNet.Entry(TarkovNet.Entry.RECIPE, recipe.id(), (recipe.id().startsWith(HideoutRecipe.UPGRADE) ? "Upgrade to " : "")
                    + result.getHoverName().getString()
                    + (result.getCount() > 1 ? " x" + result.getCount() : ""), description, id(result.getItem()), status, note,
                    costLines(player, h, recipe.costs()), 0));
        }
        int chests = Stash.containers(level, h).size();
        String summary = focus != null ? focus.label + " " + h.level(focus) + "/" + focus.max() + ". Put materials in from you and the stash."
                : "Radius " + (int) h.radius() + " blocks. Stash: " + chests + (chests == 1 ? " chest" : " chests") + " in range.";
        String station = focus != null ? Stations.moduleKind(focus) : Stations.HIDEOUT;
        ServerPlayNetworking.send(player, new TarkovNet.HideoutView(pos, (int) h.radius(), summary, entries, station));
        Stations.send(player);
    }

    private static int buildSeconds(Hideouts.Hideout h, Module.Level level) {
        // The Workshop speeds up building, as it does crafting in TarkoValheim.
        return (int) Math.max(5, level.buildSeconds() * (1 - 0.25 * h.level(Module.WORKSHOP)));
    }

    private static void hideoutAction(ServerPlayer player, BlockPos pos, int kind, String id) {
        ServerLevel level = player.serverLevel();
        Hideouts.Hideout h = Hideouts.byPos(level, pos);
        // In reach of the bench, or anywhere in its hideout (opened from another station's tab).
        if (h == null || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64 && !h.contains(player.position())) {
            return;
        }
        if (kind == TarkovNet.Entry.MODULE) {
            Module module;
            try {
                module = Module.valueOf(id);
            } catch (IllegalArgumentException e) {
                return;
            }
            int current = h.level(module);
            if (h.building == module.ordinal() || current >= module.max()) {
                return;
            }
            Module.Level next = module.levels.get(current);
            int[] given = h.given(module);
            boolean complete = true;
            for (int c = 0; c < next.costs().size(); c++) {
                Cost cost = next.costs().get(c);
                int missing = Math.max(0, cost.count() - given[c]);
                if (missing > 0) {
                    // Contribute: put in whatever you have toward it.
                    given[c] += player.getAbilities().instabuild ? missing : Stash.takeUpTo(player, h, cost, missing);
                }
                complete &= given[c] >= cost.count();
            }
            Hideouts.get(level).setDirty();
            // Contributions can go in any time; building needs everything in, the prerequisite, and a free slot.
            if (!complete || h.building >= 0 || next.needs() != null && h.level(next.needs()) < next.needsLevel()) {
                return;
            }
            h.given[module.ordinal()] = null;
            long now = level.getGameTime();
            h.building = module.ordinal();
            h.buildStarted = now;
            h.buildDone = now + buildSeconds(h, next) * 20L;
            Hideouts.get(level).setDirty();
        } else if (kind == TarkovNet.Entry.RECIPE) {
            HideoutRecipe recipe = HideoutRecipe.byId(id);
            if (recipe == null || recipe.gate() != null && h.level(recipe.gate()) < recipe.gateLevel()
                    || !Stash.canAfford(player, h, recipe.costs())) {
                return;
            }
            Stash.consume(player, h, recipe.costs());
            ItemStack result = recipe.result().get();
            if (result.getItem() instanceof com.cavmkii.seancraft.boss.BossContent.TotemItem totem) {
                // A map to the boss's dungeon comes with its totem (8.4.1).
                ItemStack map = com.cavmkii.seancraft.boss.BossSites.map(player.server.overworld(), totem);
                if (!map.isEmpty() && !player.getInventory().add(map)) {
                    player.drop(map, false);
                }
            }
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
    }

    private static List<TarkovNet.CostLine> costLines(ServerPlayer player, Hideouts.Hideout h, List<Cost> costs) {
        List<TarkovNet.CostLine> lines = new ArrayList<>();
        for (Cost cost : costs) {
            lines.add(new TarkovNet.CostLine(cost.label(), id(cost.icon()), Stash.count(player, h, cost), cost.count(), 0, track(cost)));
        }
        return lines;
    }

    private static String describe(ItemStack stack) {
        if (stack.getItem() instanceof MedItem med) {
            return med.med.describe();
        }
        if (stack.is(TarkovContent.ANTIDOTE)) {
            return "Cures every harmful effect: poison, wither, hunger, nausea...";
        }
        if (stack.getItem() instanceof SecureItem secure) {
            return "Secure pouch, " + secure.tier.width + "x" + secure.tier.height + ". Use it to install.";
        }
        if (stack.getItem() instanceof com.cavmkii.seancraft.tetris.GearItem gear) {
            return (gear.spec.kind == com.cavmkii.seancraft.tetris.GearSpec.Kind.RIG ? "Rig, " : "Backpack, ")
                    + gear.spec.gridWidth + "x" + gear.spec.gridHeight + " grid. Wear it on the GEAR tab.";
        }
        return "";
    }

    /** Furnaces inside a hideout cook faster with its Workshop: extra ticks of progress per tick. */
    public static double furnaceBonus(ServerLevel level, BlockPos pos) {
        Hideouts.Hideout h = Hideouts.at(level, Vec3.atCenterOf(pos));
        if (h == null) {
            return 0;
        }
        return switch (h.level(Module.WORKSHOP)) {
            case 1 -> 1 / 3.0;
            case 2 -> 1;
            case 3 -> 3;
            default -> 0;
        };
    }

    // ================================================================== helpers

    private static String id(net.minecraft.world.item.Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** What the gather list follows for a cost: the item, or {@code #tag} when any of a kind will do. */
    private static String track(Cost cost) {
        return cost.item() != null ? id(cost.item()) : "#" + cost.tag().location();
    }

    static String clock(long ticks) {
        long total = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    /** A banner at the top of the screen, out of the way of what you're looking at. */
    private static void title(ServerPlayer player, String title, String subtitle, ChatFormatting color) {
        Integer rgb = color.getColor();
        ServerPlayNetworking.send(player, new TarkovNet.Banner(title, subtitle, 0xFF000000 | (rgb != null ? rgb : 0xFFFFFF)));
    }
}
