package com.cavmkii.seancraft.client.rpg;

import com.cavmkii.seancraft.rpg.ActiveSkill;
import com.cavmkii.seancraft.rpg.Archetype;
import com.cavmkii.seancraft.rpg.Attr;
import com.cavmkii.seancraft.rpg.PassiveWeb;
import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.RpgNet;
import com.cavmkii.seancraft.rpg.Skill;
import com.cavmkii.seancraft.rpg.Stats;
import com.cavmkii.seancraft.rpg.WebNode;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The passive web (design doc 6.2). First you pick an origin. Until you choose a specialization you see your starter
 * web, with a faint outline of the rest; choosing one plays a one-time zoom-out to the whole ring. Drag to pan, scroll to
 * zoom, click a connected node to take it, Shift-click a learned skill to rank it up. Click a learned skill, then a slot
 * at the bottom, to slot it (in a hideout); right-click a slot to empty it.
 */
public class WebScreen extends Screen {
    private static final int PANEL = 128;
    private static final int SLOT = 20;

    private double panX;
    private double panY;
    private double zoom = 1;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private ActiveSkill selected;
    /** The one-time reveal (6.2.3) has played this session. */
    private static boolean revealSeen;
    /** Ticks into the reveal animation, or -1. */
    private int reveal = -1;
    private static final int REVEAL_TICKS = 50;
    private static final double STARTER_ZOOM = 1.4;

