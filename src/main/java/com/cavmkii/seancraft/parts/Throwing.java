package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.SeanCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Throwing (design doc 9.4): spears, throwing knives, javelins and boomerangs. Hold use to wind up, release to throw.
 * Thrown damage at iron (9.5.1): knife 22, javelin 35, boomerang 20; a thrown spear keeps its melee 32. Thrown weapons
 * stay where they land for 5 minutes, glowing so they can be found; the boomerang comes back.
 */
public final class Throwing {
    public static final int WIND_UP = 10;
    public static final int STAY_TICKS = 6000;

    public static final EntityType<ThrownWeapon> THROWN = Registry.register(BuiltInRegistries.ENTITY_TYPE, SeanCraft.id("thrown_weapon"),
            EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20)
                    .build("thrown_weapon"));

    private Throwing() {}

    static void init() {
        // Loading the class registers the entity type.
    }

    /** Thrown damage on the 5x scale, before the material factor. */
    static float thrownBase(Family family) {
        return switch (family) {
            case THROWING_KNIFE -> 22;
            case JAVELIN -> 35;
            case BOOMERANG -> 20;
            default -> 32;
        };
    }

    static float factor(ItemStack stack) {
        Parts.Installed head = Parts.installed(stack).get(PartSlot.HEAD);
        float f = head == null ? 1f : WeaponItems.FACTOR[head.part().tier() - 1];
        // Part levels are added by the pipeline's arrow step, like a bow's.
        return f;
    }

    static void release(Player player, ItemStack stack) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp)) {
            return;
        }
        Family family = Family.stock(stack).family();
        ItemStack thrown = stack.copyWithCount(1);
        ThrownWeapon entity = new ThrownWeapon(player.level(), sp, thrown, thrownBase(family) * factor(stack) / com.cavmkii.seancraft.combat.Damage.SCALE,
                family == Family.BOOMERANG);
        float speed = family == Family.THROWING_KNIFE ? 2.4f : family == Family.BOOMERANG ? 1.8f : 2.0f;
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, speed, 1f);
        player.level().addFreshEntity(entity);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1f, family == Family.THROWING_KNIFE ? 1.5f : 1f);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        // A throw costs a swing's stamina.
        com.cavmkii.seancraft.combat.Combat.spendStamina(sp, com.cavmkii.seancraft.combat.Combat.swingCost(sp));
    }
}
