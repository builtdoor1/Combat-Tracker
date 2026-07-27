package combat_tracker.mixin;

import combat_tracker.detection.ClickTimestamps;
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
        if (!self.isMouseGrabbed() || Minecraft.getInstance().screen != null) {
            return;
        }
        ClickTimestamps.record(System.nanoTime());
    }
}
