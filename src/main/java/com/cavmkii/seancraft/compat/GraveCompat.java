package com.cavmkii.seancraft.compat;

import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.boss.Arenas;
import it.hurts.sskirillss.yagm.api.compat.placement.GravePlacementLoader;
import it.hurts.sskirillss.yagm.api.compat.placement.IGravePlacementHandler;
import it.hurts.sskirillss.yagm.init.ConfigRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Graves come from YAGM (design doc 2.2). SeanCraft sets the YAGM options the design depends on and keeps graves out
 * of live boss rings (8.4.7): a grave that would land in one goes just outside it, on the side the player died.
 */
public final class GraveCompat {
    private GraveCompat() {}

    public static void init() {
        GravePlacementLoader.registerHandler(new IGravePlacementHandler() {
            @Override
            public Vec3 getPlacementPosition(Entity dead, Vec3 pos) {
                BlockPos ring = Arenas.ringAt(dead.level(), pos);
                if (ring == null) {
                    return pos;
                }
                Vec3 center = Vec3.atBottomCenterOf(ring);
                Vec3 out = new Vec3(pos.x - center.x, 0, pos.z - center.z);
                if (out.lengthSqr() < 0.01) {
                    out = new Vec3(1, 0, 0);
                }
                Vec3 at = center.add(out.normalize().scale(Arenas.RADIUS + 3));
                return new Vec3(at.x, pos.y, at.z);
            }
        });
        // YAGM's own config file still exists; these three are what the design needs, so they win every start.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var config = ConfigRegistry.CONFIG;
            // Anyone can open a grave, so a friend can bring someone's gear home.
            config.setOwnerOnlyAccess(false);
            // Half the experience is kept.
            config.setXpRetentionPercent(50);
            // No Restore Key: getting the gear back means the trip to the grave.
            config.setRestoreKeyEnabled(false);
            SeanCraft.LOG.info("YAGM: graves open to anyone, 50% experience kept, Restore Key off");
        });
    }
}
