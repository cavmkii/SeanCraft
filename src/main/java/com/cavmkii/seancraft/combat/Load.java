package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.MaterialTraits;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Equip load (design doc 9.4.7): what the equipped weapons, shield and worn armor weigh against the player's capacity,
 * which Strength raises. The doc sets the tiers and classes; the weights and capacity numbers are SeanCraft's.
 */
public final class Load {
    public enum Tier {
        LIGHT("LIGHT", 0, 0),
        MEDIUM("MEDIUM", -0.05, 0),
        HEAVY("HEAVY", -0.15, 1),
        OVERLOADED("OVERLOADED", -0.15, -1);

        public final String label;
        public final double walk;
        /** Which Combat.RollIframeTicks entry applies: 0 light or medium load, 1 heavy, -1 no roll. */
        private final int rollEntry;

        Tier(String label, double walk, int rollEntry) {
            this.label = label;
            this.walk = walk;
            this.rollEntry = rollEntry;
        }

        /** Roll invulnerability in ticks (9.6.3); 0 means no roll at all. */
        public int rollTicks() {
            return rollEntry < 0 ? 0 : com.cavmkii.seancraft.Cfg.rollIframeTicks[rollEntry];
        }
    }

    public static final double LIGHT_WEAPON = 3;
    public static final double MEDIUM_WEAPON = 5;
    public static final double HEAVY_WEAPON = 9;
    /** Capacity before Strength; each STR point adds one. */
    public static final double BASE_CAPACITY = 40;
    public static final double NETHERITE = 1.2;
    public static final double MEMBRANE = 0.85;
    private static final ResourceLocation WALK = SeanCraft.id("equip_load");

    public record State(double weight, double capacity, Tier tier) {
        public double fraction() {
            return capacity <= 0 ? 0 : weight / capacity;
        }
    }

    private Load() {}

    /** Weight class of a weapon or shield (9.4.7), or 0 for anything that isn't one. */
    public static double weaponWeight(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Family.Stock stock = Family.stock(stack);
        double base;
        if (stack.is(com.cavmkii.seancraft.parts.WeaponItems.WOODEN_CLUB) || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) {
            base = MEDIUM_WEAPON;
        } else if (stock == null || stock.family().armor()) {
            return 0;
        } else {
            base = switch (stock.family()) {
                case DAGGER, BUCKLER, THROWING_KNIFE, WAND, BOOMERANG -> LIGHT_WEAPON;
                case WARHAMMER, GREATSWORD, HALBERD, PIKE, TOWER_SHIELD -> HEAVY_WEAPON;
                default -> MEDIUM_WEAPON;
            };
        }
        return base * materialFactor(stack);
    }

    /** A worn piece weighs its armor points: leather is light, netherite heavy. */
    public static double armorWeight(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return 0;
        }
        return armor.getDefense() * materialFactor(stack);
    }

    /** Netherite is heavier, phantom membrane lighter (9.4.7). */
    private static double materialFactor(ItemStack stack) {
        double f = 1;
        if (Parts.modular(stack)) {
            Parts.Installed head = Parts.installed(stack).get(PartSlot.HEAD);
            Parts.Installed plate = Parts.installed(stack).get(PartSlot.PLATE);
            Parts.Installed core = head != null ? head : plate;
            if (core != null && core.part().tier() == 5 && core.part().kind().material == com.cavmkii.seancraft.parts.PartKind.Material.METAL) {
                f *= NETHERITE;
            }
            if (MaterialTraits.has(stack, MaterialTraits.Trait.LIGHTER)) {
                f *= MEMBRANE;
            }
        } else if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("netherite_")) {
            f *= NETHERITE;
        }
        return f;
    }

    /** Works on both sides; {@code strength} is the player's STR (server stats, or the client's synced copy). */
    public static State of(Player player, double strength) {
        double weight = weaponWeight(player.getMainHandItem()) + weaponWeight(player.getOffhandItem());
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            weight += armorWeight(player.getItemBySlot(slot));
        }
        double capacity = BASE_CAPACITY + Math.max(0, strength);
        double f = weight / capacity;
        Tier tier = f < 0.3 ? Tier.LIGHT : f < 0.7 ? Tier.MEDIUM : f <= 1.0 ? Tier.HEAVY : Tier.OVERLOADED;
        return new State(weight, capacity, tier);
    }

    /** Server, every half second: the walk penalty for the load. */
    public static void apply(Player player, State state) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(WALK);
        if (state.tier().walk == 0) {
            if (current != null) {
                speed.removeModifier(WALK);
            }
        } else if (current == null || current.amount() != state.tier().walk) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(WALK, state.tier().walk, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (state.tier() == Tier.OVERLOADED && player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
