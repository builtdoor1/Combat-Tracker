package combat_tracker.mixin;

import combat_tracker.CombatTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures precise client-side timestamps for the local player:
 * <ul>
 *   <li>{@code jumpFromGround()} — the exact moment of a real jump (not inferable
 *       from velocity, which knockback also changes).</li>
 *   <li>{@code handleDamageEvent(...)} — the exact moment the client registers a
 *       hit, so the hit time isn't quantized to the end of the tick.</li>
 * </ul>
 * Both feed nanosecond timestamps to {@link CombatTrackerClient}, letting the
 * tracker compute a real sub-tick delta instead of collapsing same-tick resets to 0 ms.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "jumpFromGround()V", at = @At("HEAD"))
    private void jumpResetTracker$onJump(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            CombatTrackerClient.jumpNano = System.nanoTime();
        }
    }

    @Inject(method = "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void jumpResetTracker$onDamageEvent(DamageSource source, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            CombatTrackerClient.hitNano = System.nanoTime();
        }
    }
}
