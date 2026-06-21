package jump_reset_tracker.detection;

import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-side jump detection. A jump is registered on the tick where the local
 * player leaves the ground (onGround true -> false) while moving upward AND the
 * jump key is held. Tying detection to the jump key distinguishes a deliberate
 * jump from being launched upward by knockback.
 *
 * <p>This is purely observational: it reads state, it never alters movement.</p>
 */
public class JumpDetector {
    private boolean wasOnGround = true;

    public boolean detectJump(LocalPlayer player, Options options) {
        boolean onGround = player.onGround();
        boolean jumped = false;
        if (wasOnGround && !onGround) {
            double dy = player.getDeltaMovement().y;
            if (dy > 0.0 && options.keyJump.isDown()) {
                jumped = true;
            }
        }
        wasOnGround = onGround;
        return jumped;
    }
}
