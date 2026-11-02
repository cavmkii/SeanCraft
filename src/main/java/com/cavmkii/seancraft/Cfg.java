package com.cavmkii.seancraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Settings, read from config/seancraft/seancraft.properties (written with defaults and comments on
 * first run). Everything runs on the server, so only the server's file matters; clients need no config.
 */
public final class Cfg {
    public enum ScalingMode { SKILLS, BOSSES, HIGHEST }

    // General
    public static boolean joeEnabled = true;
    public static boolean seanEnabled = true;
    public static double joeRespawnMinutes = 10;
    public static double seanRespawnMinutes = 20;
    public static double stayMinutesMin = 20;
    public static double stayMinutesMax = 40;
    public static double awayMinutesMin = 30;
    public static double awayMinutesMax = 60;
    public static double spawnDistanceMin = 60;
    public static double spawnDistanceMax = 140;
    public static double strandedMinutes = 5;

    // Joe
    public static double provokeSeconds = 90;
    public static String ragePhrases = "babywars,baby wars";
    public static double rageSeconds = 120;
    public static boolean stealEnabled = true;
    public static double stealCooldownSeconds = 180;
    public static int stealMaxStack = 10;
    public static double tauntIntervalMin = 18;
    public static double tauntIntervalMax = 40;
    public static double seanSeekChance = 0.5;
    public static double joeScale = 0.8;
    public static boolean pukeEnabled = true;
    public static double pukeIntervalMin = 60;
    public static double pukeIntervalMax = 150;
    public static double restrainingDistance = 15;

    // Scaling
    public static ScalingMode scaling = ScalingMode.HIGHEST;
    public static int skillsMaxLevel = 50;
    public static String bossAdvancements = String.join(",",
            "minecraft:story/smelt_iron",
            "minecraft:story/enter_the_nether",
            "minecraft:nether/obtain_blaze_rod",
            "minecraft:story/follow_ender_eye",
            "minecraft:end/kill_dragon",
            "minecraft:nether/create_beacon",
            "minecraft:nether/netherite_armor");
    public static double healthMultMin = 0.6;
    public static double healthMultMax = 10;
    public static double damageMultMin = 0.25;
    public static double damageMultMax = 2.5;

    // Sean
    public static double seanHealth = 1000;
    public static double seanNonPlayerDamageTaken = 0.1;

    // Fun
    public static boolean snowLineEnabled = true;
    public static String snowLine = "awe man the sky is cumming!";
    public static double snowLineCooldownMinutes = 10;

    // Baby Wars
    public static boolean warEnabled = true;
    public static int warTriggerCount = 3;
    public static double warTriggerWindowMinutes = 60;
    public static int warWaves = 4;
    public static int warWaveBaseSize = 6;
    public static int warWaveGrowth = 3;
    public static double warWaveIntervalSeconds = 75;
    public static double warMaxMinutes = 15;
    public static double warCooldownMinutes = 30;
    public static int warSecurityCount = 3;
    public static double babyHealthScale = 0.35;
    public static double babyDamageScale = 0.5;

    // Raid
    public enum DeathRule { VANILLA, KEEP_SECURE, LOSE_EVERYTHING }
    public static DeathRule raidDeathRule = DeathRule.LOSE_EVERYTHING;
    public static double hideoutRadius = 30;
    public static boolean raidHostility = true;
    public static boolean huntingParties = true;
    /** Raid minute each threat level (1-4) starts at. */
    public static double[] threatMinutes = {5, 20, 40, 60};
    public static double sleepHealPercent = 50;
    public static boolean distanceScaling = true;
    public static double hideoutSpacing = 500;
    public static int maxHideouts = 3;
    public static double fobPauseMinutes = 15;
    public static int spawnPoints = 3;
    /** Far enough that the 500-block hideout spacing around each spawn point leaves room to build between them (2.4). */
    public static double spawnDistance = 1000;
    public static double spawnRadius = 24;
    public static double spawnRefillMinutes = 30;

