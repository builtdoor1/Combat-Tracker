package combat_tracker.mixin;

import combat_tracker.detection.InputContext;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The housekeeping window around opening and closing a screen.
 *
 * <p>Same job as the {@code setScreen} bracket in {@link MinecraftMixin} on 1.21.x.
 * In 26.x screen management moved off {@code Minecraft} onto {@link Gui}, and
 * {@code Gui.setScreen} is what now calls {@code KeyMapping.releaseAll} and
 * {@code restoreToggleStatesOnScreenClosed} — verified against the 26.2 client.
 * Without this, opening a chest would read as every bound key being released by
 * software, which is one flag per keybind the client has.</p>
 */
@Mixin(Gui.class)
public class GuiScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void combatTracker$setScreenStart(Screen screen, CallbackInfo ci) {
        InputContext.enterHousekeeping();
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void combatTracker$setScreenEnd(Screen screen, CallbackInfo ci) {
        InputContext.exitHousekeeping();
    }
}
