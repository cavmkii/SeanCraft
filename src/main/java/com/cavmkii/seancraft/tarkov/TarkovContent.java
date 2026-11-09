package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.SeanCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class TarkovContent {
    public static final AttachmentType<TarkovData> DATA = AttachmentRegistry.<TarkovData>builder()
            .persistent(TarkovData.CODEC)
            .copyOnDeath()
            .initializer(TarkovData::new)
            .buildAndRegister(SeanCraft.id("tarkov"));

    /** The Home Bench (2.4). The Java names still say hideout. */
    public static final Block HIDEOUT_BENCH = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("home_bench"),
            new HideoutBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)));
    public static final Item HIDEOUT_BENCH_ITEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("home_bench"),
            new HideoutBenchItem(HIDEOUT_BENCH, new Item.Properties()));
    /** A camp (2.5): placed from its kit, packs back into it. */
    public static final Block CAMP = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("camp"),
            new CampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1f).sound(SoundType.WOOL).noOcclusion()
                    .lightLevel(state -> 13)));
    public static final Item CAMP_KIT = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("camp_kit"),
            new CampBlock.Kit(CAMP, new Item.Properties().stacksTo(1)));

    /** A module block's level, carried while it's picked up (2.5). */
    public static final net.minecraft.core.component.DataComponentType<Integer> MODULE_LEVEL = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, SeanCraft.id("module_level"),
            net.minecraft.core.component.DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public static final java.util.Map<Module, Block> MODULE_BLOCKS = new java.util.EnumMap<>(Module.class);
    public static final java.util.Map<Module, Item> MODULE_ITEMS = new java.util.EnumMap<>(Module.class);

    static {
        for (Module module : Module.values()) {
            String id = module.name().toLowerCase(java.util.Locale.ROOT);
            Block block = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id(id), new ModuleBlock(module,
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noLootTable().noOcclusion()));
            MODULE_BLOCKS.put(module, block);
            MODULE_ITEMS.put(module, Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(id), new ModuleBlock.Item(block, new Item.Properties())));
        }
    }

    public static final Item ANTIDOTE = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("antidote"),
            new AntidoteItem(new Item.Properties().stacksTo(8)));

    public static final java.util.Map<Med, Item> MEDS = new java.util.EnumMap<>(Med.class);

    static {
        for (Med med : Med.values()) {
            MEDS.put(med, Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(med.id),
                    new MedItem(med, new Item.Properties().stacksTo(med.stack))));
        }
    }

    private TarkovContent() {}

    public static final net.minecraft.world.inventory.MenuType<HideoutCraftMenu> HIDEOUT_CRAFT_MENU = Registry.register(BuiltInRegistries.MENU,
            SeanCraft.id("home_bench"), new net.minecraft.world.inventory.MenuType<>(HideoutCraftMenu::new,
                    net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    static void init() {
        // Loading the class registers everything.
    }
}
