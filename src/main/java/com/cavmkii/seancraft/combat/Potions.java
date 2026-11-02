package com.cavmkii.seancraft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Potion rules (design doc 9.7.2): one of each type at a time, with 5 minutes before that type can be drunk again;
 * Fire Resistance gives 20 fire resistance points instead of immunity; Resistance gives 10 armor per level on every part
 * instead of vanilla's flat cut; Strength is 30% increased damage per level.
 */
public final class Potions {
    public static final long COOLDOWN_TICKS = 6000;
    public static final float FIRE_RESISTANCE_POINTS = 20;
    public static final float RESISTANCE_ARMOR = 10;
    public static final double STRENGTH_INCREASED = 0.30;

    private static final Map<UUID, Map<String, Long>> DRUNK = new HashMap<>();

    private Potions() {}

    /** The potion's type, so two bottles of Swiftness share a cooldown but Swiftness and Healing don't. */
    static String key(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (contents == null || contents.potion().isEmpty()) {
            return base;
        }
        // Long and strong versions count as the same type.
        String id = contents.potion().get().unwrapKey().map(k -> k.location().getPath()).orElse(base);
        return id.replace("long_", "").replace("strong_", "");
    }

    /** Ticks left before this potion can be drunk, or 0. */
    public static long cooldown(Player player, ItemStack stack) {
        if (player.isCreative()) {
            return 0;
        }
        Long at = DRUNK.getOrDefault(player.getUUID(), Map.of()).get(key(stack));
        return at == null ? 0 : Math.max(0, at + COOLDOWN_TICKS - player.level().getGameTime());
    }

    public static void drank(LivingEntity entity, ItemStack stack) {
        if (entity instanceof Player player && !player.level().isClientSide) {
            DRUNK.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(key(stack), player.level().getGameTime());
        }
    }
}
