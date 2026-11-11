package com.cavmkii.seancraft.item;

import com.cavmkii.seancraft.SeanCraft;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.DyedItemColor;

public final class ModItems {
    /** "Soda": a drink that counts as food. Valheim's +35 health / +150 stamina becomes Health Boost and a full meal. */
    public static final Item SODA = register("soda", new SodaItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
            .nutrition(6)
            .saturationModifier(1.2f)
            .alwaysEdible()
            .effect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 20 * 60 * 20, 1), 1f)
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .build())));

    /** "Merch": a black leather tunic with no armor at all. Sean's Security leaves wearers alone. */
    public static final Item MERCH = register("merch", new NoArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.CHESTPLATE,
            new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0x151515, false))));

    /** "Glock": a crossbow with no reload. Fires arrows as fast as you click. */
    public static final Item GLOCK = register("glock", new GlockItem(new Item.Properties().durability(465)));

    /** "Restraining Order": carry it and Joe has to keep his distance. */
    public static final Item RESTRAINING_ORDER = register("restraining_order", new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** "Diddy Oil": Speed and Resistance for five minutes. */
    public static final Item DIDDY_OIL = register("diddy_oil", new DiddyOilItem(new Item.Properties().stacksTo(16)));

    /** "67": end-game fists. 13 attack damage (six plus seven), fast swings. */
    public static final Item SIXTY_SEVEN = register("sixty_seven", new SwordItem(Tiers.NETHERITE, new Item.Properties()
            .fireResistant()
            .rarity(Rarity.EPIC)
            .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 8, -1.0f))));

    /** "Baby Bonnet": Joe's gift to anyone who said the phrase. Babies leave wearers alone; Security doesn't. */
    public static final Item BABY_BONNET = register("baby_bonnet", new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET,
            new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(0xF7A8C8, false))));

    /** Items that get a description line in their tooltip (lang key item.seancraft.&lt;id&gt;.desc). */
    public static final List<Item> DESCRIBED = List.of(SODA, MERCH, GLOCK, RESTRAINING_ORDER, DIDDY_OIL, SIXTY_SEVEN, BABY_BONNET);

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SeanCraft.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(SODA))
                    .title(Component.translatable("itemGroup.seancraft"))
                    .displayItems((params, output) -> {
                        DESCRIBED.forEach(output::accept);
                        output.accept(com.cavmkii.seancraft.rpg.RpgContent.ALTAR_ITEM);
                        output.accept(com.cavmkii.seancraft.rpg.RpgContent.ORB_OF_REGRET);
                        output.accept(com.cavmkii.seancraft.tarkov.TarkovContent.HIDEOUT_BENCH_ITEM);
                        output.accept(com.cavmkii.seancraft.tarkov.TarkovContent.CAMP_KIT);
                        com.cavmkii.seancraft.ecology.EcoContent.ITEMS.forEach(output::accept);
                        com.cavmkii.seancraft.food.Kitchen.ITEMS.forEach(output::accept);
                        output.accept(ModItems.FIRE_ARROW);
                        output.accept(ModItems.BROADHEAD_ARROW);
                        output.accept(ModItems.BODKIN_ARROW);
                        output.accept(ModItems.BLUNT_ARROW);
                        output.accept(com.cavmkii.seancraft.boss.BossContent.TROPHY_RACK_ITEM);
                        output.accept(com.cavmkii.seancraft.boss.BossContent.MARK_TOTEM);
                        output.accept(com.cavmkii.seancraft.boss.BossContent.PEDESTAL_ITEM);
                        output.accept(com.cavmkii.seancraft.boss.BossContent.TROPHY_OLD_SALT);
                        output.accept(com.cavmkii.seancraft.boss.BossContent.MARKS_REEL);
                        com.cavmkii.seancraft.tarkov.TarkovContent.MEDS.values().forEach(output::accept);
                        output.accept(com.cavmkii.seancraft.parts.PartItems.WEAPON_BENCH_ITEM);
                        com.cavmkii.seancraft.parts.PartItems.ITEMS.values().forEach(output::accept);
                    })
                    .build());

    private ModItems() {}

    public static final Item FIRE_ARROW = register("fire_arrow", new FireArrowItem(new Item.Properties()));
    public static final Item BROADHEAD_ARROW = register("arrow_broadhead",
            new TaggedArrowItem(com.cavmkii.seancraft.combat.Damage.BROADHEAD_TAG, new Item.Properties()));
    public static final Item BODKIN_ARROW = register("arrow_bodkin",
            new TaggedArrowItem(com.cavmkii.seancraft.combat.Damage.BODKIN_TAG, new Item.Properties()));
    public static final Item BLUNT_ARROW = register("arrow_blunt",
            new TaggedArrowItem(com.cavmkii.seancraft.combat.Damage.BLUNT_ARROW_TAG, new Item.Properties()));

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, SeanCraft.id(name), item);
    }

    public static void init() {
        // Loading the class registers everything.
    }
}
