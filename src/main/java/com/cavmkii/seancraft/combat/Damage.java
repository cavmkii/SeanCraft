package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.parts.PartKind;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import com.cavmkii.seancraft.tarkov.Body;
import com.cavmkii.seancraft.tarkov.BodyPart;
import com.cavmkii.seancraft.tarkov.Health;
import com.cavmkii.seancraft.tarkov.Tarkov;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.ResourceKey;

/**
 * The damage pipeline after the character rules (design doc 9.1, 9.3, 9.5): everything on SeanCraft's scale of 5x
 * vanilla, each hit split by damage type, the target's resistance per type, then armor. Players are armored per
 * body part; mobs use their armor value through the same formula.
 */
public final class Damage {
    /** Damage, healing and mob health are all 5x vanilla; player health is 300 (5.1). */
    public static final float SCALE = 5;
    public static final double PLAYER_BASE_HEALTH = 300;
    static final float ARMOR_K = 20;
    static final float PIERCE_IGNORES = 0.25f;
    /** Enchantments as pipeline terms (9.7.1): increased damage per level, spirit share per level, armor ignored per level. */
    static final float SHARPNESS_INCREASED = 0.10f;
    static final float BANE_INCREASED = 0.15f;
    static final float SMITE_SHARE = 0.25f;
    static final float BREACH_PENETRATION = 0.15f;
    /** Resistance points per level of Fire, Blast or Projectile Protection on a piece (9.7.1). */
    static final float PROTECTION_POINTS = 4;
    /** Armor points per level of Protection, on the parts that piece covers (9.7.1). */
    static final float PROTECTION_ARMOR = 2;
    /** Damage to a destroyed limb or stomach goes to the thorax at this share (5.2). */
    static final float SPILL_TO_THORAX = 0.7f;
    public static final String FIRE_ARROW_TAG = "seancraft_fire_arrow";

    private static final ResourceLocation HEALTH_SCALE = SeanCraft.id("health_scale");
    private static final ResourceLocation BASE_HEALTH = SeanCraft.id("base_health");
    private static final String SCALED_TAG = "seancraft_scaled";

    public enum Type {
        SLASH, PIERCE, BLUNT, CHOP, PICKAXE, FIRE, FROST, LIGHTNING, POISON, SPIRIT;

        public boolean physical() {
            return this == SLASH || this == PIERCE || this == BLUNT;
        }

        public boolean elemental() {
            return this == FIRE || this == FROST || this == LIGHTNING || this == POISON;
        }
    }

    /** How a hit divides by type. Physical shares make up the hit; element shares add on top of it (9.1). */
    public static final class Split {
        final float[] share = new float[Type.values().length];

        Split set(Type type, float value) {
            share[type.ordinal()] = value;
            return this;
        }

        Split add(Type type, float value) {
            share[type.ordinal()] += value;
            return this;
        }

        public float get(Type type) {
            return share[type.ordinal()];
        }

        /** Share that tends to cause bleeds rather than fractures. */
        public float cutting() {
            float cut = get(Type.SLASH) + get(Type.PIERCE);
            float all = cut + get(Type.BLUNT);
            return all <= 0 ? 0 : cut / all;
        }
    }

    private Damage() {}

