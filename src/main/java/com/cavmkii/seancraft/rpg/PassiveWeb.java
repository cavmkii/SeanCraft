package com.cavmkii.seancraft.rpg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The passive web (design doc 6.2): six regions in a ring, 19 nodes each, with a bridge between each pair of
 * neighbours, 120 nodes in all. Each origin starts at the inner node of its home region; its starter web is that region
 * plus the two bridges out of it. Its three specialization gates are its region's far node and those two bridges.
 * There are no flat attribute nodes: every node does something on its own. Built the same way on server and client, so
 * node ids match without being sent over the network.
 */
public final class PassiveWeb {

    /** Per-region template: radius, angle offset (degrees), kind, and which template nodes it links to. */
    private record Slot(double radius, double angle, WebNode.Kind kind, int[] linksTo) {}

    private static final Slot[] TEMPLATE = {
            /* 0 */ new Slot(1.2, 0, WebNode.Kind.SMALL, new int[] {}),
            /* 1 */ new Slot(2.1, 0, WebNode.Kind.SMALL, new int[] {0}),
            /* 2 */ new Slot(2.7, -15, WebNode.Kind.SMALL, new int[] {1}),
            /* 3 */ new Slot(2.7, 15, WebNode.Kind.SMALL, new int[] {1}),
            /* 4 */ new Slot(3.2, 0, WebNode.Kind.ACTIVE, new int[] {1}),
            /* 5 */ new Slot(3.5, -22, WebNode.Kind.NOTABLE, new int[] {2}),
            /* 6 */ new Slot(3.5, 22, WebNode.Kind.NOTABLE, new int[] {3}),
            /* 7 */ new Slot(4.2, 0, WebNode.Kind.SMALL, new int[] {4}),
            /* 8 */ new Slot(4.4, -18, WebNode.Kind.SMALL, new int[] {5}),
            /* 9 */ new Slot(4.4, 18, WebNode.Kind.SMALL, new int[] {6}),
            /* 10 */ new Slot(5.0, -9, WebNode.Kind.ACTIVE, new int[] {7, 8}),
            /* 11 */ new Slot(5.0, 9, WebNode.Kind.ACTIVE, new int[] {7, 9}),
            /* 12 */ new Slot(5.6, -22, WebNode.Kind.SMALL, new int[] {8}),
            /* 13 */ new Slot(5.6, 22, WebNode.Kind.SMALL, new int[] {9}),
            /* 14 */ new Slot(6.0, 0, WebNode.Kind.NOTABLE, new int[] {10, 11}),
            /* 15 */ new Slot(6.6, -15, WebNode.Kind.NOTABLE, new int[] {12}),
            /* 16 */ new Slot(6.6, 15, WebNode.Kind.ACTIVE, new int[] {13}),
            /* 17 */ new Slot(7.0, 0, WebNode.Kind.SMALL, new int[] {14, 15, 16}),
            /* 18 */ new Slot(7.9, 0, WebNode.Kind.KEYSTONE, new int[] {17}),
    };
    private static final int[] ACTIVE_SLOTS = {4, 10, 11, 16};
    private static final int[] NOTABLE_SLOTS = {5, 6, 14, 15};
    /** In a pure region, slot 17 is the home specialization's gate; in a hybrid one it's a scaling node. */
    private static final int HOME_GATE_SLOT = 17;
    /** Slot 7 is each region's lineage resistance node. */
    private static final int LINEAGE_SLOT = 7;

    public static final List<WebNode> NODES;

