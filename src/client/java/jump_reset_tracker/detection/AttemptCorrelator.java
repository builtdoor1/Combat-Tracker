package jump_reset_tracker.detection;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.config.TimingWindow;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Correlates player hits (Event A) with jumps (Event B). When a hit and a jump
 * occur within the outer window of each other, an attempt is registered with a
 * signed delta (jumpTime - hitTime) and classified against the timing window.
 *
 * <p>Both callbacks run on the client main thread, so no synchronization is
 * needed. A chat message is sent ONLY when an attempt is registered — never for
 * ordinary jumps unrelated to combat.</p>
 */
public class AttemptCorrelator {
    private static final AttemptCorrelator INSTANCE = new AttemptCorrelator();

    public static AttemptCorrelator get() {
        return INSTANCE;
    }

    private long lastHitTime = 0;
    private boolean hitPending = false;
    private long lastJumpTime = 0;
    private boolean jumpPending = false;

    /** Called when the local player takes damage from another player. */
    public void onPlayerHit(long timeMs) {
        TimingWindow window = JrtConfig.get().window;
        if (jumpPending) {
            long diff = timeMs - lastJumpTime; // >= 0 when the jump preceded this hit
            if (diff >= 0 && diff <= window.outerWindowMs) {
                // Jumped before being hit -> signed delta is negative ("too early").
                register(lastJumpTime - timeMs);
                jumpPending = false;
                hitPending = false;
                return;
            }
        }
        lastHitTime = timeMs;
        hitPending = true;
    }

    /** Called when the local player performs a jump. */
    public void onJump(long timeMs) {
        TimingWindow window = JrtConfig.get().window;
        if (hitPending) {
            long diff = timeMs - lastHitTime; // >= 0 when the jump followed the hit
            if (diff >= 0 && diff <= window.outerWindowMs) {
                register(timeMs - lastHitTime);
                hitPending = false;
                jumpPending = false;
                return;
            }
        }
        lastJumpTime = timeMs;
        jumpPending = true;
    }

    private void register(long signedDelta) {
        TimingWindow window = JrtConfig.get().window;
        TimingWindow.Result result = window.classify(signedDelta);
        boolean success = result == TimingWindow.Result.SUCCESS;

        StatsTracker stats = StatsTracker.get();
        stats.record(signedDelta, success);

        String hudText;
        int color;
        String chatText;
        switch (result) {
            case SUCCESS -> {
                hudText = "HIT +" + signedDelta + "ms";
                color = 0xFF55FF55;
                chatText = "Jump reset HIT! (+" + signedDelta + "ms)";
            }
            case TOO_LATE -> {
                hudText = "MISS too late (+" + signedDelta + "ms)";
                color = 0xFFFF5555;
                chatText = "Jump reset MISS - too late (+" + signedDelta + "ms)";
            }
            default -> { // TOO_EARLY
                hudText = "MISS too early (" + signedDelta + "ms)";
                color = 0xFFFF5555;
                chatText = "Jump reset MISS - too early (" + signedDelta + "ms)";
            }
        }
        stats.setLastResult(hudText, color);
        stats.save();

        if (JrtConfig.get().chatEnabled) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                ChatFormatting fmt = success ? ChatFormatting.GREEN : ChatFormatting.RED;
                player.displayClientMessage(Component.literal("[JRT] " + chatText).withStyle(fmt), false);
            }
        }
    }
}
