package combat_tracker.detection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Estimates one-way latency to the server, used to compensate the timestamp of an
 * incoming hit.
 *
 * <p>The only latency figure a client can see is {@link PlayerInfo#getLatency()},
 * which is the server's own round-trip estimate from keep-alive packets. It updates
 * roughly once a second and is noisy — a single stalled packet can spike it by
 * hundreds of milliseconds, which would then skew whichever jump reset happened to
 * land next.</p>
 *
 * <p>So samples are kept in a small ring and reduced with a <b>median</b>, which
 * ignores those spikes entirely rather than averaging them in. One-way latency is
 * then half the round trip. That halving used to be a user-facing "ping
 * compensation" slider; it was never a preference, just the definition of one-way
 * latency, so it is derived here instead.</p>
 */
public final class LatencyEstimator {
    /** ~20s of history at the server's once-per-second update rate. */
    private static final int SAMPLES = 20;
    /** Assumed round-trip before the server has reported anything. */
    private static final int DEFAULT_RTT_MS = 50;

    private final int[] ring = new int[SAMPLES];
    private final int[] scratch = new int[SAMPLES];
    private int count = 0;
    private int next = 0;
    private int lastRaw = -1;

    /**
     * Feed the current reported latency. Safe to call every tick: the server only
     * refreshes this about once a second, so unchanged readings are ignored rather
     * than flooding the ring with duplicates of one measurement.
     */
    public void sample(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info == null) {
            return;
        }
        int raw = info.getLatency();
        if (raw <= 0 || raw == lastRaw) {
            return;
        }
        lastRaw = raw;
        ring[next] = raw;
        next = (next + 1) % SAMPLES;
        if (count < SAMPLES) {
            count++;
        }
    }

    /** Median round-trip time in ms, or a default until the server has reported. */
    public double roundTripMs() {
        if (count == 0) {
            return DEFAULT_RTT_MS;
        }
        System.arraycopy(ring, 0, scratch, 0, count);
        java.util.Arrays.sort(scratch, 0, count);
        int mid = count / 2;
        return (count % 2 == 1)
                ? scratch[mid]
                : (scratch[mid - 1] + scratch[mid]) / 2.0;
    }

    /** One-way latency in ms — half the round trip. */
    public double oneWayMs() {
        return roundTripMs() / 2.0;
    }

    /** Clears history, e.g. on disconnect, so a new server starts fresh. */
    public void reset() {
        count = 0;
        next = 0;
        lastRaw = -1;
    }
}
