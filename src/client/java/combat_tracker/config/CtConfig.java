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

    // ── Detection tuning (advanced; edit in config.json) ──────────────────────
    /** Minimum horizontal speed just after damage to treat it as a real combat hit
     *  (filters fall / fire / poison, which have no horizontal knockback). */
    public double knockbackThreshold = 0.065;
    /** Ticks after a grounded hit during which a jump still counts as an attempt. */
    public int windowTicksGround = 6;
    /** Ticks after an airborne hit during which a jump still counts as an attempt. */
    public int windowTicksAir = 10;
    /** Fraction of round-trip ping treated as one-way latency for hit-time compensation. */
    public double pingCompFactor = 0.5;

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
