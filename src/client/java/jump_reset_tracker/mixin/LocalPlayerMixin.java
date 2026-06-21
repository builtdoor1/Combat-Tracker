package jump_reset_tracker.mixin;

import jump_reset_tracker.JumpResetTrackerClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the local player's vertical velocity at the {@code HEAD} of
 * {@link net.minecraft.world.entity.Entity#move} — i.e. <em>before</em> the
 * physics engine resolves the movement for the tick. This is the raw impulse
 * value: a vanilla ground jump sets {@code vy ≈ 0.42} (× jump boost) and that
 * value is present here before gravity/drag are applied.
 *
 * <p>The actual jump detection lives in
 * {@link jump_reset_tracker.detection.JumpResetTracker}, which compares this
 * captured value against the previous tick's value (delta-vy) so it fires
 * reliably even on the same tick a hit lands.
 *
 * <p>Deliberately minimal: no {@code @Shadow} fields, only public API
 * ({@code getDeltaMovement()}), and {@code move()} runs every physics tick so
 * the sample is guaranteed.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void jumpResetTracker$capturePreMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        JumpResetTrackerClient.preMoveVelocityY = self.getDeltaMovement().y;
    }
}
