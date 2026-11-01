package com.cavmkii.seancraft.boss;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mark / The Old Guard, tier 1 (design doc A.3), first pass: Reel Pull, Cast Net, Seniority Swing, All at Once, the
 * transition and Tenure. He keeps his distance and drags people around; the Old Guard walks in and is hard to hit from
 * the front.
 */
public class Mark extends BossEntity {
    public Mark(EntityType<? extends Mark> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    @Override
    protected String defId() {
        return "mark";
    }

    @Override
    protected void onPhase(int phase) {
        if (phase == 1) {
            // He snaps the rod over his knee and swings what's left.
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
            say("taunt");
        }
    }

    /** Tenure: in phase 2 he braces between attacks and takes half damage from the front. */
    @Override
    protected float incoming(DamageSource source, float amount) {
        if (phase >= 1 && state() == State.IDLE && source.getEntity() != null) {
            Vec3 toAttacker = source.getEntity().position().subtract(position()).normalize();
            if (getLookAngle().dot(toAttacker) > 0.3) {
                if (level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(0.6), getZ(), 6, 0.3, 0.3, 0.3, 0.1);
                }
                return amount * 0.5f;
            }
        }
        return amount;
    }

    @Override
    protected void windupSound(BossDef.Attack attack) {
        switch (attack.action()) {
            case "pull" -> level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.5f, 0.6f);
            case "net" -> level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 1.5f, 0.5f);
            default -> super.windupSound(attack);
        }
    }

    @Override
    protected void telegraph(BossDef.Attack attack, int tick, LivingEntity target) {
        super.telegraph(attack, tick, target);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        switch (attack.action()) {
            case "pull" -> {
                // The reel clicks; the aim locks halfway through, so a sidestep after that gets away.
                if (tick % 4 == 0) {
                    level.playSound(null, blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.HOSTILE, 0.6f, 1.8f);
                }
                if (tick < attack.windup() / 2 && target != null) {
                    aim = target.position();
                }
            }
            case "net" -> {
                if (tick % 2 == 0) {
                    float r = attack.number("radius", 3);
                    for (int i = 0; i < 24; i++) {
                        double a = Math.PI * 2 * i / 24;
                        level.sendParticles(ParticleTypes.END_ROD, aim.x + Math.cos(a) * r, aim.y + 0.1, aim.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
            case "arc" -> level.sendParticles(ParticleTypes.CRIT, getX(), getY(1.2), getZ(), 2, 0.3, 0.3, 0.3, 0.05);
            case "ring" -> {
                if (tick % 3 == 0) {
                    level.sendParticles(ParticleTypes.ENCHANT, getX(), getY(0.6), getZ(), 10, 1, 0.6, 1, 0.5);
                }
            }
            default -> {}
        }
    }

    @Override
    protected void perform(BossDef.Attack attack, int tick, LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        switch (attack.action()) {
            case "pull" -> {
                if (tick == 1) {
                    reelPull(level, attack);
                }
            }
            case "net" -> {
                if (tick == 1) {
                    castNet(level, attack);
                }
            }
            case "arc" -> {
                if (tick == 1) {
                    swing(level, attack);
                }
            }
            case "ring" -> {
                if (tick == 1) {
                    allAtOnce(level, attack);
                }
            }
            default -> {}
        }
    }

    /** A hook along the line to where the target stood when the aim locked; whoever it catches is dragged 6 blocks in. */
    private void reelPull(ServerLevel level, BossDef.Attack attack) {
        Vec3 from = getEyePosition();
        Vec3 dir = aim.add(0, 1, 0).subtract(from).normalize();
        float reach = attack.maxRange();
        float width = attack.number("width", 1.2f);
        for (int i = 0; i < reach * 2; i++) {
            Vec3 p = from.add(dir.scale(i / 2.0));
            level.sendParticles(ParticleTypes.FISHING, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        for (ServerPlayer player : playersInRing()) {
            Vec3 at = player.position().add(0, 1, 0);
            double along = at.subtract(from).dot(dir);
            if (along < 0 || along > reach || at.distanceTo(from.add(dir.scale(along))) > width) {
                continue;
            }
            if (hit(player, attack, attack.damage())) {
                Vec3 pull = position().subtract(player.position()).normalize().scale(attack.number("pull", 6) / 4.0);
                player.setDeltaMovement(pull.x, 0.45, pull.z);
                player.hurtMarked = true;
            }
        }
    }

    /** Everyone inside the circle is rooted for 2 seconds, unless they cut the net with a slash hit. */
    private void castNet(ServerLevel level, BossDef.Attack attack) {
        float r = attack.number("radius", 3);
        int ticks = Math.round(attack.number("root", 2) * 20);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
                    aim.x + Math.cos(a) * r * level.random.nextDouble(), aim.y + 0.3, aim.z + Math.sin(a) * r * level.random.nextDouble(), 1, 0, 0, 0, 0);
        }
        level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 1.5f, 0.6f);
        for (ServerPlayer player : playersInRing()) {
            double dx = player.getX() - aim.x;
            double dz = player.getZ() - aim.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(player.getY() - aim.y) < 3) {
                Restraint.root(player, ticks, true, "Caught in the net! A slash cuts it.");
            }
        }
    }

    /** Seniority Swing: everyone in a 120-degree arc in front, within 4 blocks, takes a heavy blunt hit. */
    private void swing(ServerLevel level, BossDef.Attack attack) {
        float r = attack.number("radius", 4);
        double half = Math.toRadians(attack.number("angle", 120) / 2);
        Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2, getY(0.6), getZ() + look.z * 2, 3, 0.8, 0.2, 0.8, 0);
        for (ServerPlayer player : playersInRing()) {
            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > r + 0.6 || Math.abs(player.getY() - getY()) > 3) {
                continue;
            }
            double angle = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
            if (to.length() < 1 || angle <= half) {
                hit(player, attack, attack.damage());
            }
        }
    }

    /** All at Once: eight hooks and paperwork, flying out in a ring. */
    private void allAtOnce(ServerLevel level, BossDef.Attack attack) {
        int count = Math.round(attack.number("count", 8));
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1f, 1.4f);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + getRandom().nextDouble() * 0.2;
            BossShot shot = new BossShot(level, this, attack, i % 2 == 0 ? new ItemStack(Items.TRIPWIRE_HOOK) : new ItemStack(Items.PAPER));
            shot.setPos(getX(), getY(0.55), getZ());
            shot.shoot(Math.cos(a), 0, Math.sin(a), 0.9f, 0);
            level.addFreshEntity(shot);
        }
    }

    @Override
    protected List<ItemStack> drops(ServerPlayer player, boolean firstKill) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(BossContent.MARKS_REEL));
        if (firstKill) {
            out.add(new ItemStack(BossContent.TROPHY_OLD_SALT));
            out.add(BossContent.staleLogbook(player));
        }
        // A share of rare tier 1 materials.
        out.add(new ItemStack(Items.RABBIT_HIDE, 2 + getRandom().nextInt(3)));
        out.add(new ItemStack(Items.COPPER_INGOT, 6 + getRandom().nextInt(7)));
        if (getRandom().nextFloat() < 0.5f) {
            out.add(new ItemStack(Items.BONE, 2 + getRandom().nextInt(3)));
        }
        return out;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
