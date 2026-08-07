package combat_tracker.detection;

/**
 * Remembers who the player was last fighting, so an alert can say who rather than
 * only what.
 *
 * <p>Fed from the two places a target is already resolved: {@code SwingTracker},
 * which works out the intended target for whiffs as well as landed hits, and the
 * outgoing-attack mixin, which sees every hit that connects.</p>
 *
 * <p>Deliberately time-bounded. A name from four minutes ago is not who you were
 * fighting when a check tripped, and printing it as though it were would put an
 * uninvolved player's name in an alert about someone else. Past
 * {@link #FRESH_MS} the answer becomes "unknown", which is honest.</p>
 */
public final class OpponentTracker {
    /** Beyond this, the last target stops being evidence of anything. */
    private static final long FRESH_MS = 15_000L;

    private static final OpponentTracker INSTANCE = new OpponentTracker();

    public static OpponentTracker get() {
        return INSTANCE;
    }

    private String lastName;
    private long lastAtMs;

    private OpponentTracker() {
    }

    public void note(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        lastName = name;
        lastAtMs = System.currentTimeMillis();
    }

    /** The recent opponent, or null if there isn't a recent one. */
    public String recent() {
        if (lastName == null || System.currentTimeMillis() - lastAtMs > FRESH_MS) {
            return null;
        }
        return lastName;
    }

    public void clear() {
        lastName = null;
        lastAtMs = 0L;
    }
}
