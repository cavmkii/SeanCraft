package com.cavmkii.seancraft.rpg;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Region skills (design doc 6.5.5): four per region, each from one node on the web. Pure regions pay one pool; the
 * hybrid regions (Duelist, Shadow, Templar) pay from whichever of their two pools is fuller at the moment of casting
 * (6.5). Ranks 2/3/4 each cost a web point and do what {@link #ranks} says; there is no generic per-rank bonus.
 */
public enum ActiveSkill {
    // Warrior: Rage
    LEAP_SLAM(Archetype.WARRIOR, "Leap Slam", "Leap up to 8 blocks; on landing, 150% W blunt in a 3-block radius with double poise damage.",
            Resource.RAGE, null, 25, 10, Items.IRON_BOOTS, "200% W", "Radius 4, and a 2 s slow", "6 s cooldown, reset on a kill"),
    WAR_CRY(Archetype.WARRIOR, "War Cry", "Players within 8 blocks deal 15% more for 6 s. Monsters within 6 blocks turn on you and deal 10% less.",
            Resource.RAGE, null, 20, 20, Items.GOAT_HORN, "Lasts 9 s", "You can't be staggered for 3 s", "+25% damage"),
    GROUND_SLAM(Archetype.WARRIOR, "Ground Slam", "0.6 s wind-up, then a 5-block cone of 200% W blunt. Staggers anything but a boss; triple poise on bosses.",
            Resource.RAGE, null, 30, 12, Items.MACE, "7-block cone", "Cracked ground slows 30% for 4 s", "250% W"),
    BERSERK(Archetype.WARRIOR, "Berserk", "8 s of +25% attack speed, +20% damage dealt and +20% damage taken. Rage doesn't decay.",
            Resource.RAGE, null, 40, 30, Items.NETHERITE_AXE, "Lasts 10 s", "Your bleeds pause", "+35% attack speed"),
    // Duelist: Rage or stamina
    LUNGE(Archetype.DUELIST, "Lunge", "Dash 5 blocks into the enemy you're looking at for 140% W pierce. Staggers a target mid wind-up.",
            Resource.RAGE, Resource.STAMINA, 15, 6, Items.IRON_SWORD, "170% W", "7 blocks", "4 s cooldown"),
    RIPOSTE(Archetype.DUELIST, "Riposte", "A 0.6 s stance: the next melee hit on you is parried without a shield and countered with a 200% W crit.",
            Resource.RAGE, Resource.STAMINA, 10, 8, Items.SHIELD, "0.8 s stance", "The counter bleeds", "Refunded when it lands"),
    WHIRLWIND(Archetype.DUELIST, "Whirlwind", "Spin for 2 s: 60% W to everything within 2.5 blocks every 0.4 s, at half speed and unable to block.",
            Resource.RAGE, Resource.STAMINA, 30, 14, Items.DIAMOND_SWORD, "2.5 s spin", "Pulls small mobs in", "Radius 3.5"),
    BLADE_FLURRY(Archetype.DUELIST, "Blade Flurry", "5 strikes in 1 s at 50% W on the enemy in front of you. Each can crit and bleed.",
            Resource.RAGE, Resource.STAMINA, 20, 10, Items.GOLDEN_SWORD, "6 strikes", "+5% crit chance per strike", "Last strike 150%"),
    // Ranger: stamina
    DASH(Archetype.RANGER, "Dash", "Dash 6 blocks. Your next shot within 2 s is fully drawn.",
            Resource.STAMINA, null, 20, 8, Items.FEATHER, "6 s cooldown", "2 charges", "Clears slows"),
    VOLLEY(Archetype.RANGER, "Volley", "5 arrows in a 40° fan for the price of one, each 60% of a full draw.",
            Resource.STAMINA, null, 25, 10, Items.BOW, "7 arrows", "Uses your arrow type's effect", "90% of a full draw"),
    RAIN_OF_ARROWS(Archetype.RANGER, "Rain of Arrows", "After a 1 s tell, arrows fall for 3 s on a 4-block circle up to 30 blocks away: 8 hits of 40% and a 20% slow.",
            Resource.STAMINA, null, 35, 18, Items.ARROW, "Radius 5", "Lasts 4 s", "Burns if you carry fire arrows"),
    HUNTERS_MARK(Archetype.RANGER, "Hunter's Mark", "For 12 s the target takes 15% more from everyone and shows through walls.",
            Resource.STAMINA, null, 10, 15, Items.SPECTRAL_ARROW, "+20%", "No stealth, and 20% less poise", "Cooldown resets if it dies"),
    // Shadow: stamina or mana
    SMOKE_BOMB(Archetype.SHADOW, "Smoke Bomb", "A 4-block cloud for 5 s. Monsters inside lose their target; players inside count as sneaking.",
            Resource.STAMINA, Resource.MANA, 20, 16, Items.GUNPOWDER, "Lasts 6 s", "Slows enemies inside", "Radius 6"),
    SHADOW_STEP(Archetype.SHADOW, "Shadow Step", "Teleport behind a visible target up to 10 blocks away. Your next hit within 2 s is a backstab.",
            Resource.STAMINA, Resource.MANA, 20, 10, Items.ENDER_PEARL, "14 blocks", "7 s cooldown", "That hit poisons"),
    POISON_CLOUD(Archetype.SHADOW, "Poison Cloud", "A thrown vial: a 3-block cloud dealing 30% power poison a second for 6 s.",
            Resource.STAMINA, Resource.MANA, 25, 14, Items.SPIDER_EYE, "Lasts 8 s", "Lingers after leaving it", "Radius 4"),
    BLADE_FAN(Archetype.SHADOW, "Blade Fan", "5 conjured knives in a fan, 70% power slash each. They can bleed.",
            Resource.STAMINA, Resource.MANA, 20, 8, Items.IRON_NUGGET, "7 knives", "Heavier bleeds", "90% power"),
    // Mage: mana
    FIREBALL(Archetype.MAGE, "Fireball", "0.5 s cast: 2.0×SP fire to the target and 1.0×SP in a 2-block blast. Burns. Never breaks blocks.",
            Resource.MANA, null, 20, 4, Items.FIRE_CHARGE, "2.4×SP", "Radius 3, burning ground for 3 s", "0.3 s cast"),
    FROST_NOVA(Archetype.MAGE, "Frost Nova", "An instant 5-block burst of 1.2×SP frost. Roots anything but a boss for 2 s; slows bosses 30% for 3 s.",
            Resource.MANA, null, 25, 12, Items.BLUE_ICE, "3 s root", "Radius 6", "Frozen targets take 25% more from the next hit"),
    BLINK(Archetype.MAGE, "Blink", "Teleport 8 blocks toward the crosshair. Needs line of sight; never goes through walls.",
            Resource.MANA, null, 15, 8, Items.CHORUS_FRUIT, "10 blocks", "2 charges", "5 s cooldown"),
    CHAIN_LIGHTNING(Archetype.MAGE, "Chain Lightning", "0.6 s cast: 1.6×SP lightning to the target, then up to 3 more within 6 blocks, 25% less each jump. 0.3 s stun.",
            Resource.MANA, null, 30, 10, Items.LIGHTNING_ROD, "4 jumps", "No loss on the first jump", "1.9×SP"),
    // Templar: mana or Rage
    CONSECRATE(Archetype.TEMPLAR, "Consecrate", "A 4-block circle for 8 s: players in it bleed 50% slower, and the undead take 40% power spirit a second.",
            Resource.MANA, Resource.RAGE, 25, 18, Items.GLOWSTONE_DUST, "Lasts 10 s", "Fractures don't worsen in it", "Radius 5"),
    HOLY_SMITE(Archetype.TEMPLAR, "Holy Smite", "Your next melee hit or bolt adds 150% power as spirit, and staggers the undead.",
            Resource.MANA, Resource.RAGE, 15, 6, Items.BLAZE_ROD, "200% power", "Hits everything within 2 blocks", "4 s cooldown"),
    DIVINE_SHIELD(Archetype.TEMPLAR, "Divine Shield", "Shield yourself or the player you're looking at (8 blocks) for 25% of max health for 6 s. No new bleeds or fractures while it holds.",
            Resource.MANA, Resource.RAGE, 30, 25, Items.TOTEM_OF_UNDYING, "30% of max health", "Lasts 8 s", "Pushes enemies back when it breaks"),
    JUDGMENT(Archetype.TEMPLAR, "Judgment", "0.8 s cast; a second later a pillar falls up to 20 blocks away: 2.5×power spirit in a 3-block radius, double under 25% health.",
            Resource.MANA, Resource.RAGE, 35, 16, Items.ANVIL, "3×power", "Radius 4", "Kills anything but a boss under 15% outright");

    public final Archetype archetype;
    public final String label;
    public final String description;
    public final Resource resource;
    /** The second pool of a hybrid region's skill, or null. */
    public final Resource alt;
    public final int cost;
    public final int cooldownTicks;
    public final Item icon;
    /** What ranks 2, 3 and 4 add. */
    public final String[] ranks;

    ActiveSkill(Archetype archetype, String label, String description, Resource resource, Resource alt, int cost, double cooldownSeconds,
            Item icon, String rank2, String rank3, String rank4) {
        this.archetype = archetype;
        this.label = label;
        this.description = description;
        this.resource = resource;
        this.alt = alt;
        this.cost = cost;
        this.cooldownTicks = (int) Math.round(cooldownSeconds * 20);
        this.icon = icon;
        this.ranks = new String[] {rank2, rank3, rank4};
    }

    /** Cooldown at a rank, before Casting: the ranks that shorten it. */
    public int cooldownTicks(int rank) {
        int s = switch (this) {
            case LEAP_SLAM -> rank >= 4 ? 6 : 10;
            case LUNGE -> rank >= 4 ? 4 : 6;
            case DASH -> rank >= 2 ? 6 : 8;
            case SHADOW_STEP -> rank >= 3 ? 7 : 10;
            case BLINK -> rank >= 4 ? 5 : 8;
            case HOLY_SMITE -> rank >= 4 ? 4 : 6;
            default -> -1;
        };
        return s < 0 ? cooldownTicks : s * 20;
    }

    /** Casts it can bank (Dash and Blink at rank 3). */
    public int charges(int rank) {
        return (this == DASH || this == BLINK) && rank >= 3 ? 2 : 1;
    }

    /** The pool a cast draws from: the fuller of the two for a hybrid skill, by fraction of its maximum. */
    public Resource pool(float mana, float maxMana, float rage, float maxRage, float stamina, float maxStamina) {
        if (alt == null) {
            return resource;
        }
        return fraction(resource, mana, maxMana, rage, maxRage, stamina, maxStamina)
                >= fraction(alt, mana, maxMana, rage, maxRage, stamina, maxStamina) ? resource : alt;
    }

    private static float fraction(Resource r, float mana, float maxMana, float rage, float maxRage, float stamina, float maxStamina) {
        return switch (r) {
            case MANA -> maxMana <= 0 ? 0 : mana / maxMana;
            case RAGE -> maxRage <= 0 ? 0 : rage / maxRage;
            case STAMINA -> maxStamina <= 0 ? 0 : stamina / maxStamina;
        };
    }

    public static ActiveSkill byName(String name) {
        for (ActiveSkill skill : values()) {
            if (skill.name().equals(name)) {
                return skill;
            }
        }
        return null;
    }

    /** The four skills of an archetype, in web order (A, B, C, D). */
    public static ActiveSkill[] of(Archetype archetype) {
        ActiveSkill[] out = new ActiveSkill[4];
        int i = 0;
        for (ActiveSkill skill : values()) {
            if (skill.archetype == archetype) {
                out[i++] = skill;
            }
        }
        return out;
    }
}
