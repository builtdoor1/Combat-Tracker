package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The toggle-key restore paths, which call {@code setDown} without a key press.
 *
 * <p>Sprint and sneak in toggle mode are restored from saved state when a screen
 * closes, via {@code KeyMapping.restoreToggleStatesOnScreenClosed} and
 * {@code ToggleKeyMapping.reset}. Both are ordinary vanilla behaviour and both would
 * otherwise be reported as keybinds moving on their own.</p>
 */
@Mixin(ToggleKeyMapping.class)
public class ToggleKeyMappingMixin {

    @Inject(method = "reset", at = @At("HEAD"))
    private void combatTracker$resetStart(CallbackInfo ci) {
        InputContext.enterHousekeeping();
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void combatTracker$resetEnd(CallbackInfo ci) {
        InputContext.exitHousekeeping();
    }

    /**
     * Static, on {@link KeyMapping} rather than this class, but grouped here because
     * it exists for the same reason: restoring toggles when a screen closes.
     */
    @Mixin(KeyMapping.class)
    public static class Restore {

        @Inject(method = "restoreToggleStatesOnScreenClosed", at = @At("HEAD"))
        private static void combatTracker$restoreStart(CallbackInfo ci) {
            InputContext.enterHousekeeping();
        }

        @Inject(method = "restoreToggleStatesOnScreenClosed", at = @At("RETURN"))
        private static void combatTracker$restoreEnd(CallbackInfo ci) {
            InputContext.exitHousekeeping();
        }
    }
}
