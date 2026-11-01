package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.SeanCraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the character system: use-skill and class xp, the death penalty, attribute effects,
 * resources, casting, slots and respecs. All state lives in {@link PlayerData}; this class keeps only
 * per-session runtime bits (cooldowns, combat timers, movement tracking).
 */
public final class Rpg {
    public static final AttachmentType<PlayerData> DATA = AttachmentRegistry.<PlayerData>builder()
            .persistent(PlayerData.CODEC)
            .copyOnDeath()
            .initializer(PlayerData::new)
            .buildAndRegister(SeanCraft.id("character"));

    /** Per-session state that doesn't need saving. */
    static final class Runtime {
        Stats stats;
        final Map<ActiveSkill, Long> cooldownEnd = new HashMap<>();
        long combatUntil;
        long parryUntil;
        int leapTicks;
        /** Just respawned: start with full mana and stamina and no rage. */
        boolean refill;
        Vec3 lastPos;
        boolean dirty = true;
    }

    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

    private Rpg() {}

    public static PlayerData data(Player player) {
        return player.getAttachedOrCreate(DATA);
    }

    static Runtime runtime(ServerPlayer player) {
        return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime());
    }

    public static Stats stats(ServerPlayer player) {
        Runtime rt = runtime(player);
        if (rt.stats == null) {
            rt.stats = new Stats(data(player));
        }
        return rt.stats;
    }

    // ================================================================== init

    public static void init() {
        RpgContent.init();
        RpgNet.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> markDirty(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNTIME.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Next tick: Fabric copies the old player's data over after this event, which would undo the refill.
            runtime(newPlayer).refill = true;
            markDirty(newPlayer);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(RpgDamage::allowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                deathPenalty(player);
            } else if (source.getEntity() instanceof ServerPlayer killer) {
                addClassXp(killer, (long) Math.ceil(entity.getMaxHealth() * 2));
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                onBlockBroken(serverPlayer, (ServerLevel) level, pos, state);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(RpgNet.Allocate.TYPE, (payload, ctx) -> allocate(ctx.player(), payload.node()));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.SetSlot.TYPE, (payload, ctx) -> setSlot(ctx.player(), payload.slot(), payload.skill()));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.Cast.TYPE, (payload, ctx) -> cast(ctx.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.Respec.TYPE, (payload, ctx) -> respec(ctx.player(), payload.pos()));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.ChooseOrigin.TYPE, (payload, ctx) -> chooseOrigin(ctx.player(), payload.origin()));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.AssignAttribute.TYPE, (payload, ctx) -> assignAttribute(ctx.player(), payload.attr()));
    }

    // ================================================================== xp

    public static void addSkillXp(ServerPlayer player, Skill skill, double amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerData data = data(player);
        int before = data.skillLevel(skill);
        amount *= com.cavmkii.seancraft.food.Rest.skillMultiplier(player) * com.cavmkii.seancraft.boss.Trophies.xpMultiplier(player)
                * stats(player).skillXpMultiplier();
        data.skillXp.merge(skill, amount, Double::sum);
        if (skill.attr != null) {
            // Where this level's attribute points will go (6.4).
            data.levelProgress.merge(skill.attr, amount, Double::sum);
        }
        int after = data.skillLevel(skill);
        if (after != before) {
            toast(player, skill.label + " " + after, skill.describe(after));
            markDirty(player);
        }
    }

    public static void addClassXp(ServerPlayer player, long amount) {
        if (amount <= 0 || player.isSpectator()) {
            return;
        }
        PlayerData data = data(player);
        if (data.level >= PlayerData.MAX_LEVEL) {
            return;
        }
        amount = Math.round(amount * com.cavmkii.seancraft.boss.Trophies.xpMultiplier(player));
        data.xp += amount;
        boolean levelled = false;
        while (data.level < PlayerData.MAX_LEVEL && data.xp >= PlayerData.xpToNext(data.level)) {
            data.xp -= PlayerData.xpToNext(data.level);
            data.level++;
            levelled = true;
            // The level's attribute points, split by what you trained during it (6.4).
            data.split(data.levelProgress, PlayerData.ATTR_PER_LEVEL, data.origin);
            data.levelProgress.clear();
            String extra = data.level == Specialization.LEVEL ? " You can choose a specialization once you have a tier 1 trophy." : "";
            toast(player, "Level " + data.level, "+1 web point, +" + PlayerData.ATTR_PER_LEVEL + " attribute points." + extra);
        }
        if (data.level >= PlayerData.MAX_LEVEL) {
            data.xp = 0;
        }
        if (levelled) {
            markDirty(player);
        } else {
            runtime(player).dirty = true;
        }
    }

    /** Valheim's penalty: lose 5% of your progress in every use-skill. */
    private static void deathPenalty(ServerPlayer player) {
        PlayerData data = data(player);
        data.skillXp.replaceAll((skill, xp) -> xp * 0.95);
        data.rage = 0;
        markDirty(player);
    }

    private static void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            addSkillXp(player, Skill.MINING, 0.3 + Math.min(3, state.getDestroySpeed(level, pos)) * 0.2);
        }
        if (state.is(ConventionalBlockTags.ORES)) {
            addClassXp(player, state.is(ConventionalBlockTags.DIAMOND_ORES) || state.is(ConventionalBlockTags.EMERALD_ORES)
                    || state.is(ConventionalBlockTags.NETHERITE_SCRAP_ORES) ? 40 : 6);
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            int farming = data(player).skillLevel(Skill.FARMING);
            addSkillXp(player, Skill.FARMING, 2);
            if (player.getRandom().nextInt(100) < Skill.FARMING.effectValue(farming)) {
                Block.dropResources(state, level, pos, null, player, tool);
            }
        }
    }

    // ================================================================== tick

    private static void tickPlayer(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        Runtime rt = runtime(player);
        PlayerData data = data(player);
        if (rt.stats == null || rt.dirty) {
            recompute(player);
            if (rt.dirty) {
                ServerPlayNetworking.send(player, new RpgNet.Sync(data));
                rt.dirty = false;
            }
        }
        Stats stats = rt.stats;
        long now = player.serverLevel().getGameTime();
        if (rt.refill) {
            rt.refill = false;
            data.mana = stats.maxMana();
            data.stamina = stats.maxStamina();
            data.rage = 0;
        }

        // Resources, per tick.
        data.mana = Math.min(stats.maxMana(), data.mana + stats.manaRegen() / 20f);
        com.cavmkii.seancraft.combat.Combat.tick(player, data, stats.maxStamina() + com.cavmkii.seancraft.food.Food.stamina(player));
        if (now > rt.combatUntil) {
            // Rage decays 10 a second once you've been out of combat for 5 seconds (6.5).
            data.rage = Math.max(0, data.rage - 10f / 20f);
        }
        data.rage = Math.min(stats.maxRage(), data.rage);

        if (rt.leapTicks > 0) {
            SkillEffects.tickLeap(player, rt);
        }

        if (now % 20 == 0) {
            double regen = stats.healthRegen();
            if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal((float) regen);
            }
            if (stats.has(Stat.HOLY_AURA)) {
                SkillEffects.holyAura(player, stats);
            }
        }
        if (stats.has(Stat.GHOST) && player.isCrouching() && now % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, true));
        }
        if (stats.has(Stat.NO_FALL)) {
            player.fallDistance = 0;
        }

        if (now % 10 == 0) {
            trackMovement(player, rt, data);
        }
        if (now % 4 == 0) {
            sendResources(player, rt, data, now);
        }
    }

    private static void trackMovement(ServerPlayer player, Runtime rt, PlayerData data) {
        Vec3 pos = player.position();
        Vec3 last = rt.lastPos;
        rt.lastPos = pos;
        // Running bonus applies only while sprinting.
        double running = data.skillLevel(Skill.RUNNING) * 0.0015;
        setModifier(player, Attributes.MOVEMENT_SPEED, "rpg_running", player.isSprinting() ? running : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (last == null || player.isPassenger() || player.getAbilities().flying) {
            return;
        }
        double moved = Math.sqrt(Math.pow(pos.x - last.x, 2) + Math.pow(pos.z - last.z, 2));
        if (moved < 0.1 || moved > 12) {
            return;
        }
        if (player.isSwimming() || player.isInWater() && !player.onGround()) {
            addSkillXp(player, Skill.SWIMMING, moved * 0.5);
        } else if (player.isCrouching()) {
            boolean danger = !player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
                    e -> e instanceof Enemy).isEmpty();
            addSkillXp(player, Skill.SNEAKING, moved * (danger ? 0.8 : 0.4));
        } else if (player.isSprinting()) {
            addSkillXp(player, Skill.RUNNING, moved * 0.2);
        }
    }

    private static void sendResources(ServerPlayer player, Runtime rt, PlayerData data, long now) {
        int[] cooldowns = new int[PlayerData.SLOTS];
        int[] totals = new int[PlayerData.SLOTS];
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            ActiveSkill skill = data.slot(s);
            if (skill != null) {
                cooldowns[s] = (int) Math.max(0, rt.cooldownEnd.getOrDefault(skill, 0L) - now);
                // stats(), not rt.stats: a level-up earlier this tick (markDirty) may have cleared the cache.
                totals[s] = (int) Math.round(skill.cooldownTicks * stats(player).cooldownMultiplier(data.skillLevel(Skill.CASTING)));
            }
        }
        ServerPlayNetworking.send(player, new RpgNet.Resources(data.mana, data.rage, data.stamina, cooldowns, totals));
    }

    public static void markDirty(ServerPlayer player) {
        Runtime rt = runtime(player);
        rt.dirty = true;
        rt.stats = null;
    }

    /** Re-derive stats and push them onto vanilla attributes. */
    private static void recompute(ServerPlayer player) {
        PlayerData data = data(player);
        Stats stats = new Stats(data);
        runtime(player).stats = stats;

        setModifier(player, Attributes.MAX_HEALTH, "rpg_health", stats.bonusHealth(), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MAX_HEALTH, "rpg_health_pct", stats.get(Stat.MAX_HEALTH) / 100,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ATTACK_SPEED, "rpg_attack_speed", stats.attackSpeedPct() / 100,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.MOVEMENT_SPEED, "rpg_move_speed", stats.moveSpeedPct() / 100,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.ARMOR, "rpg_armor", stats.bonusArmor(), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_DAMAGE, "rpg_primary", 0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_KNOCKBACK, "rpg_knockback_str", stats.knockback(), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.KNOCKBACK_RESISTANCE, "rpg_knockback", stats.get(Stat.KNOCKBACK_RES) / 100,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.BLOCK_BREAK_SPEED, "rpg_mining", Skill.MINING.effectValue(data.skillLevel(Skill.MINING)) / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.SNEAKING_SPEED, "rpg_sneaking", data.skillLevel(Skill.SNEAKING) / 100.0 * 0.3 + stats.get(Stat.SNEAK_SPEED) / 100,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, "rpg_swimming", data.skillLevel(Skill.SWIMMING) / 100.0 * 0.5,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.OXYGEN_BONUS, "rpg_breath", data.skillLevel(Skill.SWIMMING) / 50.0,
                AttributeModifier.Operation.ADD_VALUE);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        data.mana = Math.min(data.mana, stats.maxMana());
        data.stamina = Math.min(data.stamina, stats.maxStamina() + com.cavmkii.seancraft.food.Food.stamina(player));
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        ResourceLocation id = SeanCraft.id(name);
        if (Math.abs(amount) < 1.0e-6) {
            instance.removeModifier(id);
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    static void toast(ServerPlayer player, String title, String body) {
        ServerPlayNetworking.send(player, new RpgNet.Toast(title, body));
    }

    // ================================================================== web and slots

    private static boolean inHideout(ServerPlayer player) {
        return com.cavmkii.seancraft.tarkov.Hideouts.at(player.serverLevel(), player.position()) != null;
    }

    /** Any tier 1 boss beaten, for the specialization milestone (6.2.5). */
    static boolean hasTierOneTrophy(ServerPlayer player) {
        for (String boss : player.getAttachedOrCreate(com.cavmkii.seancraft.boss.BossContent.BEATEN)) {
            com.cavmkii.seancraft.boss.BossDef def = com.cavmkii.seancraft.boss.BossDef.get(boss);
            if (def != null && def.tier() >= 1) {
                return true;
            }
        }
        return false;
    }

    /** Takes a node, ranks up a learned skill, or chooses a specialization at its gate. */
    private static void allocate(ServerPlayer player, int node) {
        PlayerData data = data(player);
        WebNode web = PassiveWeb.get(node);
        if (web == null) {
            return;
        }
        if (data.canRankUp(node)) {
            data.ranks.merge(web.active.name(), 1, Integer::sum);
            toast(player, web.active.label + " rank " + data.rank(web.active), "");
            markDirty(player);
            return;
        }
        if (!data.canAllocate(node)) {
            return;
        }
        if (web.kind == WebNode.Kind.GATE) {
            if (!hasTierOneTrophy(player)) {
                player.displayClientMessage(Component.literal("A specialization needs a tier 1 trophy too: beat Mark, Daylan or Bo Daddy."), true);
                return;
            }
            if (!inHideout(player)) {
                player.displayClientMessage(Component.literal("Choose a specialization in a hideout."), true);
                return;
            }
            data.spec = web.spec;
            data.revealed = true;
            toast(player, "Specialization: " + web.spec.label, "The whole web is open to you now.");
        }
        data.nodes.add(node);
        // A newly learned skill goes into the first empty basic slot.
        if (web.active != null) {
            for (int s = 0; s < PlayerData.ULTIMATE_SLOT; s++) {
                if (data.slots[s] == null) {
                    data.slots[s] = web.active.name();
                    break;
                }
            }
        }
        markDirty(player);
    }

    /** Picks the character's origin, once (6.2.1), and hands over its starting weapon. */
    private static void chooseOrigin(ServerPlayer player, int index) {
        PlayerData data = data(player);
        if (data.origin != null || index < 0 || index >= Origin.values().length) {
            return;
        }
        data.origin = Origin.values()[index];
        ItemStack weapon = switch (data.origin) {
            case VANGUARD -> new ItemStack(com.cavmkii.seancraft.parts.WeaponItems.WOODEN_CLUB);
            case SCOUT -> new ItemStack(net.minecraft.world.item.Items.BOW);
            case ARCANIST -> ItemStack.EMPTY;
        };
        if (!weapon.isEmpty()) {
            com.cavmkii.seancraft.tetris.Tetris.give(player, weapon);
        }
        if (data.origin == Origin.SCOUT) {
            com.cavmkii.seancraft.tetris.Tetris.give(player, new ItemStack(net.minecraft.world.item.Items.ARROW, 16));
        }
        toast(player, "Origin: " + data.origin.label, data.origin == Origin.ARCANIST
                ? "Your apprentice wand comes with wands; for now, your skills are your weapon." : "Here's your " + data.origin.weapon.toLowerCase() + ".");
        markDirty(player);
    }

    /** Places an attribute point freed by a respec (6.6). */
    private static void assignAttribute(ServerPlayer player, int index) {
        PlayerData data = data(player);
        if (data.unassigned <= 0 || index < 0 || index >= Attr.values().length) {
            return;
        }
        data.unassigned--;
        data.attrPoints.merge(Attr.values()[index], 1.0, Double::sum);
        markDirty(player);
    }

    /** The loadout changes only in a hideout, so the kit is chosen before a raid (6.5.2). */
    private static void setSlot(ServerPlayer player, int slot, String skillName) {
        PlayerData data = data(player);
        if (!data.slotOpen(slot)) {
            return;
        }
        if (!inHideout(player)) {
            player.displayClientMessage(Component.literal("Change your loadout in a hideout."), true);
            return;
        }
        if (skillName.isEmpty()) {
            data.slots[slot] = null;
        } else {
            ActiveSkill skill = ActiveSkill.byName(skillName);
            if (skill == null || !data.hasActive(skill) || slot == PlayerData.ULTIMATE_SLOT) {
                return;
            }
            for (int s = 0; s < PlayerData.SLOTS; s++) {
                if (skillName.equals(data.slots[s])) {
                    data.slots[s] = null;
                }
            }
            data.slots[slot] = skillName;
        }
        markDirty(player);
    }

    // ================================================================== casting

    private static void cast(ServerPlayer player, int slot) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        PlayerData data = data(player);
        if (!data.slotOpen(slot)) {
            return;
        }
        ActiveSkill skill = data.slot(slot);
        if (skill == null || !data.hasActive(skill)) {
            return;
        }
        Runtime rt = runtime(player);
        long now = player.serverLevel().getGameTime();
        if (now < rt.cooldownEnd.getOrDefault(skill, 0L)) {
            return;
        }
        float available = switch (skill.resource) {
            case MANA -> data.mana;
            case RAGE -> data.rage;
            case STAMINA -> data.stamina;
        };
        boolean free = player.isCreative();
        // Ranks make a skill cheaper (6.5.2); AGI makes AGI skills' stamina cheaper (6.5.1).
        float cost = skill.cost * (1 - 0.1f * (data.rank(skill) - 1));
        if (skill.resource == Resource.STAMINA) {
            cost *= (float) (1 - stats(player).staminaEfficiency() * Scaling.agiShare(skill.archetype));
        }
        if (!free && available < cost) {
            player.displayClientMessage(Component.literal("Not enough " + skill.resource.label.toLowerCase()), true);
            return;
        }
        SkillEffects.rank = data.rank(skill);
        boolean cast;
        try {
            cast = SkillEffects.cast(player, skill, stats(player), rt);
        } finally {
            SkillEffects.rank = 1;
        }
        if (!cast) {
            return;
        }
        if (!free) {
            switch (skill.resource) {
                case MANA -> data.mana -= cost;
                case RAGE -> data.rage -= cost;
                case STAMINA -> data.stamina -= cost;
            }
        }
        int casting = data.skillLevel(Skill.CASTING);
        rt.cooldownEnd.put(skill, now + Math.round(skill.cooldownTicks * stats(player).cooldownMultiplier(casting)));
        addSkillXp(player, Skill.CASTING, skill.cost * 0.5);
    }

    /** Rage from fighting. */
    static void gainRage(ServerPlayer player, float amount) {
        PlayerData data = data(player);
        Stats stats = stats(player);
        data.rage = Math.min(stats.maxRage(), data.rage + amount * (float) (1 + stats.get(Stat.RAGE_GAIN) / 100));
        runtime(player).combatUntil = player.serverLevel().getGameTime() + 100;
    }

    // ================================================================== respec

    public static int refundPercent(int level) {
        return level <= 20 ? 100 : level <= 40 ? 75 : 50;
    }

    public static boolean needsOrb(int level) {
        return level > 40;
    }

    public static void openRespec(ServerPlayer player, BlockPos pos) {
        PlayerData data = data(player);
        boolean hasOrb = findOrb(player) != null;
        ServerPlayNetworking.send(player, new RpgNet.OpenRespec(pos, refundPercent(data.level), needsOrb(data.level), hasOrb,
                data.spentPoints()));
    }

    /**
     * Clears the web and the specialization, frees your attribute points to place again, and kills you (6.6). Only in a
     * hideout, so a respec never costs a raid kit. You keep a share of the web points you'd spent; the rest are gone for
     * good. Your origin stays, and the whole web stays open if it was.
     */
    private static void respec(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36
                || !player.level().getBlockState(pos).is(RpgContent.ALTAR_OF_REGRET)) {
            return;
        }
        if (!inHideout(player)) {
            player.displayClientMessage(Component.literal("The altar only works inside a hideout."), true);
            return;
        }
        PlayerData data = data(player);
        if (data.spentPoints() == 0 && data.attrPoints.isEmpty()) {
            player.displayClientMessage(Component.literal("You have nothing to take back."), true);
            return;
        }
        if (needsOrb(data.level) && !player.isCreative()) {
            ItemStack orb = findOrb(player);
            if (orb == null) {
                player.displayClientMessage(Component.literal("At your level the altar demands an Orb of Regret."), true);
                return;
            }
            orb.shrink(1);
        }
        int spent = data.spentPoints();
        int refunded = (int) Math.round(spent * refundPercent(data.level) / 100.0);
        data.lostPoints += spent - refunded;
        data.nodes.clear();
        data.ranks.clear();
        data.spec = null;
        java.util.Arrays.fill(data.slots, null);
        double attrs = data.attrPoints.values().stream().mapToDouble(Double::doubleValue).sum();
        data.unassigned += (int) Math.round(attrs);
        data.attrPoints.clear();
        markDirty(player);
        SeanCraft.LOG.info("{} respecced: {} of {} points refunded.", player.getName().getString(), refunded, spent);
        player.displayClientMessage(Component.literal("The altar takes its price."), true);
        player.kill();
    }

    private static ItemStack findOrb(ServerPlayer player) {
        for (ItemStack stack : com.cavmkii.seancraft.tetris.Tetris.carried(player)) {
            if (stack.is(RpgContent.ORB_OF_REGRET)) {
                return stack;
            }
        }
        return null;
    }

    // ================================================================== commands

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("character").requires(src -> src.hasPermission(2))
                .then(Commands.literal("xp").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            addClassXp(target, IntegerArgumentType.getInteger(ctx, "amount"));
                            ctx.getSource().sendSuccess(() -> Component.literal("Level " + data(target).level), false);
                            return 1;
                        }))))
                .then(Commands.literal("skill").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, Skill.MAX_LEVEL)).executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    Skill skill;
                                    try {
                                        skill = Skill.valueOf(StringArgumentType.getString(ctx, "skill").toUpperCase(java.util.Locale.ROOT));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown skill"));
                                        return 0;
                                    }
                                    data(target).skillXp.put(skill, Skill.xpForLevel(IntegerArgumentType.getInteger(ctx, "level")));
                                    markDirty(target);
                                    return 1;
                                })))))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    target.setAttached(DATA, new PlayerData());
                    markDirty(target);
                    return 1;
                }))));
    }

    static MinecraftServer server(ServerPlayer player) {
        return player.server;
    }

    static AABB around(Vec3 pos, double r) {
        return new AABB(pos.x - r, pos.y - r, pos.z - r, pos.x + r, pos.y + r, pos.z + r);
    }
}
