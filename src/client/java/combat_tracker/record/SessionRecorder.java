package combat_tracker.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import combat_tracker.stats.ComboStatsTracker;
import combat_tracker.stats.StatsTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a session of jump-reset and combo events into a tamper-evident report.
 *
 * <p>On stop, writes two files into {@code config/combat_tracker/recordings/}:
 * a {@code .json} holding the canonical data plus a SHA-256 hash and an
 * HMAC-SHA256 signature, and a self-contained {@code .html} report with charts,
 * start/end time signatures, and the same integrity block.</p>
 *
 * <p>See {@link IntegrityUtil} for the limits of the signature.</p>
 */
public class SessionRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat_tracker/record");
    private static final Gson CANONICAL = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    private static final SessionRecorder INSTANCE = new SessionRecorder();

    public static SessionRecorder get() {
        return INSTANCE;
    }

    private boolean recording = false;
    private long startEpochMs = 0;
    private final List<JEvent> jumps = new ArrayList<>();
    private final List<CEvent> combos = new ArrayList<>();

    public boolean isRecording() {
        return recording;
    }

    public long startEpochMs() {
        return startEpochMs;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("combat_tracker").resolve("recordings");
    }

    public void toggle() {
        if (recording) {
            stop();
        } else {
            start();
        }
    }

    public void start() {
        recording = true;
        startEpochMs = System.currentTimeMillis();
        jumps.clear();
        combos.clear();
        chat("Recording started", ChatFormatting.GREEN);
    }

    public Path stop() {
        if (!recording) {
            return null;
        }
        recording = false;
        long endEpochMs = System.currentTimeMillis();
        try {
            Path html = write(endEpochMs);
            chat("Recording saved: " + html, ChatFormatting.GREEN);
            chat("Open the .html in a browser to view the graphs.", ChatFormatting.GRAY);
            return html;
        } catch (Exception e) {
            LOGGER.error("Failed to write recording", e);
            chat("Failed to save recording: " + e.getMessage(), ChatFormatting.RED);
            return null;
        }
    }

    public void recordJump(long deltaMs, String result) {
        if (recording) {
            jumps.add(new JEvent(System.currentTimeMillis(), deltaMs, result));
        }
    }

    public void recordCombo(long intervalMs, boolean newCombo) {
        if (recording) {
            combos.add(new CEvent(System.currentTimeMillis(), intervalMs, newCombo));
        }
    }

    // ── Writing ───────────────────────────────────────────────────────────────

    private Path write(long endEpochMs) throws Exception {
        Files.createDirectories(dir());

        SessionData data = buildData(endEpochMs);
        String canonical = CANONICAL.toJson(data);
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        String sha = IntegrityUtil.sha256Hex(bytes);
        String sig = IntegrityUtil.hmacSha256Hex(bytes);

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochMilli(startEpochMs));

        // .json: canonical data string + integrity block (verifiable independently)
        Integrity integrity = new Integrity("SHA-256", sha, "HMAC-SHA256", sig,
                "Hash/signature cover the exact 'canonicalData' string (UTF-8). The signing key ships in the "
                        + "open-source mod, so this is tamper-evidence, not unforgeable proof.");
        SessionFile fileObj = new SessionFile(canonical, integrity);
        Path jsonPath = dir().resolve("session-" + stamp + ".json");
        Files.writeString(jsonPath, PRETTY.toJson(fileObj));

        Path htmlPath = dir().resolve("session-" + stamp + ".html");
        Files.writeString(htmlPath, buildHtml(data, sha, sig, canonical));
        return htmlPath;
    }

    private SessionData buildData(long endEpochMs) {
        LocalPlayer p = Minecraft.getInstance().player;
        String name = p != null ? p.getName().getString() : "unknown";
        String uuid = p != null ? p.getUUID().toString() : "unknown";

        SessionData d = new SessionData();
        d.mod = "Combat Tracker";
        d.mcVersion = "1.21.11";
        d.player = name;
        d.playerUuid = uuid;
        d.startEpochMs = startEpochMs;
        d.endEpochMs = endEpochMs;
        d.startUtc = HUMAN.format(Instant.ofEpochMilli(startEpochMs));
        d.endUtc = HUMAN.format(Instant.ofEpochMilli(endEpochMs));
        d.jumpEvents = new ArrayList<>(jumps);
        d.comboEvents = new ArrayList<>(combos);

        // Summaries computed from this session's events only.
        int hits = 0;
        double sum = 0, sumSq = 0;
        for (JEvent j : jumps) {
            if ("SUCCESS".equals(j.result)) {
                hits++;
            }
            sum += j.deltaMs;
            sumSq += (double) j.deltaMs * j.deltaMs;
        }
        d.jumpAttempts = jumps.size();
        d.jumpHits = hits;
        d.jumpMisses = jumps.size() - hits;
        d.jumpAvgMs = jumps.isEmpty() ? 0 : sum / jumps.size();
        d.jumpSdMs = stdDev(sum, sumSq, jumps.size());

        double csum = 0, csumSq = 0;
        int comboCount = 0;
        for (CEvent c : combos) {
            csum += c.intervalMs;
            csumSq += (double) c.intervalMs * c.intervalMs;
            if (c.newCombo) {
                comboCount++;
            }
        }
        d.comboIntervals = combos.size();
        d.combos = comboCount;
        d.comboAvgMs = combos.isEmpty() ? 0 : csum / combos.size();
        d.comboJitterMs = stdDev(csum, csumSq, combos.size());
        return d;
    }

    private static double stdDev(double sum, double sumSq, int n) {
        if (n == 0) {
            return 0.0;
        }
        double mean = sum / n;
        return Math.sqrt(Math.max(0.0, (sumSq / n) - (mean * mean)));
    }

    // ── HTML report ─────────────────────────────────────────────────────────────

    private String buildHtml(SessionData d, String sha, String sig, String canonical) {
        StringBuilder s = new StringBuilder(8192);
        s.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        s.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        s.append("<title>Combat Tracker session ").append(esc(d.startUtc)).append("</title><style>");
        s.append("body{font-family:system-ui,Segoe UI,Arial,sans-serif;background:#14161a;color:#e8eaed;margin:0;padding:24px;}");
        s.append("h1{font-size:20px;margin:0 0 4px;}h2{font-size:15px;margin:24px 0 8px;color:#9fb3c8;}");
        s.append(".sub{color:#8a93a0;font-size:13px;margin-bottom:16px;}");
        s.append("table{border-collapse:collapse;font-size:13px;margin:4px 0;}td,th{border:1px solid #2a2f37;padding:4px 10px;text-align:left;}");
        s.append("th{background:#1c2026;color:#9fb3c8;}");
        s.append(".cards{display:flex;flex-wrap:wrap;gap:12px;margin:8px 0;}");
        s.append(".card{background:#1c2026;border:1px solid #2a2f37;border-radius:8px;padding:10px 16px;min-width:120px;}");
        s.append(".card .v{font-size:22px;font-weight:600;}.card .k{font-size:12px;color:#8a93a0;}");
        s.append(".intg{background:#161a20;border:1px solid #2a2f37;border-radius:8px;padding:12px;font-family:ui-monospace,Consolas,monospace;font-size:12px;word-break:break-all;}");
        s.append(".warn{color:#ffb454;}circle:hover{stroke:#fff;stroke-width:1.5;}");
        s.append("details{margin-top:8px;}pre{white-space:pre-wrap;word-break:break-all;background:#0f1114;padding:10px;border-radius:6px;font-size:11px;}");
        s.append("</style></head><body>");

        s.append("<h1>Combat Tracker &mdash; Session Report</h1>");
        s.append("<div class=\"sub\">Player <b>").append(esc(d.player)).append("</b> &middot; ")
                .append(esc(d.mod)).append(" on MC ").append(esc(d.mcVersion)).append("</div>");
        s.append("<div class=\"sub\">Charts: scroll to zoom &middot; drag to pan &middot; double-click to reset.</div>");

        s.append("<table><tr><th>Start (time signature)</th><td>").append(esc(d.startUtc))
                .append(" <span class=\"sub\">(epoch ").append(d.startEpochMs).append(")</span></td></tr>");
        s.append("<tr><th>End (time signature)</th><td>").append(esc(d.endUtc))
                .append(" <span class=\"sub\">(epoch ").append(d.endEpochMs).append(")</span></td></tr>");
        long durS = Math.max(0, (d.endEpochMs - d.startEpochMs) / 1000);
        s.append("<tr><th>Duration</th><td>").append(durS / 60).append("m ").append(durS % 60).append("s</td></tr>");
        s.append("<tr><th>Player UUID</th><td>").append(esc(d.playerUuid)).append("</td></tr></table>");

        s.append("<h2>Jump resets</h2><div class=\"cards\">");
        card(s, d.jumpAttempts + "", "attempts");
        card(s, d.jumpHits + "", "hits");
        card(s, d.jumpMisses + "", "misses");
        card(s, fmt(d.jumpAvgMs) + " ms", "avg delta");
        card(s, fmt(d.jumpSdMs) + " ms", "std dev");
        s.append("</div>");
        s.append(chart("Jump-reset delta over time (ms)", d.jumpEvents, true, d.startEpochMs, d.endEpochMs, 0x4caf50));

        s.append("<h2>Combo timing</h2><div class=\"cards\">");
        card(s, d.combos + "", "combos");
        card(s, d.comboIntervals + "", "intervals");
        card(s, fmt(d.comboAvgMs) + " ms", "avg interval");
        card(s, fmt(d.comboJitterMs) + " ms", "jitter (SD)");
        s.append("</div>");
        s.append("<div class=\"sub\">Low jitter relative to the average suggests automated (triggerbot) timing; "
                + "a human combo shows wider spread.</div>");
        s.append(chart("Combo inter-hit interval over time (ms)", comboAsJ(d.comboEvents), false, d.startEpochMs, d.endEpochMs, 0x29b6f6));

        s.append("<h2>Integrity</h2><div class=\"intg\">");
        s.append("Algorithm: SHA-256 + HMAC-SHA256<br>SHA-256: ").append(sha).append("<br>Signature: ").append(sig);
        s.append("<br><span class=\"warn\">Note:</span> the signing key ships inside the open-source mod, so this "
                + "detects casual edits but is <b>not</b> unforgeable. Verify by recomputing SHA-256 over the exact "
                + "canonicalData string below (UTF-8).</div>");
        s.append("<details><summary>Canonical data (hashed bytes)</summary><pre>").append(esc(canonical)).append("</pre></details>");

        s.append("<p class=\"sub\">Generated by Combat Tracker. Charts are SVG embedded in this file (scroll to zoom, drag to pan, double-click to reset).</p>");
        s.append("<script>document.querySelectorAll('svg.chart').forEach(function(svg){"
                + "var vb=svg.getAttribute('viewBox').split(' ').map(Number);var init=vb.slice();"
                + "var v={x:vb[0],y:vb[1],w:vb[2],h:vb[3]};"
                + "function ap(){svg.setAttribute('viewBox',v.x+' '+v.y+' '+v.w+' '+v.h);}"
                + "svg.addEventListener('wheel',function(e){e.preventDefault();var r=svg.getBoundingClientRect();"
                + "var mx=v.x+(e.clientX-r.left)/r.width*v.w;var my=v.y+(e.clientY-r.top)/r.height*v.h;"
                + "var f=e.deltaY<0?0.85:1/0.85;v.w*=f;v.h*=f;"
                + "v.x=mx-(e.clientX-r.left)/r.width*v.w;v.y=my-(e.clientY-r.top)/r.height*v.h;ap();},{passive:false});"
                + "var d=false,lx=0,ly=0;"
                + "svg.addEventListener('mousedown',function(e){d=true;lx=e.clientX;ly=e.clientY;svg.style.cursor='grabbing';});"
                + "window.addEventListener('mousemove',function(e){if(!d)return;var r=svg.getBoundingClientRect();"
                + "v.x-=(e.clientX-lx)/r.width*v.w;v.y-=(e.clientY-ly)/r.height*v.h;lx=e.clientX;ly=e.clientY;ap();});"
                + "window.addEventListener('mouseup',function(){d=false;svg.style.cursor='grab';});"
                + "svg.addEventListener('dblclick',function(){v={x:init[0],y:init[1],w:init[2],h:init[3]};ap();});"
                + "});</script>");
        s.append("</body></html>");
        return s.toString();
    }

    private static void card(StringBuilder s, String value, String key) {
        s.append("<div class=\"card\"><div class=\"v\">").append(esc(value)).append("</div><div class=\"k\">")
                .append(esc(key)).append("</div></div>");
    }

    /** Render a static SVG scatter+line chart from a list of (epochMs, valueMs) points. */
    private static String chart(String title, List<JEvent> pts, boolean isJump, long startMs, long endMs, int rgb) {
        int w = 720, h = 260, ml = 48, mr = 16, mt = 28, mb = 30;
        int pw = w - ml - mr, ph = h - mt - mb;
        String color = String.format("#%06x", rgb & 0xFFFFFF);
        StringBuilder s = new StringBuilder();
        s.append("<svg class=\"chart\" viewBox=\"0 0 ").append(w).append(" ").append(h)
                .append("\" width=\"100%\" style=\"max-width:760px;background:#161a20;border:1px solid #2a2f37;border-radius:8px;cursor:grab;touch-action:none;\">");
        s.append("<text x=\"").append(ml).append("\" y=\"18\" fill=\"#9fb3c8\" font-size=\"13\" font-family=\"sans-serif\">")
                .append(esc(title)).append("</text>");

        if (pts.isEmpty()) {
            s.append("<text x=\"").append(w / 2).append("\" y=\"").append(h / 2)
                    .append("\" fill=\"#5f6772\" font-size=\"13\" text-anchor=\"middle\" font-family=\"sans-serif\">no data recorded</text></svg>");
            return s.toString();
        }

        double yMin = 0, yMax = 1;
        for (JEvent p : pts) {
            yMax = Math.max(yMax, p.deltaMs);
            yMin = Math.min(yMin, p.deltaMs);
        }
        yMax = yMax * 1.1 + 1;
        long span = Math.max(1, endMs - startMs);

        // axes
        s.append("<line x1=\"").append(ml).append("\" y1=\"").append(mt).append("\" x2=\"").append(ml)
                .append("\" y2=\"").append(mt + ph).append("\" stroke=\"#2a2f37\"/>");
        s.append("<line x1=\"").append(ml).append("\" y1=\"").append(mt + ph).append("\" x2=\"").append(ml + pw)
                .append("\" y2=\"").append(mt + ph).append("\" stroke=\"#2a2f37\"/>");
        // y ticks (min, mid, max)
        for (int i = 0; i <= 2; i++) {
            double val = yMin + (yMax - yMin) * i / 2.0;
            int yy = (int) (mt + ph - (val - yMin) / (yMax - yMin) * ph);
            s.append("<line x1=\"").append(ml - 3).append("\" y1=\"").append(yy).append("\" x2=\"").append(ml + pw)
                    .append("\" y2=\"").append(yy).append("\" stroke=\"#21262e\"/>");
            s.append("<text x=\"").append(ml - 6).append("\" y=\"").append(yy + 3)
                    .append("\" fill=\"#6b7480\" font-size=\"10\" text-anchor=\"end\" font-family=\"sans-serif\">")
                    .append(Math.round(val)).append("</text>");
        }

        StringBuilder poly = new StringBuilder();
        StringBuilder dots = new StringBuilder();
        for (JEvent p : pts) {
            int x = (int) (ml + (double) (p.t - startMs) / span * pw);
            int y = (int) (mt + ph - (p.deltaMs - yMin) / (yMax - yMin) * ph);
            poly.append(x).append(",").append(y).append(" ");
            long tOff = (p.t - startMs);
            String tip = "+" + (tOff / 1000) + "." + String.format("%03d", tOff % 1000) + "s : " + p.deltaMs + " ms"
                    + (isJump && p.result != null ? " (" + p.result + ")" : "");
            dots.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"3\" fill=\"")
                    .append(color).append("\"><title>").append(esc(tip)).append("</title></circle>");
        }
        s.append("<polyline points=\"").append(poly.toString().trim()).append("\" fill=\"none\" stroke=\"")
                .append(color).append("\" stroke-opacity=\"0.5\" stroke-width=\"1\"/>");
        s.append(dots);
        s.append("</svg>");
        return s.toString();
    }

    /** Adapt combo events into the chart's point type. */
    private static List<JEvent> comboAsJ(List<CEvent> combos) {
        List<JEvent> out = new ArrayList<>(combos.size());
        for (CEvent c : combos) {
            out.add(new JEvent(c.t, c.intervalMs, null));
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static String esc(String in) {
        if (in == null) {
            return "";
        }
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void chat(String msg, ChatFormatting color) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.displayClientMessage(Component.literal("[Combat Tracker] " + msg).withStyle(color), false);
        }
    }

    // ── Data shapes (Gson) ────────────────────────────────────────────────────

    private static final class JEvent {
        final long t;
        final long deltaMs;
        final String result;

        JEvent(long t, long deltaMs, String result) {
            this.t = t;
            this.deltaMs = deltaMs;
            this.result = result;
        }
    }

    private static final class CEvent {
        final long t;
        final long intervalMs;
        final boolean newCombo;

        CEvent(long t, long intervalMs, boolean newCombo) {
            this.t = t;
            this.intervalMs = intervalMs;
            this.newCombo = newCombo;
        }
    }

    private static final class SessionData {
        String mod;
        String mcVersion;
        String player;
        String playerUuid;
        long startEpochMs;
        long endEpochMs;
        String startUtc;
        String endUtc;
        int jumpAttempts;
        int jumpHits;
        int jumpMisses;
        double jumpAvgMs;
        double jumpSdMs;
        int comboIntervals;
        int combos;
        double comboAvgMs;
        double comboJitterMs;
        List<JEvent> jumpEvents;
        List<CEvent> comboEvents;
    }

    private record Integrity(String hashAlgorithm, String sha256, String signatureAlgorithm, String signature,
                             String note) {
    }

    private record SessionFile(String canonicalData, Integrity integrity) {
    }
}
