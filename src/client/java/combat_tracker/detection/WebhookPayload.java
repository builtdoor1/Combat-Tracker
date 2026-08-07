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
 * close the code spans values sit inside, and newlines would let a name append
 * convincing extra lines of its own.</p>
 */
public final class WebhookPayload {
    /** Discord's per-field limits. Values are kept well inside them. */
    static final int MAX_FIELD_VALUE = 1024;

    /** Amber. Chosen to read as "look at this", not as an error or an all-clear. */
    private static final int COLOR = 0xFFB454;

    /** Renders a player head from a name or UUID. Same service the reports use. */
    private static final String HEAD_URL = "https://mc-heads.net/avatar/";

    private WebhookPayload() {
    }

    /**
     * One alert as a Discord embed.
     *
     * @param whenIso ISO-8601 instant; Discord renders it in each reader's own
     *                timezone, which beats printing one timezone at everyone
     * @param opponent who they were fighting, or null if not recently in a fight
     */
    public static String build(String player, String uuid, String server, String whenIso,
                               int hotbar, int use, int attack, int keybind,
                               boolean recording, String shareLink, String opponent) {
        String safePlayer = safe(player);
        String safeUuid = safe(uuid);

        StringBuilder fields = new StringBuilder();
        // Each check gets its own line saying what it means, because "hotbar: 3" on
        // its own tells a reader nothing about what was actually observed.
        fields.append(explain("Hotbar switched", hotbar,
                "changed slot with no key, scroll or server packet behind it"));
        fields.append(explain("Item used", use,
                "right-click use ran without the use key being pressed"));
        fields.append(explain("Attacked", attack,
                "an attack ran without the attack key being pressed"));
        fields.append(explain("Keybind pressed by code", keybind,
                "a key was held or clicked by software rather than by hand"));

        StringBuilder f = new StringBuilder(1024);
        f.append("{\"embeds\":[{");
        f.append("\"title\":").append(jsonString("Unattributed input detected"));
        f.append(",\"color\":").append(COLOR);
        f.append(",\"timestamp\":").append(jsonString(safe(whenIso)));
        // The head renders beside the embed, so an alert is identifiable at a glance
        // rather than by reading a UUID.
        f.append(",\"thumbnail\":{\"url\":")
                .append(jsonString(HEAD_URL + urlSafe(safeUuid.isEmpty() ? safePlayer : safeUuid) + "/128"))
                .append("}");
        f.append(",\"author\":{\"name\":").append(jsonString(safePlayer))
                .append(",\"icon_url\":").append(jsonString(HEAD_URL + urlSafe(safePlayer) + "/32")).append("}");

        f.append(",\"fields\":[");
        f.append(field("Player", "`" + safePlayer + "`\n`" + safeUuid + "`", false));
        f.append(",").append(field("Server", "`" + safe(server) + "`", true));
        f.append(",").append(field("Fighting",
                opponent == null || opponent.isBlank() ? "_no recent opponent_" : "`" + safe(opponent) + "`", true));
        f.append(",").append(field("What tripped", fields.toString().trim(), false));
        if (recording) {
            f.append(",").append(field("Recording", "in progress when this fired", true));
        }
        if (shareLink != null && !shareLink.isBlank()) {
            f.append(",").append(field("Last session", "[open report](" + safe(shareLink) + ")", true));
        }
        f.append("]}]");
        f.append(",\"allowed_mentions\":{\"parse\":[]}}");
        return f.toString();
    }

    /** One "N x — what that means" line, omitted entirely when the count is zero. */
    private static String explain(String label, int n, String meaning) {
        if (n <= 0) {
            return "";
        }
        return "**" + label + "** × " + n + "\n╰ _" + meaning + "_\n";
    }

    private static String field(String name, String value, boolean inline) {
        String v = value == null || value.isBlank() ? "_none_" : value;
        if (v.length() > MAX_FIELD_VALUE) {
            v = v.substring(0, MAX_FIELD_VALUE - 1) + "…";
        }
        return "{\"name\":" + jsonString(name) + ",\"value\":" + jsonString(v)
                + ",\"inline\":" + inline + "}";
    }

    /**
     * Strips what would let an untrusted string distort the message.
     *
     * <p>Long values are truncated: a 30k-character name would otherwise consume the
     * whole budget and push the real fields out of the embed.</p>
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

    /**
     * Restricts a value to what can appear in a URL path segment.
     *
     * <p>The head URL is built from a player name or UUID, and both are attacker
     * supplied. Anything outside this set could otherwise steer the request at a
     * different path or host entirely.</p>
     */
    static String urlSafe(String in) {
        String out = in == null ? "" : in.replaceAll("[^A-Za-z0-9_-]", "");
        return out.isEmpty() ? "steve" : out;
    }

    /** Minimal JSON string escaping — one payload does not justify a dependency. */
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
