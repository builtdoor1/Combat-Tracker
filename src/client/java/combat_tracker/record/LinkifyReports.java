package combat_tracker.record;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns saved HTML reports back into share links, titled by their file name.
 *
 * <p>Every report embeds the exact canonical JSON it was built from, so a link
 * can be rebuilt from the file alone with no loss.</p>
 *
 * <p><b>Integrity.</b> Every report also embeds a SHA-256 and an HMAC-SHA256 over
 * that canonical data, and this tool recomputes both before it will emit a link.
 * That check used to be missing, which made this the easiest way to launder edited
 * numbers: open a report in a text editor, change the figures in the canonical
 * block, run this, and out came a clean-looking link — while the hash sitting a few
 * lines above in the same file still described the data the report used to hold.
 * See {@link IntegrityUtil} for what the signature is and is not worth.</p>
 *
 * <p><b>A report that cannot be checked does not get a link.</b> The canonical
 * {@code <pre>} block and the integrity block were added in the same commit
 * (v1.5.0), so no report has ever had one without the other: anything older has no
 * canonical data at all and is skipped before it reaches this check. That leaves no
 * honest population for an "unsigned but linkable" path to serve, and a linkable
 * unsigned path is strictly worse than useless — perturbing one character of the
 * integrity markup would be a cheaper forgery than editing the numbers, and the
 * resulting link is indistinguishable from a verified one once it leaves this
 * machine. The link carries no verdict; only the console and
 * {@code session-links.md} do, and neither travels with a pasted URL.</p>
 *
 * <p>Run: {@code ./gradlew linkifyReports -PlinkifyDir=<folder> [-PlinkifyBase=<url>]}</p>
 */
public final class LinkifyReports {
    private static final Gson GSON = new Gson();
    private static final String OPEN = "<pre>";
    private static final String CLOSE = "</pre>";

    /**
     * The integrity container, matched loosely on purpose.
     *
     * <p>Keying "is this report signed?" on the exact 18 bytes of
     * {@code <div class="intg">} would let one character decide it: swapping the
     * quotes, adding an attribute or changing the case all render identically in a
     * browser, so the report still visibly shows its hashes while this tool
     * concludes there is nothing to check.</p>
     */
    private static final Pattern INTEGRITY_TAG = Pattern.compile(
            "<[a-z]+\\b[^>]*class\\s*=\\s*[\"']?[^\"'>]*\\bintg\\b", Pattern.CASE_INSENSITIVE);

    /** Any sign the document means to present itself as signed. */
    private static final Pattern CLAIMS_SIGNED = Pattern.compile(
            ">\\s*Integrity\\s*<|SHA-256\\s*:|Signature\\s*:", Pattern.CASE_INSENSITIVE);

    private static final Pattern SHA_LINE = Pattern.compile("SHA-256:\\s*([0-9a-fA-F]{64})");
    private static final Pattern SIG_LINE = Pattern.compile("Signature:\\s*([0-9a-fA-F]{64})");

    private LinkifyReports() {
    }

    /** What a report's own integrity block says about the data next to it. */
    private enum Verdict {
        /** Hash and signature both match. */
        OK("", true),
        /**
         * Matches once surrounding whitespace is ignored. An HTML parser drops a
         * newline straight after {@code <pre>}, so reformatting a report is a real
         * and harmless thing to have happened; calling it forged would teach people
         * to ignore the warning that matters.
         */
        REFORMATTED("  <-- ok (canonical block reformatted, data unchanged)", true),
        /** No integrity block, and nothing claiming there should be one. */
        UNSIGNED("  <-- NO INTEGRITY BLOCK, no link written", false),
        /** Structure a genuine report never has: a check that cannot be run. */
        MALFORMED("  <-- INTEGRITY BLOCK UNREADABLE, no link written", false),
        /** An integrity block that does not match the data it sits beside. */
        TAMPERED("  <-- FAILED ITS OWN INTEGRITY CHECK, no link written", false);

        final String note;
        final boolean linkable;

        Verdict(String note, boolean linkable) {
            this.note = note;
            this.linkable = linkable;
        }
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
        int refused = 0;
        for (Path f : files) {
            String name = f.getFileName().toString();
            String title = name.substring(0, name.length() - 5);   // drop ".html"
            try {
                Parsed parsed = parse(Files.readString(f));
                if (parsed == null) {
                    System.out.println("SKIP  " + name + "  (no canonical data found)");
                    continue;
                }
                Verdict verdict = parsed.verdict();
                if (!verdict.linkable) {
                    refused++;
                    String who = parsed.data() == null ? "?" : String.valueOf(parsed.data().player);
                    System.out.printf("%-28s %-12s %s%n", title, who, verdict.note.trim());
                    continue;
                }
                SessionData d = parsed.data();
                d.title = title;
                String link = SharePayload.buildLink(base, d);
                rows.add(new Row(title, d.player, d.jumpAttempts, link));
                System.out.printf("%-28s %-12s %3d jumps  %4d chars%s%s%n",
                        title, d.player, d.jumpAttempts, link.length(),
                        link.length() > 2000 ? "  <-- OVER DISCORD LIMIT" : "", verdict.note);
                ok++;
            } catch (Exception e) {
                System.out.println("FAIL  " + name + "  " + e);
            }
        }

