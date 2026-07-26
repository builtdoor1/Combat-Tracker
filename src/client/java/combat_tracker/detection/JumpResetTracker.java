package combat_tracker.detection;

import combat_tracker.CombatTrackerClient;
import combat_tracker.config.CtConfig;
import combat_tracker.config.TimingWindow;
import combat_tracker.record.SessionRecorder;
import combat_tracker.stats.StatsTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Core jump-reset detection.
 *
 * <p>Run once per client tick. Each tick:
 * <ol>
 *   <li><b>Jump</b> is detected from the real {@code jumpFromGround()} call (via
 *       {@link combat_tracker.mixin.LivingEntityMixin}) — NOT from upward
 *       velocity, so knockback (e.g. a crit while standing still) can never be
 *       mistaken for a jump.</li>
 *   <li><b>Hit</b> is detected client-side: {@code hurtTime} goes 0→+ while the
 *       invulnerability (regen) timer is active <em>and</em> horizontal knockback
 *       exceeds a threshold (filters fall / fire / poison).</li>
 *   <li>Hit and jump are paired through a short tick window, timed with
 *       {@link System#nanoTime()}. Both are observed on this client's own clock
 *       and latency delays both equally, so no ping compensation is applied —
 *       see {@link #handleHit}.</li>
 * </ol>
 *
 * <p>Only attempts where the player actually jumped are recorded.</p>
 */
public class JumpResetTracker {
    /** A jump within this many ticks BEFORE a hit is classified "too early". */
    private static final int JUMP_LOOKBACK_TICKS = 2;
    /** Per-result cooldown so one exchange can't spam multiple results. */
    private static final int RESULT_COOLDOWN_TICKS = 4;

    /**
     * Minimum horizontal speed just after damage for it to count as a real combat
     * hit. Fall, fire and poison impart no horizontal knockback and land far below
     * this; a melee hit lands far above it, so the gap is wide and there is no
     * better value to pick. Hardcoded — like {@link ComboTracker}'s combo gap — so
     * a stale or hand-edited config.json can't skew what counts as a hit.
     */
    private static final double KNOCKBACK_THRESHOLD = 0.065;

    /**
     * Ticks after a grounded hit during which a jump still counts as an attempt.
     * Six ticks (~300ms) is comfortably past any human reaction time while staying
     * short enough that an unrelated later jump isn't swept in.
     */
    private static final int WINDOW_TICKS_GROUND = 6;

    /**
     * Airborne hits get a longer window: you cannot jump until you land, so the
     * clock has to keep running through the fall.
     */
    private static final int WINDOW_TICKS_AIR = 10;

    private enum State { IDLE, WINDOW_ACTIVE }

    private State state = State.IDLE;

    private int currentTick = 0;
    private int lastResultTick = Integer.MIN_VALUE / 2;
    private int lastJumpTick = Integer.MIN_VALUE / 2;
    private long lastJumpNano = 0L;

    // Open hit-window snapshot
    private int hitTick = 0;
    private long hitNano = 0L;
    private boolean hitWasGrounded = true;

    private final LatencyEstimator latency = LatencyEstimator.get();

    // Per-tick carry-overs
    private int prevHurtTime = 0;
    private boolean prevOnGround = true;

    public void tick(Minecraft client) {
        currentTick++;

        LocalPlayer player = client.player;
        if (player == null) {
            reset();
            return;
        }

        long tickNano = System.nanoTime();

        // ── Sample this tick's state ──────────────────────────────────────────
        double vx = player.getDeltaMovement().x;
        double vz = player.getDeltaMovement().z;
        double horizMag = Math.sqrt(vx * vx + vz * vz);
        boolean onGround = player.onGround();
        int hurtTime = player.hurtTime;
        int invulnTime = player.invulnerableTime;
        latency.sample(client);

        // ── Jump detection (real jumpFromGround call) ─────────────────────────
        long jumpSignalNano = CombatTrackerClient.consumeJumpNano();
        boolean jumpNow = jumpSignalNano != 0L;
        if (jumpNow) {
            lastJumpTick = currentTick;
            lastJumpNano = jumpSignalNano;
        }

        // ── Hit detection (hurtTime + regen + horizontal knockback) ───────────
        boolean hitNow = prevHurtTime == 0
                && hurtTime > 0
                && invulnTime > 0
                && horizMag > KNOCKBACK_THRESHOLD;
        if (hitNow) {
            handleHit(tickNano);
        }

        // ── Close an expired window (player never jumped → not an attempt) ────
        if (state == State.WINDOW_ACTIVE) {
            int maxTicks = hitWasGrounded ? WINDOW_TICKS_GROUND : WINDOW_TICKS_AIR;
            if (currentTick - hitTick > maxTicks) {
                state = State.IDLE;
            }
        }

        // ── Score a jump that lands inside an open window ─────────────────────
        if (jumpNow && state == State.WINDOW_ACTIVE && readyForResult()) {
            double ms = (lastJumpNano - hitNano) / 1_000_000.0;
            registerAttempt(ms);
            state = State.IDLE;
        }

        prevHurtTime = hurtTime;
        prevOnGround = onGround;
    }

    private void handleHit(long tickNano) {
        // Use the precise hit time from the mixin; fall back to the tick time.
        //
        // NO latency compensation, deliberately. An earlier version wound this
        // timestamp back by the estimated one-way latency, which is wrong: the jump
        // is timed on the client clock, so subtracting latency from only the hit
        // mixes a server-frame time with a client-frame one and adds a flat bias of
        // half the ping to every result. At 120ms that is +60ms — most of the
        // success window — so real resets scored as TOO_LATE.
        //
        // Both events are observed on this client's own tick clock, and latency
        // delays both equally, so it cancels out on its own. Knockback is applied
        // when the client receives it, which is the same moment hurtTime flips, so
        // the physics being measured are local too.
        long hitAtNano = CombatTrackerClient.hitNano != 0L ? CombatTrackerClient.hitNano : tickNano;

        int ticksSinceJump = currentTick - lastJumpTick;

        // A real jump on this tick (same-tick reset) or 1–2 ticks before the hit:
        // pair them and use the actual signed sub-tick delta (jump − hit).
        if (ticksSinceJump >= 0 && ticksSinceJump <= JUMP_LOOKBACK_TICKS && readyForResult()) {
            registerAttempt((lastJumpNano - hitAtNano) / 1_000_000.0);
            state = State.IDLE;
            return;
        }

        // NORMAL: open (or restart) a timing window for an upcoming jump.
        if (state == State.WINDOW_ACTIVE) {
            hitTick = currentTick;
            hitNano = hitAtNano;
            hitWasGrounded = prevOnGround;
        } else if (state == State.IDLE && readyForResult()) {
            hitTick = currentTick;
            hitNano = hitAtNano;
            hitWasGrounded = prevOnGround;
            state = State.WINDOW_ACTIVE;
        }
    }

    private boolean readyForResult() {
        return currentTick - lastResultTick >= RESULT_COOLDOWN_TICKS;
    }

    private void registerAttempt(double ms) {
        lastResultTick = currentTick;
        long delta = Math.round(ms);

        TimingWindow window = CtConfig.get().window;
        TimingWindow.Result result = window.classify(delta);
        boolean success = result == TimingWindow.Result.SUCCESS;

        StatsTracker stats = StatsTracker.get();
        stats.record(delta, success);
        SessionRecorder.get().recordJump(delta, result.name());

        String hudText;
        int color;
        String chatText;
        switch (result) {
            case SUCCESS -> {
                hudText = "HIT +" + delta + "ms";
                color = 0xFF55FF55;
                chatText = "Jump reset HIT! (+" + delta + "ms)";
            }
            case TOO_LATE -> {
                hudText = "MISS too late (+" + delta + "ms)";
                color = 0xFFFF5555;
                chatText = "Jump reset MISS - too late (+" + delta + "ms)";
            }
            default -> { // TOO_EARLY
                hudText = "MISS too early (" + delta + "ms)";
                color = 0xFFFF5555;
                chatText = "Jump reset MISS - too early (" + delta + "ms)";
            }
        }
        stats.setLastResult(hudText, color);
        stats.save();

        if (CtConfig.get().chatEnabled) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                ChatFormatting fmt = success ? ChatFormatting.GREEN : ChatFormatting.RED;
                p.displayClientMessage(Component.literal("[Combat Tracker] " + chatText).withStyle(fmt), false);
            }
        }
    }

    private void reset() {
        state = State.IDLE;
        prevHurtTime = 0;
        prevOnGround = true;
        lastJumpTick = Integer.MIN_VALUE / 2;
        lastJumpNano = 0L;
        latency.reset();
    }
}
