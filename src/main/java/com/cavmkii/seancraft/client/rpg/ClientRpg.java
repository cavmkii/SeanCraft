package com.cavmkii.seancraft.client.rpg;

import com.cavmkii.seancraft.rpg.PlayerData;
import com.cavmkii.seancraft.rpg.RpgNet;
import com.cavmkii.seancraft.rpg.Stats;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** The client's copy of the local player's character, plus keys, popups and the HUD hook. */
public final class ClientRpg {
    public static PlayerData data = new PlayerData();
    public static Stats stats = new Stats(data);
    public static float mana;
    public static float rage;
    public static float stamina;
    public static int[] cooldowns = new int[PlayerData.SLOTS];
    public static int[] cooldownTotals = new int[PlayerData.SLOTS];

    private static final String CATEGORY = "key.categories.seancraft";
    public static final KeyMapping OPEN_WEB = new KeyMapping("key.seancraft.web", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.seancraft.ability1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.seancraft.ability2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.seancraft.ability3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.seancraft.ultimate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };

    private static final SystemToast.SystemToastId LEVEL_TOAST = new SystemToast.SystemToastId(5000L);
    private static final SystemToast.SystemToastId SKILL_TOAST = new SystemToast.SystemToastId(3500L);

    private ClientRpg() {}

    public static void init() {
        KeyBindingHelper.registerKeyBinding(OPEN_WEB);
        for (KeyMapping key : ABILITY_KEYS) {
            KeyBindingHelper.registerKeyBinding(key);
        }

        ClientPlayNetworking.registerGlobalReceiver(RpgNet.Sync.TYPE, (payload, ctx) -> {
            data = payload.data();
            stats = new Stats(data);
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.Resources.TYPE, (payload, ctx) -> {
            mana = payload.mana();
            rage = payload.rage();
            stamina = payload.stamina();
            cooldowns = payload.cooldowns();
            cooldownTotals = payload.cooldownTotals();
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.Toast.TYPE, (payload, ctx) -> {
            boolean level = payload.title().startsWith("Level");
            SystemToast.addOrUpdate(ctx.client().getToasts(), level ? LEVEL_TOAST : SKILL_TOAST,
                    Component.literal(payload.title()), Component.literal(payload.body()));
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.OpenRespec.TYPE, (payload, ctx) -> RespecScreen.open(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            data = new PlayerData();
            stats = new Stats(data);
        });

        ClientTickEvents.END_CLIENT_TICK.register(ClientRpg::tick);
        HudRenderCallback.EVENT.register(RpgHud::render);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        while (OPEN_WEB.consumeClick()) {
            com.cavmkii.seancraft.client.tarkov.ClientTetris.open(com.cavmkii.seancraft.client.tarkov.TarkovScreen.WEB);
        }
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (mc.screen == null && data.slot(i) != null) {
                    ClientPlayNetworking.send(new RpgNet.Cast(i));
                }
            }
        }
    }
}