        Path out = dir.resolve("session-links.md");
        StringBuilder md = new StringBuilder("# Combat Tracker session links\n\n");
        // Said once here rather than on every row: the title is the file name, and
        // the file name was never part of what the hash covered.
        md.append("Each link below was rebuilt from a report whose SHA-256 and HMAC-SHA256 "
                + "still matched its own data. Titles come from file names and are **not** "
                + "covered by that check.\n\n");
        for (Row r : rows) {
            md.append("- [").append(r.title()).append("](").append(r.link()).append(")  \n")
                    .append("  ").append(r.player()).append(", ").append(r.jumps())
                    .append(" jump resets\n");
        }
        Files.writeString(out, md.toString());
        System.out.println("\n" + ok + "/" + files.size() + " converted -> " + out.toAbsolutePath());
        if (refused > 0) {
            System.out.println(refused + " report(s) could not be verified and were skipped. "
                    + "A link is only written for a report that still matches its own hashes.");
            System.exit(2);
        }
    }

    /** A report's canonical data together with the verdict of its integrity block. */
    private record Parsed(SessionData data, Verdict verdict) {
    }

    /**
     * Pulls the canonical JSON out of the report and checks it against the hashes.
     *
     * <p>Everything here is deliberately strict about <em>structure</em> before it
     * is strict about bytes. A file handed to this tool is under the full control of
     * whoever hands it over, so "the report generator would never emit that" is not
     * a safety argument — only what this method insists on is.</p>
     */
    private static Parsed parse(String html) {
        Matcher tag = INTEGRITY_TAG.matcher(html);
        boolean hasBlock = tag.find();
        int intgAt = hasBlock ? tag.start() : -1;

        int i = html.indexOf(OPEN);
        if (i < 0 || html.indexOf(CLOSE, i + 1) < 0) {
            return null;                       // predates the canonical block entirely
        }
        if (!hasBlock) {
            // Absence has to be proven, not inferred from one failed match: a report
            // still showing "SHA-256: ..." is claiming to be signed, so failing to
            // locate the block is a defect in the file, not evidence it never had one.
            return new Parsed(null, CLAIMS_SIGNED.matcher(html).find()
                    ? Verdict.MALFORMED : Verdict.UNSIGNED);
        }
        // A genuine report has exactly one <pre>, and it follows the integrity block.
        // Anything else means the block being hashed is not the block being read.
        if (html.indexOf(OPEN) != html.lastIndexOf(OPEN) || i < intgAt) {
            return new Parsed(null, Verdict.MALFORMED);
        }

        int j = html.indexOf(CLOSE, i + 1);
        // Verified as the exact string that was hashed, before anything downstream
        // mutates the object — main() overwrites the title from the file name, and
        // re-serialising after that would hash data the report never contained.
        String canonical = unescape(html.substring(i + OPEN.length(), j));
        Verdict verdict = verify(html.substring(intgAt, i), canonical);
        return new Parsed(verdict.linkable ? GSON.fromJson(canonical, SessionData.class) : null, verdict);
    }

    /**
     * Recomputes the report's own hash and signature over its canonical data.
     *
     * <p>Searched between the integrity block and the canonical block, so neither a
     * session title rendered further up the page nor a hash-shaped string inside the
     * canonical data itself can answer for the real thing.</p>
     *
     * <p>A report edited after the fact keeps the hash of the data it used to hold,
     * so recomputing is the entire check. It catches a text editor, which is what
     * the integrity block was always advertised as catching. It does not catch
     * anyone who reads the signing key out of the jar and recomputes both values
     * themselves — see {@link IntegrityUtil}.</p>
     */
    private static Verdict verify(String block, String canonical) {
        Matcher sha = SHA_LINE.matcher(block);
        Matcher sig = SIG_LINE.matcher(block);
        if (!sha.find() || !sig.find()) {
            return Verdict.MALFORMED;
        }
        if (hashesMatch(canonical, sha.group(1), sig.group(1))) {
            return Verdict.OK;
        }
        String trimmed = canonical.strip();
        return !trimmed.equals(canonical) && hashesMatch(trimmed, sha.group(1), sig.group(1))
                ? Verdict.REFORMATTED : Verdict.TAMPERED;
    }

    private static boolean hashesMatch(String canonical, String sha, String sig) {
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        return sameHex(sha, IntegrityUtil.sha256Hex(bytes))
                && sameHex(sig, IntegrityUtil.hmacSha256Hex(bytes));
    }

    /** Hex compare, case-insensitively. */
    private static boolean sameHex(String found, String computed) {
        return MessageDigest.isEqual(
                found.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                computed.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Exact inverse of {@code ReportBuilder.esc}.
     *
     * <p>Order matters and is the reverse of escaping: {@code esc} replaces
     * {@code &} first, so {@code &amp;} has to be undone <em>last</em>. Undoing it
     * first turns the escaped form of the literal text {@code &lt;} back into a
     * {@code <}, which changes the bytes and fails the hash on a report that nobody
     * touched. {@code esc} never emits {@code &#39;}, so nothing here undoes it.</p>
     */
    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&amp;", "&");
    }
}
