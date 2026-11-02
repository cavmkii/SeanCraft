package com.cavmkii.seancraft.client.parts;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.parts.Family;
import com.cavmkii.seancraft.parts.PartDef;
import com.cavmkii.seancraft.parts.PartKind;
import com.cavmkii.seancraft.parts.PartSlot;
import com.cavmkii.seancraft.parts.Parts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Part-built weapons drawn from their installed parts (Appendix C, asset pack): each part has a texture layer per
 * family and material, and the item stacks them, so swapping a blade at the Weapon Bench changes how the weapon looks.
 * Bows, crossbows, the vanilla shield and armor keep their own models.
 */
public final class PartModels {
    private static final String[] WOOD = {"planks", "logs", "bone", "blaze_rod", "breeze_rod"};
    private static final String[] HIDE = {"string", "leather", "rabbit_hide", "phantom_membrane", "scute"};
    private static final Set<ResourceLocation> LAYERS = new HashSet<>();

    private PartModels() {}

    static String folder(Family f) {
        return switch (f) {
            case PIKE -> "spear";
            case SWORD, AXE, DAGGER, MACE, WARHAMMER, GREATSWORD, HALBERD, SPEAR, BUCKLER, TOWER_SHIELD, THROWING_KNIFE, JAVELIN,
                    BOOMERANG, WAND, STAFF -> f.name().toLowerCase(java.util.Locale.ROOT);
            default -> null;
        };
    }

    /** The pack's texture name for a part. */
    static String layer(Family family, PartDef def) {
        String material = switch (def.kind().material) {
            case METAL -> PartDef.METALS[def.tier() - 1];
            case GEM -> PartDef.GEMS[def.tier() - 1];
            case WOOD -> WOOD[def.tier() - 1];
            case HIDE -> HIDE[def.tier() - 1];
        };
        String kind = def.kind() == PartKind.HANDLE && family == Family.BOOMERANG ? "limbs" : def.kind().name().toLowerCase(java.util.Locale.ROOT);
        return kind + "_" + material;
    }

    static ResourceLocation model(String folder, String layer) {
        return SeanCraft.id("item/weapon_layer/" + folder + "/" + layer);
    }

    /** Layers bottom to top: grip, wrap, guard or focus, then the head; the tower shield's plates follow its rim. */
    static List<ResourceLocation> layers(ItemStack stack) {
        Family.Stock stock = Family.stock(stack);
        String folder = stock == null ? null : folder(stock.family());
        if (folder == null) {
            return List.of();
        }
        Map<PartSlot, Parts.Installed> parts = Parts.installed(stack);
        List<ResourceLocation> out = new ArrayList<>();
        for (PartSlot slot : new PartSlot[] {PartSlot.HANDLE, PartSlot.WRAP, PartSlot.GUARD, PartSlot.HEAD}) {
            Parts.Installed inst = parts.get(slot);
            if (inst == null) {
                continue;
            }
            if (stock.family() == Family.TOWER_SHIELD && slot == PartSlot.GUARD) {
                String metal = PartDef.METALS[inst.part().tier() - 1];
                out.add(model(folder, "plates_lower_" + metal));
                out.add(model(folder, "plates_upper_" + metal));
            }
            out.add(model(folder, layer(stock.family(), inst.part())));
        }
        out.removeIf(id -> !LAYERS.contains(id));
        return out;
    }

    public static void init() {
        // Every layer model shipped under models/item/weapon_layer/, found when resources load.
        net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin.register(
                (manager, executor) -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    Set<ResourceLocation> ids = new HashSet<>();
                    for (ResourceLocation file : manager.listResources("models/item/weapon_layer", id -> id.getPath().endsWith(".json")).keySet()) {
                        String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
                        ids.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path));
                    }
                    return ids;
                }, executor),
                (ids, ctx) -> {
                    LAYERS.clear();
                    LAYERS.addAll(ids);
                    ctx.addModels(ids);
                    ctx.modifyModelAfterBake().register((model, context) -> {
                        ModelResourceLocation top = context.topLevelId();
                        if (top == null || !"inventory".equals(top.variant())) {
                            return model;
                        }
                        Item item = BuiltInRegistries.ITEM.get(top.id());
                        Family.Stock stock = Family.stock(item);
                        if (stock == null || folder(stock.family()) == null) {
                            return model;
                        }
                        return new Layered(model);
                    });
                });
    }

    /** The item's own model for transforms and particles; the quads come from its parts. */
    static final class Layered extends ForwardingBakedModel {
        Layered(BakedModel base) {
            this.wrapped = base;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
            List<ResourceLocation> layers = layers(stack);
            if (layers.isEmpty()) {
                super.emitItemQuads(stack, random, context);
                return;
            }
            var models = Minecraft.getInstance().getModelManager();
            for (ResourceLocation id : layers) {
                BakedModel layer = models.getModel(id);
                if (layer != null) {
                    context.bakedModelConsumer().accept(layer);
                }
            }
        }
    }
}
