package combat_tracker.detection;

/**
 * Headless check that the hotbar provenance logic still reports what it should.
 *
 * <p>This part of the mod has been wrong three times now — a cheat's switch masked
 * by a legitimate scroll in the same tick, a switch reported on every respawn, and
 * a report that it had stopped detecting software switches at all. Each time the
 * answer came from reading the code and arguing about it. This runs it instead.</p>
 *
 * <p>Drives {@link IntegrityMonitor} and {@link SlotLedger} directly. Neither the
 * flag path nor the ledger touches a running game — {@code noteSetterCall} only
 * reaches the counters and {@code WebhookNotifier.onFlag}, which does nothing
 * without an endpoint — so the whole thing runs from Gradle in milliseconds.</p>
 *
 * <p>Run: {@code ./gradlew provenanceSelfTest}</p>
 */
public final class ProvenanceSelfTest {

    private static int checks;
    private static int failures;

    private ProvenanceSelfTest() {
    }

    public static void main(String[] args) {
        softwareSwitchIsReported();
        legitimateSourcesAreNotReported();
        reassertingTheSameSlotIsNotReported();
        firstSightSeedsRatherThanReports();
        fieldWriteIsCaughtByObserve();
        swapAndRevertInOneTickIsCaught();
        spentBudgetStillReportsSomethingNew();
        spentBudgetStaysQuietForRepeats();

        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    /** The case the whole check exists for: a slot moved with nothing accounting for it. */
    private static void softwareSwitchIsReported() {
        IntegrityMonitor m = fresh(0);
        m.noteSetterCall(4, InputContext.Source.NONE);
        expect("software switch is reported", 1, m.hotbarFlags());
    }

    /** A hand on the keyboard, the wheel, or the server moving your hand. */
    private static void legitimateSourcesAreNotReported() {
        for (InputContext.Source s : new InputContext.Source[]{
                InputContext.Source.KEYBIND, InputContext.Source.SCROLL, InputContext.Source.SERVER}) {
            IntegrityMonitor m = fresh(0);
            m.noteSetterCall(5, s);
            expect("switch from " + s + " is not reported", 0, m.hotbarFlags());
        }
    }

    /** Re-asserting the slot you are already on moves nothing. */
    private static void reassertingTheSameSlotIsNotReported() {
        IntegrityMonitor m = fresh(3);
        m.noteSetterCall(3, InputContext.Source.NONE);
        expect("re-asserting the current slot is not reported", 0, m.hotbarFlags());
    }

    /** Joining a world on a non-zero slot is a baseline, not a switch. */
    private static void firstSightSeedsRatherThanReports() {
        IntegrityMonitor m = IntegrityMonitor.get();
        m.reset();
        m.noteSetterCall(7, InputContext.Source.NONE);
        expect("first sight seeds instead of reporting", 0, m.hotbarFlags());
    }

    /** A cheat writing the private field never calls the setter; the sweep catches it. */
    private static void fieldWriteIsCaughtByObserve() {
        SlotLedger led = new SlotLedger();
        led.reseed(0);
        expect("field write is caught by the tick sweep", true, led.observe(6));
    }

    /**
     * Switch, act, switch back inside one tick. Nets to zero at the tick boundary,
     * which is why the ledger is kept in step with every setter call rather than
     * sampled once a tick.
     */
    private static void swapAndRevertInOneTickIsCaught() {
        IntegrityMonitor m = fresh(0);
        m.noteSetterCall(8, InputContext.Source.NONE);
        m.noteSetterCall(0, InputContext.Source.NONE);
        expect("swap and revert in one tick is reported twice", 2, m.hotbarFlags());
    }

    /**
     * The bug behind "it stopped detecting hotbar switches".
     *
     * <p>The alert budget was spent on one kind of flag, and everything after it was
     * counted and silently dropped — including the first time a different check ever
     * tripped. A kind nobody has been told about is information, and has to get out.</p>
     */
    private static void spentBudgetStillReportsSomethingNew() {
        int spent = AlertSchedule.MAX_MESSAGES;
        long since = 0L;
        long now = AlertSchedule.FIRST_DELAY_MS;
        expect("a spent budget still reports something new",
                true, AlertSchedule.due(spent, since, now, true));
    }

    /** The flood protection the budget exists for is still in force. */
    private static void spentBudgetStaysQuietForRepeats() {
        int spent = AlertSchedule.MAX_MESSAGES;
        expect("a spent budget stays quiet for more of the same",
                false, AlertSchedule.due(spent, 0L, Long.MAX_VALUE / 4, false));
        expect("even something new waits out the opening delay",
                false, AlertSchedule.due(spent, 0L, AlertSchedule.FIRST_DELAY_MS - 1, true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * A monitor whose ledger is seeded at {@code slot} and whose counters are zero.
     *
     * <p>The seeding call cannot raise a flag by construction: the ledger is unseeded
     * after a reset, so the first call adopts the slot and returns without judging
     * it. That leaves the counters at zero without needing to clear them again.</p>
     */
    private static IntegrityMonitor fresh(int slot) {
        IntegrityMonitor m = IntegrityMonitor.get();
        m.reset();
        m.noteSetterCall(slot, InputContext.Source.SERVER);
        return m;
    }

    private static void expect(String what, Object want, Object got) {
        checks++;
        boolean ok = want.equals(got);
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "  ok   " : "  FAIL ") + what
                + (ok ? "" : "  (wanted " + want + ", got " + got + ")"));
    }
}
