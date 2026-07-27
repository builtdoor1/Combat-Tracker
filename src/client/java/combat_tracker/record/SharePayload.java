package combat_tracker.record;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Packs a {@link SessionData} into the fragment of a shareable URL.
 *
 * <p>The payload goes after the {@code #}, which browsers never send to the server.
 * So a link can be handed to anyone and the data reaches them without ever being
 * uploaded anywhere — no host, no account, nothing to rate-limit or go offline.
 * The cost is that the whole session has to fit in a URL, which is what most of
 * this class is about.</p>
 *
 * <p><b>Budget.</b> Links get pasted into chat clients, and Discord caps a message
 * at 2000 characters. A link longer than that cannot be sent at all, so the encoder
 * targets {@link #DEFAULT_MAX_CHARS} and progressively drops <em>plotted points</em>
 * until it fits. Summary statistics are computed over every event before any of
 * that happens ({@link SessionStats}) and are always encoded exactly — the headline
 * numbers never become an approximation, only the graph resolution does, and the
 * viewer says so when it happens.</p>
 *
 * <p>Format: version byte, then varint-packed fields with timestamps delta-encoded
 * and measurements quantised to integers, deflated and base64url'd. The version
 * byte is the compatibility contract: once links are in the wild the viewer has to
 * keep decoding old ones, so it must never be dropped.</p>
 */
public final class SharePayload {
    /**
     * Bump only for an incompatible layout change; the viewer decodes each version.
     *
     * <p>v2 added ping and the successful-reset summary. v3 changed jump-reset
     * timing from milliseconds to tick offsets, which is a change of meaning rather
     * than of layout, so the viewer must label old links in ms and new ones in
     * ticks. Older links stay readable: the viewer branches on this byte, which is
     * the whole reason it is written first.</p>
     */
    private static final int FORMAT_VERSION = 3;

    /** Leaves room for surrounding text in a 2000-character Discord message. */
    public static final int DEFAULT_MAX_CHARS = 1800;

    /** Never downsample below this — fewer points stops being a graph. */
    private static final int MIN_POINTS = 40;

    private SharePayload() {
    }

    /** Builds the full share URL, or null if the base URL is unusable. */
    public static String buildLink(String baseUrl, SessionData d) {
        return buildLink(baseUrl, d, DEFAULT_MAX_CHARS);
    }

    public static String buildLink(String baseUrl, SessionData d, int maxChars) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        int budget = Math.max(256, maxChars - base.length() - 1);
        return base + "#" + encode(d, budget);
    }

    /** Encoded payload plus how many points per series survived the budget. */
    public record Result(String payload, int pointLimit) {
    }

    public static String encode(SessionData d, int budget) {
        return encodeWithStats(d, budget).payload();
    }

    /**
     * Encodes to base64url with as much of the graph as the budget allows.
     *
     * <p>Binary search on the point count rather than stepping down: a fixed step
     * stops at the first size that happens to fit and can leave a tenth of the
     * budget unspent, which is a tenth of the graph thrown away for nothing.
     * Encoding is a deflate over a couple of kilobytes, so a dozen probes cost
     * nothing measurable.</p>
     */
    public static Result encodeWithStats(SessionData d, int budget) {
        int total = Math.max(d.jumpEvents.size(), Math.max(d.comboEvents.size(), d.swingEvents.size()));

        String full = base64(deflate(pack(d, total)));
        if (full.length() <= budget) {
            return new Result(full, total);
        }

        // Largest point count that still fits. Falls back to MIN_POINTS if even
        // that overflows — a slightly over-long link beats no link at all.
        int lo = MIN_POINTS;
        int hi = total;
        String best = base64(deflate(pack(d, lo)));
        int bestLimit = lo;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            String candidate = base64(deflate(pack(d, mid)));
            if (candidate.length() <= budget) {
                best = candidate;
                bestLimit = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return new Result(best, bestLimit);
    }

    // ── Packing ──────────────────────────────────────────────────────────────

    private static byte[] pack(SessionData d, int limit) {
        Buf b = new Buf();
        b.u8(FORMAT_VERSION);

        b.str(d.player);
        b.str(d.playerUuid);
        b.varLong(d.startEpochMs);
        b.varLong(Math.max(0, d.endEpochMs - d.startEpochMs));

        b.varInt(d.opponents.size());
        for (String o : d.opponents) {
            b.str(o);
        }

        // Summaries: always exact, always over every event.
        b.varInt(d.jumpAttempts);
        b.varInt(d.jumpHits);
        b.cent(d.jumpAvgTicks);
        b.cent(d.jumpSdTicks);
        // v2 additions
        b.varInt((int) Math.round(Math.max(0, d.pingMs)));
        b.cent(d.hitAvgMs);
        b.cent(d.hitSdMs);
        b.zig(d.hitMinMs);
        b.zig(d.hitMaxMs);
        b.varInt(d.combos);
        b.varInt(d.comboIntervals);
        b.cent(d.comboAvgMs);
        b.cent(d.comboJitterMs);
        b.varInt(d.swings);
        b.varInt(d.swingHits);
        b.cent(d.reachAvgBlocks);
        b.cent(d.reachMaxBlocks);
        b.cent(d.reachSdBlocks);
        b.cent(d.aimAvgDeg);
        b.cent(d.aimSdDeg);

        // Series, possibly downsampled. Each carries its own total so the viewer can
        // say "showing N of M" rather than quietly presenting a thinned graph as whole.
        // Flags ride in the low bits of an adjacent varint rather than costing a
        // byte each. Worth the small awkwardness: a whole byte per event is roughly
        // a tenth of the budget, and the budget is what decides how much of the
        // graph survives.
        List<SessionData.JEvent> js = sample(d.jumpEvents, limit);
        b.varInt(d.jumpEvents.size());
        b.varInt(js.size());
        long prev = d.startEpochMs;
        for (SessionData.JEvent j : js) {
            b.varLong(Math.max(0, j.t - prev));
            prev = j.t;
            b.zig(((long) j.offsetTicks << 2) | resultCode(j.result));
        }

        List<SessionData.CEvent> cs = sample(d.comboEvents, limit);
        b.varInt(d.comboEvents.size());
        b.varInt(cs.size());
        prev = d.startEpochMs;
        for (SessionData.CEvent c : cs) {
            b.varLong(Math.max(0, c.t - prev));
            prev = c.t;
            b.varLong((Math.max(0, c.intervalMs) << 1) | (c.newCombo ? 1 : 0));
        }

        List<SessionData.SEvent> ss = sample(d.swingEvents, limit);
        b.varInt(d.swingEvents.size());
        b.varInt(ss.size());
        prev = d.startEpochMs;
        for (SessionData.SEvent e : ss) {
            b.varLong(Math.max(0, e.t - prev));
            prev = e.t;
            b.varInt((int) Math.round(Math.max(0, e.reach) * 100.0));   // centiblocks
            b.varInt((int) Math.round(Math.max(0, e.aimDeg) * 100.0));  // centidegrees
            b.zig(Math.round(e.offX * 1000.0));                          // milliblocks
            b.zig(Math.round(e.offY * 1000.0));
            // target is >= -1, so +1 keeps it non-negative with the hit bit below it
            b.varInt(((e.target + 1) << 1) | (e.hit ? 1 : 0));
        }
        return b.toByteArray();
    }

    private static int resultCode(String result) {
        if ("PERFECT".equals(result)) {
            return 0;
        }
        return "TOO_EARLY".equals(result) ? 1 : 2;
    }

    /** Evenly spaced subset, keeping first and last so the time span is preserved. */
    private static <T> List<T> sample(List<T> src, int limit) {
        int n = src.size();
        if (n <= limit) {
            return src;
        }
        List<T> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(src.get((int) ((long) i * (n - 1) / (limit - 1))));
        }
        return out;
    }

    // ── Bytes ────────────────────────────────────────────────────────────────

    private static byte[] deflate(byte[] raw) {
        // Raw deflate (nowrap) so the viewer can use DecompressionStream('deflate-raw').
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            def.setInput(raw);
            def.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
            byte[] chunk = new byte[4096];
            while (!def.finished()) {
                out.write(chunk, 0, def.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    private static String base64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Minimal growable byte writer with the varint helpers the format needs. */
    private static final class Buf extends ByteArrayOutputStream {
        void u8(int v) {
            write(v & 0xFF);
        }

        void varInt(int v) {
            varLong(v & 0xFFFFFFFFL);
        }

        void varLong(long v) {
            long x = v;
            while ((x & ~0x7FL) != 0) {
                write((int) ((x & 0x7F) | 0x80));
                x >>>= 7;
            }
            write((int) x);
        }

        /** Zig-zag so small negatives stay one byte. */
        void zig(long v) {
            varLong((v << 1) ^ (v >> 63));
        }

        /** A double as hundredths, zig-zagged. */
        void cent(double v) {
            zig(Math.round(v * 100.0));
        }

        void str(String s) {
            byte[] raw = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            varInt(raw.length);
            write(raw, 0, raw.length);
        }
    }
}
