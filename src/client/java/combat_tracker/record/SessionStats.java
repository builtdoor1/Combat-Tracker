package combat_tracker.record;

/**
 * Computes the summary figures on a {@link SessionData} from its event lists.
 *
 * <p>Game-free, and deliberately separate from both the recorder and the renderer:
 * these numbers are always derived from <em>every</em> recorded event, even when a
 * share link later has to drop points from the graphs to fit. The headline
 * statistics are never an approximation.</p>
 */
final class SessionStats {
    private SessionStats() {
    }

    static void summarise(SessionData d) {
        summariseJumps(d);
        summariseCombos(d);
        summariseSwings(d);
    }

    private static void summariseJumps(SessionData d) {
        int hits = 0;
        double sum = 0, sumSq = 0;
        double hSum = 0, hSumSq = 0;
        long hMin = Long.MAX_VALUE, hMax = Long.MIN_VALUE;
        for (SessionData.JEvent j : d.jumpEvents) {
            sum += j.offsetTicks;
            sumSq += (double) j.offsetTicks * j.offsetTicks;
            if ("PERFECT".equals(j.result)) {
                hits++;
                hSum += j.offsetTicks;
                hSumSq += (double) j.offsetTicks * j.offsetTicks;
                hMin = Math.min(hMin, j.offsetTicks);
                hMax = Math.max(hMax, j.offsetTicks);
            }
        }
        int n = d.jumpEvents.size();
        d.jumpAttempts = n;
        d.jumpHits = hits;
        d.jumpMisses = n - hits;
        d.jumpAvgTicks = n == 0 ? 0 : sum / n;
        d.jumpSdTicks = stdDev(sum, sumSq, n);

        // Spread across the successful resets alone is the human-versus-bot signal;
        // mixing in misses inflates it and hides what it is meant to show.
        d.hitAvgMs = hits == 0 ? 0 : hSum / hits;
        d.hitSdMs = stdDev(hSum, hSumSq, hits);
        d.hitMinMs = hits == 0 ? 0 : hMin;
        d.hitMaxMs = hits == 0 ? 0 : hMax;
    }

    private static void summariseCombos(SessionData d) {
        double sum = 0, sumSq = 0;
        int comboCount = 0;
        for (SessionData.CEvent c : d.comboEvents) {
            sum += c.intervalMs;
            sumSq += (double) c.intervalMs * c.intervalMs;
            if (c.newCombo) {
                comboCount++;
            }
        }
        int n = d.comboEvents.size();
        d.comboIntervals = n;
        d.combos = comboCount;
        d.comboAvgMs = n == 0 ? 0 : sum / n;
        d.comboJitterMs = stdDev(sum, sumSq, n);
    }

    private static void summariseSwings(SessionData d) {
        int hits = 0;
        double rSum = 0, rSumSq = 0, rMax = 0;
        double aSum = 0, aSumSq = 0;
        for (SessionData.SEvent e : d.swingEvents) {
            if (e.hit) {
                hits++;
            }
            rSum += e.reach;
            rSumSq += e.reach * e.reach;
            rMax = Math.max(rMax, e.reach);
            aSum += e.aimDeg;
            aSumSq += e.aimDeg * e.aimDeg;
        }
        int n = d.swingEvents.size();
        d.swings = n;
        d.swingHits = hits;
        d.swingMisses = n - hits;
        d.reachAvgBlocks = n == 0 ? 0 : rSum / n;
        d.reachMaxBlocks = rMax;
        d.reachSdBlocks = stdDev(rSum, rSumSq, n);
        d.aimAvgDeg = n == 0 ? 0 : aSum / n;
        d.aimSdDeg = stdDev(aSum, aSumSq, n);
    }

    private static double stdDev(double sum, double sumSq, int n) {
        if (n == 0) {
            return 0.0;
        }
        double mean = sum / n;
        return Math.sqrt(Math.max(0.0, (sumSq / n) - (mean * mean)));
    }
}
