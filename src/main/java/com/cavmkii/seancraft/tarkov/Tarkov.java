package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.ecology.Seasons;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * The world's danger and the home base on the server (design doc 2). There is no timer: distance from spawn, night
 * and biome set the risk. Mobs get tougher by distance band and can spawn as elites, more often at night; at night in
 * the Frontier and Far lands, hunting parties track players off safe ground. Home Benches hold modules and craft
 * home-only items from what you carry plus the stash.
 */
public final class Tarkov {
    private static final String ROLLED_TAG = "seancraft_threat_rolled";
    private static final String PARTY_TAG = "seancraft_hunting_party";
    /** Elite chances per band (2.1): one star, two stars. Doubled at night. */
    private static final double[] ONE_STAR = {0, 0.05, 0.15, 0.25};
    private static final double[] TWO_STAR = {0, 0, 0.05, 0.10};

    /** Per-session state. */
    static final class Runtime {
        long graceUntil;
        Distance band;
        Integer place;
        /** Overworld day number of the last hunting party sent at this player, and the game time of the last one. */
        long partyDay = -1;
        long lastParty;
        /** A party announced by its horn (18.2): when it arrives, and from which direction (radians). 0 for none. */
        long partyDueAt;
        double partyAngle;
        final List<UUID> party = new ArrayList<>();
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
        Camps.init();

