package com.cavmkii.seancraft.client.rpg;

import com.cavmkii.seancraft.rpg.ActiveSkill;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Resource;
import java.util.EnumSet;
import java.util.Set;
import com.cavmkii.seancraft.client.ui.Kit;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** The ability hotbar (right of the vanilla hotbar), the stamina bar, and bars for the resources your slotted skills use. */
final class RpgHud {
    static final int SLOT = 22;

    private RpgHud() {}

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        PlayerData data = ClientRpg.data;
        boolean any = false;
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            any |= data.slot(s) != null;
        }
        any |= data.spec != null;
        // Clear of Combat Roll's roll counter, which sits just right of the vanilla hotbar, unless the screen is too narrow.
        int x0 = Math.min(g.guiWidth() / 2 + 91 + 28, g.guiWidth() - PlayerData.SLOTS * SLOT - 2);
        int y0 = g.guiHeight() - SLOT - 2;
        int width = PlayerData.SLOTS * SLOT - 1;
        if (!any && data.nodes.isEmpty()) {
            // Nothing learned yet: just the stamina bar every swing and roll uses, and the reminder.
            int y = g.guiHeight() - 9;
            bar(g, mc.font, x0, y, width, ClientRpg.stamina, ClientRpg.stats.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina(), Resource.STAMINA);
            unspent(g, mc.font, x0, y - 8, data);
            return;
        }
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            drawSlot(g, mc.font, x0 + s * SLOT, y0, s, data.slotOpen(s));
        }

        // Stamina (swings and rolls spend it), plus whatever the slotted skills use, stacked above the ability bar.
        Set<Resource> used = EnumSet.of(Resource.STAMINA);
        if (data.spec != null && com.cavmkii.seancraft.rpg.Ultimate.of(data.spec) != null) {
            used.add(com.cavmkii.seancraft.rpg.Ultimate.of(data.spec).resource);
        }
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            ActiveSkill skill = data.slot(s);
            if (skill != null) {
                used.add(skill.resource);
                if (skill.alt != null) {
                    used.add(skill.alt);
                }
            }
        }
        // Each slot's cost plate sits just above it, the bars above those.
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            if (data.slotOpen(s)) {
                costPlate(g, mc.font, x0 + s * SLOT, y0 - 9, s);
            }
        }
        int y = y0 - 18;
        for (Resource resource : Resource.values()) {
            if (!used.contains(resource)) {
                continue;
            }
            float value = switch (resource) {
                case MANA -> ClientRpg.mana;
                case RAGE -> ClientRpg.rage;
                case STAMINA -> ClientRpg.stamina;
            };
            float max = switch (resource) {
                case MANA -> ClientRpg.stats.maxMana();
                case RAGE -> ClientRpg.stats.maxRage();
                case STAMINA -> ClientRpg.stats.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina();
            };
            bar(g, mc.font, x0, y, width, value, max, resource);
            y -= 8;
        }
        unspent(g, mc.font, x0, y - 1, data);
    }

    /**
     * What a slot costs (6.5): the pool's icon and the number. A hybrid skill shows both pools, the one it will draw from
     * now (the fuller) lit and the other dimmed.
     */
    private static void costPlate(GuiGraphics g, Font font, int x, int y, int slot) {
        ActiveSkill skill = ClientRpg.data.slot(slot);
        com.cavmkii.seancraft.rpg.Ultimate ult = slot == PlayerData.ULTIMATE_SLOT && ClientRpg.data.spec != null
                ? com.cavmkii.seancraft.rpg.Ultimate.of(ClientRpg.data.spec) : null;
        if (skill == null && ult == null) {
            return;
        }
        Kit.blit(g, "hud/skill_cost_plate", x, y, SLOT, 9);
        float maxStamina = ClientRpg.stats.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina();
        if (skill != null && skill.alt != null) {
            Resource pick = skill.pool(ClientRpg.mana, ClientRpg.stats.maxMana(), ClientRpg.rage, ClientRpg.stats.maxRage(), ClientRpg.stamina, maxStamina);
            icon(g, skill.resource, pick == skill.resource, x + 2, y);
            icon(g, skill.alt, pick == skill.alt, x + 11, y);
            return;
        }
        Resource pool = skill != null ? skill.resource : ult.resource;
        int cost = skill != null ? skill.cost : ult.cost;
        float have = switch (pool) {
            case MANA -> ClientRpg.mana;
            case RAGE -> ClientRpg.rage;
            case STAMINA -> ClientRpg.stamina;
        };
        icon(g, pool, have >= cost, x + 2, y);
        String text = String.valueOf(cost);
        g.pose().pushPose();
        g.pose().translate(x + 12, y + 3, 300);
        g.pose().scale(0.5f, 0.5f, 1);
        Kit.text(g, font, text, 0, 0, have >= cost ? Kit.BONE : Kit.ASH);
        g.pose().popPose();
    }

    private static void icon(GuiGraphics g, Resource pool, boolean lit, int x, int y) {
        Kit.blit(g, "icon/resource/" + pool.name().toLowerCase(java.util.Locale.ROOT) + (lit ? "" : "_dim"), x, y, 9, 9);
    }

    /** A small reminder to pick an origin, or while you have web or attribute points to spend; nothing otherwise. */
    private static void unspent(GuiGraphics g, Font font, int x, int y, PlayerData data) {
        String text = data.origin == null ? "Choose your origin (K)"
                : data.freePoints() > 0 ? "Unspent web points: " + data.freePoints() + " (K)"
                : data.unassigned > 0 ? "Unplaced attribute points: " + data.unassigned + " (K)" : null;
        if (text == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        Kit.text(g, font, text, 0, 0, Kit.GOLD_LIGHT);
        g.pose().popPose();
    }

    private static void drawSlot(GuiGraphics g, Font font, int x, int y, int slot, boolean open) {
        boolean ultimateSlot = slot == PlayerData.ULTIMATE_SLOT;
        if (!open) {
            // The ultimate slot, until a specialization opens it.
            Kit.slot(g, x, y, SLOT, Kit.SlotState.LOCKED);
            return;
        }
        Kit.blit(g, ultimateSlot ? "hud/skill_slot_ultimate" : "hud/skill_slot_basic", x, y, SLOT, SLOT);
        ActiveSkill skill = ClientRpg.data.slot(slot);
        com.cavmkii.seancraft.rpg.Ultimate ult = slot == PlayerData.ULTIMATE_SLOT && ClientRpg.data.spec != null
                ? com.cavmkii.seancraft.rpg.Ultimate.of(ClientRpg.data.spec) : null;
        net.minecraft.world.item.Item icon = skill != null ? skill.icon : ult != null ? ult.icon : null;
        if (icon != null) {
            g.renderItem(new ItemStack(icon), x + 3, y + 3);
            int cd = ClientRpg.cooldowns[slot];
            int total = Math.max(1, ClientRpg.cooldownTotals[slot]);
            if (cd > 0) {
                int h = (int) Math.ceil((SLOT - 2) * Math.min(1f, cd / (float) total));
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                // The unready fraction under the kit's cooldown overlay, seconds in Bone.
                Kit.blit(g, "hud/skill_cooldown", x + 1, y + SLOT - 1 - h, SLOT - 2, h);
                String secs = cd >= 20 ? String.valueOf((cd + 19) / 20) : String.format("%.1f", cd / 20f);
                Kit.text(g, font, secs, x + (SLOT - font.width(secs)) / 2, y + 7, Kit.BONE);
                g.pose().popPose();
            }
        }
        // Key label, small, bottom right.
        String key = ClientRpg.ABILITY_KEYS[slot].getTranslatedKeyMessage().getString();
        if (key.length() > 2) {
            key = key.substring(0, 2);
        }
        g.pose().pushPose();
        g.pose().translate(x + SLOT - 2 - font.width(key) * 0.5f, y + SLOT - 6, 300);
        g.pose().scale(0.5f, 0.5f, 1);
        Kit.text(g, font, key, 0, 0, ultimateSlot ? Kit.GOLD_LIGHT : Kit.ASH);
        g.pose().popPose();
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int width, float value, float max, Resource resource) {
        // Stamina is Moss, Rage Ember, mana Arcane (C.2): the kit's bar fills.
        Kit.bar(g, resource.name().toLowerCase(java.util.Locale.ROOT), x, y, width, max > 0 ? value / max : 0);
        String text = (int) value + "/" + (int) max;
        g.pose().pushPose();
        g.pose().translate(x + width - font.width(text) * 0.5f - 2, y + 1, 300);
        g.pose().scale(0.5f, 0.5f, 1);
        g.drawString(font, text, 0, 1, 0xFFFFFFFF, true);
        g.pose().popPose();
    }
}
