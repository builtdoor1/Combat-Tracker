package combat_tracker.mixin;

import combat_tracker.CombatTrackerClient;
import combat_tracker.detection.ClickTimestamps;
import combat_tracker.detection.InputContext;
import combat_tracker.detection.IntegrityMonitor;
import combat_tracker.detection.SwingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    // ── The keybind window ───────────────────────────────────────────────────
    //
    // Wrapped around the individual call instructions rather than the whole of
    // handleKeybinds. Bracketing the method looks equivalent and is not: the method
    // also calls continueAttack (offset 872) on every tick, so a cheat injecting at
    // the head of continueAttack would run with the window open and could switch
    // slots, attack and use with total amnesty. That turns the check into "are we
    // lexically inside handleKeybinds", which is a region an attacker can get into.
    //
    // Wrapping the invokes instead leaves a window one instruction wide, spanning
    // only the vanilla call itself and whatever it calls. There is nowhere inside it
    // for another mixin to sit. Verified call sites in 1.21.11: setSelectedSlot at
    // 243, startAttack at 714, startUseItem at 736 and 829.

    private static final String START_ATTACK = "Lnet/minecraft/client/Minecraft;startAttack()Z";
    private static final String START_USE = "Lnet/minecraft/client/Minecraft;startUseItem()V";

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = START_ATTACK))
    private void combatTracker$attackKeyStart(CallbackInfo ci) {
        InputContext.enterKeybinds();
    }

    @Inject(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = START_ATTACK, shift = At.Shift.AFTER))
    private void combatTracker$attackKeyEnd(CallbackInfo ci) {
        InputContext.exitKeybinds();
    }

    /** Two call sites; the injector applies to both. */
    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = START_USE))
    private void combatTracker$useKeyStart(CallbackInfo ci) {
        InputContext.enterKeybinds();
    }

    @Inject(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = START_USE, shift = At.Shift.AFTER))
    private void combatTracker$useKeyEnd(CallbackInfo ci) {
        InputContext.exitKeybinds();
    }

    /**
     * {@code setScreen} releases every held key ({@code KeyMapping.releaseAll}).
     * Opening a chest should not read as someone letting go of the attack button by
     * script, so it gets a housekeeping window.
     */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void combatTracker$setScreenStart(Screen screen, CallbackInfo ci) {
        InputContext.enterHousekeeping();
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void combatTracker$setScreenEnd(Screen screen, CallbackInfo ci) {
        InputContext.exitHousekeeping();
    }

    /**
     * {@code startUseItem} has exactly one vanilla caller, {@code handleKeybinds}.
     * Reaching it any other way means something invoked it directly — the shape of
     * an auto-shield-disabler, an auto-totem or a scaffold.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void combatTracker$onStartUseItem(CallbackInfo ci) {
        // Look at the slot here, not just at the tick boundary: a swap and an
        // immediate swap-back around this call would otherwise net to zero.
        IntegrityMonitor.get().onUseItem();
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void combatTracker$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;

        // Same test as startUseItem: the only vanilla caller is handleKeybinds, so
        // an attack from anywhere else was not driven by the attack key.
        IntegrityMonitor.get().onAttack();

        // Claim the click behind this swing before any guard can return early, so a
        // swing vanilla discards still clears the slot instead of leaving the press
        // to be picked up by a later attack.
        CombatTrackerClient.clickNano = ClickTimestamps.claim();

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