        ServerTickEvents.END_SERVER_TICK.register(Tarkov::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Runtime rt = runtime(handler.player);
            rt.graceUntil = handler.player.serverLevel().getGameTime() + 100;
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
            rt.place = null;
        });
        ServerEntityEvents.ENTITY_LOAD.register(Tarkov::rollElite);
        // Any bed sets respawn (2.4), except near a Summoning Pedestal, so a bed beside a boss arena doesn't skip the trip back.
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, sleepingPos) -> {
            if (!(player instanceof ServerPlayer sp) || !nearPedestal(sp.serverLevel(), sleepingPos)) {
                return true;
            }
            sp.displayClientMessage(Component.literal("Too close to a boss dungeon: this bed doesn't set your spawn.").withStyle(ChatFormatting.GRAY), true);
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

    /** Within Home.PedestalClearance of a boss dungeon's pedestal. */
    public static boolean nearPedestal(ServerLevel level, BlockPos pos) {
        return com.cavmkii.seancraft.boss.BossSites.nearSite(level, Vec3.atCenterOf(pos), Cfg.pedestalClearance);
    }

    /** Safe ground (1.1): a home base, a spawn point's safe radius, or around a lit camp. Hostile mobs don't spawn there. */
    public static boolean safeGround(ServerLevel level, Vec3 pos) {
        return Hideouts.at(level, pos) != null || SpawnPoints.safe(level, pos) || Camps.near(level, pos, Cfg.campSafeRadius) != null;
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
        if (now < rt.graceUntil) {
            ServerPlayNetworking.send(player, new TarkovNet.PlaceState(TarkovNet.PlaceState.UNKNOWN, 0, false));
            return;
        }
        Hideouts.Hideout home = Hideouts.at(level, player.position());
        Distance band = Distance.at(level, player.position());
        if (rt.band != null && band != rt.band && Cfg.distanceScaling) {
            title(player, band.label.toUpperCase(java.util.Locale.ROOT), band == Distance.HEARTLAND ? "Monsters here are ordinary"
                    : String.format("Monsters: +%d%% health, +%d%% damage", Math.round(band.health * 100), Math.round(band.damage * 100)),
                    band.ordinal() > rt.band.ordinal() ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        }
        rt.band = band;
        int place = home != null ? TarkovNet.PlaceState.HOME : SpawnPoints.safe(level, player.position()) ? TarkovNet.PlaceState.SPAWN
                : Camps.near(level, player.position(), Cfg.campSafeRadius) != null ? TarkovNet.PlaceState.CAMP : TarkovNet.PlaceState.OPEN;
        if (home != null) {
            if (home.level(Module.MEDSTATION) >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
            }
            if (home.level(Module.MAP_ROOM) >= 2 && (rt.place == null || rt.place != TarkovNet.PlaceState.HOME)) {
                forecast(player);
            }
        }
        rt.place = place;
        partyArrives(player, rt, place, band);
        huntingParty(player, rt, place, band);
        ServerPlayNetworking.send(player, new TarkovNet.PlaceState(place, band.ordinal(), hunted(player, rt)));
    }

    /** Map room 2 (2.4.1): the season and the weather ahead, on coming home. */
    private static void forecast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String season = Seasons.describe(level);
        String weather;
        if (level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData data) {
            if (level.isRaining()) {
                int left = data.getRainTime() / 1200;
                weather = (level.isThundering() ? "Storm" : "Rain") + " for about " + Math.max(1, left) + " more minutes";
            } else {
                int clear = data.getClearWeatherTime() > 0 ? data.getClearWeatherTime() : data.getRainTime();
                weather = "Dry for about " + Math.max(1, clear / 1200) + " minutes";
            }
        } else {
            weather = "No forecast";
        }
        player.sendSystemMessage(Component.literal("Map room: " + season + ". " + weather + ".").withStyle(ChatFormatting.GRAY));
    }

    // ================================================================== elites and hunting parties

    /**
     * Hostile mobs get tougher with distance from spawn (2.1) and may spawn as elites: more often further out, twice
     * as often at night, and at Far lands rates in the Nether. Decided once, where the mob appears; the mob keeps it.
     */
    private static void rollElite(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob) || !(entity instanceof net.minecraft.world.entity.monster.Enemy)
                || entity instanceof com.cavmkii.seancraft.boss.BossEntity || entity.getTags().contains(ROLLED_TAG)) {
            return;
        }
        entity.addTag(ROLLED_TAG);
        Distance band = Distance.at(level, mob.position());
        scale(mob, Cfg.elites ? rollStars(level, band, level.random.nextDouble()) : 0, band);
    }

    private static int rollStars(ServerLevel level, Distance band, double roll) {
        int b = level.dimension() == Level.NETHER ? Distance.FAR_LANDS.ordinal() : band.ordinal();
        double night = level.dimension() == Level.OVERWORLD && level.isNight() ? 2 : 1;
        // A blood moon doubles them again (18.1).
        if (com.cavmkii.seancraft.compat.Celestials.bloodMoon(level)) {
            night *= 2;
        }
        if (roll < TWO_STAR[b] * night) {
            return 2;
        }
        return roll < (TWO_STAR[b] + ONE_STAR[b]) * night ? 1 : 0;
    }

    /** Tag recording the distance band a mob appeared in, for its XP (2.1). */
    public static final String BAND_TAG = "seancraft_band_";

    /**
     * Distance and elite bonuses, added together and capped (2.1.1): each elite star is +100% health and +50% damage
     * on top of the band's own bonus.
     */
    private static void scale(Mob mob, int stars, Distance band) {
        mob.addTag(BAND_TAG + band.ordinal());
        double healthBonus = Math.min(band.health + stars, Cfg.distanceCaps[0] - 1);
        double damageBonus = Math.min(band.damage + stars * 0.5, Cfg.distanceCaps[1] - 1);
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
            // Elite tells (18.2): the name in Blood, always shown; a faint aura (client); 2-star ones 15% bigger.
            mob.setCustomName(Component.literal("★".repeat(stars) + " ").append(mob.getType().getDescription()).withColor(0xC8402E));
            mob.setCustomNameVisible(true);
            AttributeInstance size = mob.getAttribute(Attributes.SCALE);
            if (size != null && stars >= 2) {
                size.addOrReplacePermanentModifier(new AttributeModifier(SeanCraft.id("elite_size"), 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
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

    /**
     * Hunting parties (2.1): at night in the Frontier and Far lands, a player off safe ground can be tracked by two or
     * three mobs led by an elite, about once a night. In the Nether, any time, at half the rate. They give up at dawn.
     * None while the player is in a boss fight.
     */
    private static void huntingParty(ServerPlayer player, Runtime rt, int place, Distance band) {
        ServerLevel level = player.serverLevel();
        boolean nether = level.dimension() == Level.NETHER;
        boolean night = level.dimension() == Level.OVERWORLD && level.isNight();
        if (!rt.party.isEmpty() && level.dimension() == Level.OVERWORLD && !night) {
            // Dawn: the party gives up.
            for (UUID id : rt.party) {
                if (level.getEntity(id) instanceof Mob mob) {
                    mob.setTarget(null);
                    AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
                    if (follow != null) {
                        follow.removeModifier(SeanCraft.id("hunter"));
                    }
                }
            }
            rt.party.clear();
            rt.partyDueAt = 0;
        }
        if (!Cfg.huntingParties || place != TarkovNet.PlaceState.OPEN || level.getDifficulty() == Difficulty.PEACEFUL
                || com.cavmkii.seancraft.boss.Arenas.inFight(player)) {
            return;
        }
        long now = level.getGameTime();
        if (nether) {
            // Half the overworld rate, at any time: about one a day.
            if (now - rt.lastParty < 12000 || player.getRandom().nextInt(1200) != 0) {
                return;
            }
        } else {
            long day = level.getDayTime() / 24000;
            // A blood moon brings them into every band but the Heartland (18.1).
            Distance from = com.cavmkii.seancraft.compat.Celestials.bloodMoon(level) ? Distance.WILDS : Distance.FRONTIER;
            if (!night || band.ordinal() < from.ordinal() || rt.partyDay == day || player.getRandom().nextInt(400) != 0) {
                return;
            }
            rt.partyDay = day;
        }
        rt.lastParty = now;
        announceParty(player, rt);
    }

    /** Testing: a hunting party is announced for this player now. */
    public static void forceParty(ServerPlayer player) {
        Runtime rt = runtime(player);
        rt.lastParty = player.serverLevel().getGameTime();
        announceParty(player, rt);
    }

    /** True while a party is on its way or still hunting this player: the HUD's Hunted chip. */
    static boolean hunted(ServerPlayer player, Runtime rt) {
        if (rt.partyDueAt > 0) {
            return true;
        }
        for (UUID id : rt.party) {
            if (player.serverLevel().getEntity(id) instanceof Mob mob && mob.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hunting horns (18.2): a horn or a howl from the direction the party will come, 20 to 30 seconds before it
     * arrives, so the player can run, hide or pick the ground.
     */
    private static void announceParty(ServerPlayer player, Runtime rt) {
        ServerLevel level = player.serverLevel();
        rt.partyAngle = level.random.nextDouble() * Math.PI * 2;
        rt.partyDueAt = level.getGameTime() + 400 + level.random.nextInt(201);
        Vec3 from = player.position().add(Math.cos(rt.partyAngle) * 24, 4, Math.sin(rt.partyAngle) * 24);
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound = level.random.nextBoolean()
                ? net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(level.random.nextInt(4))
                : net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.WOLF_HOWL);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound, net.minecraft.sounds.SoundSource.HOSTILE,
                from.x, from.y, from.z, 6f, 0.8f + level.random.nextFloat() * 0.2f, level.random.nextLong()));
        player.displayClientMessage(Component.literal("Something is hunting you.").withColor(0xC8402E), true);
    }

    /** A party that was announced arrives, unless the player reached safe ground or a fight in the meantime. */
    private static void partyArrives(ServerPlayer player, Runtime rt, int place, Distance band) {
        if (rt.partyDueAt <= 0 || player.serverLevel().getGameTime() < rt.partyDueAt) {
            return;
        }
        rt.partyDueAt = 0;
        ServerLevel level = player.serverLevel();
        boolean dawn = level.dimension() == Level.OVERWORLD && !level.isNight();
        if (!dawn && place == TarkovNet.PlaceState.OPEN && !com.cavmkii.seancraft.boss.Arenas.inFight(player)) {
            sendParty(player, rt, band);
        }
    }

    /** Two or three of the local monsters, 30-45 blocks out, led by an elite, sent straight at you. */
    private static void sendParty(ServerPlayer player, Runtime rt, Distance band) {
        ServerLevel level = player.serverLevel();
        List<EntityType<? extends Mob>> pool = hunters(level, player.blockPosition());
        if (pool.isEmpty()) {
            return;
        }
        int size = 2 + level.random.nextInt(2);
        int sent = 0;
        for (int i = 0; i < size; i++) {
            BlockPos spot = findSpot(level, player, rt.partyAngle);
            if (spot == null) {
                continue;
            }
            Mob mob = pool.get(level.random.nextInt(pool.size())).create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            mob.addTag(ROLLED_TAG);
            mob.addTag(PARTY_TAG);
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
            // The first one leads, as an elite: two stars in the Far lands.
            int stars = i == 0 ? (band == Distance.FAR_LANDS || level.dimension() == Level.NETHER ? 2 : 1) : 0;
            scale(mob, stars, Distance.at(level, mob.position()));
            level.addFreshEntity(mob);
            mob.setTarget(player);
            rt.party.add(mob.getUUID());
            sent++;
        }
        if (sent > 0) {
            warnFriends(player);
        }
    }

    /** Map room 2 (2.4.1): everyone else hears when a hunting party is tracking a friend. */
    private static void warnFriends(ServerPlayer hunted) {
        boolean mapRoom = false;
        for (ServerLevel level : hunted.server.getAllLevels()) {
            for (Hideouts.Hideout h : Hideouts.get(level).list) {
                mapRoom |= h.level(Module.MAP_ROOM) >= 2;
            }
        }
        if (!mapRoom) {
            return;
        }
        for (ServerPlayer p : hunted.server.getPlayerList().getPlayers()) {
            if (p != hunted) {
                p.displayClientMessage(Component.literal("Map room: a hunting party is tracking " + hunted.getName().getString() + ".")
                        .withStyle(ChatFormatting.RED), true);
            }
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
        // Biome tags (10), so Terralith's biomes pick the right hunters.
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            pool.add(EntityType.DROWNED);
        } else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
            pool.add(EntityType.HUSK);
            pool.add(EntityType.SKELETON);
        } else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SNOWY)
                || biome.value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
            pool.add(EntityType.STRAY);
            pool.add(EntityType.ZOMBIE);
        } else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SWAMP)) {
            pool.add(EntityType.WITCH);
            pool.add(EntityType.ZOMBIE);
            pool.add(EntityType.SLIME);
        } else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VEGETATION_DENSE)
                && biome.is(BiomeTags.IS_FOREST) && !biome.is(BiomeTags.IS_JUNGLE)) {
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

    /** Somewhere 30-45 blocks out, toward {@code bearing} (radians) give or take 45 degrees. */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, double bearing) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = bearing + (level.random.nextDouble() - 0.5) * Math.PI / 2;
            double dist = 30 + level.random.nextDouble() * 15;
            Vec3 at = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            BlockPos ground = Wander.surface(level, at);
            if (ground != null && Math.abs(ground.getY() - player.getY()) < 25 && level.getFluidState(ground.below()).isEmpty()
                    && !safeGround(level, Vec3.atCenterOf(ground))) {
                return ground;
            }
        }
        return null;
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
                note = "Build level " + (current + 1);
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
            // No build timer: the level is done the moment everything's in.
            h.given[module.ordinal()] = null;
            h.levels[module.ordinal()]++;
            Hideouts.get(level).setDirty();
            for (ServerPlayer p : level.players()) {
                if (h.contains(p.position())) {
                    p.sendSystemMessage(Component.literal(module.label + " is now level " + h.level(module) + ".").withStyle(ChatFormatting.GOLD));
                }
            }
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
        if (stack.is(TarkovContent.CAMP_KIT)) {
            return "A fire and a bedroll: sets your respawn and makes a small safe circle. Sneak and use it to pack it up.";
        }
        return "";
    }

    /** Furnaces in a home base cook faster with its Workshop (2.4.1): +25%, +50%, +75%. Extra ticks of progress per tick. */
    public static double furnaceBonus(ServerLevel level, BlockPos pos) {
        Hideouts.Hideout h = Hideouts.at(level, Vec3.atCenterOf(pos));
        if (h == null) {
            return 0;
        }
        return switch (h.level(Module.WORKSHOP)) {
            case 1 -> 0.25;
            case 2 -> 0.5;
            case 3 -> 0.75;
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
