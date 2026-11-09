package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.tetris.InvData;
import com.cavmkii.seancraft.tetris.Tetris;
import com.cavmkii.seancraft.tetris.TetrisNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.item.ItemStack;

/** Client side of the INVENTORY screen: the last sync, and E opening it. */
public final class ClientTetris {
    public static TetrisNet.Sync sync = new TetrisNet.Sync(false, 0, List.of(), true, false, List.of(), false);
    public static TetrisNet.CraftList craft = new TetrisNet.CraftList(List.of(), java.util.Collections.nCopies(9, ItemStack.EMPTY),
            ItemStack.EMPTY, false, List.of(), true, false, "");

    /** How many syncs have arrived, so a screen can wait for the one after its own move. */
    public static int syncs;

    private ClientTetris() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TetrisNet.Sync.TYPE, (payload, ctx) -> {
            sync = payload;
            syncs++;
            InvData view = new InvData();
            view.autoRefill = payload.autoRefill();
            view.refillFromStash = payload.refillFromStash();
            view.tracked.addAll(payload.tracked());
            Tetris.clientView = view;
        });
        ClientPlayNetworking.registerGlobalReceiver(TetrisNet.CraftList.TYPE, (payload, ctx) -> craft = payload);
        ClientPlayNetworking.registerGlobalReceiver(TetrisNet.Open.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.screen instanceof TarkovScreen screen) {
                screen.setTab(payload.tab());
            } else if (mc.screen == null) {
                mc.setScreen(new TarkovScreen(payload.tab()));
            }
        });
        // The vanilla tutorial (open inventory, craft planks) can't see SeanCraft's screen, so its toasts would never clear.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
        });
        // Before vanilla handles keys: in survival, E opens the Tarkov screen. Riding something with its own
        // inventory (horse, chest boat) keeps the vanilla behaviour.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.screen != null || !Tetris.active(mc.player)
                    || mc.player.getVehicle() instanceof HasCustomInventoryScreen) {
                return;
            }
            boolean open = false;
            while (mc.options.keyInventory.consumeClick()) {
                open = true;
            }
            if (open) {
                open(TarkovScreen.GEAR);
            }
        });
    }

    public static void open(int tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            mc.setScreen(new TarkovScreen(tab));
        }
    }

    /** Same order as {@link Tetris#carried}: the vanilla inventory, then the off-hand. */
    public static List<ItemStack> carried() {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> out = new ArrayList<>(mc.player.getInventory().items);
        out.add(mc.player.getOffhandItem());
        return out;
    }
}
