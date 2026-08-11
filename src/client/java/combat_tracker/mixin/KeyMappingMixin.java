package combat_tracker.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import combat_tracker.detection.InputContext;
import combat_tracker.detection.IntegrityMonitor;
import combat_tracker.detection.WatchedKeys;
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
 * <p><b>Only the binds that matter.</b> See {@link WatchedKeys}. Watching every
 * keybind was the original design and it does not survive contact with a real
 * client: sprint, sneak and the movement keys are held by software constantly and
 * legitimately, and one innocent session produced 116 flags. The bypass this check
 * closes only runs through the binds vanilla consults to drive something this mod
 * measures, so those are the only ones it now watches.</p>
 *
 * <p><b>Limits.</b> A cheat that writes the private {@code isDown} or
 * {@code clickCount} fields directly, rather than calling these methods, is not
 * visible here — the same tier of gap as writing {@code Inventory.selected}
 * directly. Legitimate mods that programmatically press the watched binds will
 * also trip this; as everywhere else in this mod, a flag reports what happened,
 * not why.</p>
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    /**
     * Presses only. A release cannot induce anything.
     *
     * <p>{@code handleKeybinds} reaches an action through {@code consumeClick()} in
     * eighteen places and {@code isDown()} in five, and every one of them needs the
     * bind to be down or clicked. Setting a bind to false can stop an action; it can
     * never start one, so a synthetic release is not the bypass this check exists to
     * catch.</p>
     *
     * <p>This is also what was drowning the check. {@code KeyMapping.releaseAll()}
     * walks <em>every</em> registered mapping and releases it, so one sweep counted
     * once per bind that exists in the client — 59 in vanilla, 116 for the player who
     * reported this, whose client registers roughly another 57. Three sweeps produced
     * the 116 / 232 / 348 running totals in their alerts, all of them from keys going
     * up.</p>
     */
    @Inject(method = "setDown(Z)V", at = @At("HEAD"))
    private void combatTracker$onSetDown(boolean down, CallbackInfo ci) {
        if (down && !InputContext.trustedInput()
                && WatchedKeys.watched((KeyMapping) (Object) this)) {
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
        if (!InputContext.trustedInput() && WatchedKeys.boundToWatched(key)) {
            IntegrityMonitor.get().onSyntheticKeybind();
        }
    }
}