    public WebScreen() {
        super(Component.literal("Passive Web"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        PlayerData data = ClientRpg.data;
        if (data.origin != null && !data.revealed) {
            // The starter web, centred.
            zoom = STARTER_ZOOM;
            double[] c = starterCenter(data);
            panX = -c[0] * scale();
            panY = -c[1] * scale();
        } else if (data.revealed && !revealSeen) {
            reveal = 0;
        }
    }

    @Override
    public void tick() {
        PlayerData data = ClientRpg.data;
        if (data.revealed && !revealSeen && reveal < 0) {
            reveal = 0;
        }
        if (reveal >= 0) {
            // Zoom out from your starter web to the whole ring.
            double t = Math.min(1, ++reveal / (double) REVEAL_TICKS);
            double ease = t * t * (3 - 2 * t);
            double[] c = starterCenter(data);
            zoom = STARTER_ZOOM + (1 - STARTER_ZOOM) * ease;
            panX = -c[0] * scale() * (1 - ease);
            panY = -c[1] * scale() * (1 - ease);
            if (t >= 1) {
                reveal = -1;
                revealSeen = true;
            }
        }
    }

    /** The middle of your starter web, in web units. */
    private static double[] starterCenter(PlayerData data) {
        double x = 0;
        double y = 0;
        int n = 0;
        for (WebNode node : PassiveWeb.NODES) {
            if (PassiveWeb.inStarter(node, data.origin)) {
                x += node.x;
                y += node.y;
                n++;
            }
        }
        return n == 0 ? new double[] {0, 0} : new double[] {x / n, y / n};
    }

    /** Whether a node shows fully: everything once the web is open, otherwise your starter web. */
    private static boolean visible(WebNode n, PlayerData data) {
        return data.revealed || PassiveWeb.inStarter(n, data.origin);
    }

    private double scale() {
        double fit = Math.min(width - PANEL - 10, height - SLOT - 20) / 2.0 / 8.4;
        return fit * zoom;
    }

    private double centerX() {
        return PANEL + (width - PANEL) / 2.0 + panX;
    }

    private double centerY() {
        return (height - SLOT - 8) / 2.0 + panY;
    }

    private double sx(WebNode n) {
        return centerX() + n.x * scale();
    }

    private double sy(WebNode n) {
        return centerY() + n.y * scale();
    }

    private double size(WebNode n) {
        double base = switch (n.kind) {
            case ORIGIN, GATE -> 5;
            case SMALL -> 2.5;
            case NOTABLE -> 3.5;
            case ACTIVE -> 4.5;
            case KEYSTONE -> 5.5;
        };
        return Math.max(1.5, base * scale() / 12);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF0101014);
        PlayerData data = ClientRpg.data;
        if (data.origin == null) {
            renderOrigins(g, mouseX, mouseY);
            return;
        }

        // Links.
        for (WebNode n : PassiveWeb.NODES) {
            for (int linkId : n.links) {
                if (linkId < n.id) {
                    continue;
                }
                WebNode m = PassiveWeb.get(linkId);
                boolean a = data.isAllocated(n.id);
                boolean b = data.isAllocated(m.id);
                int color = a && b ? 0xFFE0C060 : a || b ? 0xFF8A8A8A : 0xFF45454C;
                if (!visible(n, data) || !visible(m, data)) {
                    color = 0x2045454C;
                }
                line(g, sx(n), sy(n), sx(m), sy(m), color);
            }
        }

        // Nodes.
        WebNode hovered = null;
        for (WebNode n : PassiveWeb.NODES) {
            double x = sx(n);
            double y = sy(n);
            double r = size(n);
            if (x < PANEL - r || x > width + r || y < -r || y > height + r) {
                continue;
            }
            if (!visible(n, data)) {
                // A glimpse of what's beyond the starter web (6.2.2): outlines only.
                int gx = (int) Math.round(x);
                int gy = (int) Math.round(y);
                int gr = (int) Math.max(1, Math.round(r * 0.7));
                g.fill(gx - gr, gy - gr, gx + gr, gy + gr, 0x18FFFFFF);
                continue;
            }
            boolean allocated = data.isAllocated(n.id);
            boolean available = data.canAllocate(n.id) || data.canRankUp(n.id);
            int color = n.kind == WebNode.Kind.GATE ? 0xFFF2F2F2 : n.archetype != null ? n.archetype.color : 0xFFDDDDDD;
            int border = allocated ? 0xFFFFE080 : available ? 0xFFFFFFFF : 0xFF4A4A50;
            int fill = allocated ? color : (color & 0x00FFFFFF) | (available ? 0x90000000 : 0x40000000);
            if (n.active != null && n.active == selected) {
                border = 0xFF55FFFF;
            }
            int x0 = (int) Math.round(x - r);
            int y0 = (int) Math.round(y - r);
            int x1 = (int) Math.round(x + r);
            int y1 = (int) Math.round(y + r);
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
            g.fill(x0, y0, x1, y1, 0xFF15151A);
            g.fill(x0, y0, x1, y1, fill);
            if (n.active != null && r >= 4) {
                g.pose().pushPose();
                float s = (float) (r * 2 / 16.0);
                g.pose().translate(x - r, y - r, 0);
                g.pose().scale(s, s, 1);
                g.renderItem(new ItemStack(n.active.icon), 0, 0);
                g.pose().popPose();
            }
            if (Math.abs(mouseX - x) <= r + 1 && Math.abs(mouseY - y) <= r + 1) {
                hovered = n;
            }
        }

        renderPanel(g, data);
        int hoveredSlot = renderSlots(g, data, mouseX, mouseY);

        if (hovered != null && mouseX > PANEL) {
            g.renderTooltip(font, tooltip(hovered, data), Optional.empty(), mouseX, mouseY);
        } else if (hoveredSlot >= 0) {
            ActiveSkill skill = data.slot(hoveredSlot);
            List<Component> lines = new ArrayList<>();
            if (hoveredSlot == PlayerData.ULTIMATE_SLOT) {
                lines.add(Component.literal("Ultimate (V)").withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal(data.spec == null ? "Comes with your specialization." : data.spec.label + "'s ultimate isn't in yet.")
                        .withStyle(ChatFormatting.GRAY));
            } else if (skill != null) {
                lines.add(Component.literal(skill.label).withStyle(ChatFormatting.YELLOW));
                lines.add(Component.literal(skill.description).withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Right-click to empty").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.literal("Empty slot").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Click a learned skill on the web, then this slot").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (hoveredSlot != PlayerData.ULTIMATE_SLOT) {
                lines.add(Component.literal("The loadout only changes in a hideout").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(WebNode n, PlayerData data) {
        List<Component> lines = new ArrayList<>();
        ChatFormatting nameColor = switch (n.kind) {
            case KEYSTONE -> ChatFormatting.GOLD;
            case NOTABLE -> ChatFormatting.YELLOW;
            case ACTIVE -> ChatFormatting.AQUA;
            default -> ChatFormatting.WHITE;
        };
        lines.add(Component.literal(n.name).withStyle(nameColor));
        String kind = switch (n.kind) {
            case ORIGIN -> n.origin.label + " start";
            case GATE -> "Specialization gate";
            case SMALL -> "Passive";
            case NOTABLE -> "Notable passive";
            case ACTIVE -> "Active skill";
            case KEYSTONE -> "Keystone";
        };
        lines.add(Component.literal(kind + (n.archetype != null ? " - " + n.archetype.label : "")).withStyle(ChatFormatting.DARK_GRAY));
        for (String line : n.describe()) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (n.kind == WebNode.Kind.GATE && !data.isAllocated(n.id)) {
            if (n.spec.origin != data.origin) {
                lines.add(Component.literal("Only a " + n.spec.origin.label + " can take this.").withStyle(ChatFormatting.RED));
            } else if (data.spec != null) {
                lines.add(Component.literal("You're already a " + data.spec.label + ".").withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.literal("Needs level " + com.cavmkii.seancraft.rpg.Specialization.LEVEL + " (you're " + data.level
                        + "), any tier 1 trophy, and to be in a hideout.").withStyle(ChatFormatting.GRAY));
            }
        }
        if (n.kind != WebNode.Kind.ORIGIN) {
            if (data.isAllocated(n.id) && n.active != null) {
                int rank = data.rank(n.active);
                lines.add(Component.literal("Rank " + rank + "/" + PlayerData.MAX_RANK + ". Click, then a slot below.").withStyle(ChatFormatting.GREEN));
                if (data.canRankUp(n.id)) {
                    lines.add(Component.literal("Shift-click to rank up (1 point): +15% damage, -10% cost").withStyle(ChatFormatting.GREEN));
                }
            } else if (data.isAllocated(n.id)) {
                lines.add(Component.literal(n.kind == WebNode.Kind.GATE ? "Your specialization" : "Allocated").withStyle(ChatFormatting.GREEN));
            } else if (data.canAllocate(n.id)) {
                lines.add(Component.literal("Click to allocate").withStyle(ChatFormatting.GREEN));
            } else if (data.freePoints() <= 0) {
                lines.add(Component.literal("No points left").withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.literal("Not connected to your web").withStyle(ChatFormatting.RED));
            }
        }
        return lines;
    }

    private void renderPanel(GuiGraphics g, PlayerData data) {
        g.fill(0, 0, PANEL, height, 0xFF1A1A20);
        g.fill(PANEL - 1, 0, PANEL, height, 0xFF3A3A44);
        Stats stats = ClientRpg.stats;
        float s = 0.75f;
        g.pose().pushPose();
        g.pose().scale(s, s, 1);
        int x = 6;
        int y = 6;
        int lh = 10;
        g.drawString(font, "Level " + data.level + (data.level >= PlayerData.MAX_LEVEL ? " (max)" : ""), x, y, 0xFFFFE080);
        y += lh;
        if (data.level < PlayerData.MAX_LEVEL) {
            long need = PlayerData.xpToNext(data.level);
            int w = (int) ((PANEL - 10) / s);
            g.fill(x, y, x + w, y + 4, 0xFF000000);
            g.fill(x, y, x + (int) (w * Math.min(1, data.xp / (double) need)), y + 4, 0xFF9B59B6);
            y += 6;
            g.drawString(font, data.xp + " / " + need + " xp", x, y, 0xFF999999);
            y += lh;
        }
        g.drawString(font, "Points: " + data.freePoints() + " free" + (data.lostPoints > 0 ? ", " + data.lostPoints + " lost" : ""), x, y,
                data.freePoints() > 0 ? 0xFF55FF55 : 0xFFAAAAAA);
        y += lh;
        g.drawString(font, data.origin.label + (data.spec != null ? ", " + data.spec.label : ""), x, y, 0xFFCCCCCC);
        y += lh;
        g.drawString(font, "Path: " + path(data), x, y, 0xFF999999);
        y += lh + 4;

        attrTop = y;
        for (Attr attr : Attr.values()) {
            g.drawString(font, attr.label + ": " + String.format("%.1f", stats.attr(attr)), x, y, 0xFF000000 | attr.color);
            if (data.unassigned > 0) {
                g.drawString(font, "[+]", (int) ((PANEL - 8) / s) - font.width("[+]"), y, 0xFF55FF55);
            }
            y += lh;
        }
        if (data.unassigned > 0) {
            g.drawString(font, data.unassigned + " attribute points to place", x, y, 0xFF55FF55);
            y += lh;
        }
        g.drawString(font, String.format("Crit %.0f%% for +%.0f%%", stats.critChance(), stats.critDamage()), x, y, 0xFF999999);
        y += lh;
        g.drawString(font, String.format("+%.0f health, +%.0f%% swing speed", stats.bonusHealth(), stats.attackSpeedPct()), x, y, 0xFF999999);
        y += lh;
        g.drawString(font, String.format("AGI stamina -%.0f%%, prep +%.0f%%", stats.staminaEfficiency() * 100, (stats.preparation() - 1) * 100), x, y,
                0xFF999999);
        y += lh;
        g.drawString(font, String.format("Mana %.0f  Rage %.0f  Stamina %.0f", stats.maxMana(), stats.maxRage(), stats.maxStamina()), x, y,
                0xFF999999);
        y += lh + 4;

        g.drawString(font, "Skills", x, y, 0xFFFFE080);
        y += lh;
        for (Skill skill : Skill.values()) {
            int level = data.skillLevel(skill);
            double xp = data.skillXp.getOrDefault(skill, 0.0);
            double from = Skill.xpForLevel(level);
            double to = Skill.xpForLevel(Math.min(Skill.MAX_LEVEL, level + 1));
            int w = 40;
            int bx = x + (int) ((PANEL - 8) / s) - w;
            g.drawString(font, skill.label, x, y, 0xFF000000 | skill.color());
            g.drawString(font, String.valueOf(level), bx - 4 - font.width(String.valueOf(level)), y, 0xFFFFFFFF);
            g.fill(bx, y + 3, bx + w, y + 6, 0xFF000000);
            if (level < Skill.MAX_LEVEL && to > from) {
                g.fill(bx, y + 3, bx + (int) (w * Math.min(1, (xp - from) / (to - from))), y + 6, 0xFF000000 | skill.color());
            }
            y += lh;
        }
        g.pose().popPose();
    }

    /** Which archetypes your web leans into, most first. */
    private static String path(PlayerData data) {
        Map<Archetype, Integer> counts = new EnumMap<>(Archetype.class);
        for (int id : data.nodes) {
            WebNode n = PassiveWeb.get(id);
            if (n != null && n.archetype != null) {
                counts.merge(n.archetype, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none yet";
        }
        List<Map.Entry<Archetype, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, sorted.size()); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sorted.get(i).getKey().label).append(' ').append(sorted.get(i).getValue());
        }
        return sb.toString();
    }

    private int slotX(int slot) {
        return (int) (PANEL + (width - PANEL) / 2.0 - PlayerData.SLOTS * SLOT / 2.0) + slot * SLOT;
    }

    private int slotY() {
        return height - SLOT - 4;
    }

    private int renderSlots(GuiGraphics g, PlayerData data, int mouseX, int mouseY) {
        int hovered = -1;
        int y = slotY();
        g.fill(slotX(0) - 2, y - 2, slotX(PlayerData.SLOTS - 1) + SLOT + 1, y + SLOT + 1, 0xC0000000);
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            int x = slotX(s);
            boolean open = data.slotOpen(s);
            boolean hover = mouseX >= x && mouseX < x + SLOT - 1 && mouseY >= y && mouseY < y + SLOT - 1;
            if (hover) {
                hovered = s;
            }
            g.fill(x, y, x + SLOT - 1, y + SLOT - 1, hover && selected != null && open ? 0xFF55FFFF : 0xFF2A2A2A);
            g.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, open ? 0xFF3C3C3C : 0xFF1C1C1C);
            ActiveSkill skill = data.slot(s);
            if (open && skill != null) {
                g.renderItem(new ItemStack(skill.icon), x + 1, y + 1);
            } else if (s == PlayerData.ULTIMATE_SLOT) {
                g.drawString(font, "U", x + (SLOT - font.width("U")) / 2, y + 6, open ? 0xFFFFD040 : 0xFF666666, false);
            }
        }
        if (selected != null) {
            String hint = "Placing " + selected.label + ": click a slot";
            g.drawString(font, hint, slotX(0) + PlayerData.SLOTS * SLOT / 2 - font.width(hint) / 2, y - 12, 0xFF55FFFF);
        }
        return hovered;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressX = mouseX;
        pressY = mouseY;
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
            dragging = true;
        }
        if (dragging) {
            panX += dragX;
            panY += dragY;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) {
            click(mouseX, mouseY, button);
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double old = zoom;
        zoom = Math.max(0.6, Math.min(3.5, zoom * (scrollY > 0 ? 1.15 : 1 / 1.15)));
        // Zoom around the cursor.
        double factor = zoom / old;
        double cx = PANEL + (width - PANEL) / 2.0;
        double cy = (height - SLOT - 8) / 2.0;
        panX = (panX + cx - mouseX) * factor - (cx - mouseX);
        panY = (panY + cy - mouseY) * factor - (cy - mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientRpg.OPEN_WEB.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void click(double mouseX, double mouseY, int button) {
        PlayerData data = ClientRpg.data;
        if (data.origin == null) {
            int o = originAt(mouseX, mouseY);
            if (o >= 0) {
                ClientPlayNetworking.send(new RpgNet.ChooseOrigin(o));
            }
            return;
        }
        if (data.unassigned > 0 && mouseX < PANEL) {
            // [+] beside an attribute places a point.
            float s = 0.75f;
            for (int i = 0; i < Attr.values().length; i++) {
                double ly = (attrTop + i * 10) * s;
                if (mouseY >= ly && mouseY < ly + 10 * s && mouseX >= PANEL - 30) {
                    ClientPlayNetworking.send(new RpgNet.AssignAttribute(i));
                    return;
                }
            }
        }
        int y = slotY();
        for (int s = 0; s < PlayerData.SLOTS; s++) {
            int x = slotX(s);
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                if (!data.slotOpen(s)) {
                    return;
                }
                if (button == 1) {
                    ClientPlayNetworking.send(new RpgNet.SetSlot(s, ""));
                } else if (selected != null) {
                    ClientPlayNetworking.send(new RpgNet.SetSlot(s, selected.name()));
                    selected = null;
                }
                return;
            }
        }
        if (mouseX < PANEL) {
            return;
        }
        for (WebNode n : PassiveWeb.NODES) {
            double r = size(n) + 1;
            if (Math.abs(mouseX - sx(n)) <= r && Math.abs(mouseY - sy(n)) <= r) {
                if (!visible(n, data)) {
                    return;
                }
                if (hasShiftDown() && data.canRankUp(n.id) && button == 0) {
                    ClientPlayNetworking.send(new RpgNet.Allocate(n.id));
                } else if (data.canAllocate(n.id) && button == 0) {
                    ClientPlayNetworking.send(new RpgNet.Allocate(n.id));
                } else if (data.isAllocated(n.id) && n.active != null) {
                    selected = selected == n.active ? null : n.active;
                }
                return;
            }
        }
        selected = null;
    }

    /** Where the attribute lines start in the panel (in its 0.75 scale), for the [+] buttons. */
    private int attrTop;

    private static final String[] ORIGIN_BLURB = {
            "Strength. Starts in the Warrior region with a wooden club. Becomes a Warrior, Knight or Paladin.",
            "Agility. Starts in the Ranger region with a bow. Becomes an Archer, Blademaster or Rogue.",
            "Intelligence. Starts in the Mage region. Becomes a Sorcerer, Warlock or Cleric."};

    private int originX(int i) {
        int w = Math.min(150, (width - 40) / 3);
        return (width - (w * 3 + 20)) / 2 + i * (w + 10);
    }

    private int originAt(double mouseX, double mouseY) {
        int w = Math.min(150, (width - 40) / 3);
        for (int i = 0; i < 3; i++) {
            if (mouseX >= originX(i) && mouseX < originX(i) + w && mouseY >= height / 2 - 50 && mouseY < height / 2 + 50) {
                return i;
            }
        }
        return -1;
    }

    /** Character creation (6.2.1): pick one of three origins. It never changes. */
    private void renderOrigins(GuiGraphics g, int mouseX, int mouseY) {
        String title = "Choose your origin";
        g.drawString(font, title, (width - font.width(title)) / 2, height / 2 - 76, 0xFFFFE080);
        String sub = "Sets your attribute, starter web and specializations. It never changes.";
        g.drawString(font, sub, (width - font.width(sub)) / 2, height / 2 - 64, 0xFF999999);
        int w = Math.min(150, (width - 40) / 3);
        int hover = originAt(mouseX, mouseY);
        com.cavmkii.seancraft.rpg.Origin[] origins = com.cavmkii.seancraft.rpg.Origin.values();
        for (int i = 0; i < origins.length; i++) {
            int x = originX(i);
            int y = height / 2 - 50;
            int color = 0xFF000000 | origins[i].attr.color;
            g.fill(x - 1, y - 1, x + w + 1, y + 101, hover == i ? 0xFFFFFFFF : color);
            g.fill(x, y, x + w, y + 100, 0xFF1A1A20);
            g.drawString(font, origins[i].label, x + 6, y + 6, color);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(ORIGIN_BLURB[i]), w - 12);
            int ly = y + 20;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 6, ly, 0xFFCCCCCC);
                ly += 10;
            }
        }
    }

    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, 0, Math.round(length), 1, color);
        g.pose().popPose();
    }
}
