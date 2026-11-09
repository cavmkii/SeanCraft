package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.client.ui.Kit;
import com.cavmkii.seancraft.tarkov.TarkovNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tabs along the top of a station's screen, one per kind of station in your hideout (Hideout Bench, Weapon Bench,
 * crafting table), so you can hop between them without walking over.
 */
public final class StationTabs {
    public static final String HIDEOUT = "hideout";
    public static final String WEAPON_BENCH = "weapon_bench";
    public static final String CRAFTING_TABLE = "crafting_table";
    private static final int W = 80;
    private static final int H = 13;

    public static TarkovNet.StationList stations = new TarkovNet.StationList(List.of(), List.of());

    private StationTabs() {}

    private static String moduleLabel(String kind) {
        for (com.cavmkii.seancraft.tarkov.Module m : com.cavmkii.seancraft.tarkov.Module.values()) {
            if (com.cavmkii.seancraft.tarkov.Stations.moduleKind(m).equals(kind)) {
                return m.label.toUpperCase();
            }
        }
        return kind.toUpperCase();
    }

    public static String label(String kind) {
        return switch (kind) {
            case HIDEOUT -> "HOME BENCH";
            case WEAPON_BENCH -> "WEAPON BENCH";
            case CRAFTING_TABLE -> "CRAFTING";
            default -> kind.startsWith("module_") ? moduleLabel(kind) : kind.toUpperCase();
        };
    }

    /** True while the station tabs are on screen (screens leave room for them). */
    public static boolean visible() {
        return shown();
    }

    /** Only in a hideout, and only worth showing with somewhere else to go. */
    private static boolean shown() {
        return ClientTarkov.place.home() && stations.kinds().size() > 1;
    }

    /** A row of tabs centred over a panel of width {@code w} starting at {@code x}, with its top at {@code y}. */
    public static void render(GuiGraphics g, Font font, int x, int y, int w, String current, int mouseX, int mouseY) {
        if (!shown()) {
            return;
        }
        List<String> kinds = stations.kinds();
        int start = x + (w - kinds.size() * (W + 2)) / 2;
        for (int i = 0; i < kinds.size(); i++) {
            int tx = start + i * (W + 2);
            boolean active = kinds.get(i).equals(current);
            boolean over = mouseX >= tx && mouseX < tx + W && mouseY >= y && mouseY < y + H;
            Kit.button(g, font, label(kinds.get(i)), tx, y, W, H, true, over && !active, active);
        }
    }

    /** True if a tab was clicked (and its station asked for). */
    public static boolean click(int x, int y, int w, String current, double mouseX, double mouseY) {
        if (!shown()) {
            return false;
        }
        List<String> kinds = stations.kinds();
        int start = x + (w - kinds.size() * (W + 2)) / 2;
        for (int i = 0; i < kinds.size(); i++) {
            int tx = start + i * (W + 2);
            if (mouseX >= tx && mouseX < tx + W && mouseY >= y && mouseY < y + H) {
                if (!kinds.get(i).equals(current)) {
                    ClientPlayNetworking.send(new TarkovNet.OpenStation(stations.positions().get(i)));
                }
                return true;
            }
        }
        return false;
    }
}
