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
    DAGGER(List.of(PartSlot.HEAD, PartSlot.HANDLE), PartKind.KNIFE_BLADE),
    MACE(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.MACE_HEAD),
    WARHAMMER(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.MACE_HEAD, PartKind.HAFT),
    GREATSWORD(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.GUARD, PartSlot.WRAP), PartKind.LONG_BLADE, PartKind.HAFT),
    HALBERD(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.HALBERD_HEAD, PartKind.HAFT),
    SPEAR(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.SPEARHEAD, PartKind.HAFT),
    PIKE(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.SPEARHEAD, PartKind.HAFT),
    BOW(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.LIMBS),
    CROSSBOW(List.of(PartSlot.HEAD, PartSlot.HANDLE, PartSlot.WRAP), PartKind.LIMBS),
    SHIELD(List.of(PartSlot.HANDLE, PartSlot.GUARD, PartSlot.WRAP), null),
    /** The Blademaster's buckler (9.4.6): light, 60% block, a wider parry window. */
    BUCKLER(List.of(PartSlot.HANDLE, PartSlot.GUARD), null),
    /** The Knight's tower shield: heavy, blocks everything frontal including arrows, no parry. */
    TOWER_SHIELD(List.of(PartSlot.HANDLE, PartSlot.GUARD, PartSlot.WRAP), null),
    HELMET(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    CHEST(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    LEGS(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    BOOTS(List.of(PartSlot.PLATE, PartSlot.PADDING, PartSlot.WRAP), null),
    /** Throwables (9.4): thrown with the use key, held like the trident. */
    THROWING_KNIFE(List.of(PartSlot.HEAD, PartSlot.WRAP), PartKind.KNIFE_BLADE),
    JAVELIN(List.of(PartSlot.HEAD, PartSlot.HANDLE), PartKind.SPEARHEAD),
    BOOMERANG(List.of(PartSlot.HANDLE, PartSlot.WRAP), null),
    /** Magic (9.4.5): crystal, focus and a grip; attacks fire bolts that cost mana. */
    WAND(List.of(PartSlot.HEAD, PartSlot.GUARD, PartSlot.HANDLE), PartKind.CRYSTAL),
    STAFF(List.of(PartSlot.HEAD, PartSlot.GUARD, PartSlot.HANDLE), PartKind.CRYSTAL, PartKind.HAFT);

    public final List<PartSlot> slots;
    public final PartKind head;
    /** What fills the handle slot: a handle, or a haft for the long weapons (4.4). */
    public final PartKind grip;

    Family(List<PartSlot> slots, PartKind head) {
        this(slots, head, PartKind.HANDLE);
    }

    Family(List<PartSlot> slots, PartKind head, PartKind grip) {
        this.slots = slots;
        this.head = head;
        this.grip = grip;
    }

    public boolean magic() {
        return this == WAND || this == STAFF;
    }

    public boolean throwable() {
        return this == SPEAR || this == THROWING_KNIFE || this == JAVELIN || this == BOOMERANG;
    }

    public boolean shield() {
        return this == SHIELD || this == BUCKLER || this == TOWER_SHIELD;
    }

    /** For items registered elsewhere (the new shields): their family and standard tiers. */
    static void stock(Item item, Stock stock) {
        STOCK.put(item, stock);
    }

    public boolean armor() {
        return this == HELMET || this == CHEST || this == LEGS || this == BOOTS;
    }

    public boolean fits(PartDef part) {
        if (!slots.contains(part.kind().slot)) {
            return false;
        }
        return switch (part.kind().slot) {
            case HEAD -> part.kind() == head;
            case HANDLE -> part.kind() == grip;
            case GUARD -> part.kind() == (magic() ? PartKind.FOCUS : PartKind.GUARD);
            default -> true;
        };
    }

    /**
     * A slot's name on this family, with the part that fills it when the names differ ("String (wrap)", "Grip (handle)"),
     * so it's clear which book entry to make.
     */
    public String slotName(PartSlot slot) {
        String flavor = slotFlavor(slot);
        PartKind part = switch (slot) {
            case HEAD -> head;
            case HANDLE -> grip;
            case GUARD -> magic() ? PartKind.FOCUS : PartKind.GUARD;
            case PLATE -> PartKind.PLATE;
            case PADDING -> PartKind.PADDING;
            case WRAP -> PartKind.WRAP;
        };
        return part == null || flavor.equalsIgnoreCase(part.label) ? flavor : flavor + " (" + part.label.toLowerCase(java.util.Locale.ROOT) + ")";
    }

    private String slotFlavor(PartSlot slot) {
        return switch (slot) {
            case HEAD -> head == null ? "Head" : head.label;
            case HANDLE -> grip == PartKind.HAFT ? (this == GREATSWORD ? "Long grip" : "Haft")
                    : this == AXE ? "Haft" : this == CROSSBOW ? "Stock" : shield() ? "Handle" : this == BOOMERANG ? "Arms" : "Grip";
            case GUARD -> shield() ? "Rim" : magic() ? "Focus" : "Guard";
            case PLATE -> this == HELMET ? "Shell" : this == LEGS ? "Greaves" : "Plates";
            case PADDING -> this == HELMET ? "Liner" : "Padding";
            case WRAP -> this == BOW || this == CROSSBOW ? "String" : shield() || armor() ? "Straps" : "Wrap";
        };
    }

    /** Family and standard tier of a vanilla item: tier 1 copper (stone, chainmail) up to 5 netherite. */
    public record Stock(Family family, int metal, int wood, int hide) {
        public Map<PartSlot, PartDef> parts() {
            Map<PartSlot, PartDef> map = new EnumMap<>(PartSlot.class);
            for (PartSlot slot : family.slots) {
                PartDef def = switch (slot) {
                    case HEAD -> PartDef.of(family.head, metal);
                    case GUARD -> PartDef.of(family.magic() ? PartKind.FOCUS : PartKind.GUARD, metal);
                    case PLATE -> PartDef.of(PartKind.PLATE, metal);
                    case HANDLE -> PartDef.of(family.grip, wood);
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
    static void tiers(Family family, Item... items) {
        for (int i = 0; i < items.length; i++) {
            int t = i + 1;
            STOCK.put(items[i], new Stock(family, t, t, t));
        }
    }

    public static Stock stock(ItemStack stack) {
        return STOCK.get(stack.getItem());
    }

    public static Stock stock(Item item) {
        return STOCK.get(item);
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