    static {
        List<WebNode> nodes = new ArrayList<>();

        int[][] regionIds = new int[6][TEMPLATE.length];
        for (Archetype archetype : Archetype.values()) {
            ActiveSkill[] actives = ActiveSkill.of(archetype);
            int smallIndex = 0;
            for (int t = 0; t < TEMPLATE.length; t++) {
                Slot slot = TEMPLATE[t];
                double angle = archetype.angle() + Math.toRadians(slot.angle);
                double x = Math.sin(angle) * slot.radius;
                double y = -Math.cos(angle) * slot.radius;
                int id = nodes.size();
                regionIds[archetype.ordinal()][t] = id;

                ActiveSkill active = null;
                WebNode node;
                Origin home = Origin.homeOf(archetype);
                if (t == 0 && home != null) {
                    node = new WebNode(id, WebNode.Kind.ORIGIN, archetype, home.label, x, y, null);
                    node.origin = home;
                } else if (t == HOME_GATE_SLOT && home != null) {
                    Specialization spec = Specialization.of(home, archetype);
                    node = new WebNode(id, WebNode.Kind.GATE, archetype, spec.label, x, y, null);
                    node.spec = spec;
                } else if (slot.kind == WebNode.Kind.ACTIVE) {
                    active = actives[indexOf(ACTIVE_SLOTS, t)];
                    node = new WebNode(id, slot.kind, archetype, active.label, x, y, active);
                } else if (slot.kind == WebNode.Kind.NOTABLE) {
                    node = notable(id, archetype, indexOf(NOTABLE_SLOTS, t), x, y);
                } else if (slot.kind == WebNode.Kind.KEYSTONE) {
                    node = keystone(id, archetype, x, y);
                } else if (t == LINEAGE_SLOT) {
                    node = lineage(id, archetype, x, y);
                } else if (t == HOME_GATE_SLOT) {
                    node = scaling(id, archetype, x, y);
                } else {
                    node = effect(id, archetype, smallIndex++, x, y);
                }
                nodes.add(node);
                for (int link : slot.linksTo) {
                    connect(nodes, id, regionIds[archetype.ordinal()][link]);
                }
            }
        }

        // Bridges between neighbouring regions. Each joins a pure region to a hybrid one, and is the gate to the
        // specialization the pure region's origin takes in that hybrid region.
        for (Archetype archetype : Archetype.values()) {
            Archetype next = archetype.next();
            int[] mine = regionIds[archetype.ordinal()];
            int[] theirs = regionIds[next.ordinal()];
            double angle = archetype.angle() + Math.toRadians(30);
            double radius = 4.7;
            int id = nodes.size();
            Origin origin = Origin.homeOf(archetype) != null ? Origin.homeOf(archetype) : Origin.homeOf(next);
            Archetype hybrid = archetype.hybrid() ? archetype : next;
            Specialization spec = Specialization.of(origin, hybrid);
            WebNode gate = new WebNode(id, WebNode.Kind.GATE, hybrid, spec.label, Math.sin(angle) * radius, -Math.cos(angle) * radius, null);
            gate.spec = spec;
            nodes.add(gate);
            connect(nodes, id, mine[9]);
            connect(nodes, id, theirs[8]);
            // Inner ring link too.
            connect(nodes, mine[3], theirs[2]);
        }

        NODES = Collections.unmodifiableList(nodes);
    }

    private PassiveWeb() {}

    public static WebNode get(int id) {
        return id >= 0 && id < NODES.size() ? NODES.get(id) : null;
    }

    /** An origin's start node. */
    public static int originNode(Origin origin) {
        for (WebNode n : NODES) {
            if (n.origin == origin) {
                return n.id;
            }
        }
        return -1;
    }

    /** In an origin's starter web (6.2.2): its home region, plus the gates out of it. */
    public static boolean inStarter(WebNode node, Origin origin) {
        return origin != null && (node.archetype == origin.home && node.kind != WebNode.Kind.GATE || node.spec != null && node.spec.origin == origin);
    }

