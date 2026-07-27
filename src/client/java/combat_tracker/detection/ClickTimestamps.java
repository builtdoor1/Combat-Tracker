package combat_tracker.detection;

/**
 * Timestamp of the most recent left mouse press, used to time combo intervals from
 * the click rather than from the tick that processed the attack.
 *
 * <p><b>Why this exists.</b> Attacks are processed inside the client tick loop, so
 * anything timed there is quantised to 50ms. Combo intervals measured that way
 * collapse onto tick boundaries (600ms, 650ms, and so on) and the natural variation
 * in a human's clicking, which is the entire signal separating a person from an
 * autoclicker, is rounded away before it can be measured. Mouse buttons arrive
 * through {@code MouseHandler.onButton}, whose queue drains once per <em>frame</em>,
 * so a timestamp taken there is accurate to a frame rather than a tick.</p>
 *
 * <p><b>Why a single slot and not a queue.</b> The first version queued presses and
 * paired them first-in-first-out with attacks, on the reasoning that vanilla calls
 * {@code startAttack} once per press. That holds right up until a press does not
 * become an attack at all, which happens whenever a click lands while the world is
 * loading, on the death screen, or as a screen opens. One stranded press offsets
 * every later pairing, so attacks start being timed against older and older clicks.
 * {@code lastHitNano} in {@link ComboTracker} then sits in the past while its
 * per-tick gap check compares against the present, which breaks the combo on every
 * tick and stops detection entirely until the game restarts.</p>
 *
 * <p>So: one slot, claimed at most once, and rejected outright if it is too old to
 * plausibly belong to the attack asking for it. A stale value cannot accumulate or
 * be reused, and the worst case is a single fallback to tick timing.</p>
 *
 * <p>Note that an autoclicker driving the real mouse is captured here exactly like a
 * human hand, which is the point: its steadiness becomes visible. Something
 * injecting attacks inside the game leaves no click at all and falls back to the
 * tick.</p>
 */
public final class ClickTimestamps {
    /**
     * A press turns into an attack on the same frame or the next one. Anything older
     * than this belongs to some earlier click, so it is discarded rather than used.
     * Generous enough to survive a frame hitch, short enough that a stranded press
     * can never be mistaken for the current one.
     */
    private static final long MAX_AGE_NANO = 200L * 1_000_000L;

    private static volatile long pending = 0L;

    private ClickTimestamps() {
    }

    /** Called from the mouse-button mixin on a left press. */
    public static void record(long nano) {
        pending = nano;
    }

    /**
     * Takes the press behind the attack being processed, or 0 if there isn't a
     * plausible one and the caller should fall back to tick timing.
     *
     * <p>Claiming clears the slot, so two attacks in one frame cannot both bill
     * themselves to the same click and produce a zero-length interval.</p>
     */
    public static long claim() {
        long v = pending;
        if (v == 0L) {
            return 0L;
        }
        pending = 0L;
        return (System.nanoTime() - v) > MAX_AGE_NANO ? 0L : v;
    }

    /** Drop anything stale, e.g. on disconnect. */
    public static void clear() {
        pending = 0L;
    }
}
