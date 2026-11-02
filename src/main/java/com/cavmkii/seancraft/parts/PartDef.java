package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.tarkov.Cost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One part: a kind at a tier (1-5). Stat formulas follow TarkoValheim: each tier adds a step, and levels 2-4
 * add a smaller step, so a maxed part slightly beats the next material at level 1.
 */
public record PartDef(String id, PartKind kind, int tier) {
    public static final int MAX_LEVEL = 4;
    public static final String[] METALS = {"copper", "iron", "gold", "diamond", "netherite"};
    public static final String[] METAL_LABELS = {"Copper", "Iron", "Gold", "Diamond", "Netherite"};
    public static final String[] WOODS = {"planks", "log", "bone", "blaze", "breeze"};
    public static final String[] WOOD_LABELS = {"Plank", "Log", "Bone", "Blaze rod", "Breeze rod"};
    public static final String[] HIDES = {"string", "leather", "rabbit", "membrane", "scute"};
    public static final String[] HIDE_LABELS = {"String", "Leather", "Rabbit hide", "Membrane", "Scute"};
    /** Crystals, weakest to strongest; their factor runs 0.85 to 1.35 like the metals (4.4.1). */
    public static final String[] GEMS = {"quartz", "lapis", "amethyst", "prismarine", "echo"};
    public static final String[] GEM_LABELS = {"Quartz", "Lapis", "Amethyst", "Prismarine", "Echo"};

    public static final List<PartDef> ALL = new ArrayList<>();

    static {
        for (int t = 1; t <= 5; t++) {
            for (PartKind kind : PartKind.values()) {
                String material = switch (kind.material) {
                    case METAL -> METALS[t - 1];
                    case WOOD -> WOODS[t - 1];
                    case HIDE -> HIDES[t - 1];
                    case GEM -> GEMS[t - 1];
                };
                ALL.add(new PartDef(kind.name().toLowerCase() + "_" + material, kind, t));
            }
        }
    }

    public static PartDef byId(String id) {
        for (PartDef def : ALL) {
            if (def.id.equals(id)) {
                return def;
            }
        }
        return null;
    }

    public static PartDef of(PartKind kind, int tier) {
        for (PartDef def : ALL) {
            if (def.kind == kind && def.tier == tier) {
                return def;
            }
        }
        return null;
    }

    public String materialLabel() {
        return switch (kind.material) {
            case METAL -> METAL_LABELS[tier - 1];
            case WOOD -> WOOD_LABELS[tier - 1];
            case HIDE -> HIDE_LABELS[tier - 1];
            case GEM -> GEM_LABELS[tier - 1];
        };
    }

    public String label() {
        return materialLabel() + " " + kind.label.toLowerCase();
    }

    /** What a level-up costs: the part's own material. */
    public Item material() {
        return switch (kind.material) {
            case METAL -> new Item[] {Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.NETHERITE_SCRAP}[tier - 1];
            case WOOD -> new Item[] {Items.OAK_PLANKS, Items.OAK_LOG, Items.BONE, Items.BLAZE_ROD, Items.BREEZE_ROD}[tier - 1];
            case HIDE -> new Item[] {Items.STRING, Items.LEATHER, Items.RABBIT_HIDE, Items.PHANTOM_MEMBRANE, Items.ARMADILLO_SCUTE}[tier - 1];
            case GEM -> new Item[] {Items.QUARTZ, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD, Items.PRISMARINE_SHARD, Items.ECHO_SHARD}[tier - 1];
        };
    }

    /**
     * What the Weapon Bench takes to make this part (4.3): 2 of its material; 1 for a knife blade; 3 for axe and mace
     * heads, limbs, hafts and padding; 4 for long blades and halberd heads; a stick more for a guard. Netherite is the
     * diamond part plus 2 netherite scrap.
     */
    public List<Cost> costs() {
        if (kind.material == PartKind.Material.METAL && tier == 5) {
            return List.of(Cost.of(PartItems.get(of(kind, 4)), 1), Cost.of(Items.NETHERITE_SCRAP, 2));
        }
        int n = switch (kind) {
            case KNIFE_BLADE -> 1;
            case AXE_HEAD, MACE_HEAD, LIMBS, HAFT, PADDING -> 3;
            case LONG_BLADE, HALBERD_HEAD -> 4;
            default -> 2;
        };
        Cost main = switch (kind.material) {
            case METAL -> Cost.of(material(), n);
            case WOOD -> tier == 1 ? Cost.planks(n) : tier == 2 ? Cost.logs(n) : Cost.of(material(), n);
            case HIDE, GEM -> Cost.of(material(), n);
        };
        return kind == PartKind.GUARD ? List.of(main, Cost.of(Items.STICK, 1)) : List.of(main);
    }

    /** Materials to go from {@code level} to {@code level + 1}. */
    public int upgradeCost(int level) {
        return (kind.material == PartKind.Material.METAL ? 1 : 2) * (level + 1);
    }

    private static float at(float base, float step, int level) {
        return base + step * (Math.max(1, Math.min(MAX_LEVEL, level)) - 1);
    }

    // Stats (fractions), by kind.
    public float damage(int level) {
        // Each level above 1 adds 6% (9.5.1, step 1).
        return kind.slot == PartSlot.HEAD ? at(0.06f * tier, 0.06f, level) : 0;
    }

    public float durability(int level) {
        return switch (kind) {
            case BLADE, LONG_BLADE, KNIFE_BLADE, AXE_HEAD, MACE_HEAD, SPEARHEAD, HALBERD_HEAD, LIMBS, PLATE, CRYSTAL, FOCUS -> at(0.05f * tier, 0.05f, level);
            case HANDLE, HAFT, PADDING -> at(0.04f * tier, 0.04f, level);
            default -> 0;
        };
    }

    /** Negative = cheaper attacks, which in Minecraft means faster swings. */
    public float stamina(int level) {
        return switch (kind) {
            case HANDLE, HAFT -> at(-0.03f * tier, -0.015f, level);
            case WRAP -> at(-0.02f * tier, -0.01f, level);
            default -> 0;
        };
    }

    public float block(int level) {
        return switch (kind) {
            case GUARD -> at(0.06f * tier, 0.025f, level);
            case WRAP -> at(0.02f * tier, 0.01f, level);
            default -> 0;
        };
    }

    public float parry(int level) {
        return kind == PartKind.GUARD ? at(0.05f * tier, 0.02f, level) : 0;
    }

    public float armor(int level) {
        return switch (kind) {
            case PLATE -> at(0.06f * tier, 0.025f, level);
            case PADDING -> at(0.02f * tier, 0.01f, level);
            default -> 0;
        };
    }

    /** Negative = lighter armor, which here means a little faster. */
    public float weight(int level) {
        return kind == PartKind.WRAP ? at(-0.04f * tier, -0.02f, level) : 0;
    }
}
