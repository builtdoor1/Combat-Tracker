package combat_tracker.stats;

/**
 * A single recorded jump-reset attempt. The raw signed delta is stored even on
 * misses so the full timing distribution is preserved (this is the data that
 * demonstrates human timing variance vs. bot-like precision).
 */
public class Attempt {
    public long deltaMs;
    public boolean success;
    public long timestamp;

    public Attempt() {
        // required for Gson deserialization
    }

    public Attempt(long deltaMs, boolean success, long timestamp) {
        this.deltaMs = deltaMs;
        this.success = success;
        this.timestamp = timestamp;
    }
}
