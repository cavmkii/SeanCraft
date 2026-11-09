package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.rpg.Rpg;
import com.cavmkii.seancraft.rpg.Skill;
import com.cavmkii.seancraft.tetris.Tetris;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What cures which status effect, used by the STATUS tab:
 * <ul>
 *   <li>milk clears every effect, good and bad;</li>
 *   <li>a honey bottle cures poison;</li>
 *   <li>an antidote cures every harmful effect.</li>
 * </ul>
 */
public final class Cures {
    private Cures() {}

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(TarkovNet.CureEffect.TYPE, (p, ctx) -> cureFromScreen(ctx.player(), p.effect(), p.slot()));
    }

    public static boolean harmful(Holder<MobEffect> effect) {
        return effect.value().getCategory() == MobEffectCategory.HARMFUL;
    }

    public static boolean cures(ItemStack stack, Holder<MobEffect> effect) {
        if (stack.is(Items.MILK_BUCKET)) {
            return effect.value().getCategory() != MobEffectCategory.BENEFICIAL;
        }
        if (stack.is(Items.HONEY_BOTTLE)) {
            return effect.is(MobEffects.POISON);
        }
        if (stack.is(TarkovContent.ANTIDOTE)) {
            return harmful(effect);
        }
        return false;
    }

    /** Uses one cure: removes what it cures, uses it up, and hands back the bucket or bottle. */
    public static void apply(ServerPlayer player, ItemStack stack) {
        ItemStack back = ItemStack.EMPTY;
        if (stack.is(Items.MILK_BUCKET)) {
            player.removeAllEffects();
            back = new ItemStack(Items.BUCKET);
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            player.removeEffect(MobEffects.POISON);
            player.getFoodData().eat(6, 0.1f);
            back = new ItemStack(Items.GLASS_BOTTLE);
        } else if (stack.is(TarkovContent.ANTIDOTE)) {
            List<Holder<MobEffect>> bad = new ArrayList<>();
            for (MobEffectInstance e : player.getActiveEffects()) {
                if (harmful(e.getEffect())) {
                    bad.add(e.getEffect());
                }
            }
            bad.forEach(player::removeEffect);
            Rpg.addSkillXp(player, Skill.BREWING, 5);
            back = new ItemStack(Items.GLASS_BOTTLE);
        } else {
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            Tetris.give(player, back);
            if (!back.isEmpty()) {
                player.drop(back, false);
            }
        }
        player.displayClientMessage(Component.literal("Cured with " + stack.getHoverName().getString().toLowerCase()).withStyle(ChatFormatting.GREEN), true);
    }

    /** STATUS tab: cure an effect with a carried item (an index into Tetris.carried). */
    private static void cureFromScreen(ServerPlayer player, String effectId, int slot) {
        ResourceLocation key = ResourceLocation.tryParse(effectId);
        if (key == null || !player.isAlive()) {
            return;
        }
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null);
        List<ItemStack> carried = Tetris.carried(player);
        if (effect == null || slot < 0 || slot >= carried.size() || !player.hasEffect(effect)) {
            return;
        }
        ItemStack stack = carried.get(slot);
        if (cures(stack, effect)) {
            apply(player, stack);
        }
    }
}
