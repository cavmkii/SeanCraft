package com.cavmkii.seancraft.parts;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Modular item families, their slots, slot names, and each vanilla item's standard build. */
public enum Family {
    SWORD(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.GUARD, PartSlot.WRAP), PartKind.BLADE),
    AXE(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.AXE_HEAD),
    BOW(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.LIMBS),
    CROSSBOW(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.LIMBS),
    SHIELD(List.of(PartSlot.HANDLE, PartSlot.GUARD, PartSlot.WRAP), null),
    HELMET(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    CHEST(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    LEGS(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    BOOTS(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null);

    public final List<PartSlot> slots;
    public final PartKind head;

    Family(List<PartSlot> slots, PartKind head) {
        this.slots = slots;
        this.head = head;
    }

    public boolean armor() {
        return this == HELMET || this == CHEST || this == LEGS || this == BOOTS;
    }

    public boolean fits(PartDef part) {
        if (!slots.contains(part.kind().slot)) {
            return false;
        }
        return part.kind().slot != PartSlot.HEAD || part.kind() == head;
    }

    public String slotName(PartSlot slot) {
        return switch (slot) {
            case HEAD -> head == PartKind.BLADE ? "Blade" : head == PartKind.AXE_HEAD ? "Axe head" : "Limbs";
            case HANDLE -> this == AXE ? "Haft" : this == CROSSBOW ? "Stock" : this == SHIELD ? "Handle" : "Grip";
            case GUARD -> this == SHIELD ? "Rim" : "Guard";
            case PLATE -> this == HELMET ? "Shell" : this == LEGS ? "Greaves" : "Plates";
            case PADDING -> this == HELMET ? "Liner" : "Padding";
            case WRAP -> this == BOW || this == CROSSBOW ? "String" : this == SHIELD || armor() ? "Straps" : "Wrap";
        };
    }

    /** Family and standard tier of a vanilla item: tier 1 copper (stone, chainmail) up to 5 netherite. */
    public record Stock(Family family, int metal, int wood, int hide) {
        public Map<PartSlot, PartDef> parts() {
            Map<PartSlot, PartDef> map = new EnumMap<>(PartSlot.class);
            for (PartSlot slot : family.slots) {
                PartDef def = switch (slot) {
                    case HEAD -> PartDef.of(family.head, metal);
                    case GUARD -> PartDef.of(PartKind.GUARD, metal);
                    case PLATE -> PartDef.of(PartKind.PLATE, metal);
                    case HANDLE -> PartDef.of(PartKind.HANDLE, wood);
                    case PADDING -> PartDef.of(PartKind.PADDING, hide);
                    case WRAP -> PartDef.of(PartKind.WRAP, hide);
                };
                map.put(slot, def);
            }
            return map;
        }
    }

    private static final Map<Item, Stock> STOCK = new java.util.HashMap<>();

    static {
        tiers(SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        tiers(AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
        STOCK.put(Items.BOW, new Stock(BOW, 1, 1, 1));
        STOCK.put(Items.CROSSBOW, new Stock(CROSSBOW, 2, 2, 1));
        STOCK.put(Items.SHIELD, new Stock(SHIELD, 2, 1, 2));
        tiers(HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
        tiers(CHEST, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        tiers(LEGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
        tiers(BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
    }

    /** Items from tier 1 up. Stone tools stand in for copper, the way chainmail does for armor. */
    private static void tiers(Family family, Item... items) {
        for (int i = 0; i < items.length; i++) {
            int t = i + 1;
            STOCK.put(items[i], new Stock(family, t, t, t));
        }
    }

    public static Stock stock(ItemStack stack) {
        return STOCK.get(stack.getItem());
    }

    /** The vanilla item a family makes at a metal tier, for crafting from parts. */
    public static Item itemFor(Family family, int metal) {
        for (Map.Entry<Item, Stock> e : STOCK.entrySet()) {
            if (e.getValue().family() == family && e.getValue().metal() == metal) {
                return e.getKey();
            }
        }
        return null;
    }
}
