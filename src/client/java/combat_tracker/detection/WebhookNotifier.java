package combat_tracker.detection;

import combat_tracker.record.SessionRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Posts a Discord message when input-provenance checks trip.
 *
 * <p>Reports who, where and when: the player and UUID, the server, the UTC time, a
 * breakdown of what tripped, and the share link for the recording if one is running.</p>
 *
 * <p><b>Batched, never per-event.</b> A triggerbot can trip a check every tick.
 * Posting one message each would be twenty a second, which Discord rate-limits
 * immediately and which makes the channel useless even if it did not. Flags are
 * accumulated and summarised at most once per {@link #WINDOW_MS}, and the whole
 * session is capped at {@link #MAX_MESSAGES} so a single bad session can never
 * flood a channel. The counts stay exact; only the number of messages is bounded.</p>
 *
 * <p><b>Never touches the game thread.</b> Sends are asynchronous and every failure
 * is swallowed — a webhook that is down, rate-limited or misconfigured must not stall
 * a tick or spam the log. Nothing here can throw into the caller.</p>
 *
 * <p><b>Mentions are disabled explicitly.</b> Player names, opponent names and server
 * names all end up in the message body, and none of them are under your control.
 * {@code allowed_mentions.parse: []} means a player calling themselves something
 * designed to look like an everyone-ping cannot make this notify a channel.</p>
 */
public final class WebhookNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat_tracker/webhook");

    /**
     * Where flag reports go. Always on — see {@link combat_tracker.config.CtConfig}
     * for why this is not a setting.
     *
     * <p>Baked in at build time from the {@code combatTrackerAlertEndpoint} Gradle
     * property (see build.gradle) rather than written into this file, so the URL
     * never enters version control. A build with the property unset produces an
     * empty value and reports nowhere, which is what a fresh clone or CI should do.</p>
     *
     * <p>Be clear about what that does and does not protect. A Discord webhook URL is
     * a bearer credential with no authentication beyond the secret itself: anyone
     * holding it can post to the channel, and a {@code DELETE} to the same URL
     * destroys the webhook outright. Keeping it out of git keeps it out of a public
     * repository and its permanent history — it does not keep it out of the jar,
     * where a few minutes with any unzip tool recovers it. The durable arrangement
     * is to point this at a relay you control that holds the real webhook
     * server-side; then the secret in the jar is only an address.</p>
     */
    private static final String ALERT_ENDPOINT = loadEndpoint();

    private static String loadEndpoint() {
        try (var in = WebhookNotifier.class.getResourceAsStream("/combat_tracker/alert-endpoint.txt")) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            // A missing or unreadable resource means "report nowhere", never a crash.
            return "";
        }
    }

    /** Minimum spacing between messages. */
    private static final long WINDOW_MS = 15_000L;
    /** Hard stop per game session, so one bad session cannot flood a channel. */
    private static final int MAX_MESSAGES = 20;

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    private static final WebhookNotifier INSTANCE = new WebhookNotifier();

    public static WebhookNotifier get() {
        return INSTANCE;
    }

    private HttpClient http;

    private int pendingHotbar;
    private int pendingUse;
    private int pendingAttack;
    private int pendingKeybind;
    private long firstPendingMs;
    private long lastSentMs;
    private int sentThisSession;

    private WebhookNotifier() {
    }

    /** Called for every flag. Cheap and non-blocking: this only accumulates. */
    public void onFlag(IntegrityMonitor.Kind kind) {
        if (!reportingConfigured()) {
            return;
        }
        switch (kind) {
            case HOTBAR -> pendingHotbar++;
            case USE -> pendingUse++;
            case ATTACK -> pendingAttack++;
            case KEYBIND -> pendingKeybind++;
        }
        if (firstPendingMs == 0L) {
            firstPendingMs = System.currentTimeMillis();
        }
    }

    /**
     * Called once per client tick. Sends a summary when the window has elapsed.
     *
     * <p>Driven from the tick rather than from {@link #onFlag} so that the last few
     * flags before a lull still get reported, instead of waiting for a flag that may
     * never come.</p>
     */
    public void tick() {
        if (firstPendingMs == 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - firstPendingMs < WINDOW_MS) {
            return;
        }
        if (sentThisSession >= MAX_MESSAGES) {
            clearPending();
            return;
        }
        String body = buildMessage(now);
        clearPending();
        if (body != null) {
            sentThisSession++;
            post(body);
        }
    }

    /** Resets the per-session message budget. Called when a recording starts. */
    public void resetBudget() {
        sentThisSession = 0;
    }

    private void clearPending() {
        pendingHotbar = 0;
        pendingUse = 0;
        pendingAttack = 0;
        pendingKeybind = 0;
        firstPendingMs = 0L;
    }

    // ── Message ──────────────────────────────────────────────────────────────

    private String buildMessage(long now) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        return WebhookPayload.build(
                player.getName().getString(),
                player.getUUID().toString(),
                describeServer(mc),
                UTC.format(Instant.ofEpochMilli(now)),
                pendingHotbar, pendingUse, pendingAttack, pendingKeybind,
                SessionRecorder.get().isRecording(),
                SessionRecorder.get().lastShareLink());
    }

    /** Server address, or a plain marker for singleplayer. */
    private static String describeServer(Minecraft mc) {
        if (mc.isSingleplayer() || mc.isLocalServer()) {
            return "singleplayer";
        }
        ServerData data = mc.getCurrentServer();
        if (data == null) {
            return "unknown";
        }
        String ip = data.ip == null ? "" : data.ip;
        String name = data.name == null ? "" : data.name;
        return ip.isBlank() ? name : (name.isBlank() ? ip : ip + " (" + name + ")");
    }

    // ── Transport ────────────────────────────────────────────────────────────

    /** True when this build has somewhere to report to. */
    public static boolean reportingConfigured() {
        return ALERT_ENDPOINT.startsWith("https://");
    }

    private void post(String jsonBody) {
        String url = ALERT_ENDPOINT;
        if (!url.startsWith("https://")) {
            return;   // refuse to send anywhere unencrypted, or to a malformed target
        }
        try {
            if (http == null) {
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CombatTracker (+https://github.com/builtdoor1/Combat-Tracker)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(r -> {
                        // 204 is the documented success. Anything else is logged once
                        // at debug and otherwise ignored: a broken webhook is not a
                        // reason to interrupt someone's game.
                        if (r.statusCode() != 204 && r.statusCode() != 200) {
                            LOGGER.debug("Webhook returned HTTP {}", r.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        LOGGER.debug("Webhook send failed: {}", e.toString());
                        return null;
                    });
        } catch (Exception e) {
            // Malformed URL, no network stack, anything at all. Never propagates.
            LOGGER.debug("Webhook not sent: {}", e.toString());
        }
    }
}
