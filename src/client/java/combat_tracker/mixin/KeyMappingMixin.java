package combat_tracker.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import combat_tracker.detection.InputContext;
import combat_tracker.detection.IntegrityMonitor;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches keybinds pressed by code rather than by a key.
 *
 * <p>The other checks in this mod ask where an <em>action</em> came from. A cheat
 * can duck all of them by not calling the action at all: set the attack keybind
 * down, or bump its click count, and vanilla's own {@code handleKeybinds} performs
 * the attack on the next tick. The call then originates inside the trusted window
 * and is indistinguishable from a real click at that level. This one sits a layer
 * lower and watches the keybind state itself.</p>
 *
 * <p>Verified against 1.21.11: {@code KeyMapping.set(Key, boolean)} reaches every
 * mapping through a lambda that calls {@code setDown}, so real presses run through
 * here too — which is exactly why the physical-input window exists. Vanilla's
 * complete set of callers is {@code KeyboardHandler.keyPress} and
 * {@code MouseHandler.onButton} for real input, plus {@code Minecraft.setScreen}
 * (releaseAll), {@code MouseHandler.grabMouse} (setAll) and the toggle-key restore
 * paths for housekeeping. All of those open a window; nothing else does.</p>
 *
 * <p><b>Limits.</b> A cheat that writes the private {@code isDown} or
 * {@code clickCount} fields directly, rather than calling these methods, is not
 * visible here — the same tier of gap as writing {@code Inventory.selected}
 * directly. Legitimate mods that programmatically press keys will also trip this;
 * as everywhere else in this mod, a flag reports what happened, not why.</p>
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Inject(method = "setDown(Z)V", at = @At("HEAD"))
    private void combatTracker$onSetDown(boolean down, CallbackInfo ci) {
        if (!InputContext.trustedInput()) {
            IntegrityMonitor.get().onSyntheticKeybind();
        }
    }

    /**
     * The click-count path. {@code click} does not route through {@code setDown} —
     * it increments the counter that {@code consumeClick} drains — so a cheat using
     * it would otherwise leave no trace here.
     */
    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V", at = @At("HEAD"))
    private static void combatTracker$onClick(InputConstants.Key key, CallbackInfo ci) {
        if (!InputContext.trustedInput()) {
            IntegrityMonitor.get().onSyntheticKeybind();
        }
    }
}
