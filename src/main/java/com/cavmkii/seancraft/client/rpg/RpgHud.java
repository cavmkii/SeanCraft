package com.cavmkii.seancraft.client.rpg;

import com.cavmkii.seancraft.rpg.ActiveSkill;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.Resource;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** The ability hotbar (right of the vanilla hotbar), the stamina bar, and bars for the resources your slotted skills use. */
final class RpgHud {
    static final int SLOT = 20;

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
            int y = g.guiHeight() - 7;
            bar(g, mc.font, x0, y, width, ClientRpg.stamina, ClientRpg.stats.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina(), Resource.STAMINA.color);
            unspent(g, mc.font, x0, y - 8, data);
            return;
        }
        g.fill(x0 - 2, y0 - 2, x0 + PlayerData.SLOTS * SLOT + 1, y0 + SLOT + 1, 0x90000000);
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
            }
        }
        int y = y0 - 7;
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
            bar(g, mc.font, x0, y, width, value, max, resource.color);
            y -= 7;
        }
        unspent(g, mc.font, x0, y - 1, data);
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
        g.drawString(font, text, 0, 0, 0xFFFFE060, true);
        g.pose().popPose();
    }

    private static void drawSlot(GuiGraphics g, Font font, int x, int y, int slot, boolean open) {
        g.fill(x, y, x + SLOT - 1, y + SLOT - 1, open ? 0xFF2A2A2A : 0xFF151515);
        g.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, open ? 0xFF3C3C3C : 0xFF1C1C1C);
        if (!open) {
            // The ultimate slot, until a specialization opens it.
            g.drawString(font, "U", x + (SLOT - font.width("U")) / 2, y + 6, 0xFF666666, false);
            return;
        }
        ActiveSkill skill = ClientRpg.data.slot(slot);
        com.cavmkii.seancraft.rpg.Ultimate ult = slot == PlayerData.ULTIMATE_SLOT && ClientRpg.data.spec != null
                ? com.cavmkii.seancraft.rpg.Ultimate.of(ClientRpg.data.spec) : null;
        net.minecraft.world.item.Item icon = skill != null ? skill.icon : ult != null ? ult.icon : null;
        if (icon != null) {
            g.renderItem(new ItemStack(icon), x + 1, y + 1);
            int cd = ClientRpg.cooldowns[slot];
            int total = Math.max(1, ClientRpg.cooldownTotals[slot]);
            if (cd > 0) {
                int h = (int) Math.ceil((SLOT - 3) * Math.min(1f, cd / (float) total));
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x + 1, y + SLOT - 2 - h, x + SLOT - 2, y + SLOT - 2, 0xB0000000);
                String secs = cd >= 20 ? String.valueOf((cd + 19) / 20) : String.format("%.1f", cd / 20f);
                g.drawString(font, secs, x + (SLOT - font.width(secs)) / 2, y + 6, 0xFFFFFFFF, true);
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
        g.drawString(font, key, 0, 0, 0xFFFFFF55, true);
        g.pose().popPose();
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int width, float value, float max, int color) {
        g.fill(x, y, x + width, y + 5, 0xC0000000);
        int filled = max > 0 ? (int) (width * Math.min(1f, value / max)) : 0;
        g.fill(x + 1, y + 1, x + 1 + Math.max(0, filled - 2), y + 4, color);
        String text = (int) value + "/" + (int) max;
        g.pose().pushPose();
        g.pose().translate(x + width - font.width(text) * 0.5f - 1, y, 300);
        g.pose().scale(0.5f, 0.5f, 1);
        g.drawString(font, text, 0, 1, 0xFFFFFFFF, true);
        g.pose().popPose();
    }
}