    // Fight
    public static double fightTriggerRange = 35;
    public static double fightSeconds = 45;
    public static double fightCooldownMinutes = 10;
    public static double bystanderDamageMultiplier = 0.5;

    private record Entry(String section, String key, String comment, Consumer<String> setter, java.util.function.Supplier<String> getter) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        b("General", "JoeEnabled", "Spawn and maintain Joe in the world.", v -> joeEnabled = v, () -> joeEnabled);
        b("General", "SeanEnabled", "Spawn and maintain Sean in the world.", v -> seanEnabled = v, () -> seanEnabled);
        d("General", "JoeRespawnMinutes", "Minutes after Joe dies before he respawns.", v -> joeRespawnMinutes = v, () -> joeRespawnMinutes);
        d("General", "SeanRespawnMinutes", "Minutes after Sean dies before he respawns.", v -> seanRespawnMinutes = v, () -> seanRespawnMinutes);
        d("General", "StayMinutesMin", "Joe and Sean come and go: each visit lasts between StayMinutesMin and StayMinutesMax.", v -> stayMinutesMin = v, () -> stayMinutesMin);
        d("General", "StayMinutesMax", "See StayMinutesMin.", v -> stayMinutesMax = v, () -> stayMinutesMax);
        d("General", "AwayMinutesMin", "After leaving, they're gone between AwayMinutesMin and AwayMinutesMax. 0 and 0 keeps them around all the time.", v -> awayMinutesMin = v, () -> awayMinutesMin);
        d("General", "AwayMinutesMax", "See AwayMinutesMin.", v -> awayMinutesMax = v, () -> awayMinutesMax);
        d("General", "SpawnDistanceMin", "Minimum distance (blocks) from a player that Joe/Sean (re)spawn at.", v -> spawnDistanceMin = v, () -> spawnDistanceMin);
        d("General", "SpawnDistanceMax", "Maximum distance (blocks) from a player that Joe/Sean (re)spawn at. Spots outside loaded chunks are skipped.", v -> spawnDistanceMax = v, () -> spawnDistanceMax);
        d("General", "StrandedMinutes", "If no player has been within 128 blocks of Joe or Sean for this long, they turn up near a random player again.", v -> strandedMinutes = v, () -> strandedMinutes);

        d("Joe", "ProvokeSeconds", "How long Joe stays hostile to a player after that player hits him.", v -> provokeSeconds = v, () -> provokeSeconds);
        s("Joe", "RagePhrases", "Comma-separated chat phrases (case and punctuation ignored) that send Joe into a rage against everyone.", v -> ragePhrases = v, () -> ragePhrases);
        d("Joe", "RageSeconds", "How long a rage phrase keeps Joe hostile to all players.", v -> rageSeconds = v, () -> rageSeconds);
        b("Joe", "StealEnabled", "Whether Joe pickpockets players.", v -> stealEnabled = v, () -> stealEnabled);
        d("Joe", "StealCooldownSeconds", "Minimum seconds between thefts.", v -> stealCooldownSeconds = v, () -> stealCooldownSeconds);
        i("Joe", "StealMaxStack", "Most items Joe takes from a single stack per theft.", v -> stealMaxStack = v, () -> stealMaxStack);
        d("Joe", "TauntIntervalMin", "Minimum seconds between Joe's insults.", v -> tauntIntervalMin = v, () -> tauntIntervalMin);
        d("Joe", "TauntIntervalMax", "Maximum seconds between Joe's insults.", v -> tauntIntervalMax = v, () -> tauntIntervalMax);
        d("Joe", "Scale", "Joe's size relative to a normal-sized mob (0.3-3).", v -> joeScale = v, () -> joeScale);
        b("Joe", "PukeEnabled", "Joe occasionally throws up when players are nearby.", v -> pukeEnabled = v, () -> pukeEnabled);
        d("Joe", "PukeIntervalMin", "Minimum seconds between Joe's vomiting fits.", v -> pukeIntervalMin = v, () -> pukeIntervalMin);
        d("Joe", "PukeIntervalMax", "Maximum seconds between Joe's vomiting fits.", v -> pukeIntervalMax = v, () -> pukeIntervalMax);
        d("Joe", "SeanSeekChance", "Chance (0-1) that Joe's next wander target is Sean's last known position.", v -> seanSeekChance = v, () -> seanSeekChance);
        d("Joe", "RestrainingOrderDistance", "How far (blocks) Joe must stay from anyone carrying the Restraining Order.", v -> restrainingDistance = v, () -> restrainingDistance);

