package com.cavmkii.seancraft.rpg;

import com.cavmkii.seancraft.combat.Damage;
import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wands and staves (design doc 9.4.5). An attack fires a bolt: 18 damage for 4 mana from a wand, 35 for 8 from a staff,
 * times the crystal's factor (spell power). A staff's heavy attack fires a charged orb that bursts. The crystal's
 * catalyst sets the element. Holding one also makes every INT skill stronger by the crystal's factor.
 */
public final class Magic {
    public static final float WAND_BOLT = 18;
    public static final float STAFF_BOLT = 35;
    public static final float WAND_MANA = 4;
    public static final float STAFF_MANA = 8;
    static final float[] CRYSTAL_FACTOR = {0.85f, 1.0f, 1.1f, 1.2f, 1.35f};
    /** Ticks between bolts: a wand every 0.6 s, a staff every second, the staff's charged orb 1.5 s. */
    public static final int WAND_COOLDOWN = 12;
    public static final int STAFF_COOLDOWN = 20;
    public static final int ORB_COOLDOWN = 30;
    /** Game time each player can fire again. */
    private static final java.util.Map<java.util.UUID, Long> READY = new java.util.HashMap<>();
    public static final String[] ELEMENTS = {"fire", "frost", "lightning", "poison", "spirit"};
    public static final String[] ELEMENT_LABELS = {"Fire", "Frost", "Lightning", "Poison", "Spirit"};

    private Magic() {}

    static Family family(ItemStack stack) {
        Family.Stock stock = Family.stock(stack);
        return stock != null && stock.family().magic() ? stock.family() : null;
    }

    /** The crystal's factor, with its part levels; 1 when not holding a wand or staff. */
    public static float spellPower(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (family(held) == null) {
            return 1f;
        }
        Parts.Installed crystal = Parts.installed(held).get(PartSlot.HEAD);
        float f = crystal == null ? 1f : CRYSTAL_FACTOR[crystal.part().tier() - 1];
        return f * Math.max(0.05f, 1 + Parts.totals(held).damage);
    }

    public static Damage.Type element(ItemStack stack) {
        Parts.Installed crystal = Parts.installed(stack).get(PartSlot.HEAD);
        String e = crystal == null || crystal.infusion() == null ? "fire" : crystal.infusion();
        return switch (e) {
            case "frost" -> Damage.Type.FROST;
            case "lightning" -> Damage.Type.LIGHTNING;
            case "poison" -> Damage.Type.POISON;
            case "spirit" -> Damage.Type.SPIRIT;
            default -> Damage.Type.FIRE;
        };
    }

    /** A swing with a wand or staff. Returns true if it was one (a bolt flew, or there wasn't the mana). */
    public static boolean onSwing(ServerPlayer player, boolean heavy) {
        ItemStack held = player.getMainHandItem();
        Family f = family(held);
        if (f == null) {
            return false;
        }
        boolean staff = f == Family.STAFF;
        // A swing during the cooldown fires nothing and costs nothing (it used to fire every swing, like a minigun).
        long now = player.serverLevel().getGameTime();
        if (now < READY.getOrDefault(player.getUUID(), 0L)) {
            return true;
        }
        float cost = (staff ? STAFF_MANA : WAND_MANA) * (heavy ? 2 : 1);
        PlayerData data = Rpg.data(player);
        if (!player.isCreative()) {
            if (data.mana < cost) {
                player.displayClientMessage(Component.literal("Not enough mana").withStyle(ChatFormatting.BLUE), true);
                return true;
            }
            data.mana -= cost;
        }
        Stats stats = Rpg.stats(player);
        double mult = 1 + stats.scalingPct(held) / 100;
        if (!Scaling.meets(held, stats)) {
            mult *= Scaling.UNDER_REQUIREMENT_DAMAGE;
        }
        mult *= com.cavmkii.seancraft.parts.Wear.factor(held);
        // The crystal's factor (4.4.1): spell power.
        mult *= spellPower(player);
        float damage = (float) ((staff ? STAFF_BOLT : WAND_BOLT) * mult * (heavy ? com.cavmkii.seancraft.combat.Heavy.DAMAGE : 1));
        Damage.Type element = element(held);
        ItemStack look = new ItemStack(switch (element) {
            case FROST -> Items.SNOWBALL;
            case LIGHTNING -> Items.PRISMARINE_CRYSTALS;
            case POISON -> Items.SPIDER_EYE;
            case SPIRIT -> Items.GLOWSTONE_DUST;
            default -> Items.FIRE_CHARGE;
        });
        // A staff's heavy attack is a charged orb that bursts (9.4.1).
        float radius = staff && heavy ? 2.5f : 0;
        SkillProjectile bolt = SkillProjectile.bolt(player.level(), player, look, damage / Damage.SCALE, radius, element);
        bolt.spell = true;
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, staff ? 1.6f : 2.2f, 0.5f);
        player.level().addFreshEntity(bolt);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, staff ? 0.9f : 1.4f);
        held.hurtAndBreak(1, player.serverLevel(), player, item -> { });
        int cooldown = staff ? (heavy ? ORB_COOLDOWN : STAFF_COOLDOWN) : WAND_COOLDOWN;
        READY.put(player.getUUID(), now + cooldown);
        // The vanilla sweep on the hotbar shows when it's ready.
        player.getCooldowns().addCooldown(held.getItem(), cooldown);
        return true;
    }
}
