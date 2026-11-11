package com.cavmkii.seancraft.food;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.ecology.Ecology;
import com.cavmkii.seancraft.ecology.Patch;
import com.cavmkii.seancraft.tarkov.Distance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * Food and cooking (design doc 11): the new crops and where they grow, forage by biome, the new animal drops, the
 * dishes and their tiers, the fermenter's crock, and the Travel Grill. Recipes are data (seancraft_patterns/kitchen),
 * made at the Kitchen module's station.
 */
public final class Kitchen {
    // ------------------------------------------------------------------ climate (11.3)

    /** Where a new crop grows, from the biome's base temperature. */
    public enum Climate {
        COLD("cold", -10, 0.5), TEMPERATE("temperate", 0.5, 1.0), WARM("warm", 0.95, 10);

        public final String label;
        final double min, max;

        Climate(String label, double min, double max) {
            this.label = label;
            this.min = min;
            this.max = max;
        }

        public boolean suits(Biome biome) {
            float t = biome.getBaseTemperature();
            return t >= min && t < max;
        }
    }

    /** A crop that only grows in its climate, or on fertile farmland (11.3). */
    public static class ClimateCrop extends CropBlock {
        public final Climate climate;
        private Item seed;

        ClimateCrop(Climate climate, Properties properties) {
            super(properties);
            this.climate = climate;
        }

        @Override
        protected ItemLike getBaseSeedId() {
            return seed;
        }

        public boolean inClimate(LevelReader level, BlockPos pos) {
            return climate.suits(level.getBiome(pos).value()) || fertileBelow(level, pos);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (inClimate(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }

        @Override
        public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
            return super.isValidBonemealTarget(level, pos, state) && inClimate(level, pos);
        }
    }

    /** Fertile mulch's farmland (11.3); filled in by the mulch item. */
    public static java.util.function.BiPredicate<LevelReader, BlockPos> fertileFarmland = (level, pos) -> false;

    static boolean fertileBelow(LevelReader level, BlockPos pos) {
        return fertileFarmland.test(level, pos.below());
    }

    public static final List<Item> ITEMS = new ArrayList<>();
    public static final List<ClimateCrop> CROPS = new ArrayList<>();

    private static Item item(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(id), item);
        ITEMS.add(item);
        return item;
    }

