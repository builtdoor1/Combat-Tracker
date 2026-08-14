package combat_tracker.mixin;

import combat_tracker.detection.ClickTimestamps;
import combat_tracker.detection.InputContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timestamps left mouse presses for combo timing.
 *
 * <p>This is the earliest point the game exposes a click. The GLFW callback hands
 * the work to {@code Minecraft.execute}, whose queue drains once per frame, so a
 * timestamp taken here is frame-accurate rather than tick-accurate. Timing combos
 * off the tick loop instead rounds every interval to a 50ms boundary and erases
 * the human variation the statistic is meant to show. See {@link ClickTimestamps}.</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /**
     * Opens the scroll window. The wheel changes the held slot inside
     * {@code onScroll} itself rather than in {@code handleKeybinds}, so without this
     * every scroll would read as an unattributed switch.
     */
    @Inject(method = "onScroll", at = @At("HEAD"))
    private void combatTracker$scrollStart(long window, double xOffset, double yOffset, CallbackInfo ci) {
        InputContext.enterScroll();
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void combatTracker$scrollEnd(long window, double xOffset, double yOffset, CallbackInfo ci) {
        InputContext.exitScroll();
    }

    /**
     * {@code grabMouse} restores every keybind that was released when a screen
     * opened ({@code KeyMapping.setAll}). Legitimate, and nothing to do with a key
     * being pressed, so it needs a window of its own.
     */
    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void combatTracker$grabStart(CallbackInfo ci) {
        InputContext.enterHousekeeping();
    }

    @Inject(method = "grabMouse", at = @At("RETURN"))
    private void combatTracker$grabEnd(CallbackInfo ci) {
        InputContext.exitHousekeeping();
    }

    /** The GLFW mouse callback: the other of the two real physical input paths. */
    @Inject(method = "onButton", at = @At("HEAD"))
    private void combatTracker$physicalStart(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        InputContext.enterPhysicalInput();
    }

    @Inject(method = "onButton", at = @At("RETURN"))
    private void combatTracker$physicalEnd(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        InputContext.exitPhysicalInput();
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void combatTracker$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (info.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return;
        }
        // Only presses that can become an attack. A click in the inventory, chat or
        // any other screen never reaches startAttack, so recording it would leave an
        // unconsumed entry and shift every interval measured afterwards. A grabbed
        // mouse is exactly the "in the world, not in a menu" condition.
        MouseHandler self = (MouseHandler) (Object) this;
        if (!self.isMouseGrabbed() || Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        ClickTimestamps.record(System.nanoTime());
    }
}
