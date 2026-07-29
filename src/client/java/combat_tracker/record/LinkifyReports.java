package combat_tracker.record;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns saved HTML reports back into share links, titled by their file name.
 *
 * <p>Every report embeds the exact canonical JSON it was built from, so a link
 * can be rebuilt from the file alone with no loss. That includes reports written
 * long before links existed: the fields added since simply come out as zero, and
 * the summary numbers and event series are the originals.</p>
 *
 * <p>Run: {@code ./gradlew linkifyReports -PlinkifyDir=<folder> [-PlinkifyBase=<url>]}</p>
 */
public final class LinkifyReports {
    private static final Gson GSON = new Gson();
    private static final String OPEN = "<pre>";
    private static final String CLOSE = "</pre>";

    private LinkifyReports() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String base = args.length > 1 && !args[1].isBlank()
                ? args[1] : "https://builtdoor1.github.io/Combat-Tracker/";

        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".html"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase()))
                    .toList();
        }

        record Row(String title, String player, int jumps, String link) { }
        List<Row> rows = new ArrayList<>();
        int ok = 0;
        for (Path f : files) {
            String name = f.getFileName().toString();
            String title = name.substring(0, name.length() - 5);   // drop ".html"
            try {
                SessionData d = parse(Files.readString(f));
                if (d == null) {
                    System.out.println("SKIP  " + name + "  (no canonical data found)");
                    continue;
                }
                d.title = title;
                String link = SharePayload.buildLink(base, d);
                rows.add(new Row(title, d.player, d.jumpAttempts, link));
                System.out.printf("%-28s %-12s %3d jumps  %4d chars%s%n",
                        title, d.player, d.jumpAttempts, link.length(),
                        link.length() > 2000 ? "  <-- OVER DISCORD LIMIT" : "");
                ok++;
            } catch (Exception e) {
                System.out.println("FAIL  " + name + "  " + e);
            }
        }

        Path out = dir.resolve("session-links.md");
        StringBuilder md = new StringBuilder("# Combat Tracker session links\n\n");
        for (Row r : rows) {
            md.append("- [").append(r.title()).append("](").append(r.link()).append(")  \n")
                    .append("  ").append(r.player()).append(", ").append(r.jumps())
                    .append(" jump resets\n");
        }
        Files.writeString(out, md.toString());
        System.out.println("\n" + ok + "/" + files.size() + " converted -> " + out.toAbsolutePath());
    }

    /** Pulls the canonical JSON out of the report's integrity section. */
    private static SessionData parse(String html) {
        int i = html.indexOf(OPEN);
        int j = html.indexOf(CLOSE, i + 1);
        if (i < 0 || j < 0) {
            return null;
        }
        return GSON.fromJson(unescape(html.substring(i + OPEN.length(), j)), SessionData.class);
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");
    }
}
