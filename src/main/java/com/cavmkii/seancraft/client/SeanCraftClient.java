package com.cavmkii.seancraft.client;

import com.cavmkii.seancraft.entity.ModEntities;
import com.cavmkii.seancraft.client.rpg.ClientRpg;
import com.cavmkii.seancraft.client.tarkov.ClientTarkov;
import com.cavmkii.seancraft.item.ModItems;
import com.cavmkii.seancraft.rpg.RpgContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.DyedItemColor;
import java.util.List;

public final class SeanCraftClient implements ClientModInitializer {
    private static String partEffects(com.cavmkii.seancraft.parts.PartDef def, int level) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (def.damage(level) != 0) parts.add(String.format("+%.0f%% damage", def.damage(level) * 100));
        if (def.armor(level) != 0) parts.add(String.format("+%.0f%% armor", def.armor(level) * 100));
        if (def.stamina(level) != 0) parts.add(String.format("+%.0f%% swing speed", -def.stamina(level) * 100));
        if (def.durability(level) != 0) parts.add(String.format("+%.0f%% durability", def.durability(level) * 100));
        if (def.block(level) != 0) parts.add(String.format("+%.0f%% block", def.block(level) * 100));
        if (def.weight(level) != 0) parts.add("lighter armor");
        if (def.draw(level) != 0) parts.add(String.format("+%.0f%% draw speed", def.draw(level) * 100));
        return String.join(", ", parts) + " (vs. no part)";
    }

    private static final int JOE_GREEN = 0xFF8FD46A;
    private static final int BABY_GREEN = 0xFFB4E68C;

    @Override
    public void onInitializeClient() {
        // Prairie plants are cross-model cutouts, like flowers.
        com.cavmkii.seancraft.food.Kitchen.CROPS.forEach(block ->
                net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block, net.minecraft.client.renderer.RenderType.cutout()));
        com.cavmkii.seancraft.food.Kitchen.clientTime = () -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? 0 : level.getGameTime();
        };
        com.cavmkii.seancraft.ecology.EcoContent.PLANTS.values().forEach(block ->
                net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block, net.minecraft.client.renderer.RenderType.cutout()));
        EntityRendererRegistry.register(ModEntities.JOE, ctx -> new GoblinRenderer<>(ctx, JOE_GREEN));
        EntityRendererRegistry.register(ModEntities.BABY_COMMANDO, ctx -> new GoblinRenderer<>(ctx, BABY_GREEN));
        EntityRendererRegistry.register(ModEntities.BABY_GRENADIER, ctx -> new GoblinRenderer<>(ctx, BABY_GREEN));
        EntityRendererRegistry.register(ModEntities.BABY_BERSERKER, ctx -> new GoblinRenderer<>(ctx, JOE_GREEN));
        EntityRendererRegistry.register(ModEntities.SEAN, SeanRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEAN_SECURITY, SecurityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPELL, ctx -> new ThrownItemRenderer<>(ctx, 1.75f, true));
        EntityRendererRegistry.register(ModEntities.BABY_BOTTLE, ThrownItemRenderer::new);
        EntityRendererRegistry.register(RpgContent.SKILL_PROJECTILE, ctx -> new ThrownItemRenderer<>(ctx, 1.25f, true));
        com.cavmkii.seancraft.client.parts.PartModels.init();
        // The vanilla theme from the asset pack (Appendix C.8): on by default, switchable in Resource Packs.
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.cavmkii.seancraft.SeanCraft.MOD_ID).ifPresent(mod ->
                net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(com.cavmkii.seancraft.SeanCraft.id("vanilla_theme"), mod,
                        Component.literal("SeanCraft vanilla theme"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED));
        EntityRendererRegistry.register(com.cavmkii.seancraft.parts.Throwing.THROWN, com.cavmkii.seancraft.client.parts.ThrownWeaponRenderer::new);
        ClientRpg.init();
        // Map markers go to JourneyMap when it's there; without it they're ignored (18.1).
        boolean journeyMap = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap");
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.cavmkii.seancraft.compat.Markers.Sync.TYPE,
                (payload, context) -> {
                    if (journeyMap) {
                        context.client().execute(() -> com.cavmkii.seancraft.client.compat.JourneyMapMarkers.show(payload.markers()));
                    }
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.cavmkii.seancraft.pattern.Patterns.Sync.TYPE,
                (payload, ctx) -> com.cavmkii.seancraft.pattern.Patterns.receive(payload));
        ClientTarkov.init();
        com.cavmkii.seancraft.client.food.ClientFood.init();
        com.cavmkii.seancraft.client.boss.ClientBoss.init();
        com.cavmkii.seancraft.client.carry.ClientCarry.init();
        // Bows pull back at their parts' pace (BowDrawMixin), so the drawing animation follows it too.
        for (net.minecraft.world.item.Item bow : new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.BOW,
                com.cavmkii.seancraft.parts.WeaponItems.LONGBOW}) {
            net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(bow,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                            entity == null || entity.getUseItem() != stack ? 0
                                    : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) * com.cavmkii.seancraft.parts.Parts.drawFactor(stack) / 20f);
            net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(bow,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        }
        net.minecraft.client.gui.screens.MenuScreens.register(com.cavmkii.seancraft.parts.PartItems.BENCH_MENU,
                com.cavmkii.seancraft.client.parts.WeaponBenchScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(com.cavmkii.seancraft.tarkov.TarkovContent.HIDEOUT_CRAFT_MENU,
                com.cavmkii.seancraft.client.tarkov.HideoutCraftScreen::new);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            com.cavmkii.seancraft.food.Food.Def meal = com.cavmkii.seancraft.food.Food.def(stack);
            if (meal != null) {
                lines.add(1, Component.literal(String.format("%s: +%d health, +%d stamina, %d min", meal.kind().label,
                        Math.round(meal.health()), Math.round(meal.stamina()), Math.round(meal.ticks() / 1200f))).withStyle(ChatFormatting.GOLD));
            }
            if (stack.getItem() instanceof com.cavmkii.seancraft.parts.PartItem part) {
                lines.add(1, Component.literal("Level " + com.cavmkii.seancraft.parts.Parts.level(stack) + "/4  -  "
                        + part.def.kind().label + ", tier " + part.def.tier()).withStyle(ChatFormatting.GOLD));
                lines.add(2, Component.literal(partEffects(part.def, com.cavmkii.seancraft.parts.Parts.level(stack))).withStyle(ChatFormatting.GRAY));
                com.cavmkii.seancraft.parts.WoodSpecies wood = com.cavmkii.seancraft.parts.WoodSpecies.byId(com.cavmkii.seancraft.parts.Parts.wood(stack));
                if (wood != null) {
                    float share = part.def.kind() == com.cavmkii.seancraft.parts.PartKind.LIMBS ? 1 : com.cavmkii.seancraft.parts.WoodSpecies.GRIP_SHARE;
                    lines.add(3, Component.literal(wood.label + " (" + wood.feel + "): " + wood.effects(share)).withStyle(ChatFormatting.DARK_GREEN));
                }
                com.cavmkii.seancraft.parts.MaterialTraits.Trait trait = com.cavmkii.seancraft.parts.MaterialTraits.of(part.def);
                if (trait != null && trait != com.cavmkii.seancraft.parts.MaterialTraits.Trait.BALANCED) {
                    lines.add(3, Component.literal(trait.label + ": " + trait.effect).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                String inf = com.cavmkii.seancraft.parts.Parts.infusion(stack);
                if (inf != null && part.def.kind() == com.cavmkii.seancraft.parts.PartKind.CRYSTAL) {
                    lines.add(Math.min(lines.size(), 3), Component.literal("Element: " + inf).withStyle(ChatFormatting.AQUA));
                } else if (inf != null) {
                    lines.add(Math.min(lines.size(), 3), Component.literal(Character.toUpperCase(inf.charAt(0)) + inf.substring(1) + " infusion: "
                            + (inf.equals("heavy") ? "all scaling to STR, +50% poise damage" : inf.equals("keen") ? "all scaling to AGI, +50% bleed"
                                    : "half the scaling to INT, +2 mana per hit")).withStyle(ChatFormatting.AQUA));
                }
            } else if (com.cavmkii.seancraft.parts.Parts.modular(stack)) {
                com.cavmkii.seancraft.parts.Family family = com.cavmkii.seancraft.parts.Family.stock(stack).family();
                int line = 1;
                if (com.cavmkii.seancraft.parts.Wear.isWorn(stack)) {
                    lines.add(line++, Component.literal("Worn out: half as effective until repaired at the Weapon Bench").withStyle(ChatFormatting.RED));
                }
                // Requirement (9.4.2) and scaling (6.3.1, 9.4.3), red when you're under it.
                var need = com.cavmkii.seancraft.rpg.Scaling.requirement(stack);
                if (!family.armor()) {
                    StringBuilder sc = new StringBuilder("Scaling:");
                    com.cavmkii.seancraft.rpg.Scaling.weights(stack).forEach((a, w) -> sc.append(' ').append(Math.round(w * 100)).append("% ").append(a.name()));
                    lines.add(line++, Component.literal(sc.toString()).withStyle(ChatFormatting.GRAY));
                }
                if (!need.isEmpty()) {
                    boolean ok = com.cavmkii.seancraft.rpg.Scaling.meets(stack, com.cavmkii.seancraft.client.rpg.ClientRpg.stats);
                    StringBuilder rq = new StringBuilder("Requires");
                    need.forEach((a, v) -> rq.append(' ').append(v).append(' ').append(a.name()));
                    lines.add(line++, Component.literal(rq + (ok ? "" : " (x0.7 damage, +30% stamina)"))
                            .withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.RED));
                }
                for (var e : com.cavmkii.seancraft.parts.Parts.installed(stack).entrySet()) {
                    com.cavmkii.seancraft.parts.MaterialTraits.Trait trait = com.cavmkii.seancraft.parts.MaterialTraits.of(e.getValue().part());
                    com.cavmkii.seancraft.parts.WoodSpecies sp = e.getValue().species();
                    Component row = Component.literal(family.slotName(e.getKey()) + ": " + (sp != null ? sp.label + " " : "")
                            + (sp != null ? e.getValue().part().label().toLowerCase(java.util.Locale.ROOT) : e.getValue().part().label()) + " L" + e.getValue().level())
                            .withStyle(ChatFormatting.DARK_AQUA);
                    if (trait != null && trait != com.cavmkii.seancraft.parts.MaterialTraits.Trait.BALANCED) {
                        row = row.copy().append(Component.literal("  " + trait.label + ": " + trait.effect).withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    lines.add(line++, row);
                }
                for (var slot : family.slots) {
                    if (!com.cavmkii.seancraft.parts.Parts.installed(stack).containsKey(slot)) {
                        lines.add(line++, Component.literal(family.slotName(slot) + ": missing").withStyle(ChatFormatting.RED));
                    }
                }
            }
        });

        // Tooltip titles in Gold light (Appendix C.4), unless rarity or a custom name already colours them.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!lines.isEmpty()) {
                Component title = lines.get(0);
                net.minecraft.network.chat.TextColor c = title.getStyle().getColor();
                if (c == null || c.getValue() == 0xFFFFFF) {
                    lines.set(0, title.copy().withColor(com.cavmkii.seancraft.client.ui.Kit.GOLD_LIGHT & 0xFFFFFF));
                }
            }
        });

        // UI-SPEC tooltips: Ash lines for size, stack, durability and weight class, then the key hints.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            int ash = com.cavmkii.seancraft.client.ui.Kit.ASH & 0xFFFFFF;
            var mc = net.minecraft.client.Minecraft.getInstance();
            String line = "";
            int use = mc.player == null ? 0 : stack.getUseDuration(mc.player);
            if (use > 0 && use < 72000 && !(stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)
                    && !(stack.getItem() instanceof net.minecraft.world.item.ShieldItem)) {
                float sec = use / 20f;
                line = "Use " + (sec == Math.round(sec) ? String.valueOf(Math.round(sec)) : String.format("%.1f", sec)) + " s   ";
            }
            line += stack.getMaxStackSize() > 1 ? "Stack " + stack.getMaxStackSize() : "";
            if (!line.isBlank()) {
                lines.add(Component.literal(line.strip()).withColor(ash));
            }
            if (stack.isDamageableItem() && !type.isAdvanced()) {
                lines.add(Component.literal("Durability " + (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage()).withColor(ash));
            }
            double w = com.cavmkii.seancraft.combat.Load.weaponWeight(stack);
            double a = com.cavmkii.seancraft.combat.Load.armorWeight(stack);
            if (w > 0) {
                String cls = w <= com.cavmkii.seancraft.combat.Load.LIGHT_WEAPON + 0.5 ? "Light" : w >= com.cavmkii.seancraft.combat.Load.HEAVY_WEAPON - 0.5 ? "Heavy" : "Medium";
                lines.add(Component.literal(cls + " weapon: weighs " + String.format("%.1f", w)).withColor(ash));
            } else if (a > 0) {
                var load = com.cavmkii.seancraft.client.combat.ClientCombat.load();
                String share = load.capacity() > 0 ? String.format(" (%d%% of your load)", Math.round(a / load.capacity() * 100)) : "";
                lines.add(Component.literal("Armor: weighs " + String.format("%.1f", a) + share).withColor(ash));
            }
            // The module level needed to craft it (Hideout Bench and module recipes).
            String gate = moduleGate(stack);
            if (gate != null) {
                lines.add(Component.literal(gate + " to craft").withColor(ash));
            }
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.cavmkii.seancraft.client.tarkov.TarkovScreen) {
                lines.add(Component.literal("T: gather list   Shift-click: to storage").withColor(ash));
            }
        });

        // Compact tooltips: the name and one short line; hold Shift for everything. Runs after every other listener,
        // other mods' included, so nothing is added back after it.
        net.minecraft.resources.ResourceLocation compact = com.cavmkii.seancraft.SeanCraft.id("compact_tooltips");
        ItemTooltipCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, compact);
        ItemTooltipCallback.EVENT.register(compact, (stack, context, type, lines) -> compactTooltip(lines));

        // Merch and the bonnet are dyed leather; tint the base layer of their icons.
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex > 0 ? -1 : DyedItemColor.getOrDefault(stack, -1),
                ModItems.MERCH, ModItems.BABY_BONNET);

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (ModItems.DESCRIBED.contains(stack.getItem()) || stack.is(RpgContent.ORB_OF_REGRET) || stack.is(RpgContent.ALTAR_ITEM)
                    || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("seancraft")
                    && net.minecraft.client.resources.language.I18n.exists("item.seancraft." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".desc")) {
                String key = "item.seancraft." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".desc";
                // What it does, in Bone (UI-SPEC tooltips).
                lines.add(1, Component.translatable(key).withColor(com.cavmkii.seancraft.client.ui.Kit.BONE & 0xFFFFFF));
            }
        });
    }

    private static java.util.Map<net.minecraft.world.item.Item, String> gates;

    /** "Medstation 1" for an item a module level unlocks, else null. */
    private static String moduleGate(net.minecraft.world.item.ItemStack stack) {
        if (gates == null) {
            java.util.Map<net.minecraft.world.item.Item, String> map = new java.util.HashMap<>();
            for (com.cavmkii.seancraft.tarkov.HideoutRecipe r : com.cavmkii.seancraft.tarkov.HideoutRecipe.ALL) {
                if (r.gate() != null && r.gateLevel() > 0) {
                    map.putIfAbsent(r.result().get().getItem(), r.gate().label + " " + r.gateLevel());
                }
            }
            gates = map;
        }
        return gates.get(stack.getItem());
    }

    /** Colours a tooltip summary is taken from, in order: our description (Bone), food and part lines (gold), stats. */
    private static final int[] SUMMARY_COLORS = {0xD9CBB0, 0xFFAA00, 0x00AA00, 0x5555FF};

    /** Without Shift: the name, one short line, and a hint that there's more. */
    private static void compactTooltip(List<Component> lines) {
        if (lines.size() <= 2 || net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            return;
        }
        Component title = lines.get(0);
        Component summary = null;
        for (int color : SUMMARY_COLORS) {
            for (int i = 1; i < lines.size() && summary == null; i++) {
                net.minecraft.network.chat.TextColor c = lines.get(i).getStyle().getColor();
                if (c != null && c.getValue() == color && !lines.get(i).getString().isBlank()) {
                    summary = Component.literal(shorten(lines.get(i).getString().trim())).withColor(color);
                }
            }
            if (summary != null) {
                break;
            }
        }
        lines.clear();
        lines.add(title);
        if (summary != null) {
            lines.add(summary);
        }
        boolean inventory = net.minecraft.client.Minecraft.getInstance().screen instanceof com.cavmkii.seancraft.client.tarkov.TarkovScreen;
        lines.add(Component.literal(inventory ? "Shift: details   T: gather list" : "Shift: details").withColor(0x5A5048));
    }

    /** The first sentence, cut to a short line. */
    private static String shorten(String text) {
        int stop = text.indexOf(". ");
        if (stop > 0) {
            text = text.substring(0, stop + 1);
        }
        return text.length() > 46 ? text.substring(0, 44).trim() + "..." : text;
    }
}
