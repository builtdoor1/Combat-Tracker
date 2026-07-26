package combat_tracker.config;

/**
 * Holds the tuneable timing bounds (in milliseconds) that define a jump reset
 * and classifies a measured delta against them.
 *
 * <p>delta = jumpTimestamp - hitTimestamp (signed). A positive delta means the
 * jump happened after the hit; a negative delta means the jump happened before
 * the hit ("too early").</p>
 */
public class TimingWindow {
    /** Lower edge of the success window. Default 0ms. */
    public long lowerBoundMs = 0L;
    /** Upper edge of the success window. Default 80ms (user requested <= 80ms). */
    public long upperBoundMs = 80L;

    public enum Result {
        SUCCESS,
        TOO_EARLY,
        TOO_LATE
    }

    public Result classify(long deltaMs) {
        if (deltaMs < lowerBoundMs) {
            return Result.TOO_EARLY;
        }
        if (deltaMs > upperBoundMs) {
            return Result.TOO_LATE;
        }
        return Result.SUCCESS;
    }
}
