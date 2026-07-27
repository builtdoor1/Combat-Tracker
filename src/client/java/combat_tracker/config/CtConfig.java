package combat_tracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted mod configuration: the timing window plus HUD/chat toggles.
 * Stored as JSON in {@code .minecraft/config/combat_tracker/config.json}.
 */
public class CtConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat_tracker/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static CtConfig instance;

    public TimingWindow window = new TimingWindow();
    public boolean hudEnabled = true;
    public boolean chatEnabled = true;
    /** Top-left position of the HUD overlay, in GUI-scaled pixels. */
    public int hudX = 4;
    public int hudY = 4;

    // ── HUD appearance ────────────────────────────────────────────────────────
    /** Overall HUD scale multiplier (0.5–2.0). */
    public double hudScale = 1.0;
    /** Background box opacity, 0–100%. */
    public int hudBgOpacityPct = 56;
    /** Compact single-block layout instead of the full multi-line panel. */
    public boolean hudCompact = false;
    /** Index into the HUD accent-color theme list. */
    public int hudThemeIndex = 0;

    // ── Sharing ───────────────────────────────────────────────────────────────
    /**
     * Base URL of the session viewer. The session itself rides in the URL fragment,
     * so this host only ever serves the static viewer page and never receives any
     * session data. Configurable so a fork can point at its own copy.
     */
    public String shareBaseUrl = "https://builtdoor1.github.io/Combat-Tracker/";

    // Detection tuning is deliberately NOT configurable. The knockback threshold and
    // the post-hit tick windows live as constants in
    // {@link combat_tracker.detection.JumpResetTracker}, and one-way latency is
    // measured automatically by {@link combat_tracker.detection.LatencyEstimator}.
    // These were never preferences — a "wrong" value silently corrupts the very
    // statistics the mod exists to defend, and a report is only worth something if
    // every copy of the mod measured the same way. Older config.json files may still
    // carry the removed keys; Gson ignores unknown fields, so they load fine and are
    // dropped on the next save.

    public static CtConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("combat_tracker");
    }

    private static Path configFile() {
        return configDir().resolve("config.json");
    }

    public static CtConfig load() {
        Path file = configFile();
        try {
            if (Files.exists(file)) {
                CtConfig cfg = GSON.fromJson(Files.readString(file), CtConfig.class);
                if (cfg != null) {
                    if (cfg.window == null) {
                        cfg.window = new TimingWindow();
                    }
                    cfg.migrate();
                    return cfg;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load config, using defaults", e);
        }
        CtConfig def = new CtConfig();
        def.saveInternal();
        return def;
    }

    /**
     * Fixes up values a saved config can be carrying from an older version.
     *
     * <p>{@link #shareBaseUrl} is persisted, so bumping its default does nothing for
     * anyone who already has a config file. The GitHub account was renamed and the
     * old Pages host stops resolving, so a stale value here produces links that
     * simply 404.</p>
     */
    private void migrate() {
        if (shareBaseUrl != null && shareBaseUrl.contains("reeeeman1.github.io")) {
            shareBaseUrl = shareBaseUrl.replace("reeeeman1.github.io", "builtdoor1.github.io");
            LOGGER.info("Updated share link host to builtdoor1.github.io after the account rename.");
        }
    }

    public static void save() {
        if (instance != null) {
            instance.saveInternal();
        }
    }

    private void saveInternal() {
        try {
            Files.createDirectories(configDir());
            Files.writeString(configFile(), GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("Failed to save config", e);
        }
    }
}