        s("Scaling", "Mode", "What 'player level' means for Joe's scaling. SKILLS = highest XP level among players within 80 blocks. BOSSES = progression advancements earned by any online player. HIGHEST = whichever is greater.",
                v -> scaling = ScalingMode.valueOf(v.trim().toUpperCase(Locale.ROOT)), () -> scaling.name());
        i("Scaling", "SkillsMaxLevel", "XP level that counts as maximum for SKILLS scaling.", v -> skillsMaxLevel = v, () -> skillsMaxLevel);
        s("Scaling", "BossAdvancements", "Comma-separated advancements that count as boss progress in BOSSES scaling.", v -> bossAdvancements = v, () -> bossAdvancements);
        d("Scaling", "HealthMultiplierMin", "Joe's health multiplier at level 0.", v -> healthMultMin = v, () -> healthMultMin);
        d("Scaling", "HealthMultiplierMax", "Joe's health multiplier at max level.", v -> healthMultMax = v, () -> healthMultMax);
        d("Scaling", "DamageMultiplierMin", "Joe's damage multiplier at level 0.", v -> damageMultMin = v, () -> damageMultMin);
        d("Scaling", "DamageMultiplierMax", "Joe's damage multiplier at max level.", v -> damageMultMax = v, () -> damageMultMax);

        d("Sean", "Health", "Sean's max health (Minecraft caps max health at 1024). Applied when he spawns.", v -> seanHealth = v, () -> seanHealth);
        d("Sean", "NonPlayerDamageTaken", "Multiplier on damage Sean takes from ordinary mobs, so zombies don't keep killing the shop.", v -> seanNonPlayerDamageTaken = v, () -> seanNonPlayerDamageTaken);

        b("Fun", "SnowLineEnabled", "Your character says SnowLine in chat when it starts snowing where you are.", v -> snowLineEnabled = v, () -> snowLineEnabled);
        s("Fun", "SnowLine", "What your character says when it starts snowing.", v -> snowLine = v, () -> snowLine);
        d("Fun", "SnowLineCooldownMinutes", "Minimum minutes between snow lines.", v -> snowLineCooldownMinutes = v, () -> snowLineCooldownMinutes);

        b("BabyWars", "Enabled", "Saying the rage phrase enough times starts the Baby Wars.", v -> warEnabled = v, () -> warEnabled);
        i("BabyWars", "TriggerCount", "How many times 'baby wars' must be said (by anyone) within the window to start the war.", v -> warTriggerCount = v, () -> warTriggerCount);
        d("BabyWars", "TriggerWindowMinutes", "The window for TriggerCount, in minutes.", v -> warTriggerWindowMinutes = v, () -> warTriggerWindowMinutes);
        i("BabyWars", "Waves", "Number of baby waves.", v -> warWaves = v, () -> warWaves);
        i("BabyWars", "WaveBaseSize", "Babies in the first wave.", v -> warWaveBaseSize = v, () -> warWaveBaseSize);
        i("BabyWars", "WaveGrowth", "Extra babies per later wave.", v -> warWaveGrowth = v, () -> warWaveGrowth);
        d("BabyWars", "WaveIntervalSeconds", "Next wave comes after this long, or sooner once most of the current wave is dead.", v -> warWaveIntervalSeconds = v, () -> warWaveIntervalSeconds);
        d("BabyWars", "MaxMinutes", "The war ends after this long no matter what.", v -> warMaxMinutes = v, () -> warMaxMinutes);
        d("BabyWars", "CooldownMinutes", "Minutes before another war can start.", v -> warCooldownMinutes = v, () -> warCooldownMinutes);
        i("BabyWars", "SecurityCount", "How many of Sean's Security guards show up.", v -> warSecurityCount = v, () -> warSecurityCount);
        d("BabyWars", "BabyHealthScale", "Baby health relative to Joe's scaled health.", v -> babyHealthScale = v, () -> babyHealthScale);
        d("BabyWars", "BabyDamageScale", "Baby damage relative to Joe's scaled damage.", v -> babyDamageScale = v, () -> babyDamageScale);

