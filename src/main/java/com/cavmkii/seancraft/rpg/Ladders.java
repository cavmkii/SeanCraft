package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.WeaponItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Recipe ladders (design doc 4.7): anyone can wield any weapon, but a player crafts only the weapon families they've
 * unlocked. Each attribute has a ladder of three rungs. The origin's first rung is known from the start; 5,000 damage to
 * hostile mobs with a line's rung 1 weapons opens rung 2, and 25,000 with rung 2 weapons opens rung 3. 1,000 damage with
 * a found weapon from another line unlocks that weapon and puts the player on the first rung of its line.
 *
 * <p>Progress lives in {@link PlayerData#ladder}: "STR1" and so on for rung progress, "x:mace" for crossover damage with a
 * weapon, "u:mace" for a weapon unlocked by crossing over, and "on:AGI" for a line joined that way.
 */
public final class Ladders {
    /** Progress syncs to the client each time it passes a multiple of this, and on every unlock. */
    private static final double SYNC_STEP = 250;

    /** Weapon family to its rung on each line (STR, AGI, INT), 0 where it isn't on that line. */
    private static final Map<String, int[]> RUNGS = new LinkedHashMap<>();

    static {
        rung("club", 1, 0, 0);
        rung("mace", 2, 0, 0);
        rung("axe", 2, 0, 0);
        rung("sword", 2, 2, 0);
        rung("warhammer", 3, 0, 0);
        rung("greatsword", 3, 0, 0);
        rung("halberd", 3, 0, 0);
        rung("bow", 0, 1, 0);
        rung("dagger", 0, 1, 0);
        rung("spear", 0, 2, 0);
        rung("crossbow", 0, 2, 0);
        rung("longbow", 2, 2, 0);
        rung("pike", 0, 3, 0);
        rung("throwing_knife", 0, 2, 0);
        rung("javelin", 0, 3, 0);
        rung("boomerang", 0, 3, 0);
        rung("wand", 0, 0, 1);
        rung("staff", 0, 0, 2);
    }

    private static void rung(String key, int str, int agi, int intl) {
        RUNGS.put(key, new int[] {str, agi, intl});
    }

    private static final Attr[] LINES = {Attr.STR, Attr.AGI, Attr.INT};

    private Ladders() {}

    /** Each mob's health as a hit lands, so a killing blow counts only what was left. */
    private static final Map<LivingEntity, Float> HEALTH_BEFORE = new java.util.WeakHashMap<>();

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer) {
                HEALTH_BEFORE.put(entity, entity.getHealth());
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken <= 0 || !(entity instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack weapon = source.getDirectEntity() == player ? player.getMainHandItem()
                    : source.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : null;
            String key = key(weapon);
            if (key != null) {
                // Mob health is on the 5x scale already; never more than the target had left.
                Float before = HEALTH_BEFORE.remove(entity);
                record(player, key, Math.min(taken, before != null ? before : entity.getMaxHealth()));
            }
        });
    }

    /** The ladder key for a weapon, or null if it isn't on a ladder (shields, armor, tools, tridents). */
    public static String key(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.is(WeaponItems.WOODEN_CLUB)) {
            return "club";
        }
        Family.Stock stock = Family.stock(stack);
        return stock != null ? key(stock.family()) : null;
    }

    /** The ladder key for a family, or null if it's always craftable. */
    public static String key(Family family) {
        String key = family.name().toLowerCase(Locale.ROOT);
        return RUNGS.containsKey(key) ? key : null;
    }

    /** The highest rung a weapon sits on, on any line (1 if it's off the ladders). */
    public static int rungOf(String key) {
        int[] r = RUNGS.get(key);
        if (r == null) {
            return 1;
        }
        return Math.max(r[0], Math.max(r[1], r[2]));
    }

    public static String label(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static int line(Attr attr) {
        return attr == Attr.STR ? 0 : attr == Attr.AGI ? 1 : 2;
    }

    private static boolean onLine(PlayerData d, int line) {
        return d.origin != null && line(d.origin.attr) == line || d.ladder.containsKey("on:" + LINES[line].name());
    }

    private static double progress(PlayerData d, int line, int rung) {
        return d.ladder.getOrDefault(LINES[line].name() + rung, 0.0);
    }

    /** The highest rung a player has on a line, 0 if they aren't on it. */
    public static int rung(PlayerData d, Attr attr) {
        int line = line(attr);
        if (!onLine(d, line)) {
            return 0;
        }
        if (progress(d, line, 1) < com.cavmkii.seancraft.Cfg.ladderDamage[1]) {
            return 1;
        }
        return progress(d, line, 2) < com.cavmkii.seancraft.Cfg.ladderDamage[2] ? 2 : 3;
    }

    /** True if the player can craft this weapon family (keys off the ladders are always craftable). */
    public static boolean known(PlayerData d, String key) {
        int[] rungs = key != null ? RUNGS.get(key) : null;
        if (rungs == null) {
            return true;
        }
        if (d.origin == null) {
            return false;
        }
        if (d.ladder.containsKey("u:" + key)) {
            return true;
        }
        for (int l = 0; l < LINES.length; l++) {
            if (rungs[l] > 0 && rungs[l] <= rung(d, LINES[l])) {
                return true;
            }
        }
        return false;
    }

    /** False for a weapon on a ladder the player hasn't unlocked. */
    public static boolean canCraft(ServerPlayer player, ItemStack result) {
        String key = key(result);
        return key == null || known(Rpg.data(player), key);
    }

    /** Why a family is locked and what opens it, for the Weapon Bench; null if it's known. */
    public static String lockReason(PlayerData d, String key) {
        if (known(d, key)) {
            return null;
        }
        if (d.origin == null) {
            return "Pick an origin first";
        }
        int[] rungs = RUNGS.get(key);
        int own = line(d.origin.attr);
        for (int l = 0; l < LINES.length; l++) {
            if (rungs[l] > 1 && onLine(d, l) && rung(d, LINES[l]) == rungs[l] - 1) {
                double need = rungs[l] == 2 ? com.cavmkii.seancraft.Cfg.ladderDamage[1] : com.cavmkii.seancraft.Cfg.ladderDamage[2];
                return String.format("%,d / %,d damage with %s rung %d weapons", Math.round(progress(d, l, rungs[l] - 1)), Math.round(need),
                        LINES[l].name(), rungs[l] - 1);
            }
        }
        if (rungs[own] > 0) {
            return "Climb the " + LINES[own].name() + " ladder to rung " + rungs[own];
        }
        return String.format("%,d / %,d damage with a found %s", Math.round(d.ladder.getOrDefault("x:" + key, 0.0)), Math.round(com.cavmkii.seancraft.Cfg.ladderDamage[0]),
                key);
    }

    /** Damage to a hostile mob with a weapon: climbs the ladders, or works toward crossing over. */
    static void record(ServerPlayer player, String key, double amount) {
        PlayerData d = Rpg.data(player);
        int[] rungs = RUNGS.get(key);
        if (rungs == null || d.origin == null) {
            return;
        }
        List<String> before = knownList(d);
        boolean sync = false;
        if (known(d, key)) {
            for (int l = 0; l < LINES.length; l++) {
                int r = rungs[l];
                if (r > 0 && r < 3 && onLine(d, l) && r <= rung(d, LINES[l])) {
                    sync |= add(d, LINES[l].name() + r, amount);
                }
            }
        } else if (rungs[line(d.origin.attr)] == 0) {
            // A weapon from another line.
            sync |= add(d, "x:" + key, amount);
            if (d.ladder.get("x:" + key) >= com.cavmkii.seancraft.Cfg.ladderDamage[0]) {
                d.ladder.put("u:" + key, 1.0);
                for (int l = 0; l < LINES.length; l++) {
                    if (rungs[l] > 0) {
                        d.ladder.put("on:" + LINES[l].name(), 1.0);
                    }
                }
            }
        }
        List<String> after = knownList(d);
        after.removeAll(before);
        if (!after.isEmpty()) {
            sync = true;
            player.displayClientMessage(Component.literal("Recipes unlocked: " + String.join(", ", after.stream().map(Ladders::label).toList())
                    + ". Make them at the Weapon Bench.").withStyle(ChatFormatting.GOLD), false);
            player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
        }
        if (sync) {
            Rpg.markDirty(player);
        }
    }

    /** Adds progress; true if it passed a sync step. */
    private static boolean add(PlayerData d, String id, double amount) {
        double old = d.ladder.getOrDefault(id, 0.0);
        d.ladder.put(id, old + amount);
        return Math.floor(old / SYNC_STEP) != Math.floor((old + amount) / SYNC_STEP);
    }

    public static List<String> knownList(PlayerData d) {
        List<String> out = new ArrayList<>();
        for (String key : RUNGS.keySet()) {
            if (known(d, key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** Admin and testing: set a ladder value directly. */
    public static void set(ServerPlayer player, String id, double value) {
        if (value <= 0) {
            Rpg.data(player).ladder.remove(id);
        } else {
            Rpg.data(player).ladder.put(id, value);
        }
        Rpg.markDirty(player);
    }
}
