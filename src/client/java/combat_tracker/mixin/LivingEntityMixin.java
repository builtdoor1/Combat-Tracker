package combat_tracker.mixin;

import combat_tracker.CombatTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the tick the local player jumped on.
 *
 * <p>{@code jumpFromGround()} is the real jump, so knockback (a crit while you
 * stand still) can never be mistaken for one. The tick number is what matters:
 * jump-reset timing is measured in ticks against the tick of the hit, because the
 * knockback and the jump impulse are both applied on ticks. See
 * {@link combat_tracker.config.TimingWindow}.</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "jumpFromGround()V", at = @At("HEAD"))
    private void combatTracker$onJump(CallbackInfo ci) {
        LocalPlayer self = Minecraft.getInstance().player;
        if ((Object) this == self) {
            CombatTrackerClient.jumpTick = self.tickCount;
        }
    }
}
