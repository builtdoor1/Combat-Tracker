package combat_tracker.stats;

/**
 * A single recorded jump-reset attempt. The signed offset is stored even on misses
 * so the full distribution survives: that spread is the data showing human timing
 * against bot-like precision.
 *
 * <p>The offset is in <b>ticks</b> from the ideal reset tick, 0 being perfect. It
 * used to be a millisecond delta; see {@link combat_tracker.config.TimingWindow}
 * for why the mechanic is only meaningful in ticks.</p>
 */
public class Attempt {
    public int offsetTicks;
    public boolean success;
    public long timestamp;

    public Attempt() {
        // required for Gson deserialization
    }

    public Attempt(int offsetTicks, boolean success, long timestamp) {
        this.offsetTicks = offsetTicks;
        this.success = success;
        this.timestamp = timestamp;
    }
}
