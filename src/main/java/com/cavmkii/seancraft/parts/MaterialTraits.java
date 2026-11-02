package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.SeanCraft;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * One trait per material (design doc 4.4.1), so materials differ in kind and not only in tier. Metal traits come from the
 * striking part (iron's also from armor plates), wood traits from the handle or haft, hide traits from wraps and padding.
 * A vanilla item carries the traits of its standard parts from the start: its default durability, swing speed and armor
 * are baked with them, and the Weapon Bench works from the untraited numbers underneath.
 */
public final class MaterialTraits {
    public enum Trait {
        CONDUCTIVE("Conductive", "+10% added lightning damage"),
        RELIABLE("Reliable", "+20% durability"),
        BLESSED("Blessed", "+15% added spirit damage"),
        KEEN("Keen", "+5% crit chance"),
        INFERNAL("Infernal", "+20% added fire damage, survives lava"),
        LIGHT("Light", "+5% swing speed, -15% durability"),
        BALANCED("Balanced", "no trait"),
        STURDY("Sturdy", "+15% durability"),
        SMOLDERING("Smoldering", "+5% added fire damage"),
        SWIFT("Swift", "+10% swing speed"),
        QUIET("Quiet", "harder to spot while sneaking"),
        LIGHTER("Lighter", "lighter to carry"),
        PLATED("Plated", "+2 armor");

        public final String label;
        public final String effect;

        Trait(String label, String effect) {
            this.label = label;
            this.effect = effect;
        }

        public float durability() {
            return switch (this) {
                case RELIABLE -> 0.20f;
                case STURDY -> 0.15f;
                case LIGHT -> -0.15f;
                default -> 0;
            };
        }

        public float speed() {
            return switch (this) {
                case LIGHT -> 0.05f;
                case SWIFT -> 0.10f;
                default -> 0;
            };
        }
    }

    private static final Trait[] HEADS = {Trait.CONDUCTIVE, Trait.RELIABLE, Trait.BLESSED, Trait.KEEN, Trait.INFERNAL};
    private static final Trait[] GRIPS = {Trait.LIGHT, Trait.BALANCED, Trait.STURDY, Trait.SMOLDERING, Trait.SWIFT};
    private static final Trait[] HIDES = {null, Trait.BALANCED, Trait.QUIET, Trait.LIGHTER, Trait.PLATED};

    public static final float QUIET_PER_PIECE = 0.08f;
    public static final float KEEN_CRIT = 5;
    static final ResourceLocation PLATED_ID = SeanCraft.id("plated");

    /** Untraited numbers for the items whose defaults were baked: max damage and the base swing speed modifier. */
    private static final Map<Item, Integer> RAW_MAX = new HashMap<>();
    private static final Map<Item, Double> RAW_SPEED = new HashMap<>();

    private MaterialTraits() {}

    /** A part's trait, or null if its material has none there. */
    public static Trait of(PartDef def) {
        int t = def.tier() - 1;
        return switch (def.kind()) {
            case BLADE, LONG_BLADE, KNIFE_BLADE, AXE_HEAD, MACE_HEAD, SPEARHEAD, HALBERD_HEAD -> HEADS[t];
            case PLATE -> def.tier() == 2 ? Trait.RELIABLE : null;
            case HANDLE, HAFT, LIMBS -> GRIPS[t];
            case WRAP -> HIDES[t] == Trait.PLATED ? null : HIDES[t];
            case PADDING -> HIDES[t];
            case GUARD, CRYSTAL, FOCUS -> null;
        };
    }

    public static boolean has(ItemStack stack, Trait trait) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Parts.Installed inst : Parts.installed(stack).values()) {
            if (of(inst.part()) == trait) {
                return true;
            }
        }
        return false;
    }

    static float durability(Iterable<PartDef> parts) {
        float m = 1;
        for (PartDef p : parts) {
            Trait t = of(p);
            if (t != null) {
                m *= 1 + t.durability();
            }
        }
        return m;
    }

    static float speed(Iterable<PartDef> parts) {
        float m = 1;
        for (PartDef p : parts) {
            Trait t = of(p);
            if (t != null) {
                m *= 1 + t.speed();
            }
        }
        return m;
    }

    static boolean plated(Iterable<PartDef> parts) {
        for (PartDef p : parts) {
            if (p.kind() == PartKind.PADDING && of(p) == Trait.PLATED) {
                return true;
            }
        }
        return false;
    }

    static Integer rawMax(Item item) {
        return RAW_MAX.get(item);
    }

    static Double rawSpeed(Item item) {
        return RAW_SPEED.get(item);
    }

    /** Crit chance a weapon's Keen head adds, in percent. */
    public static double critBonus(ItemStack weapon) {
        return has(weapon, Trait.KEEN) ? KEEN_CRIT : 0;
    }

    /** How much less visible a sneaking entity is for rabbit-hide wraps and padding on its armor and weapon. */
    public static double quiet(LivingEntity entity) {
        if (!entity.isCrouching()) {
            return 1;
        }
        int pieces = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (has(entity.getItemBySlot(slot), Trait.QUIET)) {
                pieces++;
            }
        }
        return Math.max(0.4, 1 - QUIET_PER_PIECE * pieces);
    }

    /** Bakes each modular item's standard traits into its default components. */
    static void init() {
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> Family.stock(item) != null, (builder, item) -> {
            Family.Stock stock = Family.stock(item);
            var standard = stock.parts().values();
            float dur = durability(standard);
            float spd = speed(standard);
            boolean plated = plated(standard);
            Integer max = item.components().get(DataComponents.MAX_DAMAGE);
            if (max != null) {
                RAW_MAX.put(item, max);
                if (dur != 1) {
                    builder.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(max * dur)));
                }
            }
            ItemAttributeModifiers mods = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            if (mods.modifiers().isEmpty()) {
                mods = item.getDefaultAttributeModifiers();
            }
            if (spd == 1 && !plated) {
                for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                    if (e.attribute().equals(Attributes.ATTACK_SPEED) && e.modifier().id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                        RAW_SPEED.put(item, e.modifier().amount());
                    }
                }
                return;
            }
            ItemAttributeModifiers.Builder out = ItemAttributeModifiers.builder();
            for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                AttributeModifier m = e.modifier();
                if (e.attribute().equals(Attributes.ATTACK_SPEED) && m.id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                    RAW_SPEED.put(item, m.amount());
                    m = new AttributeModifier(m.id(), (4 + m.amount()) * spd - 4, m.operation());
                }
                out.add(e.attribute(), m, e.slot());
            }
            if (plated && item instanceof ArmorItem armor) {
                out.add(Attributes.ARMOR, new AttributeModifier(PLATED_ID, 2, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()));
            }
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS, out.build());
        }));
    }
}
