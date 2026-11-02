package com.cavmkii.seancraft.client.combat;

import com.cavmkii.seancraft.client.rpg.ClientRpg;
import com.cavmkii.seancraft.combat.Combat;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.minecraft.client.Minecraft;

/** Client half of the stamina rules: what the player may start, judged from the synced stamina bar. */
public final class ClientCombat {
    private static boolean exhausted;

    private ClientCombat() {}

    private static float max() {
        return ClientRpg.stats.maxStamina() + com.cavmkii.seancraft.client.food.ClientFood.stamina();
    }

    /** Same rule as the server: run dry and sprint and rolls stay off until the bar is back to a quarter. */
    public static boolean exhausted() {
        if (ClientRpg.stamina <= 0.01f) {
            exhausted = true;
        } else if (ClientRpg.stamina >= max() * Combat.RECOVER_FRACTION) {
            exhausted = false;
        }
        return exhausted && !creative();
    }

    /** Rolls need the stamina for one, and can't start during a swing: you commit to an attack (9.6.3). */
    /** The local player's equip load, worked out the same way the server does. */
    public static com.cavmkii.seancraft.combat.Load.State load() {
        return com.cavmkii.seancraft.combat.Load.of(Minecraft.getInstance().player, ClientRpg.stats.str());
    }

    /** Overloaded players can't sprint (9.4.7). */
    public static boolean overloaded() {
        return Minecraft.getInstance().player != null && !creative() && load().tier() == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED;
    }

    public static boolean canRoll() {
        if (creative()) {
            return true;
        }
        if (exhausted() || ClientRpg.stamina < Combat.ROLL_COST || load().tier() == com.cavmkii.seancraft.combat.Load.Tier.OVERLOADED) {
            return false;
        }
        return !((MinecraftClient_BetterCombat) Minecraft.getInstance()).isWeaponSwingInProgress();
    }

    /** Shows the roll's cost at once, rather than waiting for the next sync from the server. */
    public static void rolled() {
        if (!creative()) {
            ClientRpg.stamina = Math.max(0, ClientRpg.stamina - Combat.ROLL_COST);
        }
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }
}
