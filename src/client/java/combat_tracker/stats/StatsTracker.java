package combat_tracker.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Records every jump-reset attempt and computes aggregate statistics
 * (success rate, average delta, standard deviation). Persisted as JSON in
 * {@code .minecraft/config/combat_tracker/stats.json}.
 *
 * <p>Running sums are cached so the HUD can read aggregates every frame without
 * iterating the full attempt history.</p>
 */
public class StatsTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat_tracker/stats");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static StatsTracker instance;

    /**
     * Bumped when stored attempts stop meaning what they used to. Version 1 held
     * millisecond deltas; from 2 they are tick offsets, and the two cannot be mixed
     * in one distribution, so an older file's attempts are dropped rather than
     * silently averaged together with the new ones.
     */
    private static final int CURRENT_FORMAT = 2;

    public int format = CURRENT_FORMAT;

    /** Persisted: the full distribution of attempts. */
    public List<Attempt> attempts = new ArrayList<>();

    // Cached running aggregates (recomputed on load, never serialized).
    private transient long successes = 0;
    private transient double sumDelta = 0;
    private transient double sumDeltaSq = 0;
    private transient String lastResult = "—";
    private transient int lastResultColor = 0xFFAAAAAA;

    public static StatsTracker get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path statsFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("combat_tracker")
                .resolve("stats.json");
    }

    public static StatsTracker load() {
        StatsTracker s;
        Path file = statsFile();
        try {
            if (Files.exists(file)) {
                s = GSON.fromJson(Files.readString(file), StatsTracker.class);
                if (s == null) {
                    s = new StatsTracker();
                }
            } else {
                s = new StatsTracker();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load stats, starting fresh", e);
            s = new StatsTracker();
        }
        if (s.attempts == null) {
            s.attempts = new ArrayList<>();
        }
        if (s.format < CURRENT_FORMAT && !s.attempts.isEmpty()) {
            LOGGER.info("Clearing {} jump-reset attempts recorded in milliseconds; "
                    + "timing is measured in ticks now and the two cannot be combined.",
                    s.attempts.size());
            s.attempts.clear();
        }
        s.format = CURRENT_FORMAT;
        s.recompute();
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(statsFile().getParent());
            Files.writeString(statsFile(), GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("Failed to save stats", e);
        }
    }

    private void recompute() {
        successes = 0;
        sumDelta = 0;
        sumDeltaSq = 0;
        for (Attempt a : attempts) {
            if (a.success) {
                successes++;
            }
            sumDelta += a.offsetTicks;
            sumDeltaSq += (double) a.offsetTicks * a.offsetTicks;
        }
    }

    public void record(int offsetTicks, boolean success) {
        attempts.add(new Attempt(offsetTicks, success, System.currentTimeMillis()));
        if (success) {
            successes++;
        }
        sumDelta += offsetTicks;
        sumDeltaSq += (double) offsetTicks * offsetTicks;
    }

    public void reset() {
        attempts.clear();
        successes = 0;
        sumDelta = 0;
        sumDeltaSq = 0;
        lastResult = "—";
        lastResultColor = 0xFFAAAAAA;
    }

    public int total() {
        return attempts.size();
    }

    public long hits() {
        return successes;
    }

    public long misses() {
        return total() - successes;
    }

    public double successRate() {
        return total() == 0 ? 0.0 : (successes * 100.0 / total());
    }

    public double averageDelta() {
        return total() == 0 ? 0.0 : sumDelta / total();
    }

    /** Population standard deviation of all recorded deltas. */
    public double stdDev() {
        int n = total();
        if (n == 0) {
            return 0.0;
        }
        double mean = sumDelta / n;
        double variance = (sumDeltaSq / n) - (mean * mean);
        return Math.sqrt(Math.max(0.0, variance));
    }

    public String lastResult() {
        return lastResult;
    }

    public int lastResultColor() {
        return lastResultColor;
    }

    public void setLastResult(String text, int color) {
        this.lastResult = text;
        this.lastResultColor = color;
    }
}
