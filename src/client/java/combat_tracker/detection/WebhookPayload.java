package combat_tracker.detection;

/**
 * Builds the Discord request body. Deliberately free of any Minecraft import so the
 * escaping can be tested headlessly — everything here handles strings that came from
 * other players, which is exactly the code worth testing.
 *
 * <p>Two separate protections, because they cover different attacks:</p>
 *
 * <ul>
 *   <li>{@link #jsonString} keeps a crafted name from breaking out of the JSON
 *       string and forging fields in the request.</li>
 *   <li>{@code allowed_mentions.parse: []} keeps a name that reads like an
 *       everyone-ping from actually notifying the channel. Escaping alone would not
 *       do it: {@code @everyone} is perfectly valid JSON.</li>
 * </ul>
 *
 * <p>{@link #safe} then strips what would make the message lie — backticks would
 * close the code spans these values sit inside, and newlines would let a name append
 * convincing extra lines of its own.</p>
 */
public final class WebhookPayload {
    /** Discord's hard limit is 2000; leave room rather than court a 400. */
    static final int MAX_CONTENT = 1800;

    private WebhookPayload() {
    }

    /** The full JSON body for one alert. */
    public static String build(String player, String uuid, String server, String whenUtc,
                               int hotbar, int use, int attack, int keybind,
                               boolean recording, String shareLink) {
        StringBuilder s = new StringBuilder(512);
        s.append("**Combat Tracker — unattributed input**\n");
        s.append("Player: `").append(safe(player)).append("`  (`").append(safe(uuid)).append("`)\n");
        s.append("Server: `").append(safe(server)).append("`\n");
        s.append("Time: `").append(safe(whenUtc)).append("`\n");
        s.append("Tripped: ")
                .append(part("hotbar", hotbar)).append(part("use", use))
                .append(part("attack", attack)).append(part("keybind", keybind)).append("\n");
        if (recording) {
            s.append("Recording in progress.\n");
        }
        if (shareLink != null && !shareLink.isBlank()) {
            // Angle brackets suppress Discord's link preview, which would otherwise
            // make every alert several times taller than it needs to be.
            s.append("Last session: <").append(safe(shareLink)).append(">\n");
        }
        s.append("_An unattributed action is not by itself proof of cheating; ")
                .append("controller and accessibility mods trip the keybind check legitimately._");

        String content = s.toString();
        if (content.length() > MAX_CONTENT) {
            content = content.substring(0, MAX_CONTENT - 1) + "…";
        }
        return "{\"content\":" + jsonString(content) + ",\"allowed_mentions\":{\"parse\":[]}}";
    }

    private static String part(String label, int n) {
        return n == 0 ? "" : n + " " + label + (n == 1 ? " " : "s ");
    }

    /**
     * Strips what would let an untrusted string distort the message.
     *
     * <p>Long values are truncated: a 30k-character name would otherwise consume the
     * whole content budget and push the real fields out of the message.</p>
     */
    static String safe(String in) {
        if (in == null) {
            return "unknown";
        }
        String out = in.replaceAll("[`\\r\\n]", "").trim();
        if (out.length() > 120) {
            out = out.substring(0, 120);
        }
        return out.isEmpty() ? "unknown" : out;
    }

    /** Minimal JSON string escaping — one field does not justify a dependency. */
    static String jsonString(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
