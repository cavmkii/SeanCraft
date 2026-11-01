package com.cavmkii.seancraft.boss;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.combat.Damage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A boss as data (design doc 8.4.3): its health, phases, resistances, attacks and lines, read from
 * {@code data/<namespace>/bosses/<id>.json}. Each attack names an action the boss's code knows how to perform.
 */
public record BossDef(String id, String name, int tier, float health, Map<Damage.Type, Float> resist, List<Phase> phases,
        List<Attack> attacks, Map<String, String> lines, Home home) {

    /**
     * Where the boss's dungeon goes (10): biome ids or #tags, whether it's underground (searched at deep-dark depth
     * instead of the surface), and which arena style to build.
     */
    public record Home(List<String> biomes, boolean underground, String style) {}

    /** A phase starts once health falls to {@code from} (a fraction). */
    public record Phase(String name, float from, float scale, float speed, int keepMin, int keepMax, boolean approach) {}

    /** One attack, with times in ticks (8.4.3). */
    public record Attack(String id, String name, List<Integer> phases, float minRange, float maxRange, int windup, int active,
            int recovery, int cooldown, int weight, String action, float damage, Map<Damage.Type, Float> split, JsonObject extra) {
        public float number(String key, float fallback) {
            return extra.has(key) ? extra.get(key).getAsFloat() : fallback;
        }

        public boolean flag(String key) {
            return extra.has(key) && extra.get(key).getAsBoolean();
        }
    }

    private static final Map<String, BossDef> DEFS = new HashMap<>();

    public static BossDef get(String id) {
        return DEFS.get(id);
    }

    public static java.util.Collection<BossDef> all() {
        return DEFS.values();
    }

    public String line(String key) {
        return lines.getOrDefault(key, "");
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return SeanCraft.id("bosses");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                DEFS.clear();
                for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("bosses", l -> l.getPath().endsWith(".json")).entrySet()) {
                    String path = e.getKey().getPath();
                    String id = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                    try (Reader reader = e.getValue().openAsReader()) {
                        DEFS.put(id, parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch (Exception ex) {
                        SeanCraft.LOG.error("Couldn't read boss {}: {}", e.getKey(), ex.toString());
                    }
                }
                SeanCraft.LOG.info("Loaded {} boss definitions", DEFS.size());
            }
        });
    }

    static BossDef parse(String id, JsonObject json) {
        List<Phase> phases = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("phases")) {
            JsonObject p = el.getAsJsonObject();
            JsonArray keep = p.has("keep") ? p.getAsJsonArray("keep") : null;
            phases.add(new Phase(p.get("name").getAsString(), p.get("from").getAsFloat(),
                    p.has("scale") ? p.get("scale").getAsFloat() : 1, p.has("speed") ? p.get("speed").getAsFloat() : 1,
                    keep != null ? keep.get(0).getAsInt() : 0, keep != null ? keep.get(1).getAsInt() : 0,
                    p.has("approach") && p.get("approach").getAsBoolean()));
        }
        List<Attack> attacks = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("attacks")) {
            JsonObject a = el.getAsJsonObject();
            List<Integer> on = new ArrayList<>();
            a.getAsJsonArray("phases").forEach(x -> on.add(x.getAsInt()));
            JsonArray range = a.getAsJsonArray("range");
            attacks.add(new Attack(a.get("id").getAsString(), a.get("name").getAsString(), on, range.get(0).getAsFloat(),
                    range.get(1).getAsFloat(), seconds(a, "windup"), seconds(a, "active"), seconds(a, "recovery"), seconds(a, "cooldown"),
                    a.has("weight") ? a.get("weight").getAsInt() : 1, a.get("action").getAsString(),
                    a.has("damage") ? a.get("damage").getAsFloat() : 0, types(a.getAsJsonObject("split")), a));
        }
        Map<String, String> lines = new HashMap<>();
        if (json.has("lines")) {
            json.getAsJsonObject("lines").entrySet().forEach(e -> lines.put(e.getKey(), e.getValue().getAsString()));
        }
        Home home = null;
        if (json.has("home")) {
            JsonObject h = json.getAsJsonObject("home");
            List<String> biomes = new ArrayList<>();
            h.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
            home = new Home(biomes, h.has("underground") && h.get("underground").getAsBoolean(), h.get("style").getAsString());
        }
        return new BossDef(id, json.get("name").getAsString(), json.get("tier").getAsInt(), json.get("health").getAsFloat(),
                types(json.getAsJsonObject("resist")), phases, attacks, lines, home);
    }

    private static int seconds(JsonObject a, String key) {
        return a.has(key) ? Math.round(a.get(key).getAsFloat() * 20) : 0;
    }

    private static Map<Damage.Type, Float> types(JsonObject json) {
        Map<Damage.Type, Float> map = new EnumMap<>(Damage.Type.class);
        if (json != null) {
            json.entrySet().forEach(e -> map.put(Damage.Type.valueOf(e.getKey().toUpperCase(Locale.ROOT)), e.getValue().getAsFloat()));
        }
        return map;
    }
}
