package combat_tracker.record;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a recorded session contains, as plain data.
 *
 * <p>Deliberately free of any Minecraft import: this is what gets serialised to
 * JSON, hashed, rendered to HTML by {@link ReportBuilder} and packed into a share
 * link by {@link SharePayload} — and being game-free is what lets all of that be
 * exercised headlessly by {@link ReportPreview}, without launching the game or
 * finding someone to fight.</p>
 *
 * <p>Fields are public and plainly named because Gson maps them straight onto the
 * JSON, and that JSON is the thing a third party re-hashes to check the report was
 * not edited.</p>
 */
public final class SessionData {
    public String mod;
    public String mcVersion;
    public String player;
    public String playerUuid;
    public long startEpochMs;
    public long endEpochMs;
    public String startUtc;
    public String endUtc;

    // ── Jump resets ──────────────────────────────────────────────────────────
    public int jumpAttempts;
    public int jumpHits;
    public int jumpMisses;
    public double jumpAvgMs;
    public double jumpSdMs;

    // ── Combo timing ─────────────────────────────────────────────────────────
    public int comboIntervals;
    public int combos;
    public double comboAvgMs;
    public double comboJitterMs;

    // ── Reach & aim ──────────────────────────────────────────────────────────
    public int swings;
    public int swingHits;
    public int swingMisses;
    public double reachAvgBlocks;
    public double reachMaxBlocks;
    public double reachSdBlocks;
    public double aimAvgDeg;
    public double aimSdDeg;

    /** Median round-trip ping during the session. Context only — it adjusts nothing. */
    public double pingMs;

    // ── Successful resets only ───────────────────────────────────────────────
    // Tracked separately because misses can be hundreds of milliseconds out and
    // would otherwise stretch the axis until the successes — the numbers that
    // actually demonstrate human spread — are squashed into a few pixels.
    public double hitAvgMs;
    public double hitSdMs;
    public long hitMinMs;
    public long hitMaxMs;

    /** Names of everyone swung at, referenced by index from {@link SEvent#target}. */
    public List<String> opponents = new ArrayList<>();

    public List<JEvent> jumpEvents = new ArrayList<>();
    public List<CEvent> comboEvents = new ArrayList<>();
    public List<SEvent> swingEvents = new ArrayList<>();

    /** One scored jump-reset attempt. */
    public static final class JEvent {
        public long t;
        public long deltaMs;
        public String result;

        public JEvent() {
        }

        public JEvent(long t, long deltaMs, String result) {
            this.t = t;
            this.deltaMs = deltaMs;
            this.result = result;
        }
    }

    /** One interval between consecutive combo hits. */
    public static final class CEvent {
        public long t;
        public long intervalMs;
        public boolean newCombo;

        public CEvent() {
        }

        public CEvent(long t, long intervalMs, boolean newCombo) {
            this.t = t;
            this.intervalMs = intervalMs;
            this.newCombo = newCombo;
        }
    }

    /** One attack swing, landed or whiffed, with its geometry. */
    public static final class SEvent {
        public long t;
        /** Eye-to-hitbox distance in blocks. */
        public double reach;
        /** Whether the swing actually connected. */
        public boolean hit;
        /** Angle between the crosshair and the hitbox centre, in degrees. */
        public double aimDeg;
        /** Aim offset from hitbox centre, in blocks: across the view, and vertical. */
        public double offX;
        public double offY;
        /** Index into {@link SessionData#opponents}, or -1. */
        public int target = -1;

        public SEvent() {
        }

        public SEvent(long t, double reach, boolean hit, double aimDeg, double offX, double offY, int target) {
            this.t = t;
            this.reach = reach;
            this.hit = hit;
            this.aimDeg = aimDeg;
            this.offX = offX;
            this.offY = offY;
            this.target = target;
        }
    }
}