    private static void connect(List<WebNode> nodes, int a, int b) {
        if (!nodes.get(a).links.contains(b)) {
            nodes.get(a).links.add(b);
            nodes.get(b).links.add(a);
        }
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Effect nodes: a small direct bonus themed to the region, alternating between two (6.2.6). */
    private static WebNode effect(int id, Archetype archetype, int index, double x, double y) {
        WebNode.Kind k = WebNode.Kind.SMALL;
        boolean a = index % 2 == 0;
        return switch (archetype) {
            case WARRIOR -> a ? new WebNode(id, k, archetype, "Brawn", x, y, null).stat(Stat.MELEE_DAMAGE, 4)
                    : new WebNode(id, k, archetype, "Thick Hide", x, y, null).stat(Stat.MAX_HEALTH, 3);
            case DUELIST -> a ? new WebNode(id, k, archetype, "Finesse", x, y, null).stat(Stat.ATTACK_SPEED, 3)
                    : new WebNode(id, k, archetype, "Keen Eye", x, y, null).stat(Stat.CRIT_CHANCE, 2);
            case RANGER -> a ? new WebNode(id, k, archetype, "Aim", x, y, null).stat(Stat.PROJECTILE_DAMAGE, 4)
                    : new WebNode(id, k, archetype, "Stride", x, y, null).stat(Stat.MOVE_SPEED, 2);
            case SHADOW -> a ? new WebNode(id, k, archetype, "Cruelty", x, y, null).stat(Stat.BLEED_DURATION, 10)
                    : new WebNode(id, k, archetype, "Soft Step", x, y, null).stat(Stat.SNEAK_SPEED, 5);
            case MAGE -> a ? new WebNode(id, k, archetype, "Focus", x, y, null).stat(Stat.SPELL_DAMAGE, 5)
                    : new WebNode(id, k, archetype, "Reserves", x, y, null).stat(Stat.MANA, 5);
            case TEMPLAR -> a ? new WebNode(id, k, archetype, "Fortitude", x, y, null).stat(Stat.MAX_HEALTH, 3)
                    : new WebNode(id, k, archetype, "Fervour", x, y, null).stat(Stat.SPELL_DAMAGE, 4);
        };
    }

    /** Lineage resistance (6.2.6, 9.3.2): elemental resistance points tied to the region. */
    private static WebNode lineage(int id, Archetype archetype, double x, double y) {
        WebNode.Kind k = WebNode.Kind.SMALL;
        return switch (archetype) {
            case WARRIOR -> new WebNode(id, k, archetype, "Forge-born", x, y, null).stat(Stat.FIRE_RES, 4);
            case DUELIST -> new WebNode(id, k, archetype, "Grounded", x, y, null).stat(Stat.LIGHTNING_RES, 4);
            case RANGER -> new WebNode(id, k, archetype, "Weathered", x, y, null).stat(Stat.FROST_RES, 4);
            case SHADOW -> new WebNode(id, k, archetype, "Antivenom", x, y, null).stat(Stat.POISON_RES, 4);
            case MAGE -> new WebNode(id, k, archetype, "Insulated", x, y, null).stat(Stat.LIGHTNING_RES, 4);
            case TEMPLAR -> new WebNode(id, k, archetype, "Hallowed Flame", x, y, null).stat(Stat.FIRE_RES, 4);
        };
    }

    /** Scaling nodes in the hybrid regions (6.2.6): a bonus that grows with an attribute. */
    private static WebNode scaling(int id, Archetype archetype, double x, double y) {
        WebNode.Kind k = WebNode.Kind.SMALL;
        return switch (archetype) {
            case DUELIST -> new WebNode(id, k, archetype, "Measured Strength", x, y, null).stat(Stat.MELEE_PER_STR, 1);
            case SHADOW -> new WebNode(id, k, archetype, "Practised Hand", x, y, null).stat(Stat.PROJECTILE_PER_AGI, 1);
            case TEMPLAR -> new WebNode(id, k, archetype, "Studied Faith", x, y, null).stat(Stat.SPELL_PER_INT, 1);
            default -> effect(id, archetype, 0, x, y);
        };
    }

    private static WebNode notable(int id, Archetype archetype, int index, double x, double y) {
        WebNode.Kind k = WebNode.Kind.NOTABLE;
        return switch (archetype) {
            case WARRIOR -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Iron Skin", x, y, null).stat(Stat.ARMOR, 6);
                case 1 -> new WebNode(id, k, archetype, "Bloodthirst", x, y, null).stat(Stat.LIFESTEAL, 5);
                case 2 -> new WebNode(id, k, archetype, "Heavy Hitter", x, y, null).stat(Stat.MELEE_DAMAGE, 15);
                default -> new WebNode(id, k, archetype, "Unbreakable", x, y, null).stat(Stat.KNOCKBACK_RES, 30).stat(Stat.HEALTH_REGEN, 0.3);
            };
            case DUELIST -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Precision", x, y, null).stat(Stat.CRIT_CHANCE, 10);
                case 1 -> new WebNode(id, k, archetype, "Footwork", x, y, null).stat(Stat.MOVE_SPEED, 5).stat(Stat.ATTACK_SPEED, 5);
                case 2 -> new WebNode(id, k, archetype, "Momentum", x, y, null).stat(Stat.RAGE_GAIN, 50).stat(Stat.STAMINA, 10);
                default -> new WebNode(id, k, archetype, "Duelist's Edge", x, y, null).stat(Stat.MELEE_DAMAGE, 10).stat(Stat.CRIT_CHANCE, 5).stat(Stat.CRIT_DAMAGE, 15);
            };
            case RANGER -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Fleet Foot", x, y, null).stat(Stat.MOVE_SPEED, 8);
                case 1 -> new WebNode(id, k, archetype, "Sharpshooter", x, y, null).stat(Stat.PROJECTILE_DAMAGE, 20);
                case 2 -> new WebNode(id, k, archetype, "Second Wind", x, y, null).stat(Stat.STAMINA, 20);
                default -> new WebNode(id, k, archetype, "Endurance", x, y, null).stat(Stat.STAMINA, 40);
            };
            case SHADOW -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Assassinate", x, y, null).stat(Stat.BACKSTAB, 40);
                case 1 -> new WebNode(id, k, archetype, "Venom", x, y, null).stat(Stat.VENOM, 3);
                case 2 -> new WebNode(id, k, archetype, "Elusive", x, y, null).stat(Stat.SNEAK_SPEED, 15).stat(Stat.MOVE_SPEED, 4);
                default -> new WebNode(id, k, archetype, "Shadow Arts", x, y, null).stat(Stat.SPELL_DAMAGE, 10).stat(Stat.COOLDOWN, 8);
            };
            case MAGE -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Arcane Mind", x, y, null).stat(Stat.MANA, 25);
                case 1 -> new WebNode(id, k, archetype, "Spell Power", x, y, null).stat(Stat.SPELL_DAMAGE, 20);
                case 2 -> new WebNode(id, k, archetype, "Meditation", x, y, null).stat(Stat.MANA_REGEN, 40);
                default -> new WebNode(id, k, archetype, "Quick Casting", x, y, null).stat(Stat.COOLDOWN, 12);
            };
            case TEMPLAR -> switch (index) {
                case 0 -> new WebNode(id, k, archetype, "Devotion", x, y, null).stat(Stat.HEALTH_REGEN, 0.5);
                case 1 -> new WebNode(id, k, archetype, "Sanctity", x, y, null).stat(Stat.MAX_HEALTH, 15);
                case 2 -> new WebNode(id, k, archetype, "Zeal", x, y, null).stat(Stat.SPELL_DAMAGE, 15).stat(Stat.MELEE_DAMAGE, 5);
                default -> new WebNode(id, k, archetype, "Faith", x, y, null).stat(Stat.MANA, 15).stat(Stat.ARMOR, 3);
            };
        };
    }

    private static WebNode keystone(int id, Archetype archetype, double x, double y) {
        WebNode.Kind k = WebNode.Kind.KEYSTONE;
        return switch (archetype) {
            case WARRIOR -> new WebNode(id, k, archetype, "Juggernaut", x, y, null)
                    .stat(Stat.MAX_HEALTH, 40).stat(Stat.KNOCKBACK_RES, 50).stat(Stat.MOVE_SPEED, -15);
            case DUELIST -> new WebNode(id, k, archetype, "Bladedancer", x, y, null)
                    .stat(Stat.ATTACK_SPEED, 30).stat(Stat.CRIT_CHANCE, 10).stat(Stat.CRIT_DAMAGE, 30).stat(Stat.ARMOR, -6);
            case RANGER -> new WebNode(id, k, archetype, "Wind Walker", x, y, null)
                    .stat(Stat.NO_FALL, 1).stat(Stat.MOVE_SPEED, 15).stat(Stat.MAX_HEALTH, -20);
            case SHADOW -> new WebNode(id, k, archetype, "Ghost", x, y, null)
                    .stat(Stat.GHOST, 1).stat(Stat.BACKSTAB, 30).stat(Stat.MAX_HEALTH, -20);
            case MAGE -> new WebNode(id, k, archetype, "Mind over Matter", x, y, null)
                    .stat(Stat.MIND_OVER_MATTER, 30).stat(Stat.MANA, 20);
            case TEMPLAR -> new WebNode(id, k, archetype, "Holy Aura", x, y, null)
                    .stat(Stat.HOLY_AURA, 1).stat(Stat.HEALTH_REGEN, 0.5).stat(Stat.MOVE_SPEED, -5);
        };
    }
}
