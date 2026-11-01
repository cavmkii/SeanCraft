package com.cavmkii.seancraft.rpg;

/**
 * Specialization signature nodes (design doc 6.2.4, 6.2.5): three per specialization, opened by the specialization,
 * mastery and capstone milestones. The doc gives each specialization's signature in a phrase; these make it concrete.
 */
final class Signatures {
    private Signatures() {}

    static WebNode node(int id, Specialization spec, int tier, double x, double y) {
        WebNode.Kind k = WebNode.Kind.SIGNATURE;
        WebNode n = switch (spec) {
            case WARRIOR -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Iron Will", x, y, null).stat(Stat.RAGE_STEADY, 1);
                case 2 -> new WebNode(id, k, spec.region, "Bloodlust", x, y, null).stat(Stat.RAGE_GAIN, 50).stat(Stat.MELEE_DAMAGE, 10);
                default -> new WebNode(id, k, spec.region, "Fury", x, y, null).stat(Stat.RAGE_FURY, 25);
            };
            case KNIGHT -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Guardian", x, y, null).stat(Stat.ALLY_DR, 15);
                case 2 -> new WebNode(id, k, spec.region, "Challenge", x, y, null).stat(Stat.TAUNT, 1);
                default -> new WebNode(id, k, spec.region, "Bastion", x, y, null).stat(Stat.ARMOR, 10).stat(Stat.KNOCKBACK_RES, 50);
            };
            case PALADIN -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Smite", x, y, null).stat(Stat.SPIRIT_SHARE, 20);
                case 2 -> new WebNode(id, k, spec.region, "Mercy", x, y, null).stat(Stat.ALLY_BLEED_SLOW, 30);
                default -> new WebNode(id, k, spec.region, "Radiance", x, y, null).stat(Stat.SPIRIT_SHARE, 20).stat(Stat.HOLY_AURA, 1);
            };
            case ARCHER -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Pinning Shots", x, y, null).stat(Stat.ARROW_SLOW, 1);
                case 2 -> new WebNode(id, k, spec.region, "Marksman", x, y, null).stat(Stat.PROJECTILE_DAMAGE, 15).stat(Stat.CRIT_CHANCE, 5);
                default -> new WebNode(id, k, spec.region, "Pin", x, y, null).stat(Stat.ARROW_ROOT, 1);
            };
            case BLADEMASTER -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Riposte", x, y, null).stat(Stat.RIPOSTE, 50);
                case 2 -> new WebNode(id, k, spec.region, "Quick Strikes", x, y, null).stat(Stat.QUICK_STRIKES, 1);
                default -> new WebNode(id, k, spec.region, "Flurry", x, y, null).stat(Stat.ATTACK_SPEED, 15).stat(Stat.CRIT_CHANCE, 10);
            };
            case ROGUE -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Light Step", x, y, null).stat(Stat.SNEAK_SPEED, 25).stat(Stat.BACKSTAB, 25);
                case 2 -> new WebNode(id, k, spec.region, "Shadowed", x, y, null).stat(Stat.GHOST, 1);
                default -> new WebNode(id, k, spec.region, "Assassin", x, y, null).stat(Stat.BACKSTAB, 50).stat(Stat.CRIT_DAMAGE, 25);
            };
            case SORCERER -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Wide Casting", x, y, null).stat(Stat.SPELL_AREA, 30);
                case 2 -> new WebNode(id, k, spec.region, "Elemental Mastery", x, y, null).stat(Stat.SPELL_DAMAGE, 20);
                default -> new WebNode(id, k, spec.region, "Overload", x, y, null).stat(Stat.MANA_COST, 20);
            };
            case WARLOCK -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Hex", x, y, null).stat(Stat.HEX, 15);
                case 2 -> new WebNode(id, k, spec.region, "Virulence", x, y, null).stat(Stat.VENOM, 4);
                default -> new WebNode(id, k, spec.region, "Doom's Edge", x, y, null).stat(Stat.HEX, 15).stat(Stat.SPELL_DAMAGE, 10);
            };
            case CLERIC -> switch (tier) {
                case 1 -> new WebNode(id, k, spec.region, "Field Medic", x, y, null).stat(Stat.MED_POWER, 30);
                case 2 -> new WebNode(id, k, spec.region, "Field Surgery", x, y, null).stat(Stat.SURGERY, 1);
                default -> new WebNode(id, k, spec.region, "Sanctuary", x, y, null).stat(Stat.ALLY_REGEN, 3);
            };
        };
        n.sigSpec = spec;
        n.sigTier = tier;
        return n;
    }
}
