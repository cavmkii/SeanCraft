package com.cavmkii.seancraft.client.carry;

import com.cavmkii.seancraft.carry.Carry;
import com.cavmkii.seancraft.client.rpg.ClientRpg;
import com.cavmkii.seancraft.client.tarkov.ClientTarkov;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client side of carrying a chest or barrel: who's carrying what, your hands locked (no hotbar, no menus, no
 * keys that open things), a hint on screen, and the block drawn in your hands and in front of carriers' chests.
 */
public final class ClientCarry {
    private static final Map<Integer, BlockState> CARRIERS = new HashMap<>();
    private static int lockedSlot;

    private ClientCarry() {}

    public static BlockState of(Entity entity) {
        return CARRIERS.get(entity.getId());
    }

    public static boolean carrying() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && CARRIERS.containsKey(mc.player.getId());
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Carry.State.TYPE, (p, ctx) -> {
            boolean self = ctx.player() != null && ctx.player().getId() == p.entity();
            if (p.block().isPresent()) {
                if (self && !CARRIERS.containsKey(p.entity())) {
                    lockedSlot = ctx.player().getInventory().selected;
                }
                CARRIERS.put(p.entity(), p.block().get());
            } else {
                CARRIERS.remove(p.entity());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> CARRIERS.clear());
        ClientTickEvents.START_CLIENT_TICK.register(ClientCarry::lockHands);
        HudRenderCallback.EVENT.register((g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!carrying() || mc.options.hideGui) {
                return;
            }
            String name = of(mc.player).getBlock().getName().getString();
            String text = "Carrying " + name + "  ·  Shift + right-click to set it down";
            g.drawString(mc.font, text, (g.guiWidth() - mc.font.width(text)) / 2, g.guiHeight() - 72, 0xFFE0C060, true);
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new CarriedLayer(playerRenderer));
            }
        });
    }

    /** Before the game handles input this tick: hold the hotbar slot, eat keys that would open or swap things. */
    private static void lockHands(Minecraft mc) {
        if (!carrying()) {
            return;
        }
        mc.player.getInventory().selected = lockedSlot;
        drain(mc.options.keyInventory, mc.options.keyDrop, mc.options.keySwapOffhand, mc.options.keyPickItem, mc.options.keySprint,
                ClientTarkov.OPEN_STATUS, ClientRpg.OPEN_WEB);
        drain(mc.options.keyHotbarSlots);
        drain(ClientRpg.ABILITY_KEYS);
        mc.player.setSprinting(false);
        Screen screen = mc.screen;
        if (screen != null && !(screen instanceof PauseScreen || screen instanceof ChatScreen || screen instanceof DeathScreen)
                && (screen instanceof AbstractContainerScreen<?> || screen.getClass().getPackageName().startsWith("com.cavmkii"))) {
            mc.player.closeContainer();
            mc.setScreen(null);
        }
    }

    private static void drain(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            while (key.consumeClick()) {
                // swallowed
            }
        }
    }

    /** First person: the block held out in front, low in the view. Returns true if it drew (the hands are skipped). */
    public static boolean renderFirstPerson(PoseStack pose, MultiBufferSource.BufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.player == null ? null : of(mc.player);
        if (state == null) {
            return false;
        }
        pose.pushPose();
        pose.translate(0, -0.5, -1.05);
        pose.mulPose(Axis.XP.rotationDegrees(8));
        pose.scale(0.75f, 0.75f, 0.75f);
        pose.translate(-0.5, -0.5, -0.5);
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        buffers.endBatch();
        return true;
    }

    /** Third person and other players: the block held against the chest, arms out (see the model mixin). */
    private static final class CarriedLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        CarriedLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            BlockState state = of(player);
            if (state == null) {
                return;
            }
            pose.pushPose();
            // Model space has y pointing down; in front of the body is -z.
            pose.translate(0, 0.42, -0.62);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(0.62f, 0.62f, 0.62f);
            pose.translate(-0.5, -0.5, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
