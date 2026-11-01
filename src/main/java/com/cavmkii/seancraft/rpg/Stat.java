package com.cavmkii.seancraft.rpg;

/**
 * Everything a passive node can grant. Percent stats are in whole percent; flags are 1 when present.
 */
public enum Stat {
    STR("%+.0f Strength"),
    AGI("%+.0f Agility"),
    INT("%+.0f Intelligence"),
    MAX_HEALTH("%+.0f%% maximum health"),
    ARMOR("%+.0f armor"),
    MELEE_DAMAGE("%+.0f%% melee damage"),
    PROJECTILE_DAMAGE("%+.0f%% projectile damage"),
    SPELL_DAMAGE("%+.0f%% ability damage"),
    ATTACK_SPEED("%+.0f%% attack speed"),
    MOVE_SPEED("%+.0f%% movement speed"),
    LIFESTEAL("%.0f%% of melee damage dealt heals you"),
    DODGE("%.0f%% chance to dodge attacks"),
    CRIT_CHANCE("%+.0f%% critical hit chance"),
    CRIT_DAMAGE("%+.0f%% critical hit damage"),
    MANA("%+.0f%% maximum mana"),
    MANA_REGEN("%+.0f%% mana regeneration"),
    RAGE_GAIN("%+.0f%% rage gained"),
    STAMINA("%+.0f%% maximum stamina"),
    STAMINA_REGEN("%+.0f%% stamina regeneration"),
    COOLDOWN("%.0f%% faster cooldowns"),
    KNOCKBACK_RES("%+.0f%% knockback resistance"),
    HEALTH_REGEN("%+.1f health regenerated per second"),
    BACKSTAB("%+.0f%% melee damage when hitting from behind"),
    VENOM("Melee hits poison for %.0f seconds"),
    NO_FALL("No fall damage"),
    MIND_OVER_MATTER("%.0f%% of damage taken comes from mana first"),
    GHOST("Sneaking makes you invisible"),
    HOLY_AURA("Burn nearby monsters every second"),
    // Lineage resistance (6.2.6, 9.3.2): elemental resistance points.
    FIRE_RES("%+.0f fire resistance"),
    FROST_RES("%+.0f frost resistance"),
    LIGHTNING_RES("%+.0f lightning resistance"),
    POISON_RES("%+.0f poison resistance"),
    // Scaling (6.2.6): a bonus that grows with an attribute, in percent per 10 points.
    MELEE_PER_STR("%+.0f%% melee damage per 10 Strength"),
    PROJECTILE_PER_AGI("%+.0f%% projectile damage per 10 Agility"),
    SPELL_PER_INT("%+.0f%% ability damage per 10 Intelligence"),
    BLEED_DURATION("Bleeds you cause last %.0f%% longer"),
    SNEAK_SPEED("%+.0f%% speed while sneaking"),
    // Specialization signatures (6.2.4).
    RAGE_STEADY("Can't be staggered while Rage is above half"),
    RAGE_FURY("%+.0f%% melee damage while Rage is above half"),
    ALLY_DR("Other players within 6 blocks take %.0f%% less damage"),
    TAUNT("Blocking a hit makes the attacker and monsters near you come for you"),
    SPIRIT_SHARE("Melee hits add %.0f%% as spirit damage"),
    ALLY_BLEED_SLOW("Players within 8 blocks bleed %.0f%% slower"),
    ARROW_SLOW("Your arrows slow what they hit"),
    ARROW_ROOT("Arrow crits pin the target in place"),
    RIPOSTE("Your first melee hit within 1.5s of blocking does %+.0f%%"),
    QUICK_STRIKES("Sword hits leave enemies open again quickly, like a dagger's"),
    SPELL_AREA("%+.0f%% skill area"),
    MANA_COST("Skills cost %.0f%% less mana"),
    HEX("Skill hits hex: the target takes %+.0f%% damage for 5s"),
    MED_POWER("Meds apply and heal %.0f%% better"),
    SURGERY("Surgical kits leave 15% lasting damage instead of 35%"),
    ALLY_REGEN("You and players within 8 blocks regenerate %.0f health a second");

    private final String format;

    Stat(String format) {
        this.format = format;
    }

    public String describe(double value) {
        // Flag formats have no placeholder; the extra argument is ignored.
        return String.format(format, value);
    }
}
