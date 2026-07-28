package combat_tracker.record;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads back the sessions already saved to disk.
 *
 * <p>A share link is not stored anywhere: the recorder only keeps the most recent
 * one in memory, so restarting the game used to leave every earlier session
 * unshareable. It does not have to be stored, though. The {@code .json} holds the
 * exact canonical data the link is built from, so a link can be rebuilt from any
 * saved session at any time, including ones recorded long before this existed.</p>
 *
 * <p>Rebuilding rather than storing also means a link always reflects the current
 * viewer address, so renaming the account that hosts the viewer does not strand
 * every past recording.</p>
 */
public final class SavedRecordings {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat_tracker/record");
    private static final Gson GSON = new Gson();

    /** Plenty to scroll through; avoids building a huge list from a long history. */
    private static final int MAX_LISTED = 60;

    private SavedRecordings() {
    }

    /** One saved session, newest first. */
    public record Entry(Path json, Path html, SessionData data) {

        /** Short label for a list row: when, who, and how much is in it. */
        public String label() {
            String when = data.startUtc == null ? "?" : data.startUtc.replace(" UTC", "");
            if (when.length() > 16) {
                when = when.substring(0, 16); // drop seconds
            }
            return when + "  " + (data.player == null ? "?" : data.player)
                    + "  " + data.swings + " swings, " + data.jumpAttempts + " jumps";
        }
    }

    /**
     * Every readable session in the recordings folder, newest first.
     *
     * <p>A file that cannot be parsed is skipped rather than failing the whole
     * list: one bad recording should not make the others unreachable.</p>
     */
    public static List<Entry> list() {
        return list(SessionRecorder.dir());
    }

    /** As {@link #list()} but for an explicit folder, which keeps it testable. */
    public static List<Entry> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                Entry e = read(p);
                if (e != null) {
                    out.add(e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not list recordings", e);
            return List.of();
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.data().startEpochMs).reversed());
        return out.size() > MAX_LISTED ? out.subList(0, MAX_LISTED) : out;
    }

    private static Entry read(Path json) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(json), JsonObject.class);
            if (root == null || !root.has("canonicalData")) {
                return null;
            }
            SessionData data = GSON.fromJson(root.get("canonicalData").getAsString(), SessionData.class);
            if (data == null) {
                return null;
            }
            String name = json.getFileName().toString();
            Path html = json.resolveSibling(name.substring(0, name.length() - 5) + ".html");
            return new Entry(json, html, data);
        } catch (Exception e) {
            LOGGER.warn("Skipping unreadable recording {}", json.getFileName(), e);
            return null;
        }
    }

    /** Rebuilds the share link for a saved session. */
    public static String linkFor(SessionData data, String baseUrl) {
        return SharePayload.buildLink(baseUrl, data);
    }
}
