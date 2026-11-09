package com.cavmkii.seancraft.parts;

import net.minecraft.world.item.Item;

/** A weapon or armor part. Its level lives in the {@link Parts#LEVEL} component. */
public class PartItem extends Item {
    public final PartDef def;

    public PartItem(PartDef def, Properties properties) {
        super(properties);
        this.def = def;
    }

    /** "Spruce Log Limbs": wood parts carry their tree in the name. */
    @Override
    public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
        WoodSpecies wood = WoodSpecies.byId(Parts.wood(stack));
        net.minecraft.network.chat.Component base = super.getName(stack);
        return wood == null ? base : net.minecraft.network.chat.Component.literal(wood.label + " ").append(base);
    }
}
