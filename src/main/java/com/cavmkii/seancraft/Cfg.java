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

    // Danger (2.1)
    public static boolean distanceScaling = true;
    public static boolean elites = true;
    public static boolean huntingParties = true;
    /** Where the Wilds, Frontier and Far lands start, in blocks from world spawn. */
    public static double[] distanceBands = {1000, 3000, 6000};
    /** Most a mob's health and damage can be multiplied by distance, elites and monster pressure together (2.1.1). */
    public static double[] distanceCaps = {4.0, 2.5};

    // Home base (2.4) and camps (2.5)
    public static double homeRadius = 30;
    public static int maxBenches = 1;
    /** No Home Bench, bed or camp within this many blocks of a Summoning Pedestal. */
    public static double pedestalClearance = 100;
    public static double campSafeRadius = 8;

    public static double sleepHealPercent = 50;
    public static int spawnPoints = 3;
    /** How far the other spawn points are from world spawn (2.3). */
    public static double spawnDistance = 400;
    public static double spawnRadius = 24;
    public static double spawnRefillMinutes = 30;

    // Combat
    public static double healthBase = 300;
    public static double damageScale = 5;
    public static double armorK = 20;
    public static double resistK = 20;
    public static double staminaRegen = 25;
    /** Roll invulnerability in ticks: Light or Medium load, then Heavy load (9.6.3). */
    public static int[] rollIframeTicks = {8, 4};
    public static double rollRechargeSeconds = 0.75;
    public static double potionCooldownMinutes = 5;
    public static int attributePointsPerLevel = 2;
    /** Damage to unlock a crossover weapon, ladder rung 2 and rung 3 (4.7). */
    public static double[] ladderDamage = {1000, 5000, 25000};

    // Boss scaling per extra player (8.4.6)
    public static double bossHealthPerPlayer = 85;
    public static double bossDamagePerPlayer = 10;
    public static double bossCooldownPerPlayer = 20;
    public static int bossScalingCap = 6;

    // Ecology (17)
    public static boolean ecoEnabled = true;
    public static int ecoPatchChunks = 4;
    public static double ecoTickBudgetMs = 2;
    public static double ecoDayScale = 1;
    public static double ecoCatchUpMaxDays = 30;
    public static double ecoQualityZ = 2.5;
    public static double ecoQualityK = 0.5;

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

        b("Danger", "DistanceScaling", "Hostile mobs get more health, damage and XP the further from world spawn they appear (Heartland, Wilds, Frontier, Far lands).",
                v -> distanceScaling = v, () -> distanceScaling);
        b("Danger", "Elites", "Hostile mobs can spawn as 1- or 2-star elites, more often further out and twice as often at night.", v -> elites = v, () -> elites);
        b("Danger", "HuntingParties", "At night in the Frontier and Far lands, hunting parties track players who are off safe ground.",
                v -> huntingParties = v, () -> huntingParties);
        s("Danger", "DistanceBands", "Where the Wilds, Frontier and Far lands start, in blocks from world spawn, comma-separated and ascending.",
                v -> distanceBands = list(v, 3, true), () -> join(distanceBands));
        s("Danger", "Caps", "Most a monster's health and damage can be multiplied by distance and elites together: health, damage.",
                v -> distanceCaps = list(v, 2, false), () -> join(distanceCaps));
        d("Home", "Radius", "Home base radius in blocks before Perimeter upgrades.", v -> homeRadius = v, () -> homeRadius);
        i("Home", "MaxBenches", "Home Benches each player can have placed; placing one more picks up the oldest.", v -> maxBenches = Math.max(1, v), () -> maxBenches);
        d("Home", "PedestalClearance", "No Home Bench, bed respawn or camp within this many blocks of a Summoning Pedestal (a boss dungeon).",
                v -> pedestalClearance = v, () -> pedestalClearance);
        d("Camp", "SafeRadius", "No hostile spawns within this many blocks of a lit camp.", v -> campSafeRadius = v, () -> campSafeRadius);
        d("Health", "SleepHealPercent", "Sleeping through the night heals each body part that isn't destroyed by this percent of its health.", v -> sleepHealPercent = v, () -> sleepHealPercent);
        i("Spawn", "Points", "Fixed spawn points (with a supply shelter) built in a new world. 0 turns them off. Only read when the world is first started.", v -> spawnPoints = v, () -> spawnPoints);
        d("Spawn", "Distance", "How far the other spawn points are from world spawn (blocks).", v -> spawnDistance = v, () -> spawnDistance);
        d("Spawn", "Radius", "Safe radius around each spawn point: hostile mobs don't spawn inside it.", v -> spawnRadius = v, () -> spawnRadius);
        d("Spawn", "RefillMinutes", "Empty spawn chests are restocked after this long.", v -> spawnRefillMinutes = v, () -> spawnRefillMinutes);
        d("Combat", "HealthBase", "Player base max health, before food and the web.", v -> healthBase = v, () -> healthBase);
        d("Combat", "DamageScale", "Multiplier on vanilla damage, healing and monster health. Mobs already loaded keep their old health scale. Skill and weapon numbers assume 5, so other values throw the balance off.",
                v -> damageScale = Math.max(0.1, v), () -> damageScale);
        d("Combat", "ArmorK", "K in the armor formula: a hit is multiplied by K / (armor + K).", v -> armorK = Math.max(1, v), () -> armorK);
        d("Combat", "ResistK", "K in the elemental resistance formula: K / (resistance + K).", v -> resistK = Math.max(1, v), () -> resistK);
        d("Combat", "StaminaRegen", "Stamina regenerated per second, after a second without spending any.", v -> staminaRegen = v, () -> staminaRegen);
        s("Combat", "RollIframeTicks", "Ticks of roll invulnerability at Light or Medium load, then at Heavy load.",
                v -> rollIframeTicks = java.util.Arrays.stream(list(v, 2, false)).mapToInt(x -> (int) Math.round(x)).toArray(),
                () -> rollIframeTicks[0] + "," + rollIframeTicks[1]);
        d("Combat", "RollRechargeSeconds", "Combat Roll's recharge between rolls; stamina is the real limit.", v -> {
            rollRechargeSeconds = v;
            com.cavmkii.seancraft.combat.Combat.configureRoll();
        }, () -> rollRechargeSeconds);
        d("Potion", "CooldownMinutes", "Minutes before you can drink another potion of the same type.", v -> potionCooldownMinutes = v, () -> potionCooldownMinutes);
        i("Character", "AttributePointsPerLevel", "Attribute points each level gives.", v -> attributePointsPerLevel = v, () -> attributePointsPerLevel);
        s("Weapons", "LadderDamage", "Damage to monsters to unlock a crossover weapon, ladder rung 2 and rung 3, comma-separated.",
                v -> ladderDamage = list(v, 3, false), () -> join(ladderDamage));
        d("Boss", "HealthPerPlayer", "Percent boss health added for each extra player who has been in the ring.", v -> bossHealthPerPlayer = v, () -> bossHealthPerPlayer);
        d("Boss", "DamagePerPlayer", "Percent boss damage added for each extra player.", v -> bossDamagePerPlayer = v, () -> bossDamagePerPlayer);
        d("Boss", "CooldownPerPlayer", "Percent added to a boss's attack rate for each extra player.", v -> bossCooldownPerPlayer = v, () -> bossCooldownPerPlayer);
        i("Boss", "ScalingCap", "Most players a boss scales for.", v -> bossScalingCap = Math.max(1, v), () -> bossScalingCap);
        b("Ecology", "Enabled", "Turns the population and soil model on (design doc 17).", v -> ecoEnabled = v, () -> ecoEnabled);
        i("Ecology", "PatchChunks", "Patch size in chunks per side. Changing it starts the model over.", v -> ecoPatchChunks = Math.max(1, v), () -> ecoPatchChunks);
        d("Ecology", "TickBudgetMs", "Server time per tick for patch updates, in milliseconds.", v -> ecoTickBudgetMs = v, () -> ecoTickBudgetMs);
        d("Ecology", "DayScale", "Ecological days per in-game day; raise it to speed everything up.", v -> ecoDayScale = v, () -> ecoDayScale);
        d("Ecology", "CatchUpMaxDays", "Longest absence a patch integrates when it reloads, in ecological days.", v -> ecoCatchUpMaxDays = v, () -> ecoCatchUpMaxDays);
        d("Ecology", "QualityZ", "Shape of the threat curve in habitat quality (z).", v -> ecoQualityZ = v, () -> ecoQualityZ);
        d("Ecology", "QualityK", "Half-saturation of the threat curve in habitat quality (k).", v -> ecoQualityK = v, () -> ecoQualityK);

        d("Fight", "TriggerRange", "Joe and Sean start fighting when this close (blocks).", v -> fightTriggerRange = v, () -> fightTriggerRange);
        d("Fight", "DurationSeconds", "Length of a fight.", v -> fightSeconds = v, () -> fightSeconds);
        d("Fight", "CooldownMinutes", "Minutes before they can fight again.", v -> fightCooldownMinutes = v, () -> fightCooldownMinutes);
        d("Fight", "BystanderDamageMultiplier", "Multiplier on spell damage to anyone other than Joe and Sean caught in the blast. 0 disables.", v -> bystanderDamageMultiplier = v, () -> bystanderDamageMultiplier);
    }

    private Cfg() {}

    /** {@code n} comma-separated numbers, ascending if asked; anything malformed throws, which keeps the old value. */
    private static double[] list(String v, int n, boolean ascending) {
        String[] parts = v.split(",");
        if (parts.length != n) {
            throw new IllegalArgumentException(v);
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
            if (out[i] < 0 || ascending && i > 0 && out[i] < out[i - 1]) {
                throw new IllegalArgumentException(v);
            }
        }
        return out;
    }

    private static String join(double[] values) {
        return java.util.Arrays.stream(values).mapToObj(m -> m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m))
                .collect(java.util.stream.Collectors.joining(","));
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
