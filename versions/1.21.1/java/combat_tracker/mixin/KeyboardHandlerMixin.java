package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the window in which a real keystroke is being delivered.
 *
 * <p>Variant for 1.21.5 - 1.21.8, where the callback still takes the raw GLFW
 * ints. The KeyEvent record that replaced them arrived in 1.21.9.</p>
 *
 * <p>{@code keyPress} is the GLFW keyboard callback — one of only two places a
 * physical input enters the game, the other being
 * {@code MouseHandler.onPress}. {@code KeyMapping.set} and
 * {@code KeyMapping.click} are called from here and nowhere else in vanilla, so a
 * keybind whose state moves outside this window did not move because someone
 * pressed a key.</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void combatTracker$keyPressStart(long window, int key, int scancode, int action,
                                             int modifiers, CallbackInfo ci) {
        InputContext.enterPhysicalInput();
    }

    @Inject(method = "keyPress", at = @At("RETURN"))
    private void combatTracker$keyPressEnd(long window, int key, int scancode, int action,
                                           int modifiers, CallbackInfo ci) {
        InputContext.exitPhysicalInput();
    }
}
