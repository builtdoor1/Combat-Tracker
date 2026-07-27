package combat_tracker.detection;

import java.util.ArrayDeque;

/**
 * Timestamps of left mouse presses, taken as close to the real click as the game
 * allows.
 *
 * <p><b>Why this exists.</b> Attacks are processed inside the client tick loop, so
 * anything timed there is quantised to 50ms. Combo intervals measured that way
 * collapse onto tick boundaries (600ms, 650ms, and so on) and the natural variation
 * in a human's clicking, which is the entire signal separating a person from an
 * autoclicker, is rounded away before it can be measured.</p>
 *
 * <p>Mouse buttons arrive through {@code MouseHandler.onButton}, which the GLFW
 * callback hands to {@code Minecraft.execute}. That queue drains once per
 * <em>frame</em>, so a timestamp taken there is accurate to a frame rather than a
 * tick: about 5ms at 200fps instead of 50ms. Coarse enough to still matter, fine
 * enough that human variance survives.</p>
 *
 * <p>Presses are queued rather than kept as a single value because several clicks
 * can land between two frames, and each one becomes its own attack. Vanilla drains
 * them with {@code while (keyAttack.consumeClick())}, one attack per press, so
 * first-in-first-out pairing lines up with what the game does.</p>
 *
 * <p>Note that an autoclicker driving the real mouse is captured here exactly like
 * a human hand, which is the point: its steadiness becomes visible. Something
 * injecting attacks inside the game bypasses this entirely and leaves no click at
 * all, which the fallback in {@code ComboTracker} records as tick-timed.</p>
 */
public final class ClickTimestamps {
    /** Plenty for any realistic burst between frames; oldest is dropped if exceeded. */
    private static final int MAX_PENDING = 16;

    private static final ArrayDeque<Long> PENDING = new ArrayDeque<>();

    private ClickTimestamps() {
    }

    /** Called from the mouse-button mixin on a left press. */
    public static synchronized void record(long nano) {
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.poll();
        }
        PENDING.add(nano);
    }

    /** Takes the oldest pending press, or 0 if the attack had no click behind it. */
    public static synchronized long consume() {
        Long v = PENDING.poll();
        return v == null ? 0L : v;
    }

    /** Drop anything stale, e.g. on disconnect or when a screen opens. */
    public static synchronized void clear() {
        PENDING.clear();
    }
}
