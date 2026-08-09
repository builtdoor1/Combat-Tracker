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
    /**
     * Optional label for the session, e.g. who it was against and the score.
     * Null on anything recorded before titles existed, which the report and the
     * viewer both treat as "no title" rather than an empty heading.
     */
    public String title;
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
    // No longer rendered: the report shows every attempt on one chart, which is
    // readable now that attempts past 200ms are discarded rather than scored.
    // Still computed and still written to share-link format v2, because that
    // format is already public and its field order cannot change.
    public double hitAvgMs;
    public double hitSdMs;
    public long hitMinMs;
    public long hitMaxMs;

    // ── Input provenance ─────────────────────────────────────────────────────
    // Actions that no vanilla input path asked for. See InputContext for how the
    // call graph makes this a clean test, and for the limits of it.

    // Marked transient so Gson leaves them out of the canonical JSON entirely. Zero
    // values would still have printed the field names into every saved report, which
    // announces the feature to anyone who opens one — and the report is a file the
    // player being measured can read. They stay as fields because SharePayload's v4
    // layout writes four counters and a series at the end, and that layout is a
    // published contract; it now always writes zeros.
    public transient int flagHotbar;
    public transient int flagUse;
    public transient int flagAttack;
    public transient int flagKeybind;

    /** Names of everyone swung at, referenced by index from {@link SEvent#target}. */
    public List<String> opponents = new ArrayList<>();

    public List<JEvent> jumpEvents = new ArrayList<>();
    public List<CEvent> comboEvents = new ArrayList<>();
    public List<SEvent> swingEvents = new ArrayList<>();
    public transient List<FEvent> flagEvents = new ArrayList<>();

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

    /**
     * One action that no vanilla input path accounted for.
     *
     * <p>{@code kind} is the ordinal of {@code IntegrityMonitor.Kind}: 0 hotbar,
     * 1 use, 2 attack. Stored as an int rather than the enum so this class stays
     * free of any dependency on the detection package, which is what lets the whole
     * report pipeline run headlessly.</p>
     */
    public static final class FEvent {
        public long t;
        public int kind;

        public FEvent() {
        }

        public FEvent(long t, int kind) {
            this.t = t;
            this.kind = kind;
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
