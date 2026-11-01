package com.cavmkii.seancraft.rpg;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything derived from a character (design doc 6.3): attributes (the origin's start plus points from levels; the web
 * grants none), summed node stats, and what each attribute does. There's no primary attribute: weapons and skills scale
 * with their own (6.3.1). Pure arithmetic, so the client shows the same numbers the server uses.
 */
public final class Stats {
    public final Map<Stat, Double> node = new EnumMap<>(Stat.class);
    public final Map<Attr, Double> attrs = new EnumMap<>(Attr.class);

    public Stats(PlayerData data) {
        for (int id : data.nodes) {
            WebNode n = PassiveWeb.get(id);
            if (n != null) {
                n.stats.forEach((stat, value) -> node.merge(stat, value, Double::sum));
            }
        }
        for (Attr attr : Attr.values()) {
            attrs.put(attr, data.attribute(attr));
        }
    }

    public double get(Stat stat) {
        return node.getOrDefault(stat, 0.0);
    }

    public boolean has(Stat stat) {
        return get(stat) > 0;
    }

    public double attr(Attr attr) {
        return attrs.get(attr);
    }

    public double str() { return attr(Attr.STR); }
    public double agi() { return attr(Attr.AGI); }
    public double intel() { return attr(Attr.INT); }

    // STR: rage, crit damage, knockback, a modest share of max health.
    public double bonusHealth() { return str() * 0.4; }
    public float maxRage() { return (float) (100 + str()); }
    public double knockback() { return str() * 0.01; }
    /** Extra damage a crit does, in percent: +50%, and 0.25% per STR (6.3.2), plus nodes. */
    public double critDamage() { return 50 + str() * 0.25 + get(Stat.CRIT_DAMAGE); }

    // AGI: crit chance, swing speed, cheaper AGI actions, faster meds.
    /** Percent chance for a melee or projectile hit to crit: 5%, AGI and nodes add to it, up to 75% (6.3.2). */
    public double critChance() { return Math.min(75, 5 + agi() * 0.3 + get(Stat.CRIT_CHANCE)); }
    public double attackSpeedPct() { return agi() * 0.4 + get(Stat.ATTACK_SPEED); }
    /** Stamina saved on AGI weapons and skills: 50% x AGI / (AGI + 60), never more than half (6.5.1). */
    public double staminaEfficiency() { return 0.5 * agi() / (agi() + 60); }
    /** Meds apply this much faster. */
    public double medSpeed() { return 1 + agi() * 0.005; }

    // INT: mana, cooldowns, faster skill xp, longer food, Rested and painkillers.
    public float maxMana() { return (float) ((100 + intel() * 2) * (1 + get(Stat.MANA) / 100)); }
    public float manaRegen() { return (float) ((1 + intel() * 0.05) * (1 + get(Stat.MANA_REGEN) / 100)); }
    public double skillXpMultiplier() { return 1 + intel() * 0.005; }
    /** Food, Rested and painkillers last this much longer (6.3.3: INT supports preparation). */
    public double preparation() { return 1 + intel() * 0.005; }
    public double cooldownMultiplier(int castingLevel) {
        return Math.max(0.4, 1 - get(Stat.COOLDOWN) / 100 - intel() * 0.0025 - castingLevel * 0.002);
    }

    // Nodes only.
    public double healthRegen() { return get(Stat.HEALTH_REGEN); }
    public double moveSpeedPct() { return get(Stat.MOVE_SPEED); }
    public double bonusArmor() { return get(Stat.ARMOR); }
    public double meleeDamagePct() { return get(Stat.MELEE_DAMAGE) + get(Stat.MELEE_PER_STR) * str() / 10; }
    public double projectileDamagePct() { return get(Stat.PROJECTILE_DAMAGE) + get(Stat.PROJECTILE_PER_AGI) * agi() / 10; }
    public double spellDamagePct() { return get(Stat.SPELL_DAMAGE) + get(Stat.SPELL_PER_INT) * intel() / 10; }
    /** Base 100 for everyone; AGI never raises it (6.3.3). Regeneration is flat, in Combat. */
    public float maxStamina() { return (float) (100 * (1 + get(Stat.STAMINA) / 100)); }

    /** Elemental resistance points from lineage nodes (9.3.2). */
    public double resistance(com.cavmkii.seancraft.combat.Damage.Type type) {
        return switch (type) {
            case FIRE -> get(Stat.FIRE_RES);
            case FROST -> get(Stat.FROST_RES);
            case LIGHTNING -> get(Stat.LIGHTNING_RES);
            case POISON -> get(Stat.POISON_RES);
            default -> 0;
        };
    }

    /** +1% increased damage per point of the scaling attribute; split scaling averages the two (6.3.1). */
    public double scalingPct(Attr first, Attr second) {
        return second == null ? attr(first) : (attr(first) + attr(second)) / 2;
    }
}
