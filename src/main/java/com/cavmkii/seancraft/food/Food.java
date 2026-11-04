package com.cavmkii.seancraft.food;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.combat.Damage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Food slots instead of hunger (design doc 11). Three meals at a time, each adding max health, max stamina and health
 * regeneration for a while, fading over its last quarter. You can't starve, but you head out weak until you eat.
 */
public final class Food {
    public static final int SLOTS = 3;
    /** A meal's bonus fades over this last share of its time. */
    static final float FADE_FROM = 0.25f;
    private static final ResourceLocation FOOD_HEALTH = SeanCraft.id("food_health");

    public enum Kind {
        HEALTH("Health food"), STAMINA("Stamina food"), BALANCED("Balanced food");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** What a food gives, derived from its vanilla nutrition and saturation (11). Regen is health per second. */
    public record Def(Kind kind, float health, float stamina, float regen, int ticks) {}

    public record Meal(String item, int left, int duration) {
        static final Codec<Meal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(Meal::item),
                Codec.INT.fieldOf("left").forGetter(Meal::left),
                Codec.INT.fieldOf("duration").forGetter(Meal::duration)
        ).apply(i, Meal::new));

        public Item itemType() {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
        }

        /** 1 until the meal starts to fade, then down to 0 as it runs out. */
        public float strength() {
            float fraction = left / (float) Math.max(1, duration);
            return Math.min(1, fraction / FADE_FROM);
        }
    }

    /** Meals don't survive death: you respawn hungry. */
    public static final AttachmentType<List<Meal>> MEALS = AttachmentRegistry.<List<Meal>>builder()
            .persistent(Meal.CODEC.listOf())
            .initializer(ArrayList::new)
            .buildAndRegister(SeanCraft.id("meals"));

    public record Sync(List<Meal> meals) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SeanCraft.id("meals"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.meals.size());
            for (Meal m : p.meals) {
                buf.writeUtf(m.item);
                buf.writeVarInt(m.left);
                buf.writeVarInt(m.duration);
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Meal> meals = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                meals.add(new Meal(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
            }
            return new Sync(meals);
        });

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    private Food() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player);
            }
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            String problem = level.isClientSide ? null : refusal(player, stack);
            if (problem == null) {
                return InteractionResultHolder.pass(stack);
            }
            player.displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        });
    }

    /** Null for anything that isn't food. */
    public static Def def(ItemStack stack) {
        FoodProperties props = stack.get(DataComponents.FOOD);
        if (props == null) {
            return null;
        }
        Kind kind = kind(stack);
        int n = props.nutrition();
        float s = props.saturation() / Math.max(1, 2f * n);
        int ticks = Math.round((180 + 900 * Math.min(1.2f, s)) * 20);
        return switch (kind) {
            case HEALTH -> new Def(kind, n * 6f, n * 2f, n * 0.08f, ticks);
            case BALANCED -> new Def(kind, n * 4f, n * 4f, n * 0.05f, ticks);
            case STAMINA -> new Def(kind, n * 1.5f, n * 7f, n * 0.02f, ticks);
        };
    }

    /** Meat and stews build health, fish and fancy food are balanced, the rest is stamina food. */
    static Kind kind(ItemStack stack) {
        if (stack.is(ItemTags.MEAT) || stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW) || stack.is(Items.BEETROOT_SOUP)
                || stack.is(Items.SUSPICIOUS_STEW)) {
            return Kind.HEALTH;
        }
        if (stack.is(ItemTags.FISHES) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return Kind.BALANCED;
        }
        return Kind.STAMINA;
    }

    /** Why this can't be eaten now, or null if it can (or isn't food). */
    static String refusal(Player player, ItemStack stack) {
        if (def(stack) == null || player.isCreative()) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        List<Meal> meals = player.getAttachedOrCreate(MEALS);
        for (Meal m : meals) {
            if (m.item.equals(id)) {
                return m.left > m.duration / 2 ? "You can eat this again once it's half gone." : null;
            }
        }
        return meals.size() >= SLOTS ? "You're full. Wait for a meal to wear off." : null;
    }

    /** A meal finished eating: it fills an empty slot, or starts the same food over. */
    public static void eaten(ServerPlayer player, ItemStack stack) {
        Def def = def(stack);
        if (def == null) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        List<Meal> meals = new ArrayList<>(player.getAttachedOrCreate(MEALS));
        meals.removeIf(m -> m.item.equals(id));
        if (meals.size() >= SLOTS) {
            // Only reachable in creative, which skips the check: replace the meal closest to running out.
            meals.sort((a, b) -> Integer.compare(a.left, b.left));
            meals.remove(0);
        }
        // INT makes food last longer (6.3).
        int ticks = (int) Math.round(def.ticks() * com.cavmkii.seancraft.rpg.Rpg.stats(player).preparation());
        meals.add(new Meal(id, ticks, ticks));
        player.setAttached(MEALS, meals);
        apply(player, meals);
        ServerPlayNetworking.send(player, new Sync(meals));
    }

    private static void tick(ServerPlayer player) {
        List<Meal> meals = player.getAttachedOrCreate(MEALS);
        if (!player.isAlive()) {
            return;
        }
        boolean changed = false;
        List<Meal> next = new ArrayList<>(meals.size());
        for (Meal m : meals) {
            if (m.left > 1) {
                next.add(new Meal(m.item, m.left - 1, m.duration));
            } else {
                changed = true;
            }
        }
        player.setAttached(MEALS, next);
        if (player.tickCount % 20 == 0 || changed) {
            apply(player, next);
            float regen = 0;
            for (Meal m : next) {
                Def def = def(new ItemStack(m.itemType()));
                if (def != null) {
                    regen += def.regen() * m.strength();
                }
            }
            regen *= Rest.regenMultiplier(player);
            if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                // heal() is on the 5x scale, and these numbers already are.
                player.heal(regen / Damage.scale());
            }
            ServerPlayNetworking.send(player, new Sync(next));
        }
    }

    /** Max health from meals, fading with them. */
    private static void apply(ServerPlayer player, List<Meal> meals) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) {
            return;
        }
        float bonus = health(meals);
        AttributeModifier current = max.getModifier(FOOD_HEALTH);
        if (current != null && Math.abs(current.amount() - bonus) < 0.5) {
            return;
        }
        max.addOrReplacePermanentModifier(new AttributeModifier(FOOD_HEALTH, bonus, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) {
            // A fading meal never takes the last of a part's health (5.10).
            player.setHealth(Math.max(1, player.getMaxHealth()));
        }
    }

    public static float health(List<Meal> meals) {
        float total = 0;
        for (Meal m : meals) {
            Def def = def(new ItemStack(m.itemType()));
            if (def != null) {
                total += def.health() * m.strength();
            }
        }
        return total;
    }

    public static float stamina(List<Meal> meals) {
        float total = 0;
        for (Meal m : meals) {
            Def def = def(new ItemStack(m.itemType()));
            if (def != null) {
                total += def.stamina() * m.strength();
            }
        }
        return total;
    }

    /** Extra max stamina from meals, for the server's stamina rules. */
    public static float stamina(Player player) {
        return stamina(player.getAttachedOrCreate(MEALS));
    }
}
