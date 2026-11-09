package com.cavmkii.seancraft.ecology;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import com.cavmkii.seancraft.SeanCraft;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Ecology items and blocks (16.2): the soil kit, insecticide, bat guano and the six prairie plants (17.16). */
public final class EcoContent {
    /** The prairie plants and their coefficients of conservatism (17.16). */
    public enum Prairie {
        BIG_BLUESTEM("big_bluestem", 5),
        COMPASS_PLANT("compass_plant", 6),
        BUTTERFLY_MILKWEED("butterfly_milkweed", 7),
        PALE_PURPLE_CONEFLOWER("pale_purple_coneflower", 8),
        RATTLESNAKE_MASTER("rattlesnake_master", 8),
        LEADPLANT("leadplant", 9);

        public final String id;
        public final double c;

        Prairie(String id, double c) {
            this.id = id;
            this.c = c;
        }
    }

    public static final Map<Prairie, Block> PLANTS = new EnumMap<>(Prairie.class);
    public static final List<Item> ITEMS = new ArrayList<>();

    public static final Item SOIL_KIT = item("soil_kit", new EcoToolItem(EcoToolItem.Kind.SOIL_KIT, new Item.Properties().stacksTo(1)));
    public static final Item INSECTICIDE = item("insecticide", new EcoToolItem(EcoToolItem.Kind.INSECTICIDE, new Item.Properties().stacksTo(16)));
    public static final Item BAT_GUANO = item("bat_guano", new EcoToolItem(EcoToolItem.Kind.BAT_GUANO, new Item.Properties()));

    static {
        for (Prairie p : Prairie.values()) {
            Block block = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id(p.id), new PrairiePlantBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.DESTROY).randomTicks()));
            PLANTS.put(p, block);
            item(p.id, new BlockItem(block, new Item.Properties()));
        }
    }

    private EcoContent() {}

    private static Item item(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(id), item);
        ITEMS.add(item);
        return item;
    }

    public static String id(Prairie p) {
        return SeanCraft.id(p.id).toString();
    }

    /** Remnant prairie (17.16.1): only placed at world generation, in plains, savanna and meadows. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> REMNANT_PRAIRIE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, SeanCraft.id("remnant_prairie"));

    public static void init() {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                // By tag, so Terralith's grasslands get it too (v23 10). Snowy plains are left out.
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(ConventionalBiomeTags.IS_PLAINS)
                        .or(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(ConventionalBiomeTags.IS_SAVANNA))
                        .or(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.MEADOW))
                        .and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(ConventionalBiomeTags.IS_SNOWY).negate())
                        .and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld()),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION, REMNANT_PRAIRIE);
    }
}