        s("Raid", "DeathRule", "What happens to your inventory when you die outside a hideout. VANILLA: normal drops. KEEP_SECURE: normal drops, but the secure pouch stays with you. LOSE_EVERYTHING: no drops; everything outside the secure pouch is gone.",
                v -> raidDeathRule = DeathRule.valueOf(v.trim().toUpperCase(Locale.ROOT)), () -> raidDeathRule.name());
        d("Raid", "SleepHealPercent", "Sleeping through the night heals each body part that isn't destroyed by this percent of its health.", v -> sleepHealPercent = v, () -> sleepHealPercent);
        d("Raid", "HideoutRadius", "Radius (blocks) of a Hideout Bench's safe zone before Perimeter upgrades.", v -> hideoutRadius = v, () -> hideoutRadius);
        b("Raid", "Hostility", "Threat rises the longer you stay out: elite mobs and hunting parties.", v -> raidHostility = v, () -> raidHostility);
        b("Raid", "HuntingParties", "Send hunting parties at raiding players as threat rises.", v -> huntingParties = v, () -> huntingParties);
        b("Raid", "DistanceScaling", "Hostile mobs get more health, damage and XP the further from world spawn they appear (Heartland, Wilds, Frontier, Far lands).",
                v -> distanceScaling = v, () -> distanceScaling);
        d("Raid", "HideoutSpacing", "A Hideout Bench can't be placed within this many blocks of another hideout or a spawn point.", v -> hideoutSpacing = v, () -> hideoutSpacing);
        i("Raid", "MaxHideouts", "How many Hideout Benches each player can have placed at once.", v -> maxHideouts = v, () -> maxHideouts);
        d("Raid", "FobPauseMinutes", "Minutes of paused raid time each raid gets from FOB tables, shared between all of them. Refills when you extract.",
                v -> fobPauseMinutes = v, () -> fobPauseMinutes);
        i("Spawn", "Points", "Fixed spawn points (with a supply shelter) built in a new world. 0 turns them off. Only read when the world is first started.", v -> spawnPoints = v, () -> spawnPoints);
        d("Spawn", "Distance", "How far the other spawn points are from world spawn (blocks).", v -> spawnDistance = v, () -> spawnDistance);
        d("Spawn", "Radius", "Safe radius around each spawn point: no raid starts until you leave it.", v -> spawnRadius = v, () -> spawnRadius);
        d("Spawn", "RefillMinutes", "Empty spawn chests are restocked after this long.", v -> spawnRefillMinutes = v, () -> spawnRefillMinutes);
        s("Raid", "ThreatMinutes", "Raid minute each threat level starts at, levels 1 to 4, comma-separated.",
                v -> threatMinutes = parseThreat(v), () -> java.util.Arrays.stream(threatMinutes)
                        .mapToObj(m -> m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m))
                        .collect(java.util.stream.Collectors.joining(",")));

        d("Fight", "TriggerRange", "Joe and Sean start fighting when this close (blocks).", v -> fightTriggerRange = v, () -> fightTriggerRange);
        d("Fight", "DurationSeconds", "Length of a fight.", v -> fightSeconds = v, () -> fightSeconds);
        d("Fight", "CooldownMinutes", "Minutes before they can fight again.", v -> fightCooldownMinutes = v, () -> fightCooldownMinutes);
        d("Fight", "BystanderDamageMultiplier", "Multiplier on spell damage to anyone other than Joe and Sean caught in the blast. 0 disables.", v -> bystanderDamageMultiplier = v, () -> bystanderDamageMultiplier);
    }

    private Cfg() {}

    /** Four ascending minutes; anything malformed keeps the defaults. */
    private static double[] parseThreat(String v) {
        String[] parts = v.split(",");
        if (parts.length != 4) {
            return threatMinutes;
        }
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            out[i] = Math.max(0, Double.parseDouble(parts[i].trim()));
            if (i > 0 && out[i] < out[i - 1]) {
                return threatMinutes;
            }
        }
        return out;
    }

    public static Path dir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("seancraft");
    }

    private static Path file() {
        return dir().resolve("seancraft.properties");
    }

    /** Reads the file (keeping defaults for missing or bad values), then rewrites it so new keys show up. */
    public static String load() {
        Path path = file();
        List<String> problems = new ArrayList<>();
        try {
            Properties props = new Properties();
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            }
            for (Entry entry : ENTRIES) {
                String raw = props.getProperty(entry.section + "." + entry.key);
                if (raw == null) {
                    continue;
                }
                try {
                    entry.setter.accept(raw.trim());
                } catch (RuntimeException e) {
                    problems.add(entry.section + "." + entry.key + "=" + raw);
                }
            }
            if (spawnDistance == 400) {
                // The old default, from before spawn points moved out to make room for hideouts between them.
                spawnDistance = 1000;
            }
            write(path);
        } catch (IOException e) {
            return "Couldn't read " + path + ", using defaults: " + e.getMessage();
        }
        return problems.isEmpty() ? "Loaded " + path : "Loaded " + path + "; ignored bad values: " + String.join(", ", problems);
    }

    private static void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Map<String, List<Entry>> bySection = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            bySection.computeIfAbsent(entry.section, k -> new ArrayList<>()).add(entry);
        }
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# SeanCraft settings. Server-side only. Reload in-game with /seancraft reload\n");
            for (Map.Entry<String, List<Entry>> section : bySection.entrySet()) {
                w.write("\n# ---- " + section.getKey() + "\n");
                for (Entry entry : section.getValue()) {
                    w.write("# " + entry.comment + "\n");
                    w.write(entry.section + "." + entry.key + "=" + escape(entry.getter.get()) + "\n");
                }
            }
        }
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '=' || c == ':' || c == '#' || c == '!') {
                sb.append('\\');
            }
            sb.append(c);
        }
        if (sb.length() > 0 && sb.charAt(0) == ' ') {
            sb.insert(0, '\\');
        }
        return sb.toString();
    }

    private static void b(String section, String key, String comment, Consumer<Boolean> set, java.util.function.Supplier<Boolean> get) {
        ENTRIES.add(new Entry(section, key, comment, v -> {
            if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(v);
            }
            set.accept(Boolean.parseBoolean(v));
        }, () -> String.valueOf(get.get())));
    }

    private static void d(String section, String key, String comment, Consumer<Double> set, java.util.function.Supplier<Double> get) {
        ENTRIES.add(new Entry(section, key, comment, v -> set.accept(Double.parseDouble(v)), () -> String.valueOf(get.get())));
    }

    private static void i(String section, String key, String comment, Consumer<Integer> set, java.util.function.Supplier<Integer> get) {
        ENTRIES.add(new Entry(section, key, comment, v -> set.accept(Integer.parseInt(v)), () -> String.valueOf(get.get())));
    }

    private static void s(String section, String key, String comment, Consumer<String> set, java.util.function.Supplier<String> get) {
        ENTRIES.add(new Entry(section, key, comment, set, get));
    }
}