    public static void init() {
        Traits.init();
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LivingEntity living && !(entity instanceof Player) && !(entity instanceof com.cavmkii.seancraft.boss.BossEntity)) {
                scaleHealth(living);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    playerHealth(player);
                }
            }
        });
    }

    /** Mobs get 5x vanilla health, once, keeping how hurt they already were. */
    static void scaleHealth(LivingEntity mob) {
        if (mob.getTags().contains(SCALED_TAG)) {
            return;
        }
        mob.addTag(SCALED_TAG);
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) {
            return;
        }
        float fraction = mob.getHealth() / Math.max(1, mob.getMaxHealth());
        max.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_SCALE, SCALE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        mob.setHealth(mob.getMaxHealth() * fraction);
    }

    /** Players start from 300 max health. The first time, keep how hurt they were rather than leaving them at 20. */
    static void playerHealth(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null || max.getModifier(BASE_HEALTH) != null) {
            return;
        }
        float fraction = player.getHealth() / Math.max(1, player.getMaxHealth());
        max.addPermanentModifier(new AttributeModifier(BASE_HEALTH, PLAYER_BASE_HEALTH - max.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(player.getMaxHealth() * fraction);
    }

    /** Healing is on the same scale as damage. */
    public static float heal(float amount) {
        return amount * SCALE;
    }

    // ================================================================== the pipeline

    /**
     * Called at the start of LivingEntity.hurt, after the character rules. Returns what the hit deals before vanilla's
     * own steps (blocking, the Resistance effect), which no longer include armor.
     */
    public static float apply(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0 || source.is(Health.BLEED) || source.is(com.cavmkii.seancraft.hazard.Hazards.DAMAGE) || victim.level().isClientSide) {
            // Bleeding is already sized to the player's health.
            return amount;
        }
        // Boss attacks are written on SeanCraft's scale already (8.4.4).
        com.cavmkii.seancraft.boss.BossEntity boss = source.getEntity() instanceof com.cavmkii.seancraft.boss.BossEntity b ? b : null;
        if (boss == null) {
            amount *= SCALE;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        amount = Traits.beforeHit(victim, source, amount);
        amount *= enchantIncreased(victim, source);
        Split split = FORCED.get() != null ? fromMap(FORCED.get()) : boss != null && boss.dealing != null ? fromMap(boss.dealing) : split(source);
        BodyPart part = null;
        if (victim instanceof ServerPlayer player) {
            part = Health.locate(player, source, source.is(DamageTypeTags.IS_FALL));
            if (part != null && part != BodyPart.HEAD && part != BodyPart.THORAX && Body.destroyed(Tarkov.data(player), part)) {
                part = BodyPart.THORAX;
                amount *= SPILL_TO_THORAX;
            }
            Health.pending(player, source, part, split == null ? -1 : split.cutting());
        }
        if (split == null) {
            return amount;
        }
        boolean armored = !source.is(DamageTypeTags.BYPASSES_ARMOR);
        float armor = !armored ? 0 : victim instanceof ServerPlayer player ? partArmor(player, part) : victim.getArmorValue() * 2f;
        float total = 0;
        for (Type type : Type.values()) {
            float share = split.get(type);
            if (share <= 0) {
                continue;
            }
            float dealt = amount * share * resistance(victim, type);
            if (type.physical() && armor > 0) {
                float x = Traits.effectiveArmor(armor, type == Type.PIERCE, Traits.family(source), breach(source));
                dealt *= ARMOR_K / (x + ARMOR_K);
            }
            if (type.elemental() && victim instanceof ServerPlayer player) {
                dealt *= ARMOR_K / (elementPoints(player, type) + ARMOR_K);
            }
            total += dealt;
        }
        if (victim instanceof ServerPlayer player) {
            float points = sourcePoints(player, source);
            if (points > 0) {
                total *= ARMOR_K / (points + ARMOR_K);
            }
        }
        com.cavmkii.seancraft.boss.Trophies.afterHit(victim, source, total);
        return total;
    }

    private static Split fromMap(java.util.Map<Type, Float> shares) {
        Split split = new Split();
        shares.forEach(split::set);
        return split;
    }

    /** The type split for a hit, or null for damage with no type (drowning, starving, the void). */
    public static Split split(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (source.is(DamageTypeTags.IS_FIRE) || direct instanceof SmallFireball) {
            return new Split().set(Type.FIRE, 1);
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return new Split().set(Type.FROST, 1);
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return new Split().set(Type.LIGHTNING, 1);
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.DRAGON_BREATH)) {
            return new Split().set(Type.POISON, 1);
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)) {
            return new Split().set(Type.BLUNT, 1);
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.THORNS)
                || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)) {
            return new Split().set(Type.PIERCE, 1);
        }
        if (direct instanceof AbstractArrow arrow) {
            Split split = new Split().set(Type.PIERCE, 1);
            if (arrow.getTags().contains(FIRE_ARROW_TAG)) {
                split.add(Type.FIRE, 0.3f);
            } else if (arrow.isOnFire()) {
                // Flame: a smaller fire share than a fire arrow's.
                split.add(Type.FIRE, 0.15f);
            }
            if (arrow instanceof ThrownTrident) {
                materialElements(split, arrow.getWeaponItem());
            }
            return split;
        }
        if (direct instanceof Projectile) {
            return new Split().set(Type.BLUNT, 1);
        }
        if (attacker instanceof LivingEntity living && direct == attacker) {
            return melee(living);
        }
        return direct != null ? new Split().set(Type.BLUNT, 1) : null;
    }

    /** A melee hit: the striking part (or the vanilla weapon) sets the split, the material adds elements. */
    static Split melee(LivingEntity attacker) {
        ItemStack weapon = attacker.getMainHandItem();
        Split split = new Split();
        Parts.Installed head = Parts.installed(weapon).get(PartSlot.HEAD);
        if (head != null) {
            PartKind kind = head.part().kind();
            switch (kind) {
                case BLADE -> split.set(Type.SLASH, 0.8f).set(Type.PIERCE, 0.2f);
                case LONG_BLADE -> split.set(Type.SLASH, 1);
                case KNIFE_BLADE -> split.set(Type.PIERCE, 0.6f).set(Type.SLASH, 0.4f);
                case AXE_HEAD -> split.set(Type.SLASH, 0.7f).set(Type.CHOP, 0.3f);
                case SPEARHEAD -> split.set(Type.PIERCE, 1);
                case HALBERD_HEAD -> split.set(Type.SLASH, 0.6f).set(Type.PIERCE, 0.4f);
                default -> split.set(Type.BLUNT, 1);
            }
        } else if (weapon.is(ItemTags.SWORDS)) {
            split.set(Type.SLASH, 0.8f).set(Type.PIERCE, 0.2f);
        } else if (weapon.is(ItemTags.AXES)) {
            split.set(Type.SLASH, 0.7f).set(Type.CHOP, 0.3f);
        } else if (weapon.is(Items.TRIDENT)) {
            split.set(Type.PIERCE, 1);
        } else if (weapon.is(ItemTags.PICKAXES)) {
            split.set(Type.PIERCE, 0.5f).set(Type.PICKAXE, 0.5f);
        } else if (weapon.is(ItemTags.HOES)) {
            split.set(Type.SLASH, 1);
        } else if (weapon.isEmpty() && !(attacker instanceof Player) && clawed(attacker.getType())) {
            split.set(Type.SLASH, 1);
        } else {
            split.set(Type.BLUNT, 1);
        }
        materialElements(split, weapon);
        int fireAspect = level(attacker, weapon, Enchantments.FIRE_ASPECT);
        if (fireAspect > 0) {
            split.add(Type.FIRE, 0.15f * fireAspect);
        }
        if (attacker instanceof net.minecraft.server.level.ServerPlayer sp) {
            // The Paladin's Smite signature: a spirit share on every melee hit.
            double share = com.cavmkii.seancraft.rpg.Rpg.stats(sp).get(com.cavmkii.seancraft.rpg.Stat.SPIRIT_SHARE);
            if (share > 0) {
                split.add(Type.SPIRIT, (float) share / 100);
            }
        }
        if (attacker instanceof Player) {
            // Smite: added spirit, which only hurts what's weak to it (9.7.1).
            int smite = level(attacker, weapon, Enchantments.SMITE);
            if (smite > 0) {
                split.add(Type.SPIRIT, SMITE_SHARE * smite);
            }
        }
        return split;
    }

    private static final ThreadLocal<java.util.Map<Type, Float>> FORCED = new ThreadLocal<>();

    /** Sets the split for hits dealt on this thread until cleared with null (ultimates that deal one damage type). */
    public static void forced(java.util.Map<Type, Float> split) {
        FORCED.set(split);
    }

    /** Sharpness, and Bane of Arthropods against arthropods, as increased damage on a player's melee hit (9.7.1). */
    private static float enchantIncreased(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
            return 1;
        }
        ItemStack weapon = player.getMainHandItem();
        float increased = SHARPNESS_INCREASED * level(player, weapon, Enchantments.SHARPNESS);
        if (victim.getType().is(net.minecraft.tags.EntityTypeTags.ARTHROPOD)) {
            increased += BANE_INCREASED * level(player, weapon, Enchantments.BANE_OF_ARTHROPODS);
        }
        return 1 + increased;
    }

    /** Breach: extra share of armor a player's melee hit ignores. */
    private static float breach(DamageSource source) {
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
            return 0;
        }
        return BREACH_PENETRATION * level(player, player.getMainHandItem(), Enchantments.BREACH);
    }

    /** Gold adds +15% as spirit and netherite +20% as fire, on top of the physical damage (9.1). */
    static void materialElements(Split split, ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        Parts.Installed head = Parts.installed(weapon).get(PartSlot.HEAD);
        String id = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
        int tier = head != null ? head.part().tier() : id.startsWith("golden_") ? 3 : id.startsWith("netherite_") ? 5 : 0;
        if (tier == 3) {
            split.add(Type.SPIRIT, 0.15f);
        } else if (tier == 5) {
            split.add(Type.FIRE, 0.20f);
        }
    }

    private static boolean clawed(EntityType<?> type) {
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER || type == EntityType.WOLF || type == EntityType.CAT
                || type == EntityType.OCELOT || type == EntityType.FOX || type == EntityType.POLAR_BEAR || type == EntityType.PHANTOM;
    }

    /**
     * How much of a type a target takes (9.1): 2 very weak, 1.5 weak, 1 normal, 0.5 resistant, 0 immune. Spirit only
     * hurts the undead and players; chop and pickaxe only work on things made of wood and stone.
     */
    static float resistance(LivingEntity victim, Type type) {
        float r = baseResistance(victim, type);
        // Doom (the Warlock's ultimate): weaknesses count double.
        return r > 1 && com.cavmkii.seancraft.rpg.Rpg.doomed(victim) ? r * 2 : r;
    }

    private static float baseResistance(LivingEntity victim, Type type) {
        if (victim instanceof com.cavmkii.seancraft.boss.BossEntity boss) {
            return boss.resistance(type);
        }
        if (victim instanceof Player) {
            return type == Type.CHOP || type == Type.PICKAXE ? 0 : 1;
        }
        EntityType<?> t = victim.getType();
        boolean undead = t.is(EntityTypeTags.UNDEAD);
        boolean bony = t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.WITHER_SKELETON || t == EntityType.BOGGED
                || t == EntityType.SKELETON_HORSE;
        return switch (type) {
            case SPIRIT -> undead ? 1.5f : 0;
            case CHOP -> 0;
            case PICKAXE -> t == EntityType.IRON_GOLEM ? 1 : 0;
            case BLUNT -> bony || t == EntityType.IRON_GOLEM ? 1.5f : 1;
            case PIERCE -> bony || t == EntityType.IRON_GOLEM ? 0.5f : 1;
            case SLASH -> t == EntityType.IRON_GOLEM ? 0.5f : 1;
            case FIRE -> victim.fireImmune() ? 0 : 1;
            case FROST -> t == EntityType.BLAZE || t == EntityType.MAGMA_CUBE ? 1.5f
                    : t == EntityType.STRAY || t == EntityType.SNOW_GOLEM || t == EntityType.POLAR_BEAR ? 0.5f : 1;
            case POISON -> t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER || undead ? 0 : 1;
            case LIGHTNING -> 1;
        };
    }

    // ================================================================== players' armor and resistance

    /** Armor (X) on one body part (9.3.1): helmet for the head, chestplate for the body and arms, legs from both leg pieces. */
    static float partArmor(ServerPlayer player, BodyPart part) {
        if (part == null) {
            return 0;
        }
        return switch (part) {
            case HEAD -> pieceArmor(player, EquipmentSlot.HEAD);
            case THORAX, STOMACH, LEFT_ARM, RIGHT_ARM -> pieceArmor(player, EquipmentSlot.CHEST);
            case LEFT_LEG, RIGHT_LEG -> (pieceArmor(player, EquipmentSlot.LEGS) + pieceArmor(player, EquipmentSlot.FEET)) / 2;
        };
    }

    /** One piece's armor points: its material's value, scaled by its parts, plus Protection. */
    static float pieceArmor(ServerPlayer player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem item)) {
            return 0;
        }
        float points = tierArmor(item.getMaterial());
        double base = item.getDefense();
        if (base > 0) {
            // Part-built armor rewrites its armor modifier; the ratio carries its material and part levels.
            double[] now = {0};
            stack.forEachModifier(slot, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    now[0] += modifier.amount();
                }
            });
            points *= (float) (now[0] / base);
        }
        return points + PROTECTION_ARMOR * level(player, stack, Enchantments.PROTECTION);
    }

    /** Armor points by tier (9.3.1). */
    static float tierArmor(Holder<ArmorMaterial> material) {
        if (material.is(ArmorMaterials.LEATHER)) {
            return 5;
        }
        if (material.is(ArmorMaterials.CHAIN) || material.is(ArmorMaterials.GOLD)) {
            return 8;
        }
        if (material.is(ArmorMaterials.IRON) || material.is(ArmorMaterials.TURTLE)) {
            return 12;
        }
        if (material.is(ArmorMaterials.DIAMOND)) {
            return 20;
        }
        if (material.is(ArmorMaterials.NETHERITE)) {
            return 25;
        }
        return 8;
    }

    /** Elemental resistance points (9.3.2). For now from Fire Protection; food, web nodes and trophy powers come later. */
    static float elementPoints(ServerPlayer player, Type type) {
        float web = (float) com.cavmkii.seancraft.rpg.Rpg.stats(player).resistance(type);
        return web + (type == Type.FIRE ? protection(player, Enchantments.FIRE_PROTECTION) : 0);
    }

    /** Blast and Projectile Protection: resistance points against explosions and projectiles. */
    static float sourcePoints(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return protection(player, Enchantments.BLAST_PROTECTION);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return protection(player, Enchantments.PROJECTILE_PROTECTION);
        }
        return 0;
    }

    private static float protection(ServerPlayer player, ResourceKey<Enchantment> enchantment) {
        int levels = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            levels += level(player, player.getItemBySlot(slot), enchantment);
        }
        return levels * PROTECTION_POINTS;
    }

    private static int level(LivingEntity holder, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack.isEmpty()) {
            return 0;
        }
        return holder.level().registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .flatMap(r -> r.get(enchantment))
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack))
                .orElse(0);
    }
}
