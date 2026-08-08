package combat_tracker.detection;

/**
 * Decides when the next alert about an ongoing session is due.
 *
 * <p>The old rule was a flat 15 seconds up to twenty messages, so one cheating
 * player produced twenty alerts saying the same thing. Nineteen carried nothing the
 * first had not already said: same player, same checks, still going.</p>
 *
 * <p>This says it once, says it again if it persists, then stops. Free of any
 * Minecraft import so the timing can be tested without launching the game, which is
 * worth doing for a schedule whose whole content is an array index that is easy to
 * get one off.</p>
 */
public final class AlertSchedule {
    /**
     * Wait before the opening alert.
     *
     * <p>Short, but not zero. Sending the instant a check trips produces an alert
     * reading "1 hotbar switch", the least informative moment there is. Ten seconds
     * gathers the opening burst at a cost nobody notices.</p>
     */
    static final long FIRST_DELAY_MS = 10_000L;

    /** Gaps before the second and third alerts, if flags keep arriving. */
    static final long[] BACKOFF_MS = { 120_000L, 600_000L };

    /** The opening alert plus one per backoff step. */
    public static final int MAX_MESSAGES = 1 + BACKOFF_MS.length;

    private AlertSchedule() {
    }

    /** True once the budget for this session is spent. */
    public static boolean exhausted(int sent) {
        return sent >= MAX_MESSAGES;
    }

    /**
     * How long to wait before the alert after {@code sent} have already gone.
     *
     * <p>Callers must check {@link #exhausted} first; past the last backoff there is
     * no next alert to schedule and asking for one is a bug rather than a value.</p>
     */
    public static long dueAfter(int sent) {
        if (exhausted(sent)) {
            throw new IllegalStateException("no alert due after " + sent + " sent");
        }
        return sent == 0 ? FIRST_DELAY_MS : BACKOFF_MS[sent - 1];
    }

    /**
     * Whether an alert is due now.
     *
     * @param sent      how many have already been sent this session
     * @param since     when the clock started — the first flag, or the last send
     * @param now       current time
     */
    public static boolean due(int sent, long since, long now) {
        return !exhausted(sent) && now - since >= dueAfter(sent);
    }
}
