package combat_tracker.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
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
 * Records a session of jump-reset, combo and swing events into a tamper-evident
 * report.
 *
 * <p>On stop, writes two files into {@code config/combat_tracker/recordings/}: a
 * {@code .json} holding the canonical data plus a SHA-256 hash and an HMAC-SHA256
 * signature, and a self-contained {@code .html} report. It also builds a shareable
 * link ({@link SharePayload}).</p>
 *
 * <p>This class owns the recording lifecycle and everything that needs Minecraft.
 * Rendering lives in {@link ReportBuilder} and encoding in {@link SharePayload},
 * both game-free so they can be tested headlessly.</p>
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
    private final List<SessionData.JEvent> jumps = new ArrayList<>();
    private final List<SessionData.CEvent> combos = new ArrayList<>();
    private final List<SessionData.SEvent> swings = new ArrayList<>();
    private final List<SessionData.FEvent> flags = new ArrayList<>();
    private final List<String> opponents = new ArrayList<>();

    private int flagHotbar;
    private int flagUse;
    private int flagAttack;
    private int flagKeybind;

    /** Link for the most recently saved session, or null if there isn't one yet. */
    private String lastShareLink = null;

    // Ping is averaged across the whole recording rather than read at the moment
    // you stop. A snapshot at stop time reports whatever spike happened to be in
    // flight, which is how a session played at ~25ms came out as 90.
    private long pingSumMs = 0;
    private int pingSamples = 0;

    // Who recorded this, captured while playing rather than when saving. Reading
    // it at stop time meant that stopping after leaving a server produced a report
    // with no name on it at all: the player is gone by then, even though every
    // event recorded correctly.
    private String playerName = null;
    private String playerUuid = null;

    public boolean isRecording() {
        return recording;
    }

    public long startEpochMs() {
        return startEpochMs;
    }

    public String lastShareLink() {
        return lastShareLink;
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
        swings.clear();
        flags.clear();
        opponents.clear();
        flagHotbar = 0;
        flagUse = 0;
        flagAttack = 0;
        flagKeybind = 0;
        pingSumMs = 0;
        pingSamples = 0;
        playerName = null;
        playerUuid = null;
        LocalPlayer self = Minecraft.getInstance().player;
        if (self != null) {
            playerName = self.getName().getString();
            playerUuid = self.getUUID().toString();
        }
        combat_tracker.detection.WebhookNotifier.get().resetBudget();
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
            if (lastShareLink != null) {
                shareLinkChat(lastShareLink);
            }
            return html;
        } catch (Exception e) {
            LOGGER.error("Failed to write recording", e);
            chat("Failed to save recording: " + e.getMessage(), ChatFormatting.RED);
            return null;
        }
    }

    // ── Event intake ──────────────────────────────────────────────────────────

    public void recordJump(long deltaMs, String result) {
        if (recording) {
            jumps.add(new SessionData.JEvent(System.currentTimeMillis(), deltaMs, result));
        }
    }

    public void recordCombo(long intervalMs, boolean newCombo) {
        if (recording) {
            combos.add(new SessionData.CEvent(System.currentTimeMillis(), intervalMs, newCombo));
        }
    }

    /**
     * One action with no vanilla input behind it. See {@code IntegrityMonitor}.
     *
     * <p>Counted even past {@code maxEvents} so the totals stay honest; only the
     * plotted timeline stops growing. A session that trips this thousands of times
     * is already described by the first couple of hundred.</p>
     */
    public void recordFlag(combat_tracker.detection.IntegrityMonitor.Kind kind, int maxEvents) {
        if (!recording) {
            return;
        }
        switch (kind) {
            case HOTBAR -> flagHotbar++;
            case USE -> flagUse++;
            case ATTACK -> flagAttack++;
            case KEYBIND -> flagKeybind++;
        }
        if (flags.size() < maxEvents) {
            flags.add(new SessionData.FEvent(System.currentTimeMillis(), kind.ordinal()));
        }
    }

    /** Called once per client tick while recording, to average ping over the session. */
    public void samplePing(int roundTripMs) {
        if (recording && roundTripMs > 0) {
            pingSumMs += roundTripMs;
            pingSamples++;
        }
    }

    /**
     * Remembers who is playing, called every tick while recording.
     *
     * <p>Taken here rather than when the report is written. Stopping a recording
     * after leaving a server is entirely normal, and by then there is no player to
     * ask, which produced reports headed "unknown" with no skin despite every event
     * having recorded correctly.</p>
     */
    public void noteIdentity(String name, String uuid) {
        if (recording && name != null && uuid != null) {
            playerName = name;
            playerUuid = uuid;
        }
    }

    /** One attack swing with its measured geometry. See {@code SwingTracker}. */
    public void recordSwing(double reach, boolean hit, double aimDeg, double offX, double offY, String targetName) {
        if (!recording) {
            return;
        }
        swings.add(new SessionData.SEvent(System.currentTimeMillis(), reach, hit, aimDeg, offX, offY,
                opponentIndex(targetName)));
    }

    /** Removes Minecraft colour and style codes, and any leftover whitespace. */
    static String stripFormatting(String in) {
        return in == null ? null : in.replaceAll("§.", "").trim();
    }

    private static String firstNonNull(String a, String b, String fallback) {
        if (a != null) {
            return a;
        }
        return b != null ? b : fallback;
    }

    /**
     * Opponents are stored once and referenced by index, keeping the link small.
     *
     * <p>Names are stripped of formatting codes first. Practice servers use entities
     * whose display name carries colour codes, and storing those raw put a literal
     * paragraph sign into the report, the share link and the skin lookup URL.</p>
     */
    private int opponentIndex(String rawName) {
        String name = stripFormatting(rawName);
        if (name == null || name.isEmpty()) {
            return -1;
        }
        int i = opponents.indexOf(name);
        if (i >= 0) {
            return i;
        }
        opponents.add(name);
        return opponents.size() - 1;
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
        Files.writeString(htmlPath, ReportBuilder.build(data, sha, sig, canonical));

        lastShareLink = SharePayload.buildLink(combat_tracker.config.CtConfig.get().shareBaseUrl, data);
        return htmlPath;
    }

    private SessionData buildData(long endEpochMs) {
        LocalPlayer p = Minecraft.getInstance().player;

        SessionData d = new SessionData();
        d.mod = "Combat Tracker";
        d.mcVersion = "1.21.11";
        d.player = firstNonNull(playerName, p != null ? p.getName().getString() : null, "unknown");
        d.playerUuid = firstNonNull(playerUuid, p != null ? p.getUUID().toString() : null, "unknown");
        d.startEpochMs = startEpochMs;
        d.endEpochMs = endEpochMs;
        d.startUtc = HUMAN.format(Instant.ofEpochMilli(startEpochMs));
        d.endUtc = HUMAN.format(Instant.ofEpochMilli(endEpochMs));
        d.jumpEvents = new ArrayList<>(jumps);
        d.comboEvents = new ArrayList<>(combos);
        d.swingEvents = new ArrayList<>(swings);
        d.flagEvents = new ArrayList<>(flags);
        d.opponents = new ArrayList<>(opponents);
        d.flagHotbar = flagHotbar;
        d.flagUse = flagUse;
        d.flagAttack = flagAttack;
        d.flagKeybind = flagKeybind;
        // Mean across the whole recording, not a reading taken at the moment you
        // stopped. Context only; it adjusts no measurement.
        d.pingMs = pingSamples == 0 ? 0 : (double) pingSumMs / pingSamples;
        SessionStats.summarise(d);
        return d;
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    private void chat(String msg, ChatFormatting color) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.displayClientMessage(Component.literal("[Combat Tracker] " + msg).withStyle(color), false);
        }
    }

    /**
     * Prints the share link as a click-to-copy chat component. Copying rather than
     * opening keeps it one click — Minecraft interrupts an outbound link with a
     * confirmation screen, and the point of this is to paste it somewhere.
     */
    private void shareLinkChat(String link) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        Component msg = Component.literal("[Combat Tracker] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Click to copy share link]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(link))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal(link).withStyle(ChatFormatting.GRAY)))));
        p.displayClientMessage(msg, false);
    }

    // ── Integrity file shape ─────────────────────────────────────────────────

    private record Integrity(String hashAlgorithm, String sha256, String signatureAlgorithm, String signature,
                             String note) {
    }

    private record SessionFile(String canonicalData, Integrity integrity) {
    }
}
