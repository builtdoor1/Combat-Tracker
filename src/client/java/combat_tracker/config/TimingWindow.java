package combat_tracker.config;

/**
 * Classifies a jump reset by how many ticks the jump landed from the ideal one.
 *
 * <p>Modelled on <a href="https://github.com/sootysplash/jump-reset">sootysplash's
 * jump-reset</a>, which compares {@code player.tickCount} at the jump against
 * {@code tickCount} at the hit. A reset is <b>perfect when you jump on the tick
 * immediately after taking the hit</b>, so the signed offset is:</p>
 *
 * <pre>offset = jumpTick - (hurtTick + 1)</pre>
 *
 * <p>0 is perfect, positive is late, negative is early.</p>
 *
 * <p>Ticks rather than milliseconds because the mechanic is tick-quantised: the
 * knockback is applied on a tick and your jump impulse is applied on a tick. A
 * millisecond figure was only ever a tick count in disguise, which is why the old
 * numbers clustered on 50ms boundaries with nothing meaningful in between.</p>
 */
public class TimingWindow {
    /**
     * Largest tick gap between hit and jump that still counts as an attempt at a
     * reset. Beyond this the two events are unrelated and nothing is recorded.
     * sootysplash uses the same threshold and the same default.
     */
    public int maxTicks = 10;

    public enum Result {
        /** Jumped on the tick right after the hit. */
        PERFECT,
        /** Jumped before the ideal tick. */
        TOO_EARLY,
        /** Jumped after the ideal tick. */
        TOO_LATE;

        public boolean success() {
            return this == PERFECT;
        }
    }

    /** Signed tick offset from the ideal reset tick. */
    public static int offset(int jumpTick, int hurtTick) {
        return jumpTick - (hurtTick + 1);
    }

    public Result classify(int offsetTicks) {
        if (offsetTicks == 0) {
            return Result.PERFECT;
        }
        return offsetTicks < 0 ? Result.TOO_EARLY : Result.TOO_LATE;
    }

    /** Whether a hit/jump pair is close enough together to be an attempt at all. */
    public boolean isAttempt(int jumpTick, int hurtTick) {
        return Math.abs(jumpTick - hurtTick) < maxTicks;
    }

    /** Human-readable form: "Perfect!", "Late 2t", "Early 1t". */
    public static String describe(int offsetTicks) {
        if (offsetTicks == 0) {
            return "Perfect!";
        }
        return (offsetTicks < 0 ? "Early " : "Late ") + Math.abs(offsetTicks) + "t";
    }
}
