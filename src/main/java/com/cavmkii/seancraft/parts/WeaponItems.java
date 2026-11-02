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

    static final Map<Family, Spec> SPECS = new EnumMap<>(Map.of(
            Family.DAGGER, new Spec(20, 2.0f, 0.8f, ResourceLocation.parse("bettercombat:dagger")),
            Family.MACE, new Spec(35, 1.1f, 1.0f, ResourceLocation.parse("bettercombat:mace")),
            Family.WARHAMMER, new Spec(45, 0.8f, 1.2f, ResourceLocation.parse("bettercombat:hammer")),
            Family.GREATSWORD, new Spec(50, 0.9f, 1.2f, ResourceLocation.parse("bettercombat:claymore")),
            Family.HALBERD, new Spec(45, 1.0f, 1.2f, ResourceLocation.parse("bettercombat:halberd")),
            Family.SPEAR, new Spec(32, 1.2f, 1.0f, ResourceLocation.parse("bettercombat:spear")),
            Family.PIKE, new Spec(40, 0.9f, 1.2f, SeanCraft.id("pike"))));

    /** Items by family, copper to netherite. */
    public static final Map<Family, Item[]> ITEMS = new EnumMap<>(Family.class);

    static {
        for (Map.Entry<Family, Spec> e : SPECS.entrySet()) {
            Item[] items = new Item[METALS.length];
            for (int t = 0; t < METALS.length; t++) {
                String id = METALS[t] + "_" + e.getKey().name().toLowerCase(java.util.Locale.ROOT);
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

    /** A part-built weapon of one of the new families. A sword in vanilla's eyes, so grids, storage and enchanting treat it as one. */
    public static class PartWeapon extends SwordItem {
        public final Family family;

        public PartWeapon(Family family, Tier tier, Properties properties) {
            super(tier, properties);
            this.family = family;
        }
    }
}
