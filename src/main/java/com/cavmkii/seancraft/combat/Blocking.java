package com.cavmkii.seancraft.combat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.Parts;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Rpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

/**
 * Shields (design doc 9.4.6, 9.6.1). SeanCraft handles blocking itself instead of vanilla's all-or-nothing block: a
 * shield takes a share of a frontal physical hit by its rim's material, the rest leaks through, and the blocked part
 * costs half its damage in stamina. Raising a shield just before a hit parries it. A block that empties the stamina
 * bar breaks the guard.
 */
public final class Blocking {
    /** Standard shield by rim material, copper to netherite (80% wood to 95% netherite in the doc). */
    static final float[] SHIELD_BLOCK = {0.80f, 0.84f, 0.88f, 0.92f, 0.95f};
    static final float BUCKLER_BLOCK = 0.60f;
    /** Strength makes a block hold better: +0.2% per STR point above 10. */
    static final float BLOCK_PER_STR = 0.002f;
    static final int PARRY_TICKS = 4;
    static final int BUCKLER_PARRY_TICKS = 7;
    static final int GUARD_BREAK_TICKS = 20;
    static final double TOWER_SLOW = -0.30;
    private static final ResourceLocation TOWER = SeanCraft.id("tower_raised");

    public enum Outcome { NONE, BLOCKED, PARRIED, GUARD_BROKEN }

    public record Result(float amount, Outcome outcome) {}

    private Blocking() {}

    static Family family(ItemStack shield) {
        Family.Stock stock = Family.stock(shield);
        return stock != null && stock.family().shield() ? stock.family() : null;
    }

    /** Share of a frontal physical hit this shield stops. */
    public static float blockShare(ServerPlayer player, ItemStack shield) {
        Family f = family(shield);
        if (f == Family.TOWER_SHIELD) {
            return 1f;
        }
        float base;
        if (f == Family.BUCKLER) {
            base = BUCKLER_BLOCK;
        } else {
            Parts.Installed rim = Parts.installed(shield).get(PartSlot.GUARD);
            base = SHIELD_BLOCK[rim != null ? rim.part().tier() - 1 : 1];
        }
        float str = (float) Math.max(0, Rpg.stats(player).str() - 10) * BLOCK_PER_STR;
        return Math.min(f == Family.BUCKLER ? 0.75f : 0.99f, base + str);
    }

    private static boolean frontal(ServerPlayer player, DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 view = player.calculateViewVector(0, player.getYHeadRot());
        Vec3 to = from.vectorTo(player.position());
        to = new Vec3(to.x, 0, to.z).normalize();
        return to.dot(view) < 0;
    }

    /** The hit on a player, from RpgDamage before the pipeline (vanilla scale). */
    public static Result onHit(ServerPlayer player, DamageSource source, float amount) {
        if (!player.isUsingItem() || player.getUseItem().getUseAnimation() != UseAnim.BLOCK || amount <= 0) {
            return new Result(amount, Outcome.NONE);
        }
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_FIRE) || !frontal(player, source)) {
            return new Result(amount, Outcome.NONE);
        }
        ItemStack shield = player.getUseItem();
        Family f = family(shield);
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        int raised = player.getTicksUsingItem();
        int parryWindow = f == Family.TOWER_SHIELD ? -1 : f == Family.BUCKLER ? BUCKLER_PARRY_TICKS : PARRY_TICKS;
        if (raised <= parryWindow && !arrow) {
            parry(player, source);
            return new Result(0, Outcome.PARRIED);
        }
        if (raised < 5 && f != Family.TOWER_SHIELD) {
            // Still coming up: neither a parry nor a block.
            return new Result(amount, Outcome.NONE);
        }
        float share = blockShare(player, shield) * com.cavmkii.seancraft.parts.Wear.factor(shield);
        float blocked = amount * share;
        float cost = blocked * com.cavmkii.seancraft.combat.Damage.SCALE / 2;
        PlayerData data = Rpg.data(player);
        shield.hurtAndBreak(Math.max(1, (int) blocked), player, player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        if (!player.isCreative() && cost >= data.stamina) {
            // Guard break (9.6.1): the block still lands, then the shield drops and the player staggers.
            data.stamina = 0;
            player.getCooldowns().addCooldown(shield.getItem(), GUARD_BREAK_TICKS * 3);
            player.stopUsingItem();
            com.cavmkii.seancraft.boss.Restraint.stagger(player, GUARD_BREAK_TICKS);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1f, 1f);
            return new Result(amount - blocked, Outcome.GUARD_BROKEN);
        }
        Combat.spendStamina(player, cost);
        return new Result(amount - blocked, Outcome.BLOCKED);
    }

    /** No damage, no stamina, and the attacker staggers (9.4.6). */
    private static void parry(ServerPlayer player, DamageSource source) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.8f);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, player.getX(), player.getY(1.2), player.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            Poise.stagger(attacker);
            attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }
        Rpg.openRiposte(player);
    }

    /** Every tick: a raised tower shield slows the player. */
    public static void tick(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean raised = player.isUsingItem() && family(player.getUseItem()) == Family.TOWER_SHIELD;
        if (raised && speed.getModifier(TOWER) == null) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(TOWER, TOWER_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!raised && speed.getModifier(TOWER) != null) {
            speed.removeModifier(TOWER);
        }
    }

    static boolean isShield(ItemStack stack) {
        return stack.is(Items.SHIELD) || family(stack) != null;
    }
}
