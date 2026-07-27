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
 * Jump-reset detection, following
 * <a href="https://github.com/sootysplash/jump-reset">sootysplash's jump-reset</a>.
 *
 * <p>Two tick stamps, both read from {@code player.tickCount}: the tick you jumped
 * and the tick you were hit. A reset is <b>perfect when the jump lands on the tick
 * right after the hit</b>, and everything else is a whole number of ticks early or
 * late. That is the entire model.</p>
 *
 * <p>The earlier version measured a nanosecond delta and scored it against a
 * millisecond window. That was measuring something the game does not have: the
 * knockback is applied on a tick and the jump impulse is applied on a tick, so the
 * millisecond figure was a tick count in disguise and its values piled up on 50ms
 * boundaries.</p>
 *
 * <p>No ping compensation, deliberately. Both stamps come from this client's own
 * tick counter, so latency shifts both equally and cancels. See the note in
 * {@link LatencyEstimator}.</p>
 *
 * <p><b>Kept from before:</b> the knockback filter on what counts as a hit.
 * sootysplash times off any {@code handleDamageEvent}, which includes fall, fire
 * and poison damage. For a report meant to describe PvP, a jump after fall damage
 * is not a jump reset, so a hit still has to carry horizontal knockback.</p>
 */
public class JumpResetTracker {
    /**
     * Minimum horizontal speed just after damage for it to count as a real combat
     * hit. Fall, fire and poison impart no horizontal knockback and land far below
     * this; a melee hit lands far above it. Hardcoded so a stale or hand-edited
     * config.json can't skew what counts as a hit.
     */
    private static final double KNOCKBACK_THRESHOLD = 0.065;

    /**
     * A jump older than this stops being considered against any hit, matching
     * sootysplash's 2500ms staleness on the last jump.
     */
    private static final long JUMP_STALE_MS = 2500L;

    /** Sentinel for "no tick recorded yet". */
    private static final int NONE = Integer.MIN_VALUE;

    private int jumpTick = NONE;
    private long jumpAtMs = 0L;
    private int hurtTick = NONE;

    /** The hit already scored, so one hit yields at most one recorded attempt. */
    private int scoredHurtTick = NONE;

    private final LatencyEstimator latency = LatencyEstimator.get();

    // Per-tick carry-overs
    private int prevHurtTime = 0;

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            reset();
            return;
        }

        latency.sample(client);

        double vx = player.getDeltaMovement().x;
        double vz = player.getDeltaMovement().z;
        double horizMag = Math.sqrt(vx * vx + vz * vz);
        int hurtTime = player.hurtTime;
        int invulnTime = player.invulnerableTime;

        // ── Jump: recorded by the mixin on the real jumpFromGround call ───────
        int jumpedAt = CombatTrackerClient.consumeJumpTick();
        if (jumpedAt != NONE) {
            jumpTick = jumpedAt;
            jumpAtMs = System.currentTimeMillis();
        }

        // ── Hit: hurtTime rising while invulnerable, with horizontal knockback ─
        boolean hitNow = prevHurtTime == 0 && hurtTime > 0 && invulnTime > 0
                && horizMag > KNOCKBACK_THRESHOLD;
        if (hitNow) {
            hurtTick = player.tickCount;
        }
        prevHurtTime = hurtTime;

        evaluate();
    }

    /**
     * Scores the current jump/hit pair if there is one worth scoring.
     *
     * <p>Runs every tick rather than only on a jump so that both orderings are
     * caught: jumping after the hit (late) and jumping before it (early), which is
     * only knowable once the hit arrives.</p>
     */
    private void evaluate() {
        if (jumpTick == NONE || hurtTick == NONE || hurtTick == scoredHurtTick) {
            return;
        }
        if (System.currentTimeMillis() - jumpAtMs > JUMP_STALE_MS) {
            return; // the jump is too old to be about this hit
        }
        TimingWindow window = CtConfig.get().window;
        if (!window.isAttempt(jumpTick, hurtTick)) {
            return; // unrelated jump and hit, not an attempt at a reset
        }
        scoredHurtTick = hurtTick;
        registerAttempt(TimingWindow.offset(jumpTick, hurtTick));
    }

    private void registerAttempt(int offsetTicks) {
        TimingWindow window = CtConfig.get().window;
        TimingWindow.Result result = window.classify(offsetTicks);
        boolean success = result.success();

        StatsTracker stats = StatsTracker.get();
        stats.record(offsetTicks, success);
        SessionRecorder.get().recordJump(offsetTicks, result.name());

        String label = TimingWindow.describe(offsetTicks);
        int color = success ? 0xFF55FF55 : 0xFFFF5555;
        stats.setLastResult(label, color);
        stats.save();

        if (CtConfig.get().chatEnabled) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                ChatFormatting fmt = success ? ChatFormatting.GREEN : ChatFormatting.RED;
                String msg = success
                        ? "Jump reset " + label
                        : "Jump reset " + label + " (" + (offsetTicks < 0 ? "jumped early" : "jumped late") + ")";
                p.displayClientMessage(Component.literal("[Combat Tracker] " + msg).withStyle(fmt), false);
            }
        }
    }

    private void reset() {
        jumpTick = NONE;
        hurtTick = NONE;
        scoredHurtTick = NONE;
        jumpAtMs = 0L;
        prevHurtTime = 0;
        latency.reset();
    }
}
