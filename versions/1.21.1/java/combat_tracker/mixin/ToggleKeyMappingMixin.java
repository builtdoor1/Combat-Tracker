package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The toggle-key restore paths, which call {@code setDown} without a key press.
 *
 * <p>Variant for 1.21.8 and earlier, which has only {@code ToggleKeyMapping.reset}.
 * The companion {@code KeyMapping.restoreToggleStatesOnScreenClosed} arrived in
 * 1.21.9, so the nested mixin that brackets it is absent here and the entry is
 * dropped from the mixin config alongside it.</p>

 * <p>Sneak and sprint in toggle mode are reset without anyone touching the
 * keyboard, which would otherwise read as keybinds moving on their own.</p>
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
}
