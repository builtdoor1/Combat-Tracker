package combat_tracker.detection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Reads the current round-trip latency to the server.
 *
 * <p>The only figure a client can see is {@link PlayerInfo#getLatency()}, the
 * server's own estimate from keep-alive packets. It refreshes about once a second
 * and is lumpy.</p>
 *
 * <p>This adjusts no measurement. Jump-reset timing deliberately applies no ping
 * compensation (see {@link JumpResetTracker#handleHit}); latency is recorded only
 * so a report says what the connection was like. The averaging over a recording
 * lives in the session recorder, which is the only thing that needs it.</p>
 */
public final class LatencyEstimator {
    private static final LatencyEstimator INSTANCE = new LatencyEstimator();

    public static LatencyEstimator get() {
        return INSTANCE;
    }

    private int lastRaw = -1;

    /** Refresh from the player list. Cheap enough to call every tick. */
    public void sample(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info == null) {
            return;
        }
        int raw = info.getLatency();
        if (raw > 0) {
            lastRaw = raw;
        }
    }

    /** Most recent round-trip reading in ms, or -1 if the server hasn't reported. */
    public int currentMs() {
        return lastRaw;
    }

    public void reset() {
        lastRaw = -1;
    }
}
