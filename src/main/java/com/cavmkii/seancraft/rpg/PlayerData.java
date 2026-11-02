package com.cavmkii.seancraft.rpg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A player's character (design doc 6): origin, level and class xp, use-skill xp, attribute points, allocated web nodes
 * and skill ranks, specialization, the loadout, points lost to respecs, and current resources. Saved with the player and
 * kept through death.
 */
public final class PlayerData {
    public static final int MAX_LEVEL = 60;
    /** Three basic skills on Z, X and C, and the ultimate on V (6.5.2). */
    public static final int SLOTS = 4;
    public static final int ULTIMATE_SLOT = 3;
    /** Attribute points each level grants, split by use (6.4). */
    public static final int ATTR_PER_LEVEL = 2;
    public static final int MAX_RANK = 4;
    /** The web's layout version; characters from an older layout get their points back once. */
    static final int WEB_VERSION = 2;

    public Origin origin;
    public int level = 1;
    public long xp;
    public final Map<Skill, Double> skillXp = new EnumMap<>(Skill.class);
    public final Set<Integer> nodes = new LinkedHashSet<>();
    /** Ranks above the first, by active skill name (6.5.2). */
    public final Map<String, Integer> ranks = new HashMap<>();
    public final String[] slots = new String[SLOTS];
    /** Attribute points earned from levels (6.4), on top of the origin's starting points. */
    public final Map<Attr, Double> attrPoints = new EnumMap<>(Attr.class);
    /** Skill progress earned this level by attribute, which decides where the next level's points go. */
    public final Map<Attr, Double> levelProgress = new EnumMap<>(Attr.class);
    /** Attribute points waiting to be placed after a respec (6.6). */
    public int unassigned;
    public Specialization spec;
    /**
     * Milestones reached (6.2.5): 0 none, 1 specialization, 2 mastery (level 40 and a tier 2 trophy), 3 capstone (level 60
     * and a tier 3 trophy). Worked out on the server, which knows the trophies; it sets the ultimate's rank and which
     * signature nodes can be taken.
     */
    public int milestone;
    /** The whole web is shown once a specialization has been chosen (6.2.3); a respec keeps it. */
    public boolean revealed;
    int webVersion = WEB_VERSION;
    public int lostPoints;
    /** Recipe ladder progress and unlocks (4.7, see {@link Ladders}). */
    public final Map<String, Double> ladder = new HashMap<>();
    public float mana;
    public float rage;
    public float stamina;

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("origin", "").forGetter(d -> d.origin == null ? "" : d.origin.name()),
            Codec.STRING.optionalFieldOf("spec", "").forGetter(d -> d.spec == null ? "" : d.spec.name()),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("attrs", Map.of()).forGetter(d -> attrMap(d.attrPoints)),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("progress", Map.of()).forGetter(d -> attrMap(d.levelProgress)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(d -> d.ranks),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(d -> (d.revealed ? 1 : 0) | d.webVersion << 1),
            Codec.INT.optionalFieldOf("unassigned", 0).forGetter(d -> d.unassigned),
            Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(d -> d.xp),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("skills", Map.of()).forGetter(PlayerData::skillMap),
            Codec.INT.listOf().optionalFieldOf("nodes", List.of()).forGetter(d -> new ArrayList<>(d.nodes)),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(d -> slotList(d.slots)),
            Codec.INT.optionalFieldOf("lostPoints", 0).forGetter(d -> d.lostPoints),
            Codec.FLOAT.optionalFieldOf("mana", 0f).forGetter(d -> d.mana),
            Codec.FLOAT.optionalFieldOf("stamina", 0f).forGetter(d -> d.stamina),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("ladder", Map.of()).forGetter(d -> d.ladder)
    ).apply(i, PlayerData::fromSaved));

    private static PlayerData fromSaved(String origin, String spec, Map<String, Double> attrs, Map<String, Double> progress,
            Map<String, Integer> ranks, int flags, int unassigned, int level, long xp, Map<String, Double> skills, List<Integer> nodes,
            List<String> slots, int lostPoints, float mana, float stamina, Map<String, Double> ladder) {
        PlayerData d = new PlayerData();
        d.origin = parse(Origin.class, origin);
        d.spec = parse(Specialization.class, spec);
        readAttrs(attrs, d.attrPoints);
        readAttrs(progress, d.levelProgress);
        d.ranks.putAll(ranks);
        d.revealed = (flags & 1) != 0;
        d.webVersion = flags >> 1;
        d.unassigned = unassigned;
        d.level = Math.max(1, Math.min(MAX_LEVEL, level));
        d.xp = xp;
        skills.forEach((name, value) -> {
            try {
                d.skillXp.put(Skill.valueOf(name), value);
            } catch (IllegalArgumentException ignored) {
                // A skill that no longer exists.
            }
        });
        d.nodes.addAll(nodes);
        for (int s = 0; s < SLOTS && s < slots.size(); s++) {
            d.slots[s] = slots.get(s).isEmpty() ? null : slots.get(s);
        }
        d.lostPoints = lostPoints;
        d.mana = mana;
        d.stamina = stamina;
        d.ladder.putAll(ladder);
        d.migrate();
        return d;
    }

    /**
     * Characters from before origins and the attribute budget: the old web's nodes don't fit the new one, so its points
     * come back; and the levels already gained get their attribute points, split by the skills trained so far.
     */
    private void migrate() {
        if (webVersion < WEB_VERSION) {
            nodes.clear();
            ranks.clear();
            java.util.Arrays.fill(slots, null);
            lostPoints = 0;
            spec = null;
            revealed = false;
            if (attrPoints.isEmpty() && level > 1) {
                Map<Attr, Double> share = new EnumMap<>(Attr.class);
                for (Skill skill : Skill.values()) {
                    if (skill.attr != null) {
                        share.merge(skill.attr, skillXp.getOrDefault(skill, 0.0), Double::sum);
                    }
                }
                split(share, ATTR_PER_LEVEL * (level - 1.0), null);
            }
            webVersion = WEB_VERSION;
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return name.isEmpty() ? null : Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Double> attrMap(Map<Attr, Double> map) {
        Map<String, Double> out = new HashMap<>();
        map.forEach((a, v) -> out.put(a.name(), v));
        return out;
    }

    private static void readAttrs(Map<String, Double> in, Map<Attr, Double> out) {
        in.forEach((name, v) -> {
            Attr a = parse(Attr.class, name);
            if (a != null) {
                out.put(a, v);
            }
        });
    }

    /**
     * Adds {@code points} attribute points, divided by the shares given (6.4). With no shares, they go to the origin's
     * attribute, or evenly if there's no origin yet.
     */
    void split(Map<Attr, Double> shares, double points, Origin fallback) {
        double total = shares.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Attr a : Attr.values()) {
            double part = total > 0 ? points * shares.getOrDefault(a, 0.0) / total
                    : fallback != null ? (a == fallback.attr ? points : 0) : points / Attr.values().length;
            if (part > 0) {
                attrPoints.merge(a, part, Double::sum);
            }
        }
    }

    /** An attribute's value: the origin's starting points plus what levels have given it (6.4). The web grants none. */
    public double attribute(Attr attr) {
        return (origin != null && origin.attr == attr ? Origin.START_POINTS : 0) + attrPoints.getOrDefault(attr, 0.0);
    }

    /** A learned active skill's rank, 1-4, or 0 if it isn't learned. */
    public int rank(ActiveSkill skill) {
        return hasActive(skill) ? 1 + ranks.getOrDefault(skill.name(), 0) : 0;
    }

    private Map<String, Double> skillMap() {
        Map<String, Double> map = new HashMap<>();
        skillXp.forEach((skill, value) -> map.put(skill.name(), value));
        return map;
    }

    private static List<String> slotList(String[] slots) {
        List<String> list = new ArrayList<>();
        for (String s : slots) {
            list.add(s == null ? "" : s);
        }
        return list;
    }

    // ------------------------------------------------------------------ levels and points

    /** Class xp needed to go from a level to the next. */
    public static long xpToNext(int level) {
        return (long) (40 + 20 * Math.pow(level, 1.4));
    }

    public int skillLevel(Skill skill) {
        return Skill.levelForXp(skillXp.getOrDefault(skill, 0.0));
    }

    public int totalPoints() {
        return level;
    }

    /** Web points spent: nodes (gates included) and skill ranks above the first. */
    public int spentPoints() {
        return nodes.size() + ranks.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int freePoints() {
        return Math.max(0, totalPoints() - lostPoints - spentPoints());
    }

    /** Whether a slot can hold a skill: the basic three always; the ultimate slot once you have a specialization. */
    public boolean slotOpen(int slot) {
        return slot >= 0 && slot < SLOTS && (slot != ULTIMATE_SLOT || spec != null);
    }

    public boolean isAllocated(int node) {
        return nodes.contains(node) || origin != null && node == PassiveWeb.originNode(origin);
    }

    /**
     * A node can be taken if it's free, you have a point, and it links to something you already have. Before the web
     * opens, only your starter web; a gate needs level 20 and no specialization yet, and only your own origin's gates.
     * (The server also checks for a tier 1 trophy and that you're in a hideout.)
     */
    public boolean canAllocate(int id) {
        WebNode node = PassiveWeb.get(id);
        if (node == null || origin == null || isAllocated(id) || freePoints() <= 0) {
            return false;
        }
        if (node.kind == WebNode.Kind.SIGNATURE) {
            if (node.sigSpec != spec || node.sigTier > milestone) {
                return false;
            }
        } else if (node.kind == WebNode.Kind.GATE) {
            if (spec != null || node.spec.origin != origin || level < Specialization.LEVEL) {
                return false;
            }
        } else if (!revealed && !PassiveWeb.inStarter(node, origin)) {
            return false;
        }
        for (int link : node.links) {
            if (isAllocated(link)) {
                return true;
            }
        }
        return false;
    }

    /** A learned skill can take another rank for a point, up to 4 (6.5.2). */
    public boolean canRankUp(int id) {
        WebNode node = PassiveWeb.get(id);
        return node != null && node.active != null && isAllocated(id) && rank(node.active) < MAX_RANK && freePoints() > 0;
    }

    public boolean hasActive(ActiveSkill skill) {
        for (int id : nodes) {
            WebNode node = PassiveWeb.get(id);
            if (node != null && node.active == skill) {
                return true;
            }
        }
        return false;
    }

    public ActiveSkill slot(int index) {
        return index >= 0 && index < SLOTS && slots[index] != null ? ActiveSkill.byName(slots[index]) : null;
    }

    // ------------------------------------------------------------------ network

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(origin == null ? -1 : origin.ordinal());
        buf.writeVarInt(spec == null ? -1 : spec.ordinal());
        buf.writeBoolean(revealed);
        buf.writeVarInt(milestone);
        buf.writeVarInt(unassigned);
        for (Attr a : Attr.values()) {
            buf.writeDouble(attrPoints.getOrDefault(a, 0.0));
            buf.writeDouble(levelProgress.getOrDefault(a, 0.0));
        }
        buf.writeVarInt(ranks.size());
        ranks.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeVarInt(v);
        });
        buf.writeVarInt(level);
        buf.writeVarLong(xp);
        for (Skill skill : Skill.values()) {
            buf.writeDouble(skillXp.getOrDefault(skill, 0.0));
        }
        buf.writeVarInt(nodes.size());
        for (int id : nodes) {
            buf.writeVarInt(id);
        }
        for (String s : slots) {
            buf.writeUtf(s == null ? "" : s);
        }
        buf.writeVarInt(lostPoints);
        buf.writeVarInt(ladder.size());
        ladder.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeDouble(v);
        });
    }

    public static PlayerData read(FriendlyByteBuf buf) {
        PlayerData d = new PlayerData();
        int o = buf.readVarInt();
        d.origin = o < 0 ? null : Origin.values()[o];
        int sp = buf.readVarInt();
        d.spec = sp < 0 ? null : Specialization.values()[sp];
        d.revealed = buf.readBoolean();
        d.milestone = buf.readVarInt();
        d.unassigned = buf.readVarInt();
        for (Attr a : Attr.values()) {
            d.attrPoints.put(a, buf.readDouble());
            d.levelProgress.put(a, buf.readDouble());
        }
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            d.ranks.put(buf.readUtf(), buf.readVarInt());
        }
        d.level = buf.readVarInt();
        d.xp = buf.readVarLong();
        for (Skill skill : Skill.values()) {
            d.skillXp.put(skill, buf.readDouble());
        }
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            d.nodes.add(buf.readVarInt());
        }
        for (int s = 0; s < SLOTS; s++) {
            String name = buf.readUtf();
            d.slots[s] = name.isEmpty() ? null : name;
        }
        d.lostPoints = buf.readVarInt();
        int ladders = buf.readVarInt();
        for (int i = 0; i < ladders; i++) {
            d.ladder.put(buf.readUtf(), buf.readDouble());
        }
        return d;
    }
}
