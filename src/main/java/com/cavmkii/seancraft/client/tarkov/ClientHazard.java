package com.cavmkii.seancraft.client.tarkov;

import com.cavmkii.seancraft.hazard.Hazards;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;

/** The biome hazard you're standing in, from the server once a second (design doc 9.3.3). */
public final class ClientHazard {
    static Hazards.Hazard hazard;
    static int state;
    private static int passSeconds;
    private static long receivedAt;

    private ClientHazard() {}

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Hazards.Sync.TYPE, (payload, ctx) -> {
            hazard = payload.hazard() < 0 ? null : Hazards.Hazard.values()[payload.hazard()];
            state = payload.state();
            passSeconds = payload.passSeconds();
            receivedAt = Util.getMillis();
        });
    }

    /** Seconds left on the pass, counting down between updates. */
    static int passLeft() {
        return Math.max(0, passSeconds - (int) ((Util.getMillis() - receivedAt) / 1000));
    }
}
