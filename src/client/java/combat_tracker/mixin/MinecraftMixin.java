package combat_tracker.mixin;

import combat_tracker.CombatTrackerClient;
import combat_tracker.detection.ClickTimestamps;
import combat_tracker.detection.SwingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swing detection. {@code startAttack()} runs on every left-click attack —
 * including the ones that hit nothing, which {@code MultiPlayerGameMode.attack}
 * never sees. That is what lets reach and aim be measured for misses as well as
 * hits.
 *
 * <p>Injecting at HEAD means we also see clicks vanilla is about to throw away, so
 * the same guards it applies are repeated here. Without them, a click during the
 * post-whiff cooldown or while eating would enter the data as a phantom swing.
 * All three are public members, so none of this needs an accessor mixin.</p>
 *
 * <p>Combos are unaffected: {@link MultiPlayerGameModeMixin} still handles those,
 * and simply runs a moment later on the hits that land.</p>
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void combatTracker$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;

        // Take the click behind this swing first, before any guard can return.
        // Vanilla calls startAttack once per queued press, so consuming here
        // unconditionally keeps the click queue paired with attacks; bailing out
        // early would leave a press stranded and shift every later interval.
        CombatTrackerClient.clickNano = ClickTimestamps.consume();

        LocalPlayer player = client.player;
        if (player == null || client.hitResult == null) {
            return;
        }
        if (client.missTime > 0 || player.isHandsBusy()) {
            return;
        }
        SwingTracker.get().onSwing(client, client.hitResult);
    }
}
