package combat_tracker.record;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * Renders a synthetic session to disk so the report and the share link can be
 * checked without launching Minecraft or finding someone to fight.
 *
 * <p>Everything downstream of {@link SessionData} is game-free precisely so this
 * can exist: charts, statistics and link encoding are all exercised here on data
 * shaped like a real fight. Run it with {@code ./gradlew reportPreview}.</p>
 *
 * <p>The generated numbers deliberately look <em>human</em> — spread-out timings,
 * aim scattered across the hitbox, reach around 2.5-3.0 blocks — so the charts show
 * what an honest player's session is supposed to look like.</p>
 */
public final class ReportPreview {
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    private ReportPreview() {
    }

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of(args.length > 0 ? args[0] : "build/preview");
        int events = args.length > 1 ? Integer.parseInt(args[1]) : 120;
        Files.createDirectories(outDir);

        SessionData d = synthesise(events, 20250726_000000L, 38.0);
        String canonical = new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(d);
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        String sha = IntegrityUtil.sha256Hex(bytes);
        String sig = IntegrityUtil.hmacSha256Hex(bytes);

        Path html = outDir.resolve("preview-report.html");
        Files.writeString(html, ReportBuilder.build(d, sha, sig, canonical));

        String payload = SharePayload.encode(d, SharePayload.DEFAULT_MAX_CHARS);
        Path frag = outDir.resolve("preview-fragment.txt");
        Files.writeString(frag, payload);

        System.out.println("report   : " + html.toAbsolutePath());
        System.out.println("fragment : " + frag.toAbsolutePath() + "  (" + payload.length() + " chars)");
        System.out.println("swings=" + d.swings + " jumps=" + d.jumpAttempts + " combos=" + d.comboIntervals);

        // A consistent player whose successful resets cluster within a few ms. This
        // is the case the successes-only chart exists for: on a shared 0-80ms axis
        // this session is a flat line, and the spread that proves a human is doing
        // it only becomes visible once the axis fits the data.
        SessionData tight = synthesise(events, 777L, 6.0);
        String tightCanonical = new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(tight);
        byte[] tb = tightCanonical.getBytes(StandardCharsets.UTF_8);
        Path tightHtml = outDir.resolve("preview-report-tight.html");
        Files.writeString(tightHtml, ReportBuilder.build(tight, IntegrityUtil.sha256Hex(tb),
                IntegrityUtil.hmacSha256Hex(tb), tightCanonical));
        System.out.println("tight    : " + tightHtml.toAbsolutePath()
                + "  hits " + tight.hitMinMs + "-" + tight.hitMaxMs + " ms, sd " + fmt(tight.hitSdMs));

        // Size behaviour across session lengths, to confirm downsampling engages
        // and does not throw away more of the graph than the budget requires.
        String base = "https://reeeeman1.github.io/Combat-Tracker/";
        int budget = SharePayload.DEFAULT_MAX_CHARS - base.length() - 1;
        for (int n : new int[]{50, 500, 5000}) {
            SessionData big = synthesise(n, 1234L, 38.0);
            SharePayload.Result res = SharePayload.encodeWithStats(big, budget);
            int linkLen = base.length() + 1 + res.payload().length();
            System.out.printf("n=%-5d link %4d chars  plotted %d/%d swings  %s%n",
                    n, linkLen, Math.min(res.pointLimit(), n), n,
                    linkLen <= 2000 ? "fits Discord" : "TOO LONG");
        }
    }

    /** Builds a plausible human session: spread timings, scattered aim, sane reach. */
    static String fmt(double v) { return String.format("%.2f", v); }

    /** @param jumpSpreadMs standard deviation of the jump-reset timings. */
    static SessionData synthesise(int events, long seed, double jumpSpreadMs) {
        Random rng = new Random(seed);
        long start = Instant.parse("2026-07-26T02:00:00Z").toEpochMilli();
        long end = start + events * 900L;

        SessionData d = new SessionData();
        d.mod = "Combat Tracker";
        d.mcVersion = "1.21.11";
        d.player = "builtdoor";
        d.playerUuid = "6ce8b1a1-0000-4000-8000-0000deadbeef";
        d.startEpochMs = start;
        d.endEpochMs = end;
        d.startUtc = HUMAN.format(Instant.ofEpochMilli(start));
        d.endUtc = HUMAN.format(Instant.ofEpochMilli(end));
        d.opponents = List.of("Notch", "jeb_");
        d.pingMs = 118;

        for (int i = 0; i < events; i++) {
            long t = start + i * 900L + rng.nextInt(120);

            // Jump resets: centred a little inside the success window, human spread.
            // Anything past 200ms is discarded by the tracker as "not an attempt",
            // so the preview drops it too and stays representative.
            long delta = Math.round(45 + rng.nextGaussian() * jumpSpreadMs);
            if (Math.abs(delta) <= 200) {
                String result = delta < 0 ? "TOO_EARLY" : (delta > 80 ? "TOO_LATE" : "SUCCESS");
                d.jumpEvents.add(new SessionData.JEvent(t, delta, result));
            }

            // Combo intervals around the sword cooldown, with real jitter.
            if (i % 2 == 0) {
                long interval = Math.round(640 + rng.nextGaussian() * 55);
                d.comboEvents.add(new SessionData.CEvent(t, Math.max(80, interval), i % 8 == 0));
            }

            // Swings: reach is eye-to-nearest-hitbox-point, so a landed hit can never
            // exceed vanilla's 3.0 interaction range. Whiffs are swings thrown from
            // beyond it, which is exactly why they missed.
            boolean hit = rng.nextDouble() < 0.72;
            double reach = hit
                    ? Math.min(3.0, 2.2 + rng.nextGaussian() * 0.35)
                    : 3.0 + Math.abs(rng.nextGaussian()) * 0.6;
            reach = Math.max(0.4, Math.min(5.5, reach));
            double offX = rng.nextGaussian() * 0.16;
            double offY = rng.nextGaussian() * 0.42;
            double aimDeg = Math.toDegrees(Math.atan2(Math.hypot(offX, offY), Math.max(0.5, reach)));
            d.swingEvents.add(new SessionData.SEvent(
                    t, reach, hit, aimDeg, offX, offY, rng.nextInt(d.opponents.size())));
        }

        SessionStats.summarise(d);
        return d;
    }
}
