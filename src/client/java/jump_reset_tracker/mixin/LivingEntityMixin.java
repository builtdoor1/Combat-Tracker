package jump_reset_tracker.mixin;

import jump_reset_tracker.JumpResetTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects a genuine jump by hooking {@link LivingEntity#jumpFromGround()} — the
 * exact method the game calls when the player jumps. This is far more reliable
 * than inferring a jump from upward velocity, which cannot be distinguished from
 * the upward component of knockback (a crit while standing still would otherwise
 * look like a perfect 0 ms jump reset).
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "jumpFromGround()V", at = @At("HEAD"))
    private void jumpResetTracker$onJump(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            JumpResetTrackerClient.jumpNano = System.nanoTime();
        }
    }
}
