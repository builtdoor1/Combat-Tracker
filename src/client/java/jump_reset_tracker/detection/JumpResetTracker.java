package jump_reset_tracker.detection;

import jump_reset_tracker.JumpResetTrackerClient;
import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.config.TimingWindow;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Core jump-reset detection, reworked from sombreror/JumpReset-mod.
 *
 * <p>Run once per client tick. Each tick:
 * <ol>
 *   <li><b>Jump</b> is detected by the vertical-velocity impulse (delta-vy) using
 *       the pre-move sample captured by {@link jump_reset_tracker.mixin.LocalPlayerMixin}
 *       — not by an {@code onGround} flip, so it fires even on the same tick a hit lands.</li>
 *   <li><b>Hit</b> is detected fully client-side: {@code hurtTime} goes 0→+ while the
 *       invulnerability (regen) timer is active <em>and</em> horizontal knockback exceeds a
 *       threshold — which filters fall / fire / poison damage (no horizontal push).</li>
 *   <li>Hit and jump are paired through a short tick window. Timing uses
 *       {@link System#nanoTime()} with one-way ping compensation.</li>
 * </ol>
 *
 * <p>Only attempts where the player actually jumped are recorded. A hit with no
 * following jump (an expired window) is not counted.</p>
 */
public class JumpResetTracker {
    /** A jump within this many ticks BEFORE a hit is classified "too early". */
    private static final int JUMP_LOOKBACK_TICKS = 2;
    /** Per-result cooldown so one exchange can't spam multiple results. */
    private static final int RESULT_COOLDOWN_TICKS = 4;
    /** Minimum pre-move vy for a delta to count as a jump (filters tiny bumps). */
    private static final double JUMP_MIN_VY = 0.10;

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

    private double lastKnownPing = 1.0;

    // Per-tick carry-overs
    private int prevHurtTime = 0;
    private boolean prevOnGround = true;
    private double prevVelocityY = 0.0;

    public void tick(Minecraft client) {
        currentTick++;

        LocalPlayer player = client.player;
        if (player == null) {
            reset();
            return;
        }

        JrtConfig cfg = JrtConfig.get();
        long tickNano = System.nanoTime();

        // ── Sample this tick's state ──────────────────────────────────────────
        double preMoveVy = JumpResetTrackerClient.preMoveVelocityY;
        double vx = player.getDeltaMovement().x;
        double vz = player.getDeltaMovement().z;
        double horizMag = Math.sqrt(vx * vx + vz * vz);
        boolean onGround = player.onGround();
        int hurtTime = player.hurtTime;
        int invulnTime = player.invulnerableTime;
        double ping = measurePing(client);

        // ── Jump detection (delta-vy impulse) ─────────────────────────────────
        boolean jumpNow = (preMoveVy - prevVelocityY) >= cfg.jumpDeltaThreshold
                && preMoveVy >= JUMP_MIN_VY
                && !onGround;
        if (jumpNow) {
            lastJumpTick = currentTick;
            lastJumpNano = tickNano;
        }

        // ── Hit detection (hurtTime + regen + horizontal knockback) ───────────
        boolean hitNow = prevHurtTime == 0
                && hurtTime > 0
                && invulnTime > 0
                && horizMag > cfg.knockbackThreshold;
        if (hitNow) {
            handleHit(cfg, tickNano, ping);
        }

        // ── Close an expired window (player never jumped → not an attempt) ────
        if (state == State.WINDOW_ACTIVE) {
            int maxTicks = hitWasGrounded ? cfg.windowTicksGround : cfg.windowTicksAir;
            if (currentTick - hitTick > maxTicks) {
                state = State.IDLE;
            }
        }

        // ── Score a jump that lands inside an open window ─────────────────────
        if (jumpNow && state == State.WINDOW_ACTIVE) {
            double ms = (lastJumpNano - hitNano) / 1_000_000.0;
            registerAttempt(ms, ping);
            state = State.IDLE;
        }

        prevHurtTime = hurtTime;
        prevOnGround = onGround;
        prevVelocityY = preMoveVy;
    }

    private void handleHit(JrtConfig cfg, long tickNano, double ping) {
        double oneWayMs = ping * cfg.pingCompFactor;
        long compHitNano = tickNano - (long) (oneWayMs * 1_000_000.0);

        int ticksSinceJump = currentTick - lastJumpTick;

        // PRE-HIT JUMP: jumped 1–2 ticks before the hit registered → too early.
        if (ticksSinceJump > 0 && ticksSinceJump <= JUMP_LOOKBACK_TICKS && readyForResult()) {
            registerAttempt((lastJumpNano - compHitNano) / 1_000_000.0, ping);
            state = State.IDLE;
            return;
        }

        // SAME-TICK: jump and hit on the same tick → simultaneous (0 ms).
        if (currentTick == lastJumpTick && readyForResult()) {
            registerAttempt(0.0, ping);
            state = State.IDLE;
            return;
        }

        // NORMAL: open (or restart) a timing window for an upcoming jump.
        if (state == State.WINDOW_ACTIVE) {
            hitTick = currentTick;
            hitNano = compHitNano;
            hitWasGrounded = prevOnGround;
        } else if (state == State.IDLE && readyForResult()) {
            hitTick = currentTick;
            hitNano = compHitNano;
            hitWasGrounded = prevOnGround;
            state = State.WINDOW_ACTIVE;
        }
    }

    private boolean readyForResult() {
        return currentTick - lastResultTick >= RESULT_COOLDOWN_TICKS;
    }

    private void registerAttempt(double ms, double ping) {
        lastResultTick = currentTick;
        long delta = Math.round(ms);

        TimingWindow window = JrtConfig.get().window;
        TimingWindow.Result result = window.classify(delta);
        boolean success = result == TimingWindow.Result.SUCCESS;

        StatsTracker stats = StatsTracker.get();
        stats.record(delta, success);

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

        if (JrtConfig.get().chatEnabled) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                ChatFormatting fmt = success ? ChatFormatting.GREEN : ChatFormatting.RED;
                p.displayClientMessage(Component.literal("[Combat Tracker] " + chatText).withStyle(fmt), false);
            }
        }
    }

    private double measurePing(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return lastKnownPing;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info == null) {
            return lastKnownPing;
        }
        lastKnownPing = Math.max(1.0, info.getLatency());
        return lastKnownPing;
    }

    private void reset() {
        state = State.IDLE;
        prevHurtTime = 0;
        prevOnGround = true;
        prevVelocityY = 0.0;
        lastJumpTick = Integer.MIN_VALUE / 2;
        lastJumpNano = 0L;
    }
}
