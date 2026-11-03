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

public final class SeanCraftClient implements ClientModInitializer {
    private static String partEffects(com.cavmkii.seancraft.parts.PartDef def, int level) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (def.damage(level) != 0) parts.add(String.format("+%.0f%% damage", def.damage(level) * 100));
        if (def.armor(level) != 0) parts.add(String.format("+%.0f%% armor", def.armor(level) * 100));
        if (def.stamina(level) != 0) parts.add(String.format("+%.0f%% swing speed", -def.stamina(level) * 100));
        if (def.durability(level) != 0) parts.add(String.format("+%.0f%% durability", def.durability(level) * 100));
        if (def.block(level) != 0) parts.add(String.format("+%.0f%% block", def.block(level) * 100));
        if (def.weight(level) != 0) parts.add("lighter armor");
        return String.join(", ", parts) + " (vs. no part)";
    }

    private static final int JOE_GREEN = 0xFF8FD46A;
    private static final int BABY_GREEN = 0xFFB4E68C;

    @Override
    public void onInitializeClient() {
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
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.cavmkii.seancraft.pattern.Patterns.Sync.TYPE,
                (payload, ctx) -> com.cavmkii.seancraft.pattern.Patterns.receive(payload));
        ClientTarkov.init();
        com.cavmkii.seancraft.client.food.ClientFood.init();
        com.cavmkii.seancraft.client.boss.ClientBoss.init();
        com.cavmkii.seancraft.client.carry.ClientCarry.init();
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
                com.cavmkii.seancraft.parts.MaterialTraits.Trait trait = com.cavmkii.seancraft.parts.MaterialTraits.of(part.def);
                if (trait != null && trait != com.cavmkii.seancraft.parts.MaterialTraits.Trait.BALANCED) {
                    lines.add(3, Component.literal(trait.label + ": " + trait.effect).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                String inf = com.cavmkii.seancraft.parts.Parts.infusion(stack);
                if (inf != null && part.def.kind() == com.cavmkii.seancraft.parts.PartKind.CRYSTAL) {
                    lines.add(Math.min(lines.size(), 3), Component.literal("Element: " + inf).withStyle(ChatFormatting.AQUA));
                } else if (inf != null) {
                    lines.add(Math.min(lines.size(), 3), Component.literal(Character.toUpperCase(inf.charAt(0)) + inf.substring(1)
                            + " infusion: shifts 25% of a weapon's scaling to " + (inf.equals("heavy") ? "STR" : inf.equals("keen") ? "AGI" : "INT"))
                            .withStyle(ChatFormatting.AQUA));
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
                    Component row = Component.literal(family.slotName(e.getKey()) + ": " + e.getValue().part().label() + " L" + e.getValue().level())
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
            int[] size = com.cavmkii.seancraft.tetris.ItemSize.of(stack);
            String line = "Size " + size[0] + "x" + size[1] + (stack.getMaxStackSize() > 1 ? "   Stacks to " + stack.getMaxStackSize() : "");
            lines.add(Component.literal(line).withColor(ash));
            if (stack.isDamageableItem() && !type.isAdvanced()) {
                lines.add(Component.literal("Durability " + (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage()).withColor(ash));
            }
            double w = com.cavmkii.seancraft.combat.Load.weaponWeight(stack);
            double a = com.cavmkii.seancraft.combat.Load.armorWeight(stack);
            if (w > 0) {
                String cls = w <= com.cavmkii.seancraft.combat.Load.LIGHT_WEAPON + 0.5 ? "Light" : w >= com.cavmkii.seancraft.combat.Load.HEAVY_WEAPON - 0.5 ? "Heavy" : "Medium";
                lines.add(Component.literal(cls + " weapon: weighs " + String.format("%.1f", w)).withColor(ash));
            } else if (a > 0) {
                lines.add(Component.literal("Armor: weighs " + String.format("%.1f", a)).withColor(ash));
            }
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.cavmkii.seancraft.client.tarkov.TarkovScreen) {
                lines.add(Component.literal("T: gather list   Shift: to storage").withColor(ash));
            }
        });

        // Merch and the bonnet are dyed leather; tint the base layer of their icons.
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex > 0 ? -1 : DyedItemColor.getOrDefault(stack, -1),
                ModItems.MERCH, ModItems.BABY_BONNET);

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (ModItems.DESCRIBED.contains(stack.getItem()) || stack.is(RpgContent.ORB_OF_REGRET) || stack.is(RpgContent.ALTAR_ITEM)
                    || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("seancraft")
                    && net.minecraft.client.resources.language.I18n.exists("item.seancraft." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".desc")) {
                String key = "item.seancraft." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".desc";
                lines.add(1, Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
