package com.cavmkii.seancraft.client.compat;

import com.cavmkii.seancraft.compat.Markers;
import java.util.List;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;

/**
 * JourneyMap (optional, 18.1): SeanCraft's home bases, camps and graves as waypoints. Only loaded when JourneyMap is,
 * through its "journeymap" entrypoint. The waypoints aren't saved by JourneyMap: the server sends them again on join.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapMarkers implements IClientPlugin {
    private static IClientAPI api;
    private static List<Markers.Marker> pending;

    @Override
    public String getModId() {
        return "seancraft";
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        if (pending != null) {
            show(pending);
        }
    }

    /** Replaces SeanCraft's waypoints with these. */
    public static void show(List<Markers.Marker> markers) {
        if (api == null) {
            pending = markers;
            return;
        }
        pending = null;
        com.cavmkii.seancraft.SeanCraft.LOG.debug("JourneyMap: {} SeanCraft waypoints", markers.size());
        api.removeAllWaypoints("seancraft");
        for (Markers.Marker m : markers) {
            Waypoint w = WaypointFactory.createClientWaypoint("seancraft", BlockPos.of(m.pos()), m.name(), m.dimension(), false);
            w.setColor(switch (m.kind()) {
                case Markers.HOME -> 0xE0BC62;
                case Markers.CAMP -> 0xE8743B;
                default -> 0x9A9A9A;
            });
            api.addWaypoint("seancraft", w);
        }
    }
}
