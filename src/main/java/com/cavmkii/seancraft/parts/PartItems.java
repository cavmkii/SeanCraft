package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.SeanCraft;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class PartItems {
    public static final Map<PartDef, Item> ITEMS = new LinkedHashMap<>();

    static {
        for (PartDef def : PartDef.ALL) {
            ITEMS.put(def, Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(def.id()),
                    new PartItem(def, new Item.Properties().stacksTo(1).fireResistant())));
        }
    }

    public static final Block WEAPON_BENCH = Registry.register(BuiltInRegistries.BLOCK, SeanCraft.id("weapon_bench"),
            new WeaponBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.ANVIL)));
    public static final Item WEAPON_BENCH_ITEM = Registry.register(BuiltInRegistries.ITEM, SeanCraft.id("weapon_bench"),
            new BlockItem(WEAPON_BENCH, new Item.Properties()));

    public static final net.minecraft.world.inventory.MenuType<WeaponBenchMenu> BENCH_MENU = Registry.register(BuiltInRegistries.MENU,
            SeanCraft.id("weapon_bench"), new net.minecraft.world.inventory.MenuType<>(WeaponBenchMenu::new,
                    net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    private PartItems() {}

    public static Item get(PartDef def) {
        return ITEMS.get(def);
    }

    public static void init() {
        Parts.init();
        WeaponItems.init();
    }
}
