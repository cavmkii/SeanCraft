package com.cavmkii.seancraft.rpg;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One node of the passive web. Positions are in web units; the screen scales them. */
public final class WebNode {
    /** Node types (design doc 6.2.6): an origin's start, effect nodes, notables, skills, keystones and specialization gates. */
    public enum Kind { ORIGIN, SMALL, NOTABLE, ACTIVE, KEYSTONE, GATE, SIGNATURE }

    public final int id;
    public final Kind kind;
    public final Archetype archetype;
    public final String name;
    public final double x;
    public final double y;
    public final Map<Stat, Double> stats = new EnumMap<>(Stat.class);
    public final ActiveSkill active;
    public final List<Integer> links = new ArrayList<>();
    /** For a gate, the specialization it opens. */
    public Specialization spec;
    /** For an origin node, whose start it is. */
    public Origin origin;
    /** For a signature node: whose it is, and which milestone opens it (1 specialization, 2 mastery, 3 capstone). */
    public Specialization sigSpec;
    public int sigTier;

    WebNode(int id, Kind kind, Archetype archetype, String name, double x, double y, ActiveSkill active) {
        this.id = id;
        this.kind = kind;
        this.archetype = archetype;
        this.name = name;
        this.x = x;
        this.y = y;
        this.active = active;
    }

    WebNode stat(Stat stat, double value) {
        stats.merge(stat, value, Double::sum);
        return this;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        if (sigSpec != null) {
            lines.add(sigSpec.label + " " + (sigTier == 1 ? "signature" : sigTier == 2 ? "mastery" : "capstone")
                    + (sigTier == 1 ? " (opens with the specialization)" : sigTier == 2 ? " (level 40 and a tier 2 trophy)" : " (level 60 and a tier 3 trophy)"));
        }
        if (spec != null) {
            lines.add(spec.role + ".");
            lines.add("Signature: " + spec.signature + ".");
        }
        if (active != null) {
            lines.add("Active: " + active.description);
            String pools = active.alt == null ? active.resource.label : active.resource.label + " or " + active.alt.label.toLowerCase()
                    + " (whichever is fuller)";
            lines.add("Costs " + active.cost + " " + pools + ", " + active.cooldownTicks / 20.0 + " s cooldown");
            lines.add("Ranks 2/3/4: " + String.join(" / ", active.ranks));
        }
        stats.forEach((stat, value) -> lines.add(stat.describe(value)));
        return lines;
    }
}
