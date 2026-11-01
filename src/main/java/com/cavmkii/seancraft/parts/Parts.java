package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.SeanCraft;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Parts installed in a weapon or armor piece, stored as a data component, and how they change the item's
 * real stats. The standard build at level 1 is exactly the vanilla item; everything is relative to that.
 */
public final class Parts {
    /** Installed parts: slot name to "partId:level". */
    public static final DataComponentType<Map<String, String>> INSTALLED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            SeanCraft.id("parts"), DataComponentType.<Map<String, String>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING))
                    .networkSynchronized(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8))
                    .build());

    /** A part item's level, 1-4. */
    public static final DataComponentType<Integer> LEVEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            SeanCraft.id("part_level"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private static final float NO_HEAD_DAMAGE = -0.75f;
    private static final float NO_HANDLE_STAMINA = 0.3f;
    private static final float NO_HANDLE_DURABILITY = -0.3f;
    private static final float NO_PLATE_ARMOR = -0.6f;

    public record Installed(PartDef part, int level) {}

    /** Summed differences from the standard build. */
    public static final class Totals {
        public float damage;
        public float durability;
        public float stamina;
        public float block;
        public float parry;
        public float spirit;
        public float fire;
        public float armor;
        public float weight;
        public boolean modified;
        public boolean missingCore;
    }

    private Parts() {}

    public static void init() {
        // Loading the class registers the components.
    }

    public static boolean modular(ItemStack stack) {
        return Family.stock(stack) != null;
    }

    public static int level(ItemStack part) {
        return Math.max(1, Math.min(PartDef.MAX_LEVEL, part.getOrDefault(LEVEL, 1)));
    }

    public static ItemStack partStack(Installed installed) {
        ItemStack stack = new ItemStack(PartItems.get(installed.part()));
        if (installed.level() > 1) {
            stack.set(LEVEL, installed.level());
        }
        return stack;
    }

    /** Installed parts. A modular item with no record yet has its standard parts. */
    public static Map<PartSlot, Installed> installed(ItemStack stack) {
        Map<PartSlot, Installed> out = new EnumMap<>(PartSlot.class);
        Family.Stock stock = Family.stock(stack);
        if (stock == null) {
            return out;
        }
        Map<String, String> raw = stack.get(INSTALLED);
        if (raw == null) {
            stock.parts().forEach((slot, def) -> out.put(slot, new Installed(def, 1)));
            return out;
        }
        raw.forEach((slotName, value) -> {
            try {
                PartSlot slot = PartSlot.valueOf(slotName);
                String[] bits = value.split(":");
                PartDef def = PartDef.byId(bits[0]);
                int level = bits.length > 1 ? Integer.parseInt(bits[1]) : 1;
                if (def != null && stock.family().fits(def) && def.kind().slot == slot) {
                    out.put(slot, new Installed(def, Math.max(1, Math.min(PartDef.MAX_LEVEL, level))));
                }
            } catch (RuntimeException ignored) {
                // A part that no longer exists.
            }
        });
        return out;
    }

    /** Record the parts and rewrite the item's stats from them. */
    public static void setInstalled(ItemStack stack, Map<PartSlot, Installed> parts) {
        Map<String, String> raw = new HashMap<>();
        parts.forEach((slot, inst) -> raw.put(slot.name(), inst.part().id() + ":" + inst.level()));
        stack.set(INSTALLED, raw);
        applyStats(stack);
    }

    public static Totals totals(ItemStack stack) {
        Totals t = new Totals();
        Family.Stock stock = Family.stock(stack);
        if (stock == null) {
            return t;
        }
        Map<PartSlot, Installed> parts = installed(stack);
        Map<PartSlot, PartDef> standard = stock.parts();
        for (PartSlot slot : stock.family().slots) {
            Installed inst = parts.get(slot);
            PartDef std = standard.get(slot);
            if (inst != null) {
                add(t, inst.part(), inst.level(), 1);
            }
            if (std != null) {
                add(t, std, 1, -1);
                if (inst == null || inst.part() != std || inst.level() > 1) {
                    t.modified = true;
                }
                if (inst == null && slot == PartSlot.HEAD) {
                    t.damage += NO_HEAD_DAMAGE;
                    t.missingCore = true;
                }
                if (inst == null && slot == PartSlot.HANDLE) {
                    t.stamina += NO_HANDLE_STAMINA;
                    t.durability += NO_HANDLE_DURABILITY;
                }
                if (inst == null && slot == PartSlot.PLATE) {
                    t.armor += NO_PLATE_ARMOR;
                    t.missingCore = true;
                }
            }
        }
        return t;
    }

    private static void add(Totals t, PartDef p, int level, float sign) {
        t.damage += sign * p.damage(level);
        t.durability += sign * p.durability(level);
        t.stamina += sign * p.stamina(level);
        t.block += sign * p.block(level);
        t.parry += sign * p.parry(level);
        t.spirit += sign * p.spirit(level);
        t.fire += sign * p.fire(level);
        t.armor += sign * p.armor(level);
        t.weight += sign * p.weight(level);
    }

    /**
     * Rewrites the item's attribute modifiers and durability in vanilla terms, so the normal tooltip shows the
     * result: damage and swing speed for weapons, armor and toughness for armor, durability for both.
     */
    public static void applyStats(ItemStack stack) {
        Family.Stock stock = Family.stock(stack);
        if (stock == null) {
            return;
        }
        Totals t = totals(stack);
        Item item = stack.getItem();
        // Swords and axes keep their modifiers in the item's default components; armor computes them.
        ItemAttributeModifiers defaults = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (defaults.modifiers().isEmpty()) {
            defaults = item.getDefaultAttributeModifiers();
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : defaults.modifiers()) {
            AttributeModifier m = e.modifier();
            double amount = m.amount();
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                amount = amount * Math.max(0.05, 1 + t.damage);
            } else if (e.attribute().equals(Attributes.ATTACK_SPEED)) {
                double speed = 4 + amount;
                amount = speed * Math.max(0.3, 1 - t.stamina) - 4;
            } else if (e.attribute().equals(Attributes.ARMOR) || e.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
                amount = amount * Math.max(0, 1 + t.armor);
            }
            builder.add(e.attribute(), new AttributeModifier(m.id(), amount, m.operation()), e.slot());
        }
        EquipmentSlotGroup group = stock.family().armor() && item instanceof ArmorItem armor
                ? EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()) : EquipmentSlotGroup.MAINHAND;
        if (stock.family() == Family.SWORD) {
            // The family's Better Combat moveset (data/seancraft/weapon_attributes/sword.json), for every material.
            // The newer families carry theirs on the item (WeaponItems).
            stack.set(net.bettercombat.api.component.BetterCombatDataComponents.WEAPON_PRESET_ID, com.cavmkii.seancraft.SeanCraft.id("sword"));
        }
        if (stock.family() == Family.SWORD || stock.family() == Family.GREATSWORD) {
            extra(builder, Attributes.SWEEPING_DAMAGE_RATIO, "parts_sweep", t.block, group);
            extra(builder, Attributes.ATTACK_KNOCKBACK, "parts_knockback", t.parry * 2, group);
        }
        if (stock.family().armor() && Math.abs(t.weight) > 1.0e-4) {
            extra(builder, Attributes.MOVEMENT_SPEED, "parts_weight", -t.weight * 0.25, group);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        Integer vanillaMax = item.components().get(DataComponents.MAX_DAMAGE);
        if (vanillaMax != null) {
            int oldMax = stack.getMaxDamage();
            int newMax = Math.max(1, Math.round(vanillaMax * Math.max(0.1f, 1 + t.durability)));
            int damage = stack.getDamageValue();
            stack.set(DataComponents.MAX_DAMAGE, newMax);
            stack.setDamageValue(Math.min(newMax - 1, Math.round(damage * newMax / (float) Math.max(1, oldMax))));
        }
    }

    private static void extra(ItemAttributeModifiers.Builder builder, Holder<Attribute> attribute, String name, double amount,
            EquipmentSlotGroup group) {
        if (Math.abs(amount) > 1.0e-4) {
            builder.add(attribute, new AttributeModifier(SeanCraft.id(name), amount,
                    attribute.equals(Attributes.MOVEMENT_SPEED) ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE),
                    group);
        }
    }
}
