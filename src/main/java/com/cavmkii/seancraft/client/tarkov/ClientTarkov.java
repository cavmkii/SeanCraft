package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.tarkov.TarkovNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client side of the raid loop: raid HUD, Hideout screen, and the J (gear) and H (status) keys. */
public final class ClientTarkov {
    public static TarkovNet.RaidState raid = new TarkovNet.RaidState(0, 0, 0, -1, 0, -1, false);

    public static TarkovNet.Banner banner;
    public static long bannerAt;

    public static TarkovNet.BodySync body = new TarkovNet.BodySync(new float[] {1, 1, 1, 1, 1, 1, 1}, new int[7], 0, new float[7], new float[7], new float[7]);

    public static final KeyMapping OPEN_STATUS = new KeyMapping("key.seancraft.status", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.seancraft");

    public static final KeyMapping OPEN_SECURE = new KeyMapping("key.seancraft.secure", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.seancraft");

    private ClientTarkov() {}

    public static void init() {
        KeyBindingHelper.registerKeyBinding(OPEN_SECURE);
        KeyBindingHelper.registerKeyBinding(OPEN_STATUS);
        ClientPlayNetworking.registerGlobalReceiver(TarkovNet.BodySync.TYPE, (payload, ctx) -> body = payload);
        ClientHazard.init();
        ClientPlayNetworking.registerGlobalReceiver(TarkovNet.StationList.TYPE, (payload, ctx) -> StationTabs.stations = payload);
        ClientPlayNetworking.registerGlobalReceiver(TarkovNet.Banner.TYPE, (payload, ctx) -> {
            banner = payload;
            bannerAt = net.minecraft.Util.getMillis();
        });
        ClientPlayNetworking.registerGlobalReceiver(TarkovNet.RaidState.TYPE, (payload, ctx) -> raid = payload);
        ClientPlayNetworking.registerGlobalReceiver(TarkovNet.HideoutView.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.screen instanceof HideoutScreen screen && screen.pos.equals(payload.pos())) {
                screen.update(payload);
            } else if (mc.screen == null) {
                mc.setScreen(new HideoutScreen(payload));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_STATUS.consumeClick()) {
                ClientTetris.open(TarkovScreen.STATUS);
            }
            while (OPEN_SECURE.consumeClick()) {
                ClientTetris.open(TarkovScreen.GEAR);
            }
        });
        HudRenderCallback.EVENT.register(InjuryGlow::render);
        HudRenderCallback.EVENT.register(RaidHud::render);
        HudRenderCallback.EVENT.register(ClientTracker::render);
        ClientTetris.init();
        FurnacePanel.init();
    }
}