    private static Item crop(String id, Climate climate) {
        ClimateCrop block = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id(id), new ClimateCrop(climate,
                BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak()
                        .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));
        CROPS.add(block);
        Item item = item(id, new ItemNameBlockItem(block, new Item.Properties()));
        block.seed = item;
        return item;
    }

    private static Item plain(String id) {
        return item(id, new Item(new Item.Properties()));
    }

    private static Item snack(String id, int nutrition, float saturation) {
        return item(id, new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    // ------------------------------------------------------------------ crops and forage (11.2)
    public static final Item GARLIC = crop("garlic", Climate.TEMPERATE);
    public static final Item ONION = crop("onion", Climate.COLD);
    public static final Item CHILI = crop("chili", Climate.WARM);
    public static final Item RICE = crop("rice", Climate.TEMPERATE);
    public static final Item SOYBEANS = crop("soybeans", Climate.TEMPERATE);
    public static final Item MORELS = plain("morels");
    public static final Item CACTUS_FRUIT = snack("cactus_fruit", 2, 0.3f);
    public static final Item MANGO = snack("mango", 3, 0.4f);
    public static final Item GLOWCAP = plain("glowcap");
    public static final Item FROSTMINT = plain("frostmint");
    public static final Item CLOUDBERRIES = snack("cloudberries", 2, 0.3f);
    public static final Item GOAT_MEAT = snack("goat_meat", 2, 0.3f);
    public static final Item HOGLIN_CHOP = snack("hoglin_chop", 3, 0.3f);
    public static final Item SNOW_HARE = snack("snow_hare", 2, 0.3f);
    public static final Item ICE_FISH = snack("ice_fish", 2, 0.2f);
    // ------------------------------------------------------------------ ingredients
    public static final Item KOJI = plain("koji");
    public static final Item TOFU = plain("tofu");
    public static final Item SOY_SAUCE = item("soy_sauce", new Item(new Item.Properties().craftRemainder(Items.GLASS_BOTTLE).stacksTo(16)));

    // ------------------------------------------------------------------ dishes (11.1, 11.5)

    /** Where a dish is made, which sets how long it lasts (11.1). */
    public enum Station {
        CAMPFIRE(10), POT(20), OVEN(25), FERMENTER(30);

        public final int minutes;

        Station(int minutes) {
            this.minutes = minutes;
        }
    }

    public record Dish(int tier, Food.Kind kind, Station station) {}

    /** Health and stamina by tier and kind (11.1): {health, stamina}. */
    private static final float[][][] VALUES = {
            {{30, 10}, {10, 25}, {20, 18}},
            {{50, 15}, {15, 40}, {35, 28}},
            {{70, 20}, {20, 55}, {50, 38}},
            {{95, 25}, {25, 70}, {65, 48}}};

    public static final Map<Item, Dish> DISHES = new LinkedHashMap<>();
    public static final Item SAKE;

    /** Eaten from a bowl or drunk from a bottle: what's left. */
    static final class DishItem extends Item {
        final boolean drink;

        DishItem(Properties properties, boolean drink) {
            super(properties);
            this.drink = drink;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return drink ? UseAnim.DRINK : UseAnim.EAT;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            Item remainder = getCraftingRemainingItem();
            if (remainder == null || entity instanceof Player p && p.getAbilities().instabuild) {
                return left;
            }
            if (left.isEmpty()) {
                return new ItemStack(remainder);
            }
            if (entity instanceof Player p && !p.getInventory().add(new ItemStack(remainder))) {
                p.drop(new ItemStack(remainder), false);
            }
            return left;
        }
    }

    private static Item dish(String id, int tier, Food.Kind kind, Station station, Item remainder) {
        boolean drink = remainder == Items.GLASS_BOTTLE;
        Item.Properties props = new Item.Properties().stacksTo(remainder == null ? 16 : 8)
                .food(new FoodProperties.Builder().nutrition(4 + 2 * tier).saturationModifier(0.6f).build());
        if (remainder != null) {
            props.craftRemainder(remainder);
        }
        Item item = item(id, new DishItem(props, drink));
        DISHES.put(item, new Dish(tier, kind, station));
        return item;
    }

    static {
        Food.Kind H = Food.Kind.HEALTH, S = Food.Kind.STAMINA, B = Food.Kind.BALANCED;
        dish("honey_glazed_carrots", 1, S, Station.POT, null);
        dish("garlic_chicken", 1, H, Station.POT, null);
        dish("fried_egg", 1, H, Station.CAMPFIRE, null);
        dish("hunters_stew", 2, H, Station.POT, null);
        dish("morel_risotto", 2, S, Station.POT, null);
        dish("salmon_and_rice", 2, B, Station.POT, null);
        dish("tofu_stir_fry", 2, B, Station.POT, null);
        dish("berry_tart", 2, S, Station.OVEN, null);
        dish("mead", 2, B, Station.FERMENTER, Items.GLASS_BOTTLE);
        dish("chili_con_carne", 3, H, Station.POT, null);
        dish("canopy_curry", 3, B, Station.POT, null);
        dish("glowcap_broth", 3, S, Station.POT, null);
        dish("goat_roast", 3, H, Station.OVEN, null);
        dish("sushi", 3, S, Station.POT, null);
        dish("cactus_sorbet", 3, S, Station.POT, null);
        SAKE = dish("sake", 3, B, Station.FERMENTER, Items.GLASS_BOTTLE);
        dish("cloudberry_pie", 4, S, Station.OVEN, null);
        dish("ice_fish_chowder", 4, B, Station.POT, null);
        dish("snow_hare_stew", 4, H, Station.POT, null);
        dish("hoglin_feast", 4, H, Station.OVEN, null);
    }

    /** A dish's values, or null for anything else (vanilla food keeps its derived values). */
    static Food.Def def(Item item) {
        Dish d = DISHES.get(item);
        if (d == null) {
            return null;
        }
        float[] v = VALUES[d.tier - 1][d.kind.ordinal() == 0 ? 0 : d.kind == Food.Kind.STAMINA ? 1 : 2];
        return new Food.Def(d.kind, v[0], v[1], v[0] / 75f, d.station.minutes * 1200);
    }

    // ------------------------------------------------------------------ the fermenter (11.4)

    /** A crock still fermenting: what comes out, how long it takes, and the game time it's ready (0 until it starts). */
    public record Ferment(ItemStack result, int seconds, long readyAt) {
        static final Codec<Ferment> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("result").forGetter(Ferment::result),
                Codec.INT.fieldOf("seconds").forGetter(Ferment::seconds),
                Codec.LONG.fieldOf("ready").forGetter(Ferment::readyAt)).apply(i, Ferment::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Ferment> STREAM = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Ferment::result, ByteBufCodecs.VAR_INT, Ferment::seconds, ByteBufCodecs.VAR_LONG, Ferment::readyAt,
                Ferment::new);

        @Override
        public boolean equals(Object o) {
            return o instanceof Ferment f && f.seconds == seconds && f.readyAt == readyAt && ItemStack.matches(f.result, result);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(readyAt) * 31 + seconds;
        }
    }

    /** The client's game time, for crock tooltips; set by the client. */
    public static java.util.function.LongSupplier clientTime = () -> 0;

    public static final DataComponentType<Ferment> FERMENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SeanCraft.id("ferment"),
            DataComponentType.<Ferment>builder().persistent(Ferment.CODEC).networkSynchronized(Ferment.STREAM).build());

    /** Becomes what's in it once the time is up, wherever it's carried (it doesn't ferment sitting in a chest). */
    static final class CrockItem extends Item {
        CrockItem(Properties properties) {
            super(properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
                return;
            }
            Ferment f = stack.get(FERMENT);
            if (f == null) {
                return;
            }
            if (f.readyAt <= 0) {
                start(stack, level);
                return;
            }
            if (level.getGameTime() >= f.readyAt) {
                ItemStack out = f.result.copy();
                if (player.getInventory().getItem(slot) == stack) {
                    player.getInventory().setItem(slot, out);
                } else {
                    stack.setCount(0);
                    com.cavmkii.seancraft.tetris.Tetris.give(player, out);
                }
                player.displayClientMessage(Component.literal(out.getHoverName().getString() + " is ready.").withStyle(ChatFormatting.GOLD), true);
            }
        }

        @Override
        public Component getName(ItemStack stack) {
            Ferment f = stack.get(FERMENT);
            return f == null ? super.getName(stack) : Component.literal("Fermenting " + f.result.getHoverName().getString());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            Ferment f = stack.get(FERMENT);
            if (f == null) {
                return;
            }
            lines.add(Component.literal("Makes " + f.result.getCount() + " " + f.result.getHoverName().getString()).withStyle(ChatFormatting.GRAY));
            long left = f.readyAt <= 0 ? f.seconds * 20L : Math.max(0, f.readyAt - clientTime.getAsLong());
            lines.add(Component.literal("Ready in " + Math.max(1, (left + 1199) / 1200) + " min (carry it; it doesn't ferment in a chest)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static final Item CROCK = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("fermenting_crock"),
            new CrockItem(new Item.Properties().stacksTo(1)));

    /** The fermenter's output, in a crock that turns into it after {@code seconds}. */
    public static ItemStack fermenting(ItemStack result, int seconds) {
        ItemStack crock = new ItemStack(CROCK);
        crock.set(FERMENT, new Ferment(result.copy(), seconds, 0));
        return crock;
    }

    /** Starts a crock's clock (when it's taken from the fermenter). */
    public static void start(ItemStack stack, Level level) {
        Ferment f = stack.get(FERMENT);
        if (f != null && f.readyAt <= 0) {
            stack.set(FERMENT, new Ferment(f.result, f.seconds, level.getGameTime() + f.seconds * 20L));
        }
    }

    // ------------------------------------------------------------------ the Travel Grill (11.4)

    static final class GrillItem extends Item {
        GrillItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                com.cavmkii.seancraft.tarkov.HideoutCraftMenu.openGrill(sp);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }

    public static final Item TRAVEL_GRILL = item("travel_grill", new GrillItem(new Item.Properties().stacksTo(1)));

    // ------------------------------------------------------------------ gathering (11.2)

    public static final TagKey<Item> RAW_MEAT = TagKey.create(Registries.ITEM, SeanCraft.id("raw_meat"));

    /** One forage find: what a broken block can give in a biome. */
    private record Forage(Item item, double chance) {}

    private Kitchen() {}

    public static void init() {
        Mulch.init();
        // Foraging (11.2, 11.3): wild plants come from the patch's forage, so stripping one makes them scarce there.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel server && !player.isCreative()) {
                Forage f = forage(server, pos, state);
                if (f != null) {
                    double abundance = 1;
                    Patch p = Ecology.enabled() ? Ecology.update(server, pos) : null;
                    if (p != null) {
                        abundance = p.forage;
                    }
                    if (server.getRandom().nextDouble() < f.chance * Math.max(0.1, abundance)) {
                        Block.popResource(server, pos, new ItemStack(f.item));
                        if (p != null) {
                            p.forage = Math.max(0.02, p.forage - 0.03);
                        }
                    }
                }
            }
        });
        // Snow hares: rabbits in the frozen far lands (11.2).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() == EntityType.RABBIT && entity.level() instanceof ServerLevel level && frozenFarLands(level, entity.blockPosition())) {
                level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.3, entity.getZ(), new ItemStack(SNOW_HARE)));
            }
        });
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            if (key.equals(EntityType.GOAT.getDefaultLootTable())) {
                builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(GOAT_MEAT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))));
            } else if (key.equals(EntityType.HOGLIN.getDefaultLootTable())) {
                builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(HOGLIN_CHOP)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))));
            }
            String path = key.location().getPath();
            if (!key.location().getNamespace().equals("minecraft")) {
                return;
            }
            // Matt isn't in the game yet: until he is, his rice, soybeans and Koji turn up in village chests (11.3),
            // and sake in village and desert temple chests (8.1).
            if (path.startsWith("chests/village/village_") && (path.endsWith("_house") || path.endsWith("farmer"))) {
                builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.25f))
                        .add(LootItem.lootTableItem(RICE).setWeight(3).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3))))
                        .add(LootItem.lootTableItem(SOYBEANS).setWeight(3).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3))))
                        .add(LootItem.lootTableItem(KOJI).setWeight(2))
                        .add(LootItem.lootTableItem(SAKE).setWeight(1)));
            } else if (path.equals("chests/desert_pyramid")) {
                builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.4f))
                        .add(LootItem.lootTableItem(SAKE).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))));
            }
        });
    }

    static boolean frozenFarLands(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return (biome.is(ConventionalBiomeTags.IS_SNOWY) || biome.is(ConventionalBiomeTags.IS_ICY) || biome.value().getBaseTemperature() < 0.15f)
                && Distance.at(level, Vec3.atCenterOf(pos)) == Distance.FAR_LANDS;
    }

    /** A catch in the frozen far lands: fish become ice fish, two times in three. */
    public static void iceFish(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        if (!frozenFarLands(level, pos)) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(net.minecraft.tags.ItemTags.FISHES) && level.getRandom().nextFloat() < 0.67f) {
                items.set(i, new ItemStack(ICE_FISH, items.get(i).getCount()));
            }
        }
    }

    private static boolean grass(BlockState s) {
        return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN);
    }

    private static Forage forage(ServerLevel level, BlockPos pos, BlockState s) {
        Holder<Biome> biome = level.getBiome(pos);
        boolean grass = grass(s);
        if (biome.is(Biomes.DEEP_DARK)) {
            return s.is(Blocks.SCULK) || s.is(Blocks.SCULK_VEIN) || s.is(Blocks.GLOW_LICHEN) ? new Forage(GLOWCAP, 0.08) : null;
        }
        if (frozenFarLands(level, pos) && (grass || s.is(Blocks.SNOW) || s.is(Blocks.SWEET_BERRY_BUSH))) {
            return new Forage(CLOUDBERRIES, 0.12);
        }
        if ((biome.is(ConventionalBiomeTags.IS_MOUNTAIN_PEAK) || biome.is(ConventionalBiomeTags.IS_MOUNTAIN_SLOPE))
                && (grass || s.is(Blocks.SNOW))) {
            return new Forage(FROSTMINT, 0.10);
        }
        if (biome.is(BiomeTags.IS_JUNGLE) && s.is(Blocks.JUNGLE_LEAVES)) {
            return new Forage(MANGO, 0.06);
        }
        if (biome.is(BiomeTags.IS_BADLANDS) && (grass || s.is(Blocks.DEAD_BUSH))) {
            return new Forage(CHILI, 0.15);
        }
        if (biome.is(ConventionalBiomeTags.IS_DESERT) && (s.is(Blocks.DEAD_BUSH) || s.is(Blocks.CACTUS))) {
            return new Forage(CACTUS_FRUIT, 0.15);
        }
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (path.contains("dark_forest") && (grass || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM))) {
            return new Forage(MORELS, s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM) ? 0.5 : 0.08);
        }
        if (biome.is(BiomeTags.IS_TAIGA) && grass) {
            return new Forage(ONION, 0.10);
        }
        if (biome.is(BiomeTags.IS_FOREST) && grass) {
            return new Forage(GARLIC, 0.10);
        }
        return null;
    }

    /** Crop items, for the yield function. */
    public static boolean produce(ItemStack stack) {
        for (ClimateCrop c : CROPS) {
            if (stack.is(c.seed)) {
                return true;
            }
        }
        return false;
    }

    /** Soybeans fix nitrogen (11.3): they give back fertility as they grow instead of taking it. */
    public static boolean fixesNitrogen(BlockState state) {
        return state.getBlock() == BuiltInRegistries.BLOCK.get(SeanCraft.id("soybeans"));
    }

}
