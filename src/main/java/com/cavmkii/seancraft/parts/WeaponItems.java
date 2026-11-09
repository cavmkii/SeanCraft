package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.SeanCraft;
import java.util.EnumMap;
import java.util.Map;
import net.bettercombat.api.component.BetterCombatDataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The part-built weapon families vanilla doesn't have (design doc 9.4): daggers, maces, warhammers, greatswords,
 * halberds, spears and pikes, one item per material like vanilla's swords, plus the wooden club. Base damage and speed
 * at iron come from 9.5.1, on the 5x scale; the material factor scales damage (copper 0.85 up to netherite 1.35).
 */
public final class WeaponItems {
    public static final String[] METALS = {"copper", "iron", "gold", "diamond", "netherite"};
    static final float[] FACTOR = {0.85f, 1.0f, 1.1f, 1.2f, 1.35f};
    static final int[] DURABILITY = {190, 250, 140, 1561, 2031};
    static final Tier[] TIERS = {Tiers.STONE, Tiers.IRON, Tiers.GOLD, Tiers.DIAMOND, Tiers.NETHERITE};

    /** A family's numbers at iron: base damage on the 5x scale, attacks per second, durability multiplier, moveset. */
    record Spec(float damage, float speed, float durability, ResourceLocation moveset) {}

    static final Map<Family, Spec> SPECS = new EnumMap<>(Family.class);

    static {
        SPECS.put(Family.DAGGER, new Spec(20, 2.0f, 0.8f, ResourceLocation.parse("bettercombat:dagger")));
        SPECS.put(Family.MACE, new Spec(35, 1.1f, 1.0f, ResourceLocation.parse("bettercombat:mace")));
        SPECS.put(Family.WARHAMMER, new Spec(45, 0.8f, 1.2f, ResourceLocation.parse("bettercombat:hammer")));
        SPECS.put(Family.GREATSWORD, new Spec(50, 0.9f, 1.2f, ResourceLocation.parse("bettercombat:claymore")));
        SPECS.put(Family.HALBERD, new Spec(45, 1.0f, 1.2f, ResourceLocation.parse("bettercombat:halberd")));
        SPECS.put(Family.SPEAR, new Spec(32, 1.2f, 1.0f, ResourceLocation.parse("bettercombat:spear")));
        SPECS.put(Family.PIKE, new Spec(40, 0.9f, 1.2f, SeanCraft.id("pike")));
        SPECS.put(Family.THROWING_KNIFE, new Spec(15, 1.6f, 0.6f, ResourceLocation.parse("bettercombat:dagger")));
        SPECS.put(Family.JAVELIN, new Spec(25, 1.0f, 0.8f, ResourceLocation.parse("bettercombat:spear")));
        SPECS.put(Family.WAND, new Spec(10, 1.5f, 0.6f, ResourceLocation.parse("bettercombat:wand")));
        SPECS.put(Family.STAFF, new Spec(18, 0.8f, 0.9f, ResourceLocation.parse("bettercombat:staff")));
    }

    /** Items by family, copper to netherite. */
    public static final Map<Family, Item[]> ITEMS = new EnumMap<>(Family.class);

    static {
        for (Map.Entry<Family, Spec> e : SPECS.entrySet()) {
            Item[] items = new Item[METALS.length];
            for (int t = 0; t < METALS.length; t++) {
                String id = (e.getKey().magic() ? PartDef.GEMS[t] : METALS[t]) + "_" + e.getKey().name().toLowerCase(java.util.Locale.ROOT);
                items[t] = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(id), new PartWeapon(e.getKey(), TIERS[t],
                        properties(e.getValue(), FACTOR[t], Math.round(DURABILITY[t] * e.getValue().durability()), t == 4)));
            }
            ITEMS.put(e.getKey(), items);
            Family.tiers(e.getKey(), items);
        }
    }

    /**
     * The wooden club (4.3): the first weapon, from 2 logs and a stick at the crafting table. Blunt, about a wooden
     * sword's durability, no parts.
     */
    public static final Item WOODEN_CLUB = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("wooden_club"),
            new SwordItem(Tiers.WOOD, new Item.Properties().attributes(attributes(20, 1.2f))
                    .component(BetterCombatDataComponents.WEAPON_PRESET_ID, ResourceLocation.parse("bettercombat:mace"))));

    /** The Blademaster's buckler and the Knight's tower shield (9.4.6): shields in vanilla's eyes, so blocking works. */
    public static final Item BUCKLER = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("buckler"),
            new PartShield(Family.BUCKLER, new Item.Properties().durability(200)));
    public static final Item TOWER_SHIELD = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("tower_shield"),
            new PartShield(Family.TOWER_SHIELD, new Item.Properties().durability(500)));

    /** The boomerang: wooden arms and a wrap, one item like the bow. */
    public static final Item BOOMERANG = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("boomerang"),
            new PartWeapon(Family.BOOMERANG, Tiers.WOOD, new Item.Properties().durability(180).attributes(attributes(12, 1.4f))
                    .component(BetterCombatDataComponents.WEAPON_PRESET_ID, ResourceLocation.parse("bettercombat:dagger"))));

    /** The longbow: a bow in vanilla's eyes (arrows, enchantments), slower to draw and faster arrows (Bows mixin). */
    public static final Item LONGBOW = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("longbow"),
            new net.minecraft.world.item.BowItem(new Item.Properties().durability(480)));

    static {
        Family.stock(LONGBOW, new Family.Stock(Family.LONGBOW, 1, 1, 1));
        Family.stock(BOOMERANG, new Family.Stock(Family.BOOMERANG, 1, 1, 1));
        Family.stock(BUCKLER, new Family.Stock(Family.BUCKLER, 2, 2, 1));
        Family.stock(TOWER_SHIELD, new Family.Stock(Family.TOWER_SHIELD, 2, 2, 2));
    }

    private WeaponItems() {}

    static void init() {
        // Loading the class registers the items.
    }

    private static Item.Properties properties(Spec spec, float factor, int durability, boolean fireproof) {
        Item.Properties p = new Item.Properties().durability(durability).attributes(attributes(spec.damage() * factor, spec.speed()))
                .component(BetterCombatDataComponents.WEAPON_PRESET_ID, spec.moveset());
        return fireproof ? p.fireResistant() : p;
    }

    /** Vanilla terms: attack damage is a fifth of the 5x-scale number, less the player's base 1; speed is per second, less 4. */
    static ItemAttributeModifiers attributes(float damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage / 5 - 1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed - 4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static class PartShield extends net.minecraft.world.item.ShieldItem {
        public final Family family;

        public PartShield(Family family, Properties properties) {
            super(properties);
            this.family = family;
        }
    }

    /** A part-built weapon of one of the new families. A sword in vanilla's eyes, so grids, storage and enchanting treat it as one. */
    public static class PartWeapon extends SwordItem {
        public final Family family;

        public PartWeapon(Family family, Tier tier, Properties properties) {
            super(tier, properties);
            this.family = family;
        }

        // Throwables (9.4): hold use to wind up, release to throw.
        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            if (!family.throwable()) {
                return super.use(level, player, hand);
            }
            player.startUsingItem(hand);
            return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
            return family.throwable() ? net.minecraft.world.item.UseAnim.SPEAR : super.getUseAnimation(stack);
        }

        @Override
        public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
            return family.throwable() ? 72000 : super.getUseDuration(stack, entity);
        }

        @Override
        public void releaseUsing(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity user, int left) {
            if (family.throwable() && 72000 - left >= Throwing.WIND_UP && user instanceof net.minecraft.world.entity.player.Player player) {
                Throwing.release(player, stack);
            }
        }
    }
}
